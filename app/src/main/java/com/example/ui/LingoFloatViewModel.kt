package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.SpeechAudioEngine
import com.example.data.model.AudioMeterFrame
import com.example.data.model.PracticeSession
import com.example.data.model.PronunciationAnalysisResult
import com.example.data.model.SavedPhrase
import com.example.data.model.SupportedLanguages
import com.example.data.model.TranslationBreakdown
import com.example.data.model.UiState
import com.example.data.model.WordPronunciationFeedback
import com.example.data.repository.LanguageAiRepository
import com.example.data.repository.LearningRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TranslationUiState {
    data object Idle : TranslationUiState
    data object Loading : TranslationUiState
    data class Success(val breakdown: TranslationBreakdown) : TranslationUiState
    data class Error(val message: String) : TranslationUiState
}

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data object Recording : AnalysisUiState
    data object Analyzing : AnalysisUiState
    data class Success(val result: PronunciationAnalysisResult) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

data class StudioUiState(
    val sourceLanguageCode: String = "en-US",
    val targetLanguageCode: String = "es-ES",
    val inputText: String = SupportedLanguages.byCode("en-US").sampleBookExcerpt,
    val translationState: TranslationUiState = TranslationUiState.Idle,
    val analysisState: AnalysisUiState = AnalysisUiState.Idle,
    val selectedWordFeedback: WordPronunciationFeedback? = null,
    val speakingRate: Float = 1.0f,
    val isInAppFloatingOpen: Boolean = false,
    val floatingMiniInput: String = "",
    val floatingMiniResult: TranslationBreakdown? = null,
    val isFloatingMiniLoading: Boolean = false,
    val activePhraseId: String? = null,
    val activePhrasePracticeCount: Long = 0L,
    val statusBanner: String? = null
)

class LingoFloatViewModel(
    private val learningRepository: LearningRepository,
    private val aiRepository: LanguageAiRepository,
    private val audioEngine: SpeechAudioEngine,
    private val currentUserId: String
) : ViewModel() {

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    val audioMeter: StateFlow<AudioMeterFrame> = audioEngine.meterState
    val isSpeaking: StateFlow<Boolean> = audioEngine.isSpeaking
    val ttsEngineLabel: StateFlow<String> = audioEngine.ttsEngineLabel

    val hasCloudApiKey: Boolean =
        BuildConfig.GOOGLE_CLOUD_API_KEY.isNotBlank() &&
            BuildConfig.GOOGLE_CLOUD_API_KEY != "MY_GOOGLE_CLOUD_API_KEY"

    val phrasesState: StateFlow<UiState<List<SavedPhrase>>> =
        learningRepository.observePhrases(currentUserId)
            .map<List<SavedPhrase>, UiState<List<SavedPhrase>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing phrases", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load saved phrases"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val sessionsState: StateFlow<UiState<List<PracticeSession>>> =
        learningRepository.observePracticeSessions(currentUserId)
            .map<List<PracticeSession>, UiState<List<PracticeSession>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing practice sessions", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load practice sessions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private var recordingJob: Job? = null

    fun setSourceLanguage(code: String) {
        _studioState.update { it.copy(sourceLanguageCode = code) }
    }

    fun setTargetLanguage(code: String) {
        _studioState.update { it.copy(targetLanguageCode = code) }
    }

    fun swapLanguages() {
        _studioState.update { state ->
            state.copy(
                sourceLanguageCode = state.targetLanguageCode,
                targetLanguageCode = state.sourceLanguageCode
            )
        }
    }

    fun updateInputText(text: String) {
        _studioState.update {
            it.copy(
                inputText = text,
                activePhraseId = null,
                activePhrasePracticeCount = 0L
            )
        }
    }

    fun loadSamplePassage() {
        val source = SupportedLanguages.byCode(_studioState.value.sourceLanguageCode)
        _studioState.update {
            it.copy(
                inputText = source.sampleBookExcerpt,
                translationState = TranslationUiState.Idle,
                analysisState = AnalysisUiState.Idle,
                selectedWordFeedback = null,
                activePhraseId = null,
                activePhrasePracticeCount = 0L
            )
        }
    }

    fun loadSavedPhraseIntoStudio(phrase: SavedPhrase) {
        _studioState.update {
            it.copy(
                sourceLanguageCode = phrase.sourceLanguage,
                targetLanguageCode = phrase.targetLanguage,
                inputText = phrase.sourceText,
                translationState = TranslationUiState.Success(
                    TranslationBreakdown(
                        sourceText = phrase.sourceText,
                        translatedText = phrase.translatedText,
                        sourceLanguage = phrase.sourceLanguage,
                        targetLanguage = phrase.targetLanguage,
                        ipaPhonetics = phrase.ipa,
                        syllableStressGuide = "",
                        accentTip = phrase.accentTip,
                        naturalnessNote = ""
                    )
                ),
                analysisState = AnalysisUiState.Idle,
                selectedWordFeedback = null,
                activePhraseId = phrase.id,
                activePhrasePracticeCount = phrase.practiceCount
            )
        }
    }

    fun translateCurrentText() {
        val state = _studioState.value
        val text = state.inputText.trim()
        if (text.isEmpty()) {
            _studioState.update {
                it.copy(translationState = TranslationUiState.Error("Enter or paste text first."))
            }
            return
        }
        viewModelScope.launch {
            _studioState.update { it.copy(translationState = TranslationUiState.Loading) }
            val result = aiRepository.translateAndAnalyze(
                sourceText = text,
                sourceLanguageCode = state.sourceLanguageCode,
                targetLanguageCode = state.targetLanguageCode
            )
            result.onSuccess { breakdown ->
                _studioState.update { it.copy(translationState = TranslationUiState.Success(breakdown)) }
            }.onFailure { err ->
                _studioState.update {
                    it.copy(
                        translationState = TranslationUiState.Error(
                            err.localizedMessage ?: "Translation failed."
                        )
                    )
                }
            }
        }
    }

    fun pronounceTargetPhrase(rate: Float) {
        val state = _studioState.value
        val translation = (state.translationState as? TranslationUiState.Success)?.breakdown
        val textToSpeak = translation?.translatedText?.takeIf { it.isNotBlank() } ?: state.inputText.trim()
        val langCode = if (translation != null) state.targetLanguageCode else state.sourceLanguageCode
        _studioState.update { it.copy(speakingRate = rate) }
        viewModelScope.launch {
            audioEngine.speakText(textToSpeak, langCode, rate).onFailure { err ->
                _studioState.update {
                    it.copy(statusBanner = err.localizedMessage ?: "Could not play audio.")
                }
            }
        }
    }

    fun pronounceSingleWord(word: String, rate: Float = 0.72f) {
        val state = _studioState.value
        val hasTranslation = state.translationState is TranslationUiState.Success
        val langCode = if (hasTranslation) state.targetLanguageCode else state.sourceLanguageCode
        pronounceTextInLanguage(word, langCode, rate)
    }

    fun pronounceTextInLanguage(text: String, languageCode: String, rate: Float = 0.95f) {
        viewModelScope.launch {
            audioEngine.speakText(text, languageCode, rate).onFailure { err ->
                _studioState.update {
                    it.copy(statusBanner = err.localizedMessage ?: "Could not play audio.")
                }
            }
        }
    }

    fun stopSpeaking() {
        audioEngine.stopPlayback()
    }

    fun startSpeechRecording() {
        val state = _studioState.value
        val targetPhrase = currentPracticePhrase()
        if (targetPhrase.isBlank()) {
            _studioState.update {
                it.copy(analysisState = AnalysisUiState.Error("Enter or translate a phrase before recording."))
            }
            return
        }
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            _studioState.update {
                it.copy(
                    analysisState = AnalysisUiState.Recording,
                    selectedWordFeedback = null
                )
            }
            try {
                audioEngine.startRecordingLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                _studioState.update {
                    it.copy(
                        analysisState = AnalysisUiState.Error(
                            e.localizedMessage ?: "Microphone recording failed."
                        )
                    )
                }
            }
        }
    }

    fun stopAndAnalyzeSpeech() {
        val capture = audioEngine.stopRecordingAndCollect()
        recordingJob?.cancel()
        recordingJob = null

        val state = _studioState.value
        val targetPhrase = currentPracticePhrase()
        val targetLang = currentPracticeLanguage()

        if (capture.pcmBytes.size < 1600) {
            _studioState.update {
                it.copy(
                    analysisState = AnalysisUiState.Error(
                        "Recording was too short. Hold or speak for at least 1 second."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _studioState.update { it.copy(analysisState = AnalysisUiState.Analyzing) }
            val cloudStt = audioEngine.transcribeWithCloudStt(capture.pcmBytes, targetLang)
            val analysisRes = aiRepository.analyzePronunciation(
                targetPhrase = targetPhrase,
                targetLanguageCode = targetLang,
                wavBytes = capture.wavBytes,
                cloudStt = cloudStt
            )
            analysisRes.onSuccess { analysis ->
                _studioState.update {
                    it.copy(
                        analysisState = AnalysisUiState.Success(analysis),
                        selectedWordFeedback = analysis.wordFeedback.firstOrNull { w ->
                            w.status != com.example.data.model.WordStatus.ACCURATE
                        } ?: analysis.wordFeedback.firstOrNull()
                    )
                }
                // Persist practice session to Firestore
                learningRepository.recordPracticeSession(analysis).onFailure { err ->
                    Log.w(TAG, "Failed to persist practice session", err)
                }
                // Update saved phrase mastery if practicing an existing saved phrase
                state.activePhraseId?.let { phraseId ->
                    learningRepository.updatePhraseScore(
                        phraseId = phraseId,
                        newScore = analysis.overallScore,
                        newPracticeCount = state.activePhrasePracticeCount + 1L
                    )
                }
            }.onFailure { err ->
                _studioState.update {
                    it.copy(
                        analysisState = AnalysisUiState.Error(
                            err.localizedMessage ?: "Speech analysis failed."
                        )
                    )
                }
            }
        }
    }

    fun selectWordFeedback(word: WordPronunciationFeedback) {
        _studioState.update { it.copy(selectedWordFeedback = word) }
    }

    fun saveCurrentToPhrasebook() {
        val state = _studioState.value
        val breakdown = (state.translationState as? TranslationUiState.Success)?.breakdown
            ?: TranslationBreakdown(
                sourceText = state.inputText.trim(),
                translatedText = state.inputText.trim(),
                sourceLanguage = state.sourceLanguageCode,
                targetLanguage = state.targetLanguageCode,
                ipaPhonetics = "",
                syllableStressGuide = "",
                accentTip = "",
                naturalnessNote = ""
            )
        val score = (state.analysisState as? AnalysisUiState.Success)?.result?.overallScore ?: 0
        viewModelScope.launch {
            learningRepository.savePhrase(breakdown, initialScore = score)
                .onSuccess { newId ->
                    _studioState.update {
                        it.copy(
                            activePhraseId = newId,
                            activePhrasePracticeCount = if (score > 0) 1L else 0L,
                            statusBanner = "Saved to your Phrasebook"
                        )
                    }
                }
                .onFailure { err ->
                    _studioState.update {
                        it.copy(statusBanner = err.localizedMessage ?: "Could not save phrase.")
                    }
                }
        }
    }

    fun deleteSavedPhrase(phraseId: String) {
        viewModelScope.launch {
            learningRepository.deletePhrase(phraseId).onFailure { err ->
                _studioState.update {
                    it.copy(statusBanner = err.localizedMessage ?: "Failed to delete phrase.")
                }
            }
        }
    }

    fun deletePracticeSession(sessionId: String) {
        viewModelScope.launch {
            learningRepository.deletePracticeSession(sessionId).onFailure { err ->
                _studioState.update {
                    it.copy(statusBanner = err.localizedMessage ?: "Failed to delete session.")
                }
            }
        }
    }

    // ---- In-App Floating Mini-Card ----

    fun setInAppFloatingOpen(open: Boolean) {
        _studioState.update { it.copy(isInAppFloatingOpen = open) }
    }

    fun updateFloatingMiniInput(text: String) {
        _studioState.update { it.copy(floatingMiniInput = text) }
    }

    fun translateFloatingMini() {
        val state = _studioState.value
        val text = state.floatingMiniInput.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _studioState.update { it.copy(isFloatingMiniLoading = true) }
            aiRepository.translateAndAnalyze(
                sourceText = text,
                sourceLanguageCode = state.sourceLanguageCode,
                targetLanguageCode = state.targetLanguageCode
            ).onSuccess { breakdown ->
                _studioState.update {
                    it.copy(
                        floatingMiniResult = breakdown,
                        isFloatingMiniLoading = false
                    )
                }
            }.onFailure { err ->
                _studioState.update {
                    it.copy(
                        isFloatingMiniLoading = false,
                        statusBanner = err.localizedMessage ?: "Quick translate failed."
                    )
                }
            }
        }
    }

    fun speakFloatingMini() {
        val state = _studioState.value
        val result = state.floatingMiniResult
        val text = result?.translatedText?.takeIf { it.isNotBlank() } ?: state.floatingMiniInput.trim()
        val lang = if (result != null) state.targetLanguageCode else state.sourceLanguageCode
        if (text.isBlank()) return
        viewModelScope.launch {
            audioEngine.speakText(text, lang, 0.95f)
        }
    }

    fun sendFloatingMiniToStudio() {
        val state = _studioState.value
        val miniResult = state.floatingMiniResult
        _studioState.update {
            it.copy(
                inputText = state.floatingMiniInput.ifBlank { it.inputText },
                translationState = miniResult?.let { b -> TranslationUiState.Success(b) } ?: it.translationState,
                isInAppFloatingOpen = false
            )
        }
    }

    fun clearStatusBanner() {
        _studioState.update { it.copy(statusBanner = null) }
    }

    private fun currentPracticePhrase(): String {
        val state = _studioState.value
        return (state.translationState as? TranslationUiState.Success)
            ?.breakdown?.translatedText?.takeIf { it.isNotBlank() }
            ?: state.inputText.trim()
    }

    private fun currentPracticeLanguage(): String {
        val state = _studioState.value
        return if (state.translationState is TranslationUiState.Success) {
            state.targetLanguageCode
        } else {
            state.sourceLanguageCode
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        audioEngine.release()
    }

    companion object {
        private const val TAG = "LingoFloatVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
