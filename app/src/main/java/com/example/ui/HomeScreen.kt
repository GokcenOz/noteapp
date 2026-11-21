package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.PlaybackState
import com.example.data.CategoryDoc
import com.example.data.DecryptedNote
import com.example.data.SyncStatus
import com.example.data.UiState

@Composable
fun VaultHomeScreen(
  viewModel: VaultViewModel,
  userEmail: String?,
  userName: String?,
  isDarkTheme: Boolean,
  onToggleTheme: (Boolean) -> Unit,
  onSignOut: () -> Unit,
) {
  val notesState by viewModel.notesState.collectAsStateWithLifecycle()
  val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
  val vaultConfig by viewModel.vaultConfig.collectAsStateWithLifecycle()
  val isVaultLocked by viewModel.isVaultLocked.collectAsStateWithLifecycle()
  val isPinPromptVisible by viewModel.isPinPromptVisible.collectAsStateWithLifecycle()
  val pinError by viewModel.pinError.collectAsStateWithLifecycle()
  val revealedNoteIds by viewModel.revealedLockedNoteIds.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
  val filterVoiceOnly by viewModel.filterVoiceOnly.collectAsStateWithLifecycle()
  val filterLockedOnly by viewModel.filterLockedOnly.collectAsStateWithLifecycle()
  val editorDraft by viewModel.editorDraft.collectAsStateWithLifecycle()
  val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
  val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
  val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

  var showSecurityCenter by remember { mutableStateOf(false) }
  var showCreateCategory by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(bannerMessage) {
    val msg = bannerMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearMessage()
    }
  }

  val categories = (categoriesState as? UiState.Success)?.data ?: emptyList()
  val boardData = (notesState as? UiState.Success)?.data
  val allNotes = boardData?.allNotes ?: emptyList()
  val syncStatus = boardData?.syncStatus ?: SyncStatus.SYNCING

  val filteredNotes =
    remember(allNotes, searchQuery, selectedCategoryId, filterVoiceOnly, filterLockedOnly) {
      val q = searchQuery.trim().lowercase()
      allNotes.filter { note ->
        val catMatch = selectedCategoryId == null || note.categoryId == selectedCategoryId
        val voiceMatch = !filterVoiceOnly || note.hasAudio
        val lockMatch = !filterLockedOnly || note.locked
        val queryMatch =
          if (q.isEmpty()) true
          else {
            (!note.locked && (note.title.lowercase().contains(q) || note.body.lowercase().contains(q))) ||
              note.tags.any { it.lowercase().contains(q) }
          }
        catMatch && voiceMatch && lockMatch && queryMatch
      }
    }

  Box(modifier = Modifier.fillMaxSize()) {
    if (editorDraft != null) {
      NoteEditorScreen(
        draft = editorDraft!!,
        categories = categories,
        recordingState = recordingState,
        playbackState = playbackState,
        onDraftChange = viewModel::updateDraft,
        onStartRecording = viewModel::startRecording,
        onStopRecording = viewModel::stopRecording,
        onClearAudio = viewModel::clearRecordedAudio,
        onTogglePlayDraftAudio = viewModel::toggleDraftAudioPlayback,
        onSeekAudio = viewModel::seekAudio,
        onSave = viewModel::saveDraftNote,
        onDelete = viewModel::deleteNote,
        onClose = viewModel::closeEditor,
      )
    } else {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        Scaffold(
          containerColor = MaterialTheme.colorScheme.background,
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
          floatingActionButton = {
            Column(
              horizontalAlignment = Alignment.End,
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.navigationBarsPadding(),
            ) {
              FloatingActionButton(
                onClick = { viewModel.openNewNoteEditor(startRecording = true) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("quick_voice_note_fab"),
              ) {
                Icon(imageVector = Icons.Rounded.Mic, contentDescription = "Hızlı Sesli Not")
              }
              ExtendedFloatingActionButton(
                onClick = { viewModel.openNewNoteEditor(startRecording = false) },
                icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_note)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_note_fab"),
              )
            }
          },
        ) { innerPadding ->
          Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isWide) {
              NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight(),
              ) {
                NavigationRailItem(
                  selected = selectedCategoryId == null,
                  onClick = { viewModel.selectCategory(null) },
                  icon = { Icon(Icons.Rounded.Shield, contentDescription = "Tümü") },
                  label = { Text("Tümü") },
                )
                categories.take(5).forEach { cat ->
                  NavigationRailItem(
                    selected = selectedCategoryId == cat.id,
                    onClick = { viewModel.selectCategory(cat.id) },
                    icon = {
                      Icon(
                        imageVector = categoryIconForKey(cat.iconKey),
                        contentDescription = cat.name,
                      )
                    },
                    label = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                  )
                }
                Spacer(modifier = Modifier.weight(1f))
                NavigationRailItem(
                  selected = false,
                  onClick = { showSecurityCenter = true },
                  icon = { Icon(Icons.Rounded.EnhancedEncryption, contentDescription = "Güvenlik") },
                  label = { Text("Güvenlik") },
                )
              }
            }

            Column(
              modifier =
                Modifier.weight(1f)
                  .fillMaxHeight()
                  .padding(horizontal = if (isWide) 24.dp else 16.dp),
            ) {
              Spacer(modifier = Modifier.height(8.dp))

              // Vault Top Header
              VaultTopHeader(
                syncStatus = syncStatus,
                pinEnabled = vaultConfig?.pinEnabled == true,
                onLockClick = { viewModel.lockVaultManually() },
                onSecurityClick = { showSecurityCenter = true },
              )

              Spacer(modifier = Modifier.height(12.dp))

              // Search & Quick Security Filters
              OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                placeholder = { Text(stringResource(R.string.search_notes_hint)) },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors =
                  OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                  ),
                modifier = Modifier.fillMaxWidth().testTag("search_notes_input"),
              )

              Spacer(modifier = Modifier.height(10.dp))

              // Horizontal Category & Filter Rail
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                FilterChip(
                  selected = selectedCategoryId == null,
                  onClick = { viewModel.selectCategory(null) },
                  label = { Text("Tümü (${allNotes.size})") },
                  modifier = Modifier.testTag("cat_chip_all"),
                )
                categories.forEach { cat ->
                  val count = allNotes.count { it.categoryId == cat.id }
                  val catColor = parseHexColor(cat.colorHex, MaterialTheme.colorScheme.primary)
                  FilterChip(
                    selected = selectedCategoryId == cat.id,
                    onClick = {
                      viewModel.selectCategory(if (selectedCategoryId == cat.id) null else cat.id)
                    },
                    label = { Text("${cat.name} ($count)") },
                    leadingIcon = {
                      Icon(
                        imageVector = categoryIconForKey(cat.iconKey),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(16.dp),
                      )
                    },
                    colors =
                      FilterChipDefaults.filterChipColors(
                        selectedContainerColor = catColor.copy(alpha = 0.18f),
                      ),
                    modifier = Modifier.testTag("cat_chip_${cat.id}"),
                  )
                }
                FilterChip(
                  selected = filterVoiceOnly,
                  onClick = viewModel::toggleVoiceFilter,
                  label = { Text("Sesli") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Rounded.GraphicEq,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  modifier = Modifier.testTag("filter_voice_chip"),
                )
                FilterChip(
                  selected = filterLockedOnly,
                  onClick = viewModel::toggleLockedFilter,
                  label = { Text("Kilitli") },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Rounded.Lock,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                  },
                  modifier = Modifier.testTag("filter_locked_chip"),
                )
                Surface(
                  onClick = { showCreateCategory = true },
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                  modifier = Modifier.height(34.dp).testTag("add_category_chip"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Add,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Text("Kategori", style = MaterialTheme.typography.labelMedium)
                  }
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Notes Content
              when (val state = notesState) {
                is UiState.Loading -> {
                  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                      horizontalAlignment = Alignment.CenterHorizontally,
                      verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      CircularProgressIndicator()
                      Text(
                        text = "Şifreli kasa senkronize ediliyor…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
                is UiState.Error -> {
                  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                      color = MaterialTheme.colorScheme.errorContainer,
                      shape = MaterialTheme.shapes.medium,
                      modifier = Modifier.padding(16.dp),
                    ) {
                      Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                      )
                    }
                  }
                }
                is UiState.Success -> {
                  if (filteredNotes.isEmpty()) {
                    EmptyVaultState(
                      isFiltered =
                        searchQuery.isNotBlank() ||
                          selectedCategoryId != null ||
                          filterVoiceOnly ||
                          filterLockedOnly,
                      onNewNote = { viewModel.openNewNoteEditor(false) },
                    )
                  } else {
                    LazyColumn(
                      modifier = Modifier.fillMaxSize(),
                      contentPadding = PaddingValues(top = 4.dp, bottom = 148.dp),
                      verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      items(filteredNotes, key = { it.id }) { note ->
                        val cat = categories.firstOrNull { it.id == note.categoryId }
                        val isRevealed = !note.locked || note.id in revealedNoteIds
                        EncryptedNoteCard(
                          note = note,
                          category = cat,
                          isRevealed = isRevealed,
                          playbackState = playbackState,
                          onOpen = { viewModel.openNoteForEditing(note) },
                          onToggleReveal = { viewModel.revealLockedCard(note) },
                          onPlayAudio = { viewModel.revealOrPlayNoteAudio(note) },
                          onTogglePin = { viewModel.togglePin(note) },
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    if (showSecurityCenter) {
      SecurityCenterSheet(
        userEmail = userEmail,
        userName = userName,
        keyFingerprint = viewModel.keyFingerprint(),
        encryptedNoteCount = allNotes.size,
        voiceMemoCount = allNotes.count { it.hasAudio },
        syncStatus = syncStatus,
        vaultConfig = vaultConfig,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        onSetPin = viewModel::setVaultPin,
        onDisablePin = viewModel::disableVaultPin,
        onToggleLockOnLaunch = viewModel::toggleLockOnLaunch,
        onLockVaultNow = viewModel::lockVaultManually,
        onSignOut = onSignOut,
        onDismiss = { showSecurityCenter = false },
      )
    }

    if (showCreateCategory) {
      CreateCategoryDialog(
        onConfirm = { name, colorHex, iconKey ->
          showCreateCategory = false
          viewModel.createCategory(name, colorHex, iconKey)
        },
        onDismiss = { showCreateCategory = false },
      )
    }

    // Full-screen Vault Lock Gate
    AnimatedVisibility(visible = isVaultLocked) {
      VaultPinGate(
        title = stringResource(R.string.vault_locked_title),
        subtitle = stringResource(R.string.vault_locked_subtitle),
        errorMessage = pinError,
        allowDismiss = false,
        onPinEntered = { pin -> viewModel.unlockVaultWithPin(pin) },
        onSignOutFallback = onSignOut,
      )
    }

    // Per-Note PIN Prompt
    AnimatedVisibility(visible = isPinPromptVisible && !isVaultLocked) {
      VaultPinGate(
        title = "Gizli Not Kilidi",
        subtitle = "Bu kilitli notu çözmek için 4 haneli Kasa PIN'inizi girin.",
        errorMessage = pinError,
        allowDismiss = true,
        onPinEntered = { pin -> viewModel.unlockVaultWithPin(pin) },
        onDismiss = viewModel::dismissPinPrompt,
      )
    }
  }
}

@Composable
private fun VaultTopHeader(
  syncStatus: SyncStatus,
  pinEnabled: Boolean,
  onLockClick: () -> Unit,
  onSecurityClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
          shape = RoundedCornerShape(100.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector =
                when (syncStatus) {
                  SyncStatus.SYNCED -> Icons.Rounded.CloudDone
                  SyncStatus.SYNCING -> Icons.Rounded.CloudSync
                  SyncStatus.OFFLINE_CACHE -> Icons.Rounded.CloudOff
                },
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text =
                when (syncStatus) {
                  SyncStatus.SYNCED -> stringResource(R.string.sync_status_synced)
                  SyncStatus.SYNCING -> stringResource(R.string.sync_status_syncing)
                  SyncStatus.OFFLINE_CACHE -> stringResource(R.string.sync_status_offline)
                },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
      Text(
        text = "AES-256-GCM Şifreli Not Kasası",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Surface(
        onClick = onLockClick,
        shape = CircleShape,
        color =
          if (pinEnabled) MaterialTheme.colorScheme.surfaceVariant
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.size(48.dp).testTag("header_lock_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (pinEnabled) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
            contentDescription = "Kasayı kilitle",
            tint =
              if (pinEnabled) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      Surface(
        onClick = onSecurityClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.size(48.dp).testTag("header_security_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = "Güvenlik merkezi",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EncryptedNoteCard(
  note: DecryptedNote,
  category: CategoryDoc?,
  isRevealed: Boolean,
  playbackState: PlaybackState,
  onOpen: () -> Unit,
  onToggleReveal: () -> Unit,
  onPlayAudio: () -> Unit,
  onTogglePin: () -> Unit,
) {
  val catColor = parseHexColor(category?.colorHex ?: "#10B981", MaterialTheme.colorScheme.primary)
  val isPlayingThis = playbackState.activeId == note.id && playbackState.isPlaying

  Card(
    onClick = onOpen,
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
      ),
    border =
      BorderStroke(
        1.dp,
        if (note.pinned) MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
      ),
    modifier = Modifier.fillMaxWidth().testTag("note_card_${note.id}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Top Meta Row: Category + AES-256 + Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            color = catColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(8.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = categoryIconForKey(category?.iconKey ?: "folder"),
                contentDescription = null,
                tint = catColor,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = category?.name ?: "Genel",
                style = MaterialTheme.typography.labelSmall,
                color = catColor,
              )
            }
          }

          if (note.locked) {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
              shape = RoundedCornerShape(8.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Lock,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(12.dp),
                )
                Text(
                  text = "KİLİTLİ",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (note.locked) {
            IconButton(
              onClick = onToggleReveal,
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                imageVector =
                  if (isRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (isRevealed) "İçeriği gizle" else "İçeriği göster",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          IconButton(
            onClick = onTogglePin,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.PushPin,
              contentDescription = if (note.pinned) "Sabitlemeyi kaldır" else "Sabitle",
              tint =
                if (note.pinned) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      // Title & Body (masked when locked & not revealed)
      if (isRevealed) {
        Text(
          text = note.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (note.body.isNotBlank()) {
          Text(
            text = note.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
      } else {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.EnhancedEncryption,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "••••••••••  •  Şifreli içerik (Açmak için dokunun)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // Inline Encrypted Voice Player Strip
      if (note.hasAudio) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
          modifier =
            Modifier.fillMaxWidth()
              .clickable(onClick = onPlayAudio)
              .testTag("card_play_audio_${note.id}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier =
                Modifier.size(36.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primary),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlayingThis) "Duraklat" else "Sesli notu oynat",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = if (isPlayingThis) "Oynatılıyor…" else "Şifreli Sesli Kayıt",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = formatDurationMs(note.audioDurationMs),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      }

      // Footer Row: Tags + Timestamp
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (note.tags.isNotEmpty() && isRevealed) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            note.tags.take(3).forEach { tag ->
              Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
              )
            }
          }
        } else {
          Spacer(modifier = Modifier.weight(1f))
        }

        Text(
          text = formatTimestampShort(note.updatedAt ?: note.createdAt),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun EmptyVaultState(
  isFiltered: Boolean,
  onNewNote: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier.widthIn(max = 360.dp),
    ) {
      Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().height(170.dp),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_empty_notes),
          contentDescription = "Boş kasa görseli",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      }
      Text(
        text = if (isFiltered) "Eşleşen şifreli not bulunamadı" else stringResource(R.string.empty_vault_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text =
          if (isFiltered) "Arama terimini veya kategori filtresini temizleyip yeniden deneyin."
          else stringResource(R.string.empty_vault_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (!isFiltered) {
        Surface(
          onClick = onNewNote,
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.padding(top = 4.dp).testTag("empty_create_note_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
              text = "İlk Şifreli Notu Ekle",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
    }
  }
}
