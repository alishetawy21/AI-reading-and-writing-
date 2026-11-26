package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AudioMeterFrame
import com.example.data.model.SupportedLanguages
import com.example.data.remote.CloudSpeechClient
import com.example.data.remote.CloudSttAudio
import com.example.data.remote.CloudSttConfig
import com.example.data.remote.CloudSttRequest
import com.example.data.remote.CloudSttWordInfo
import com.example.data.remote.CloudTtsAudioConfig
import com.example.data.remote.CloudTtsInput
import com.example.data.remote.CloudTtsRequest
import com.example.data.remote.CloudTtsVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

data class RecordedAudioCapture(
    val pcmBytes: ByteArray,
    val wavBytes: ByteArray,
    val durationMs: Long
)

data class CloudSttOutcome(
    val transcript: String,
    val overallConfidence: Double?,
    val wordInfos: List<CloudSttWordInfo>,
    val usedCloudApi: Boolean,
    val statusNote: String
)

class SpeechAudioEngine(context: Context) {
    private val appContext = context.applicationContext

    private val _meterState = MutableStateFlow(AudioMeterFrame())
    val meterState: StateFlow<AudioMeterFrame> = _meterState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsEngineLabel = MutableStateFlow(
        if (hasConfiguredCloudKey()) "Google Cloud TTS Ready" else "Device TTS (Add Cloud key for Neural2)"
    )
    val ttsEngineLabel: StateFlow<String> = _ttsEngineLabel.asStateFlow()

    private var androidTts: TextToSpeech? = null
    private var ttsInitialized = false
    private var mediaPlayer: MediaPlayer? = null
    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private val pcmBuffer = ByteArrayOutputStream()
    private var recordingStartMs: Long = 0L

    init {
        androidTts = TextToSpeech(appContext) { status ->
            ttsInitialized = status == TextToSpeech.SUCCESS
        }
    }

    fun hasConfiguredCloudKey(): Boolean {
        val key = BuildConfig.GOOGLE_CLOUD_API_KEY
        return key.isNotBlank() && key != "MY_GOOGLE_CLOUD_API_KEY"
    }

    suspend fun speakText(
        text: String,
        languageCode: String,
        speakingRate: Float = 1.0f
    ): Result<String> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("No text to pronounce"))
        stopPlayback()
        _isSpeaking.value = true

        if (hasConfiguredCloudKey()) {
            try {
                val langOption = SupportedLanguages.byCode(languageCode)
                val response = withTimeout(20_000L) {
                    CloudSpeechClient.ttsApi.synthesizeSpeech(
                        apiKey = BuildConfig.GOOGLE_CLOUD_API_KEY,
                        request = CloudTtsRequest(
                            input = CloudTtsInput(text = text),
                            voice = CloudTtsVoice(
                                languageCode = languageCode,
                                name = langOption.cloudVoiceName
                            ),
                            audioConfig = CloudTtsAudioConfig(
                                audioEncoding = "MP3",
                                speakingRate = speakingRate.toDouble().coerceIn(0.5, 1.5)
                            )
                        )
                    )
                }
                val base64Audio = response.body()?.audioContent
                if (response.isSuccessful && !base64Audio.isNullOrBlank()) {
                    val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                    playMp3Bytes(audioBytes)
                    val label = "Google Cloud TTS (${langOption.cloudVoiceName})"
                    _ttsEngineLabel.value = label
                    return Result.success(label)
                } else {
                    Log.w(TAG, "Cloud TTS failed (${response.code()}); falling back to Android TTS")
                }
            } catch (e: CancellationException) {
                _isSpeaking.value = false
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud TTS exception; falling back to Android TTS", e)
            }
        }

        return speakWithAndroidTts(text, languageCode, speakingRate)
    }

    private suspend fun playMp3Bytes(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("cloud_tts_", ".mp3", appContext.cacheDir)
        FileOutputStream(tempFile).use { it.write(bytes) }
        withContext(Dispatchers.Main) {
            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener { mp ->
                    mp.release()
                    if (mediaPlayer === mp) mediaPlayer = null
                    _isSpeaking.value = false
                    tempFile.delete()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (mediaPlayer === mp) mediaPlayer = null
                    _isSpeaking.value = false
                    tempFile.delete()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
        }
    }

    private suspend fun speakWithAndroidTts(
        text: String,
        languageCode: String,
        speakingRate: Float
    ): Result<String> = withContext(Dispatchers.Main) {
        val tts = androidTts
        if (tts == null || !ttsInitialized) {
            _isSpeaking.value = false
            return@withContext Result.failure(
                IllegalStateException("Text-to-Speech engine is still initializing. Please try again.")
            )
        }
        val locale = Locale.forLanguageTag(languageCode)
        val langResult = tts.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }
        tts.setSpeechRate(speakingRate.coerceIn(0.5f, 1.5f))
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
        val utteranceId = "lingo_${System.currentTimeMillis()}"
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        val label = if (hasConfiguredCloudKey()) {
            "On-Device TTS (Cloud fallback)"
        } else {
            "On-Device TTS"
        }
        _ttsEngineLabel.value = label
        Result.success(label)
    }

    fun stopPlayback() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        runCatching { androidTts?.stop() }
        _isSpeaking.value = false
    }

    @SuppressLint("MissingPermission")
    suspend fun startRecordingLoop() = withContext(Dispatchers.IO) {
        stopPlayback()
        pcmBuffer.reset()
        val sampleRate = SAMPLE_RATE_HZ
        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufSize * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone AudioRecord failed to initialize.")
        }

        audioRecord = recorder
        recordingStartMs = System.currentTimeMillis()
        val rollingWaveform = MutableList(28) { 0.08f }
        val readBuffer = ShortArray(minBufSize / 2)
        val byteChunk = ByteArray(minBufSize)

        recorder.startRecording()
        _meterState.value = AudioMeterFrame(isRecording = true, waveform = rollingWaveform.toList())

        try {
            while (currentCoroutineContext().isActive && _meterState.value.isRecording) {
                val readShorts = recorder.read(readBuffer, 0, readBuffer.size)
                if (readShorts > 0) {
                    var sumSquares = 0.0
                    for (i in 0 until readShorts) {
                        val s = readBuffer[i]
                        sumSquares += s.toDouble() * s.toDouble()
                        byteChunk[i * 2] = (s.toInt() and 0xFF).toByte()
                        byteChunk[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                    }
                    synchronized(pcmBuffer) {
                        pcmBuffer.write(byteChunk, 0, readShorts * 2)
                    }
                    val rms = sqrt(sumSquares / readShorts).toFloat()
                    val normalized = (rms / 9500f).coerceIn(0.06f, 1f)
                    val db = if (rms > 1f) (20f * log10(rms / 32768f)).coerceIn(-60f, 0f) else -60f
                    rollingWaveform.removeAt(0)
                    rollingWaveform.add(normalized)
                    _meterState.value = AudioMeterFrame(
                        isRecording = true,
                        amplitude = normalized,
                        decibels = db,
                        elapsedMs = System.currentTimeMillis() - recordingStartMs,
                        waveform = rollingWaveform.toList()
                    )
                }
            }
        } finally {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
            }
            if (audioRecord === recorder) audioRecord = null
        }
    }

    fun stopRecordingAndCollect(): RecordedAudioCapture {
        _meterState.value = _meterState.value.copy(isRecording = false)
        runCatching {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
            }
        }
        val pcm = synchronized(pcmBuffer) { pcmBuffer.toByteArray() }
        val durationMs = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(0L)
        val wav = encodePcmAsWav(pcm, SAMPLE_RATE_HZ, 1, 16)
        _meterState.value = AudioMeterFrame(isRecording = false)
        return RecordedAudioCapture(pcmBytes = pcm, wavBytes = wav, durationMs = durationMs)
    }

    suspend fun transcribeWithCloudStt(
        pcmBytes: ByteArray,
        languageCode: String
    ): CloudSttOutcome {
        if (!hasConfiguredCloudKey()) {
            return CloudSttOutcome(
                transcript = "",
                overallConfidence = null,
                wordInfos = emptyList(),
                usedCloudApi = false,
                statusNote = "Using Gemini multimodal audio (add GOOGLE_CLOUD_API_KEY in Secrets for Cloud STT)"
            )
        }
        if (pcmBytes.isEmpty()) {
            return CloudSttOutcome(
                transcript = "",
                overallConfidence = null,
                wordInfos = emptyList(),
                usedCloudApi = false,
                statusNote = "Empty audio capture"
            )
        }
        return try {
            val base64Pcm = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
            val response = withTimeout(25_000L) {
                CloudSpeechClient.sttApi.recognizeSpeech(
                    apiKey = BuildConfig.GOOGLE_CLOUD_API_KEY,
                    request = CloudSttRequest(
                        config = CloudSttConfig(
                            encoding = "LINEAR16",
                            sampleRateHertz = SAMPLE_RATE_HZ,
                            languageCode = languageCode
                        ),
                        audio = CloudSttAudio(content = base64Pcm)
                    )
                )
            }
            val alt = response.body()?.results?.firstOrNull()?.alternatives?.firstOrNull()
            if (response.isSuccessful && alt != null) {
                CloudSttOutcome(
                    transcript = alt.transcript.orEmpty(),
                    overallConfidence = alt.confidence,
                    wordInfos = alt.words.orEmpty(),
                    usedCloudApi = true,
                    statusNote = "Google Cloud STT + Gemini"
                )
            } else {
                Log.w(TAG, "Cloud STT returned ${response.code()}; using Gemini audio directly")
                CloudSttOutcome(
                    transcript = "",
                    overallConfidence = null,
                    wordInfos = emptyList(),
                    usedCloudApi = false,
                    statusNote = "Gemini Audio (Cloud STT HTTP ${response.code()})"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cloud STT error; falling back to Gemini audio directly", e)
            CloudSttOutcome(
                transcript = "",
                overallConfidence = null,
                wordInfos = emptyList(),
                usedCloudApi = false,
                statusNote = "Gemini Audio (Cloud STT fallback)"
            )
        }
    }

    fun release() {
        stopPlayback()
        recordJob?.cancel()
        runCatching { audioRecord?.release() }
        runCatching { androidTts?.shutdown() }
        androidTts = null
    }

    companion object {
        private const val TAG = "SpeechAudioEngine"
        const val SAMPLE_RATE_HZ = 16000
    }
}

fun encodePcmAsWav(
    pcmData: ByteArray,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int
): ByteArray {
    val totalDataLen = pcmData.size + 36
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val header = ByteArray(44)

    header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xff).toByte()
    header[5] = ((totalDataLen shr 8) and 0xff).toByte()
    header[6] = ((totalDataLen shr 16) and 0xff).toByte()
    header[7] = ((totalDataLen shr 24) and 0xff).toByte()
    header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
    header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
    header[20] = 1; header[21] = 0
    header[22] = channels.toByte(); header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = ((sampleRate shr 8) and 0xff).toByte()
    header[26] = ((sampleRate shr 16) and 0xff).toByte()
    header[27] = ((sampleRate shr 24) and 0xff).toByte()
    header[28] = (byteRate and 0xff).toByte()
    header[29] = ((byteRate shr 8) and 0xff).toByte()
    header[30] = ((byteRate shr 16) and 0xff).toByte()
    header[31] = ((byteRate shr 24) and 0xff).toByte()
    header[32] = blockAlign.toByte(); header[33] = 0
    header[34] = bitsPerSample.toByte(); header[35] = 0
    header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
    header[40] = (pcmData.size and 0xff).toByte()
    header[41] = ((pcmData.size shr 8) and 0xff).toByte()
    header[42] = ((pcmData.size shr 16) and 0xff).toByte()
    header[43] = ((pcmData.size shr 24) and 0xff).toByte()

    return header + pcmData
}
