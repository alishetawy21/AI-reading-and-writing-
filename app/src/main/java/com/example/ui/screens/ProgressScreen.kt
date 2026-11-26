package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PracticeSession
import com.example.data.model.SupportedLanguages
import com.example.data.model.UiState
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMastery
import com.example.ui.theme.OnEmeraldContainer
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ProgressScreen(
    sessionsState: UiState<List<PracticeSession>>,
    userEmail: String?,
    hasCloudApiKey: Boolean,
    onDeleteSession: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Speech Progress & Cloud Engine",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                EngineAndAccountCard(
                    userEmail = userEmail,
                    hasCloudApiKey = hasCloudApiKey,
                    onSignOut = onSignOut
                )
            }

            when (sessionsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = sessionsState.message,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val sessions = sessionsState.data
                    item {
                        ProgressSummaryStrip(sessions = sessions)
                    }

                    if (sessions.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RecordVoiceOver,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No pronunciation sessions yet",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Record yourself speaking in Studio to track accent, fluency, and intonation progress over time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "Recent Coaching Sessions",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        items(sessions, key = { it.id }) { session ->
                            PracticeSessionCard(
                                session = session,
                                onDelete = { onDeleteSession(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineAndAccountCard(
    userEmail: String?,
    hasCloudApiKey: Boolean,
    onSignOut: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (hasCloudApiKey) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                    contentDescription = null,
                    tint = if (hasCloudApiKey) EmeraldMastery else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasCloudApiKey) {
                            "Google Cloud TTS & STT Connected"
                        } else {
                            "Hybrid On-Device + Gemini Audio Active"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (hasCloudApiKey) {
                            "Using Cloud Neural2 synthesis & word-confidence STT."
                        } else {
                            "Set GOOGLE_CLOUD_API_KEY in AI Studio Secrets for Neural2 TTS & Cloud STT."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = userEmail?.takeIf { it.isNotBlank() } ?: "Google Account Synced",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

@Composable
private fun ProgressSummaryStrip(sessions: List<PracticeSession>) {
    val avgOverall = remember(sessions) {
        if (sessions.isEmpty()) 0 else sessions.map { it.overallScore }.average().toInt()
    }
    val avgAccent = remember(sessions) {
        if (sessions.isEmpty()) 0 else sessions.map { it.accentScore }.average().toInt()
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SummaryStatBox(
            label = "Sessions",
            value = "${sessions.size}",
            modifier = Modifier.weight(1f)
        )
        SummaryStatBox(
            label = "Avg Overall",
            value = "$avgOverall%",
            modifier = Modifier.weight(1f)
        )
        SummaryStatBox(
            label = "Avg Accent",
            value = "$avgAccent%",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PracticeSessionCard(
    session: PracticeSession,
    onDelete: () -> Unit
) {
    val lang = SupportedLanguages.byCode(session.targetLanguage)
    val scoreColor = when {
        session.overallScore >= 85L -> EmeraldMastery
        session.overallScore >= 65L -> AmberCaution
        else -> MaterialTheme.colorScheme.secondary
    }
    val dateLabel = remember(session.createdAt, session.updatedAt) {
        val ts = session.updatedAt ?: session.createdAt ?: Timestamp.now()
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(ts.toDate())
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_card_${session.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldContainer
                ) {
                    Text(
                        text = "${session.overallScore}% • ${lang.shortBadge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnEmeraldContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_session_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete practice session",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = session.phraseText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (session.recognizedTranscript.isNotBlank()) {
                Text(
                    text = "Heard: \"${session.recognizedTranscript}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = scoreColor
                )
            }
            if (session.coachSummary.isNotBlank()) {
                Text(
                    text = session.coachSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Accent ${session.accentScore}% • Fluency ${session.fluencyScore}% • Tone ${session.intonationScore}% • ${session.speechEngine}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
