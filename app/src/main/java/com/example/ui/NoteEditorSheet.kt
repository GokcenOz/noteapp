package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.audio.PlaybackState
import com.example.audio.RecordingState
import com.example.audio.VoiceAudioManager
import com.example.data.CategoryDoc

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
  draft: NoteEditorDraft,
  categories: List<CategoryDoc>,
  recordingState: RecordingState,
  playbackState: PlaybackState,
  onDraftChange: ((NoteEditorDraft) -> NoteEditorDraft) -> Unit,
  onStartRecording: () -> Unit,
  onStopRecording: () -> Unit,
  onClearAudio: () -> Unit,
  onTogglePlayDraftAudio: () -> Unit,
  onSeekAudio: (Long) -> Unit,
  onSave: () -> Unit,
  onDelete: (String) -> Unit,
  onClose: () -> Unit,
) {
  BackHandler { onClose() }

  val context = LocalContext.current
  var tagInput by remember { mutableStateOf("") }
  var showMicRationale by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        onStartRecording()
      } else {
        showMicRationale = true
      }
    }

  fun requestMicAndRecord() {
    val hasPerm =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (hasPerm) {
      onStartRecording()
    } else {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  LaunchedEffect(draft.startRecordingImmediately) {
    if (draft.startRecordingImmediately && !recordingState.isRecording && recordingState.recordedBytes == null) {
      requestMicAndRecord()
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
        navigationIcon = {
          IconButton(onClick = onClose, modifier = Modifier.testTag("editor_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = "Geri",
            )
          }
        },
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.EnhancedEncryption,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = if (draft.noteId == null) "Yeni Şifreli Not" else "Notu Düzenle",
              style = MaterialTheme.typography.titleLarge,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { onDraftChange { it.copy(pinned = !it.pinned) } },
            modifier = Modifier.testTag("editor_pin_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.PushPin,
              contentDescription = if (draft.pinned) "Sabitlemeyi kaldır" else "Notu sabitle",
              tint =
                if (draft.pinned) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(
            onClick = { onDraftChange { it.copy(locked = !it.locked) } },
            modifier = Modifier.testTag("editor_lock_button"),
          ) {
            Icon(
              imageVector = if (draft.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
              contentDescription = if (draft.locked) "Gizli not kilidini aç" else "Gizli not olarak kilitle",
              tint =
                if (draft.locked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (draft.noteId != null) {
            IconButton(
              onClick = { showDeleteConfirm = true },
              modifier = Modifier.testTag("editor_delete_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Notu sil",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
        },
      )
    },
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .navigationBarsPadding()
              .imePadding()
              .padding(horizontal = 20.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(100.dp),
          ) {
            Text(
              text = "AES-256-GCM",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
          Spacer(modifier = Modifier.weight(1f))
          Button(
            onClick = onSave,
            enabled = !draft.isSaving,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.height(48.dp).testTag("editor_save_button"),
          ) {
            if (draft.isSaving) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Şifreleniyor…")
            } else {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Kaydet & Senkronize Et")
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Column(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .widthIn(max = 720.dp)
            .align(Alignment.TopCenter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Security Banner if Locked Note
        AnimatedVisibility(visible = draft.locked) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Gizli Not: Liste görünümünde önizlemesi maskelenir ve PIN ile korunur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }

        // Category Selector
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "KATEGORİ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            categories.forEach { cat ->
              val selected = draft.categoryId == cat.id
              val catColor = parseHexColor(cat.colorHex, MaterialTheme.colorScheme.primary)
              FilterChip(
                selected = selected,
                onClick = { onDraftChange { it.copy(categoryId = cat.id) } },
                label = { Text(cat.name) },
                leadingIcon = {
                  Icon(
                    imageVector = categoryIconForKey(cat.iconKey),
                    contentDescription = null,
                    tint = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                  )
                },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = catColor.copy(alpha = 0.18f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                  ),
                modifier = Modifier.testTag("editor_cat_${cat.id}"),
              )
            }
          }
        }

        // Title Input
        OutlinedTextField(
          value = draft.title,
          onValueChange = { next -> onDraftChange { it.copy(title = next) } },
          label = { Text("Not Başlığı") },
          placeholder = { Text("Örn. Proje Anahtarları veya Toplantı Özeti") },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
          modifier = Modifier.fillMaxWidth().testTag("editor_title_input"),
        )

        // Voice Recorder & Encrypted Audio Card
        VoiceMemoEditorCard(
          recordingState = recordingState,
          playbackState = playbackState,
          onRequestRecord = { requestMicAndRecord() },
          onStopRecord = onStopRecording,
          onClearAudio = onClearAudio,
          onTogglePlay = onTogglePlayDraftAudio,
          onSeek = onSeekAudio,
        )

        // Body Input
        OutlinedTextField(
          value = draft.body,
          onValueChange = { next -> onDraftChange { it.copy(body = next) } },
          label = { Text("Şifreli Not İçeriği") },
          placeholder = { Text("Notunuzu buraya yazın… Kaydederken cihazda şifrelenir.") },
          minLines = 6,
          maxLines = 16,
          shape = MaterialTheme.shapes.medium,
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
          modifier = Modifier.fillMaxWidth().testTag("editor_body_input"),
        )

        // Tags Input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "ETİKETLER (${draft.tags.size}/10)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedTextField(
              value = tagInput,
              onValueChange = { tagInput = it.take(20) },
              placeholder = { Text("Etiket ekle…") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              keyboardActions =
                KeyboardActions(
                  onDone = {
                    val clean = tagInput.trim()
                    if (clean.isNotEmpty() && draft.tags.size < 10 && clean !in draft.tags) {
                      onDraftChange { it.copy(tags = it.tags + clean) }
                      tagInput = ""
                    }
                  }
                ),
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.weight(1f).testTag("editor_tag_input"),
            )
            Button(
              onClick = {
                val clean = tagInput.trim()
                if (clean.isNotEmpty() && draft.tags.size < 10 && clean !in draft.tags) {
                  onDraftChange { it.copy(tags = it.tags + clean) }
                  tagInput = ""
                }
              },
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.height(52.dp).testTag("editor_add_tag_button"),
            ) {
              Icon(imageVector = Icons.Rounded.Add, contentDescription = "Etiket ekle")
            }
          }
          if (draft.tags.isNotEmpty()) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              draft.tags.forEach { tag ->
                InputChip(
                  selected = false,
                  onClick = { onDraftChange { it.copy(tags = it.tags - tag) } },
                  label = { Text("#$tag") },
                  trailingIcon = {
                    Icon(
                      imageVector = Icons.Rounded.Close,
                      contentDescription = "$tag etiketini kaldır",
                      modifier = Modifier.size(16.dp),
                    )
                  },
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }

  if (showMicRationale) {
    AlertDialog(
      onDismissRequest = { showMicRationale = false },
      title = { Text("Mikrofon İzni Gerekli") },
      text = { Text(stringResource(R.string.mic_permission_rationale)) },
      confirmButton = {
        TextButton(onClick = { showMicRationale = false }) {
          Text("Tamam")
        }
      },
    )
  }

  if (showDeleteConfirm && draft.noteId != null) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Notu Sil") },
      text = { Text("Bu şifreli not ve varsa ses kaydı buluttan kalıcı olarak silinecek. Devam edilsin mi?") },
      confirmButton = {
        Button(
          onClick = {
            showDeleteConfirm = false
            onDelete(draft.noteId)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Sil")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteConfirm = false }) {
          Text("İptal")
        }
      },
    )
  }
}

@Composable
private fun VoiceMemoEditorCard(
  recordingState: RecordingState,
  playbackState: PlaybackState,
  onRequestRecord: () -> Unit,
  onStopRecord: () -> Unit,
  onClearAudio: () -> Unit,
  onTogglePlay: () -> Unit,
  onSeek: (Long) -> Unit,
) {
  val isRecording = recordingState.isRecording
  val hasAudio = recordingState.recordedBytes != null && recordingState.recordedBytes.isNotEmpty()
  val isPlayingDraft = playbackState.activeId == "draft_preview" && playbackState.isPlaying

  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    shape = MaterialTheme.shapes.medium,
    border =
      BorderStroke(
        1.dp,
        if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier =
              Modifier.size(10.dp)
                .clip(CircleShape)
                .background(
                  when {
                    isRecording -> MaterialTheme.colorScheme.error
                    hasAudio -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                  }
                )
          )
          Text(
            text =
              when {
                isRecording -> "SES KAYDEDİLİYOR"
                hasAudio -> "ŞİFRELİ SESLİ NOT EKLİ"
                else -> "SESLİ KAYIT EKLE"
              },
            style = MaterialTheme.typography.labelSmall,
            color =
              if (isRecording) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Text(
          text =
            when {
              isRecording ->
                "${formatDurationMs(recordingState.elapsedMs)} / ${formatDurationMs(VoiceAudioManager.MAX_DURATION_MS)}"
              hasAudio -> formatDurationMs(recordingState.recordedDurationMs)
              else -> "Maks 02:00"
            },
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      if (recordingState.errorMessage != null) {
        Text(
          text = recordingState.errorMessage,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      }

      if (isRecording) {
        LiveWaveformCanvas(
          amplitudes = recordingState.amplitudes,
          barColor = MaterialTheme.colorScheme.error,
          modifier = Modifier.fillMaxWidth().height(48.dp),
        )
        Button(
          onClick = onStopRecord,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("stop_recording_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Kaydı Durdur ve Şifrele")
        }
      } else if (hasAudio) {
        val total =
          playbackState.durationMs.takeIf { playbackState.activeId == "draft_preview" && it > 0L }
            ?: recordingState.recordedDurationMs.coerceAtLeast(1000L)
        val pos =
          if (playbackState.activeId == "draft_preview") playbackState.positionMs.coerceIn(0L, total)
          else 0L

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Surface(
            onClick = onTogglePlay,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp).testTag("draft_play_audio_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (isPlayingDraft) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlayingDraft) "Duraklat" else "Dinle",
                tint = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }

          Column(modifier = Modifier.weight(1f)) {
            Slider(
              value = pos.toFloat(),
              onValueChange = { onSeek(it.toLong()) },
              valueRange = 0f..total.toFloat().coerceAtLeast(1f),
              colors =
                SliderDefaults.colors(
                  thumbColor = MaterialTheme.colorScheme.primary,
                  activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = formatDurationMs(pos),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = formatDurationMs(total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          IconButton(
            onClick = onClearAudio,
            modifier = Modifier.size(48.dp).testTag("draft_delete_audio_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Delete,
              contentDescription = "Ses kaydını sil",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      } else {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(MaterialTheme.shapes.small)
              .clickable(onClick = onRequestRecord)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("start_recording_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "Mikrofonla Sesli Kayıt Başlat",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    }
  }
}

@Composable
private fun LiveWaveformCanvas(
  amplitudes: List<Float>,
  barColor: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val barCount = 40
    val gap = size.width / (barCount * 2f)
    val barWidth = gap
    val samples =
      if (amplitudes.size >= barCount) amplitudes.takeLast(barCount)
      else List(barCount - amplitudes.size) { 0.08f } + amplitudes

    samples.forEachIndexed { index, amp ->
      val barHeight = (size.height * amp).coerceIn(6f, size.height)
      val x = index * (barWidth + gap)
      val y = (size.height - barHeight) / 2f
      drawRoundRect(
        color = barColor,
        topLeft = Offset(x, y),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
      )
    }
  }
}
