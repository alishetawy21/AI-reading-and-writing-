package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.AudioMeterFrame
import com.example.data.model.PronunciationAnalysisResult
import com.example.data.model.SupportedLanguages
import com.example.data.model.WordPronunciationFeedback
import com.example.data.model.WordStatus
import com.example.floating.FloatingAssistService
import com.example.ui.AnalysisUiState
import com.example.ui.LingoFloatViewModel
import com.example.ui.StudioUiState
import com.example.ui.TranslationUiState
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMastery
import com.example.ui.theme.OnEmeraldContainer

@Composable
fun StudioScreen(
    studioState: StudioUiState,
    audioMeter: AudioMeterFrame,
    isSpeaking: Boolean,
    ttsEngineLabel: String,
    isFloatingBubbleRunning: Boolean,
    viewModel: LingoFloatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var micPermissionError by remember { mutableStateOf<String?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionError = null
            viewModel.startSpeechRecording()
        } else {
            micPermissionError = "Microphone permission is needed to listen and analyze your accent."
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            FloatingAssistService.start(context, studioState.targetLanguageCode)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StudioHeaderCard(
                    ttsEngineLabel = ttsEngineLabel,
                    isFloatingBubbleRunning = isFloatingBubbleRunning,
                    isInAppFloatingOpen = studioState.isInAppFloatingOpen,
                    onToggleSystemBubble = {
                        if (isFloatingBubbleRunning) {
                            FloatingAssistService.stop(context)
                        } else if (Settings.canDrawOverlays(context)) {
                            FloatingAssistService.start(context, studioState.targetLanguageCode)
                        } else {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        }
                    },
                    onToggleInAppFloat = {
                        viewModel.setInAppFloatingOpen(!studioState.isInAppFloatingOpen)
                    }
                )
            }

            item {
                LanguageSelectorCard(
                    sourceCode = studioState.sourceLanguageCode,
                    targetCode = studioState.targetLanguageCode,
                    onSelectSource = viewModel::setSourceLanguage,
                    onSelectTarget = viewModel::setTargetLanguage,
                    onSwap = viewModel::swapLanguages
                )
            }

            item {
                ReaderAndTranslatorCard(
                    studioState = studioState,
                    isSpeaking = isSpeaking,
                    onTextChange = viewModel::updateInputText,
                    onPaste = {
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                            viewModel.updateInputText(it)
                        }
                    },
                    onLoadSample = viewModel::loadSamplePassage,
                    onTranslate = viewModel::translateCurrentText,
                    onPronounceNormal = { viewModel.pronounceTargetPhrase(1.0f) },
                    onPronounceSlow = { viewModel.pronounceTargetPhrase(0.70f) },
                    onStopSpeaking = viewModel::stopSpeaking,
                    onSavePhrase = viewModel::saveCurrentToPhrasebook
                )
            }

            item {
                SpeechAnalysisCard(
                    studioState = studioState,
                    audioMeter = audioMeter,
                    micPermissionError = micPermissionError,
                    onStartOrStopRecording = {
                        if (studioState.analysisState is AnalysisUiState.Recording) {
                            viewModel.stopAndAnalyzeSpeech()
                        } else {
                            val hasMic = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                micPermissionError = null
                                viewModel.startSpeechRecording()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    onSelectWord = viewModel::selectWordFeedback,
                    onSpeakWord = { word -> viewModel.pronounceSingleWord(word, 0.72f) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StudioHeaderCard(
    ttsEngineLabel: String,
    isFloatingBubbleRunning: Boolean,
    isInAppFloatingOpen: Boolean,
    onToggleSystemBubble: () -> Unit,
    onToggleInAppFloat: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_studio),
                    contentDescription = "LingoFloat editorial studio header",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "LingoFloat Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ttsEngineLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                FilledTonalButton(
                    onClick = onToggleSystemBubble,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_system_bubble_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFloatingBubbleRunning) "Bubble Active" else "Float Bubble",
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = onToggleInAppFloat,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toggle_inapp_float_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isInAppFloatingOpen) "Hide Mini" else "Mini Float",
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSelectorCard(
    sourceCode: String,
    targetCode: String,
    onSelectSource: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onSwap: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Reading From",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onSwap,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("swap_languages_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "Swap reading and target languages",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Target Accent",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SupportedLanguages.all, key = { "src_${it.code}" }) { lang ->
                    val selected = lang.code == sourceCode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSource(lang.code) },
                        label = { Text(lang.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SupportedLanguages.all, key = { "tgt_${it.code}" }) { lang ->
                    val selected = lang.code == targetCode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTarget(lang.code) },
                        label = { Text("${lang.displayName} • ${lang.accentLabel}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderAndTranslatorCard(
    studioState: StudioUiState,
    isSpeaking: Boolean,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onLoadSample: () -> Unit,
    onTranslate: () -> Unit,
    onPronounceNormal: () -> Unit,
    onPronounceSlow: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSavePhrase: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Read, Translate & Pronounce",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onPaste,
                    modifier = Modifier.testTag("paste_reader_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste")
                }
            }

            OutlinedTextField(
                value = studioState.inputText,
                onValueChange = onTextChange,
                label = { Text("Book excerpt, sentence, or phrase") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reader_input_field")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onLoadSample,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("sample_passage_button")
                ) {
                    Text("Sample Book")
                }
                Button(
                    onClick = onTranslate,
                    enabled = studioState.translationState !is TranslationUiState.Loading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("translate_button")
                ) {
                    if (studioState.translationState is TranslationUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Translating...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Translate & IPA")
                    }
                }
            }

            when (val tState = studioState.translationState) {
                is TranslationUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tState.message,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is TranslationUiState.Success -> {
                    val b = tState.breakdown
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = b.translatedText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (b.ipaPhonetics.isNotBlank()) {
                                Text(
                                    text = b.ipaPhonetics,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (b.syllableStressGuide.isNotBlank()) {
                                Text(
                                    text = "Stress: ${b.syllableStressGuide}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (b.accentTip.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TipsAndUpdates,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = b.accentTip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(
                    onClick = if (isSpeaking) onStopSpeaking else onPronounceNormal,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pronounce_button")
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSpeaking) "Stop" else "Listen 1.0x")
                }

                FilledTonalButton(
                    onClick = onPronounceSlow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pronounce_slow_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SlowMotionVideo,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Slow 0.7x")
                }

                IconButton(
                    onClick = onSavePhrase,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("save_phrase_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = "Save phrase to Phrasebook",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeechAnalysisCard(
    studioState: StudioUiState,
    audioMeter: AudioMeterFrame,
    micPermissionError: String?,
    onStartOrStopRecording: () -> Unit,
    onSelectWord: (WordPronunciationFeedback) -> Unit,
    onSpeakWord: (String) -> Unit
) {
    val isRecording = studioState.analysisState is AnalysisUiState.Recording
    val isAnalyzing = studioState.analysisState is AnalysisUiState.Analyzing

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(
            width = if (isRecording) 2.dp else 1.dp,
            color = if (isRecording) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Real-Time Speech & Accent Coach",
                        style = MaterialTheme.typography.titleLarge
                    )
                    val targetLang = SupportedLanguages.byCode(
                        if (studioState.translationState is TranslationUiState.Success) {
                            studioState.targetLanguageCode
                        } else {
                            studioState.sourceLanguageCode
                        }
                    )
                    Text(
                        text = "Target: ${targetLang.displayName} (${targetLang.accentLabel})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Waveform Visualizer
            LiveWaveformMeter(
                meterFrame = audioMeter,
                isRecording = isRecording
            )

            micPermissionError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = onStartOrStopRecording,
                enabled = !isAnalyzing,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("record_speech_button")
            ) {
                when {
                    isAnalyzing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Analyzing Accent & Phonemes...")
                    }
                    isRecording -> {
                        Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop & Score Pronunciation")
                    }
                    else -> {
                        Icon(imageVector = Icons.Rounded.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Speak Now to Check Accent")
                    }
                }
            }

            when (val aState = studioState.analysisState) {
                is AnalysisUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = aState.message,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is AnalysisUiState.Success -> {
                    PronunciationReportSection(
                        result = aState.result,
                        selectedWord = studioState.selectedWordFeedback,
                        onSelectWord = onSelectWord,
                        onSpeakWord = onSpeakWord
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun LiveWaveformMeter(
    meterFrame: AudioMeterFrame,
    isRecording: Boolean
) {
    val activeColor = if (isRecording) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    }
    val trackBg = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        color = trackBg.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRecording) {
                        val secs = meterFrame.elapsedMs / 1000f
                        "Live Acoustic Capture • %.1fs • %.0f dB".format(secs, meterFrame.decibels)
                    } else {
                        "Tap Speak Now and read the phrase aloud"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                val bars = meterFrame.waveform
                val count = bars.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.56f).coerceAtLeast(4f)
                val centerY = size.height / 2f
                bars.forEachIndexed { index, level ->
                    val barHeight = (level * size.height).coerceIn(6f, size.height)
                    val left = index * slotWidth + (slotWidth - barWidth) / 2f
                    val top = centerY - barHeight / 2f
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PronunciationReportSection(
    result: PronunciationAnalysisResult,
    selectedWord: WordPronunciationFeedback?,
    onSelectWord: (WordPronunciationFeedback) -> Unit,
    onSpeakWord: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Score Metrics Strip
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ScoreBadgeCard("Overall", result.overallScore, Modifier.weight(1f), highlight = true)
            ScoreBadgeCard("Accent", result.accentScore, Modifier.weight(1f))
            ScoreBadgeCard("Fluency", result.fluencyScore, Modifier.weight(1f))
            ScoreBadgeCard("Tone", result.intonationScore, Modifier.weight(1f))
        }

        // What we heard + Engine tag
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Heard (${result.speechEngine})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = result.recognizedTranscript.ifBlank { "(No clear words detected)" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (result.coachSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.coachSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Interactive Word-by-Word Chips
        if (result.wordFeedback.isNotEmpty()) {
            Text(
                text = "Tap any word for IPA & accent correction:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                result.wordFeedback.forEachIndexed { idx, wf ->
                    val isSelected = selectedWord == wf
                    val (bgColor, fgColor) = when (wf.status) {
                        WordStatus.ACCURATE -> EmeraldContainer to OnEmeraldContainer
                        WordStatus.NEEDS_WORK -> AmberContainer to Color(0xFF543000)
                        WordStatus.MISSED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bgColor,
                        border = if (isSelected) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onSelectWord(wf) }
                            .testTag("word_feedback_chip_$idx")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = wf.word,
                                style = MaterialTheme.typography.labelLarge,
                                color = fgColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${wf.confidencePercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = fgColor.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // Interactive Selected Word Coaching Detail
        AnimatedVisibility(visible = selectedWord != null) {
            selectedWord?.let { wf ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${wf.word}  ${wf.targetIpa}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (wf.heardAs.isNotBlank()) {
                                    Text(
                                        text = "Heard as: \"${wf.heardAs}\" (${wf.confidencePercent}%)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { onSpeakWord(wf.word) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("hear_selected_word_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hear")
                            }
                        }
                        if (wf.articulatoryTip.isNotBlank()) {
                            Text(
                                text = wf.articulatoryTip,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Accent Corrections List
        if (result.accentCorrections.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Accent Corrections",
                    style = MaterialTheme.typography.titleSmall
                )
                result.accentCorrections.forEach { tip ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldMastery,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Text(
                            text = tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBadgeCard(
    label: String,
    score: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    val scoreColor = when {
        score >= 85 -> EmeraldMastery
        score >= 65 -> AmberCaution
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) {
            scoreColor.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = if (highlight) BorderStroke(1.dp, scoreColor.copy(alpha = 0.4f)) else null,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.titleLarge,
                color = scoreColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
