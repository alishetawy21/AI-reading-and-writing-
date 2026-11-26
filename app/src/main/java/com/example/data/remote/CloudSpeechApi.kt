package com.example.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ---- Google Cloud Text-to-Speech v1 DTOs ----

@JsonClass(generateAdapter = true)
data class CloudTtsRequest(
    val input: CloudTtsInput,
    val voice: CloudTtsVoice,
    val audioConfig: CloudTtsAudioConfig
)

@JsonClass(generateAdapter = true)
data class CloudTtsInput(
    val text: String
)

@JsonClass(generateAdapter = true)
data class CloudTtsVoice(
    val languageCode: String,
    val name: String? = null,
    val ssmlGender: String = "NEUTRAL"
)

@JsonClass(generateAdapter = true)
data class CloudTtsAudioConfig(
    val audioEncoding: String = "MP3",
    val speakingRate: Double = 1.0,
    val pitch: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class CloudTtsResponse(
    val audioContent: String? = null
)

interface CloudTextToSpeechApi {
    @POST("v1/text:synthesize")
    suspend fun synthesizeSpeech(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Body request: CloudTtsRequest
    ): Response<CloudTtsResponse>
}

// ---- Google Cloud Speech-to-Text v1 DTOs ----

@JsonClass(generateAdapter = true)
data class CloudSttRequest(
    val config: CloudSttConfig,
    val audio: CloudSttAudio
)

@JsonClass(generateAdapter = true)
data class CloudSttConfig(
    val encoding: String = "LINEAR16",
    val sampleRateHertz: Int = 16000,
    val languageCode: String,
    val enableAutomaticPunctuation: Boolean = true,
    val enableWordConfidence: Boolean = true,
    val enableWordTimeOffsets: Boolean = true,
    val model: String = "latest_short"
)

@JsonClass(generateAdapter = true)
data class CloudSttAudio(
    val content: String
)

@JsonClass(generateAdapter = true)
data class CloudSttResponse(
    val results: List<CloudSttResult>? = null
)

@JsonClass(generateAdapter = true)
data class CloudSttResult(
    val alternatives: List<CloudSttAlternative>? = null
)

@JsonClass(generateAdapter = true)
data class CloudSttAlternative(
    val transcript: String? = null,
    val confidence: Double? = null,
    val words: List<CloudSttWordInfo>? = null
)

@JsonClass(generateAdapter = true)
data class CloudSttWordInfo(
    val word: String? = null,
    val confidence: Double? = null,
    val startTime: String? = null,
    val endTime: String? = null
)

interface CloudSpeechToTextApi {
    @POST("v1/speech:recognize")
    suspend fun recognizeSpeech(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Body request: CloudSttRequest
    ): Response<CloudSttResponse>
}

object CloudSpeechClient {
    private val okHttpClient: OkHttpClient by lazy {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .build()
    }

    val ttsApi: CloudTextToSpeechApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://texttospeech.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(CloudTextToSpeechApi::class.java)
    }

    val sttApi: CloudSpeechToTextApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://speech.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(CloudSpeechToTextApi::class.java)
    }
}
