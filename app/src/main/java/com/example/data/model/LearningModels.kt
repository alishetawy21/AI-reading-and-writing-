package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class LanguageOption(
    val code: String,
    val displayName: String,
    val accentLabel: String,
    val shortBadge: String,
    val cloudVoiceName: String,
    val sampleBookExcerpt: String
)

object SupportedLanguages {
    val all = listOf(
        LanguageOption(
            code = "en-US",
            displayName = "English (US)",
            accentLabel = "General American",
            shortBadge = "EN-US",
            cloudVoiceName = "en-US-Neural2-J",
            sampleBookExcerpt = "Through narrow alleys lined with warm lanterns, the traveler paused to listen to the cadence of evening conversation."
        ),
        LanguageOption(
            code = "en-GB",
            displayName = "English (UK)",
            accentLabel = "British RP",
            shortBadge = "EN-GB",
            cloudVoiceName = "en-GB-Neural2-B",
            sampleBookExcerpt = "She opened the leather-bound chronicle and read the margin notes aloud beneath the quiet library skylights."
        ),
        LanguageOption(
            code = "es-ES",
            displayName = "Spanish",
            accentLabel = "Castilian",
            shortBadge = "ES",
            cloudVoiceName = "es-ES-Neural2-A",
            sampleBookExcerpt = "Cada palabra pronunciada con calma abre una puerta nueva hacia la comprension y la confianza."
        ),
        LanguageOption(
            code = "fr-FR",
            displayName = "French",
            accentLabel = "Parisian",
            shortBadge = "FR",
            cloudVoiceName = "fr-FR-Neural2-A",
            sampleBookExcerpt = "Au petit matin, le lecteur feuillette les pages encore fraiches tout en ecoutant la melodie de chaque syllabe."
        ),
        LanguageOption(
            code = "de-DE",
            displayName = "German",
            accentLabel = "Standard German",
            shortBadge = "DE",
            cloudVoiceName = "de-DE-Neural2-B",
            sampleBookExcerpt = "Mit deutlicher Betonung und ruhigem Rhythmus klingt selbst ein langer Satz klar und naturlich."
        ),
        LanguageOption(
            code = "ja-JP",
            displayName = "Japanese",
            accentLabel = "Tokyo Standard",
            shortBadge = "JA",
            cloudVoiceName = "ja-JP-Neural2-B",
            sampleBookExcerpt = "静かな朝に本を開き、美しい発音と自然なイントネーションを声に出して練習しましょう。"
        ),
        LanguageOption(
            code = "ar-XA",
            displayName = "Arabic",
            accentLabel = "Modern Standard",
            shortBadge = "AR",
            cloudVoiceName = "ar-XA-Wavenet-B",
            sampleBookExcerpt = "القراءة بصوت مرتفع تساعد المتعلم على اتقان مخارج الحروف ونبرة الجملة بوضوح."
        ),
        LanguageOption(
            code = "cmn-CN",
            displayName = "Mandarin",
            accentLabel = "Standard Putonghua",
            shortBadge = "ZH",
            cloudVoiceName = "cmn-CN-Wavenet-A",
            sampleBookExcerpt = "每天大声朗读几分钟，可以明显改善声调准确度和口语流利度。"
        )
    )

    fun byCode(code: String): LanguageOption =
        all.firstOrNull { it.code == code } ?: all.first()
}

@Keep
@IgnoreExtraProperties
data class SavedPhrase(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sourceText: String = "",
    val translatedText: String = "",
    val sourceLanguage: String = "en-US",
    val targetLanguage: String = "es-ES",
    val ipa: String = "",
    val accentTip: String = "",
    val lastScore: Long = 0L,
    val practiceCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PracticeSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val phraseText: String = "",
    val targetLanguage: String = "en-US",
    val recognizedTranscript: String = "",
    val overallScore: Long = 0L,
    val accentScore: Long = 0L,
    val fluencyScore: Long = 0L,
    val intonationScore: Long = 0L,
    val coachSummary: String = "",
    val speechEngine: String = "Gemini Audio",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class TranslationBreakdown(
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val ipaPhonetics: String,
    val syllableStressGuide: String,
    val accentTip: String,
    val naturalnessNote: String
)

enum class WordStatus {
    ACCURATE,
    NEEDS_WORK,
    MISSED;

    companion object {
        fun fromRaw(raw: String): WordStatus {
            return when (raw.trim().lowercase()) {
                "accurate", "correct", "good" -> ACCURATE
                "missed", "omitted", "incorrect" -> MISSED
                else -> NEEDS_WORK
            }
        }
    }
}

data class WordPronunciationFeedback(
    val word: String,
    val targetIpa: String,
    val heardAs: String,
    val status: WordStatus,
    val confidencePercent: Int,
    val articulatoryTip: String
)

data class PronunciationAnalysisResult(
    val targetPhrase: String,
    val targetLanguage: String,
    val recognizedTranscript: String,
    val overallScore: Int,
    val accentScore: Int,
    val fluencyScore: Int,
    val intonationScore: Int,
    val coachSummary: String,
    val accentCorrections: List<String>,
    val wordFeedback: List<WordPronunciationFeedback>,
    val speechEngine: String
)

data class AudioMeterFrame(
    val isRecording: Boolean = false,
    val amplitude: Float = 0f,
    val decibels: Float = -60f,
    val elapsedMs: Long = 0L,
    val waveform: List<Float> = List(28) { 0.08f }
)
