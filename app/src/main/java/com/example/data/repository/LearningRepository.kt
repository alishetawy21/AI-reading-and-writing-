package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.PracticeSession
import com.example.data.model.PronunciationAnalysisResult
import com.example.data.model.SavedPhrase
import com.example.data.model.TranslationBreakdown
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class LearningRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observePhrases(userId: String): Flow<List<SavedPhrase>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/phrases"
        emitAll(
            db.collection("users").document(uid).collection("phrases")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedPhrase::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePracticeSessions(userId: String): Flow<List<PracticeSession>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$uid/practiceSessions"
        emitAll(
            db.collection("users").document(uid).collection("practiceSessions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PracticeSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun savePhrase(
        breakdown: TranslationBreakdown,
        initialScore: Int = 0
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/phrases"
        return try {
            val uid = requireUserId()
            val cleanSource = breakdown.sourceText.trim().take(2000)
            val cleanTranslated = breakdown.translatedText.trim().take(2000)
            require(cleanSource.isNotEmpty() && cleanTranslated.isNotEmpty()) {
                "Source and translated text cannot be empty."
            }
            val docRef = db.collection("users").document(uid).collection("phrases").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "sourceText" to cleanSource,
                "translatedText" to cleanTranslated,
                "sourceLanguage" to breakdown.sourceLanguage.take(30),
                "targetLanguage" to breakdown.targetLanguage.take(30),
                "ipa" to breakdown.ipaPhonetics.take(1000),
                "accentTip" to breakdown.accentTip.take(1000),
                "lastScore" to initialScore.coerceIn(0, 100).toLong(),
                "practiceCount" to if (initialScore > 0) 1L else 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePhraseScore(
        phraseId: String,
        newScore: Int,
        newPracticeCount: Long
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/phrases/$phraseId"
        return try {
            val uid = requireUserId()
            require(phraseId.isNotBlank()) { "Phrase ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("phrases").document(phraseId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "lastScore" to newScore.coerceIn(0, 100).toLong(),
                    "practiceCount" to newPracticeCount.coerceIn(0L, 100000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePhrase(phraseId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/phrases/$phraseId"
        return try {
            val uid = requireUserId()
            require(phraseId.isNotBlank()) { "Phrase ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("phrases").document(phraseId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordPracticeSession(analysis: PronunciationAnalysisResult): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/practiceSessions"
        return try {
            val uid = requireUserId()
            val cleanPhrase = analysis.targetPhrase.trim().take(2000)
            require(cleanPhrase.isNotEmpty()) { "Practiced phrase cannot be empty" }
            val docRef = db.collection("users").document(uid).collection("practiceSessions").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "phraseText" to cleanPhrase,
                "targetLanguage" to analysis.targetLanguage.take(30),
                "recognizedTranscript" to analysis.recognizedTranscript.take(2000),
                "overallScore" to analysis.overallScore.coerceIn(0, 100).toLong(),
                "accentScore" to analysis.accentScore.coerceIn(0, 100).toLong(),
                "fluencyScore" to analysis.fluencyScore.coerceIn(0, 100).toLong(),
                "intonationScore" to analysis.intonationScore.coerceIn(0, 100).toLong(),
                "coachSummary" to analysis.coachSummary.take(2000),
                "speechEngine" to analysis.speechEngine.ifBlank { "Gemini Audio" }.take(60),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePracticeSession(sessionId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/practiceSessions/$sessionId"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank()) { "Session ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("practiceSessions").document(sessionId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
