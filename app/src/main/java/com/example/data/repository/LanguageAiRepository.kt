package com.example.data.repository

import android.util.Log
import com.example.audio.CloudSttOutcome
import com.example.data.model.PronunciationAnalysisResult
import com.example.data.model.SupportedLanguages
import com.example.data.model.TranslationBreakdown
import com.example.data.model.WordPronunciationFeedback
import com.example.data.model.WordStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class LanguageAiRepository {

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "translatedText" to Schema.string(description = "Natural translation in the target language"),
            "ipaPhonetics" to Schema.string(description = "IPA phonetic transcription of the target phrase"),
            "syllableStressGuide" to Schema.string(description = "Syllable-by-syllable pronunciation guide with stressed syllables capitalized"),
            "accentTip" to Schema.string(description = "Primary accent and articulation tip for sounding natural"),
            "naturalnessNote" to Schema.string(description = "Brief context or grammar note for learners")
        )
    )

    private val pronunciationSchema = Schema.obj(
        properties = mapOf(
            "recognizedTranscript" to Schema.string(description = "Exact words heard from the speaker"),
            "overallScore" to Schema.integer(description = "Overall pronunciation accuracy from 0 to 100"),
            "accentScore" to Schema.integer(description = "Target accent closeness from 0 to 100"),
            "fluencyScore" to Schema.integer(description = "Fluency and pacing smoothness from 0 to 100"),
            "intonationScore" to Schema.integer(description = "Stress and intonation melody from 0 to 100"),
            "coachSummary" to Schema.string(description = "Encouraging, specific 2-sentence pronunciation coaching summary"),
            "accentCorrections" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 concrete accent and mouth/tongue placement corrections"
            ),
            "wordFeedback" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "word" to Schema.string(description = "Target word from the phrase"),
                        "targetIpa" to Schema.string(description = "Target IPA for this word"),
                        "heardAs" to Schema.string(description = "How the speaker pronounced it or approximate heard sound"),
                        "status" to Schema.enumeration(
                            values = listOf("accurate", "needs_work", "missed"),
                            description = "Accuracy status for this word"
                        ),
                        "confidencePercent" to Schema.integer(description = "Word clarity score from 0 to 100"),
                        "articulatoryTip" to Schema.string(description = "Short mouth, lip, or tongue placement tip for this word")
                    )
                ),
                description = "Per-word pronunciation analysis in phrase order"
            )
        )
    )

    private val translationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
                temperature = 0.2f
            }
        )
    }

    private val pronunciationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = pronunciationSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun translateAndAnalyze(
        sourceText: String,
        sourceLanguageCode: String,
        targetLanguageCode: String
    ): Result<TranslationBreakdown> {
        val trimmed = sourceText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter or paste text to read and translate."))
        }
        val sourceLang = SupportedLanguages.byCode(sourceLanguageCode)
        val targetLang = SupportedLanguages.byCode(targetLanguageCode)

        val prompt = buildString {
            appendLine("You are an expert phonetician and language tutor.")
            appendLine("Translate the following text from ${sourceLang.displayName} ($sourceLanguageCode) into ${targetLang.displayName} (${targetLang.accentLabel}, $targetLanguageCode).")
            appendLine("If the source and target language are the same, keep the phrase in ${targetLang.displayName} and provide a polished natural version or clear reading breakdown.")
            appendLine("Provide the IPA transcription and syllable stress guide specifically for the TARGET text (${targetLang.displayName} - ${targetLang.accentLabel}).")
            appendLine("Text: \"$trimmed\"")
        }

        return try {
            val response = withTimeout(60_000L) {
                translationModel.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Translation model returned an empty response."))
            val json = JSONObject(rawJson)
            Result.success(
                TranslationBreakdown(
                    sourceText = trimmed,
                    translatedText = json.optString("translatedText", trimmed),
                    sourceLanguage = sourceLanguageCode,
                    targetLanguage = targetLanguageCode,
                    ipaPhonetics = json.optString("ipaPhonetics", ""),
                    syllableStressGuide = json.optString("syllableStressGuide", ""),
                    accentTip = json.optString("accentTip", ""),
                    naturalnessNote = json.optString("naturalnessNote", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Translation timed out", e)
            Result.failure(Exception("Translation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzePronunciation(
        targetPhrase: String,
        targetLanguageCode: String,
        wavBytes: ByteArray,
        cloudStt: CloudSttOutcome
    ): Result<PronunciationAnalysisResult> {
        val cleanTarget = targetPhrase.trim()
        if (cleanTarget.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter or select a target phrase before recording."))
        }
        if (wavBytes.size <= 44) {
            return Result.failure(IllegalArgumentException("No audio was captured. Speak clearly into your microphone and try again."))
        }

        val targetLang = SupportedLanguages.byCode(targetLanguageCode)
        val cloudHint = buildString {
            if (cloudStt.transcript.isNotBlank()) {
                appendLine("Google Cloud Speech-to-Text transcript: \"${cloudStt.transcript}\"")
                cloudStt.overallConfidence?.let {
                    appendLine("Cloud STT overall confidence: ${(it * 100).toInt()}%")
                }
                if (cloudStt.wordInfos.isNotEmpty()) {
                    val wordSummary = cloudStt.wordInfos.joinToString(", ") { w ->
                        "${w.word.orEmpty()}:${((w.confidence ?: 0.0) * 100).toInt()}%"
                    }
                    appendLine("Cloud STT per-word confidence: $wordSummary")
                }
            } else {
                appendLine("Analyze directly from the attached user audio recording.")
            }
        }

        val prompt = buildString {
            appendLine("You are a real-time pronunciation and accent coach for ${targetLang.displayName} (${targetLang.accentLabel}, $targetLanguageCode).")
            appendLine("Listen carefully to the attached audio recording of the learner attempting to say:")
            appendLine("Target phrase: \"$cleanTarget\"")
            appendLine(cloudHint)
            appendLine("Evaluate actual pronunciation accuracy, vowel/consonant articulation, syllable stress, and rhythm against a ${targetLang.accentLabel} speaker.")
            appendLine("Be honest and accurate: if words were mispronounced, skipped, or spoken in a different language or silence, reflect that in the scores and wordFeedback.")
            appendLine("Return wordFeedback for every word in the target phrase in order.")
        }

        return try {
            val response = withTimeout(60_000L) {
                pronunciationModel.generateContent(
                    content {
                        inlineData(wavBytes, "audio/wav")
                        text(prompt)
                    }
                )
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Pronunciation analysis returned an empty response."))
            val json = JSONObject(rawJson)

            val correctionsJson = json.optJSONArray("accentCorrections")
            val corrections = buildList {
                if (correctionsJson != null) {
                    for (i in 0 until correctionsJson.length()) {
                        val item = correctionsJson.optString(i).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }

            val wordsJson = json.optJSONArray("wordFeedback")
            val wordFeedback = buildList {
                if (wordsJson != null) {
                    for (i in 0 until wordsJson.length()) {
                        val obj = wordsJson.optJSONObject(i) ?: continue
                        add(
                            WordPronunciationFeedback(
                                word = obj.optString("word", ""),
                                targetIpa = obj.optString("targetIpa", ""),
                                heardAs = obj.optString("heardAs", ""),
                                status = WordStatus.fromRaw(obj.optString("status", "needs_work")),
                                confidencePercent = obj.optInt("confidencePercent", 70).coerceIn(0, 100),
                                articulatoryTip = obj.optString("articulatoryTip", "")
                            )
                        )
                    }
                }
            }

            val engineLabel = if (cloudStt.usedCloudApi) {
                "Google Cloud STT + Gemini"
            } else {
                "Gemini Multimodal Audio"
            }

            Result.success(
                PronunciationAnalysisResult(
                    targetPhrase = cleanTarget,
                    targetLanguage = targetLanguageCode,
                    recognizedTranscript = json.optString("recognizedTranscript", cloudStt.transcript),
                    overallScore = json.optInt("overallScore", 0).coerceIn(0, 100),
                    accentScore = json.optInt("accentScore", 0).coerceIn(0, 100),
                    fluencyScore = json.optInt("fluencyScore", 0).coerceIn(0, 100),
                    intonationScore = json.optInt("intonationScore", 0).coerceIn(0, 100),
                    coachSummary = json.optString("coachSummary", ""),
                    accentCorrections = corrections,
                    wordFeedback = wordFeedback,
                    speechEngine = engineLabel
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Pronunciation analysis timed out", e)
            Result.failure(Exception("Speech analysis timed out after 60 seconds. Please try a shorter phrase."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Pronunciation analysis failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "LanguageAiRepository"
    }
}
