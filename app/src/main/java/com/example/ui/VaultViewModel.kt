package com.example.ui

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.PlaybackState
import com.example.audio.RecordingState
import com.example.audio.VoiceAudioManager
import com.example.crypto.VaultCryptoManager
import com.example.data.CategoryDoc
import com.example.data.DecryptedNote
import com.example.data.NoteDoc
import com.example.data.SyncStatus
import com.example.data.UiState
import com.example.data.VaultConfigDoc
import com.example.data.VaultRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NotesBoardData(
  val allNotes: List<DecryptedNote>,
  val syncStatus: SyncStatus,
)

data class NoteEditorDraft(
  val noteId: String? = null,
  val title: String = "",
  val body: String = "",
  val categoryId: String = "cat_personal",
  val locked: Boolean = false,
  val pinned: Boolean = false,
  val tags: List<String> = emptyList(),
  val isSaving: Boolean = false,
  val startRecordingImmediately: Boolean = false,
)

class VaultViewModel(
  private val repository: VaultRepository,
  private val cryptoManager: VaultCryptoManager,
  private val audioManager: VoiceAudioManager,
  val currentUserId: String,
) : ViewModel() {

  private val _keyEpoch = MutableStateFlow(0)
  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedCategoryId = MutableStateFlow<String?>(null)
  val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

  private val _filterVoiceOnly = MutableStateFlow(false)
  val filterVoiceOnly: StateFlow<Boolean> = _filterVoiceOnly.asStateFlow()

  private val _filterLockedOnly = MutableStateFlow(false)
  val filterLockedOnly: StateFlow<Boolean> = _filterLockedOnly.asStateFlow()

  private val _isVaultLocked = MutableStateFlow(false)
  val isVaultLocked: StateFlow<Boolean> = _isVaultLocked.asStateFlow()

  private var initialLockEvaluated = false

  private val _revealedLockedNoteIds = MutableStateFlow<Set<String>>(emptySet())
  val revealedLockedNoteIds: StateFlow<Set<String>> = _revealedLockedNoteIds.asStateFlow()

  private val _editorDraft = MutableStateFlow<NoteEditorDraft?>(null)
  val editorDraft: StateFlow<NoteEditorDraft?> = _editorDraft.asStateFlow()

  private val _pendingPinProtectedAction = MutableStateFlow<(() -> Unit)?>(null)
  val isPinPromptVisible: StateFlow<Boolean> =
    _pendingPinProtectedAction
      .map { it != null }
      .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  private val _pinError = MutableStateFlow<String?>(null)
  val pinError: StateFlow<String?> = _pinError.asStateFlow()

  private val _bannerMessage = MutableStateFlow<String?>(null)
  val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

  val recordingState: StateFlow<RecordingState> = audioManager.recordingState
  val playbackState: StateFlow<PlaybackState> = audioManager.playbackState

  val vaultConfig: StateFlow<VaultConfigDoc?> =
    repository
      .observeVaultConfig(currentUserId)
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing vault config", firestoreError ?: error)
        emit(null)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  val categoriesState: StateFlow<UiState<List<CategoryDoc>>> =
    repository
      .observeCategories(currentUserId)
      .map<List<CategoryDoc>, UiState<List<CategoryDoc>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing categories", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Kategoriler yüklenemedi."))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val notesState: StateFlow<UiState<NotesBoardData>> =
    combine(repository.observeNotes(currentUserId), _keyEpoch) { batch, _ ->
        val decrypted =
          withContext(Dispatchers.Default) {
            batch.notes
              .map { decryptNoteDoc(it) }
              .sortedWith(
                compareByDescending<DecryptedNote> { it.pinned }
                  .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
              )
          }
        UiState.Success(NotesBoardData(allNotes = decrypted, syncStatus = batch.syncStatus)) as UiState<NotesBoardData>
      }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing notes", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Notlar senkronize edilemedi."))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  init {
    viewModelScope.launch {
      repository.ensureStarterCategories(currentUserId)
    }
    viewModelScope.launch {
      vaultConfig.collect { config ->
        if (!initialLockEvaluated && config != null) {
          initialLockEvaluated = true
          if (config.pinEnabled && config.lockOnLaunch) {
            _isVaultLocked.value = true
          }
        }
      }
    }
  }

  private fun decryptNoteDoc(doc: NoteDoc): DecryptedNote {
    val decTitle = cryptoManager.decryptText(doc.encryptedTitle, doc.titleIv)
    val decBody =
      if (doc.encryptedBody.isBlank() || doc.bodyIv.isBlank()) {
        ""
      } else {
        cryptoManager.decryptText(doc.encryptedBody, doc.bodyIv)
      }
    val failed = decTitle == null || decBody == null
    return DecryptedNote(
      id = doc.id,
      categoryId = doc.categoryId,
      title = decTitle ?: "Şifreli Not (Anahtar Bekleniyor)",
      body = decBody ?: "",
      encryptedAudio = doc.encryptedAudio,
      audioIv = doc.audioIv,
      audioDurationMs = doc.audioDurationMs,
      hasAudio = doc.hasAudio,
      locked = doc.locked,
      pinned = doc.pinned,
      tags = doc.tags,
      decryptionFailed = failed,
      createdAt = doc.createdAt,
      updatedAt = doc.updatedAt,
    )
  }

  fun keyFingerprint(): String = cryptoManager.keyFingerprint()

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun selectCategory(categoryId: String?) {
    _selectedCategoryId.value = categoryId
  }

  fun toggleVoiceFilter() {
    _filterVoiceOnly.update { !it }
  }

  fun toggleLockedFilter() {
    _filterLockedOnly.update { !it }
  }

  fun clearMessage() {
    _bannerMessage.value = null
  }

  fun lockVaultManually() {
    val cfg = vaultConfig.value
    if (cfg == null || !cfg.pinEnabled) {
      _bannerMessage.value = "Kasayı kilitlemek için önce Güvenlik Merkezi'nden 4 haneli Kasa PIN'i belirleyin."
      return
    }
    audioManager.stopPlayback()
    _revealedLockedNoteIds.value = emptySet()
    _isVaultLocked.value = true
  }

  fun unlockVaultWithPin(pin: String): Boolean {
    val cfg = vaultConfig.value
    if (cfg == null || !cfg.pinEnabled) {
      _isVaultLocked.value = false
      _pinError.value = null
      return true
    }
    val ok =
      cryptoManager.verifyPinAndUnwrapKey(
        pin = pin,
        pinSaltBase64 = cfg.pinSalt,
        pinHashBase64 = cfg.pinHash,
        wrappedVaultKeyBase64 = cfg.wrappedVaultKey,
        wrappedVaultKeyIvBase64 = cfg.wrappedVaultKeyIv,
      )
    if (ok) {
      _pinError.value = null
      _isVaultLocked.value = false
      _keyEpoch.update { it + 1 }
      val pending = _pendingPinProtectedAction.value
      _pendingPinProtectedAction.value = null
      pending?.invoke()
      return true
    } else {
      _pinError.value = "Hatalı PIN. Lütfen tekrar deneyin."
      return false
    }
  }

  fun dismissPinPrompt() {
    _pendingPinProtectedAction.value = null
    _pinError.value = null
  }

  fun openNewNoteEditor(startRecording: Boolean = false) {
    val defaultCat =
      if (startRecording) "cat_voice"
      else _selectedCategoryId.value ?: (categoriesState.value as? UiState.Success)?.data?.firstOrNull()?.id ?: "cat_personal"
    audioManager.clearRecordedAudio()
    _editorDraft.value =
      NoteEditorDraft(
        categoryId = defaultCat,
        startRecordingImmediately = startRecording,
      )
  }

  fun openNoteForEditing(note: DecryptedNote) {
    if (note.decryptionFailed) {
      _bannerMessage.value = "Bu not başka bir cihaz anahtarıyla şifrelenmiş. Güvenlik Merkezi'nden Kasa PIN'inizi doğrulayın."
      return
    }
    val openAction = {
      viewModelScope.launch {
        audioManager.clearRecordedAudio()
        if (note.hasAudio && !note.encryptedAudio.isNullOrBlank() && !note.audioIv.isNullOrBlank()) {
          val bytes =
            withContext(Dispatchers.Default) {
              cryptoManager.decryptBytes(note.encryptedAudio, note.audioIv)
            }
          audioManager.setExistingAudio(bytes, note.audioDurationMs)
        }
        _editorDraft.value =
          NoteEditorDraft(
            noteId = note.id,
            title = note.title,
            body = note.body,
            categoryId = note.categoryId,
            locked = note.locked,
            pinned = note.pinned,
            tags = note.tags,
          )
      }
      Unit
    }
    val cfg = vaultConfig.value
    if (note.locked && cfg?.pinEnabled == true && note.id !in _revealedLockedNoteIds.value) {
      _pinError.value = null
      _pendingPinProtectedAction.value = {
        _revealedLockedNoteIds.update { it + note.id }
        openAction()
      }
    } else {
      _revealedLockedNoteIds.update { it + note.id }
      openAction()
    }
  }

  fun revealOrPlayNoteAudio(note: DecryptedNote) {
    if (note.decryptionFailed) {
      _bannerMessage.value = "Şifreli ses çözülemedi. Kasa PIN'i ile anahtarı doğrulayın."
      return
    }
    val playAction = {
      val encAudio = note.encryptedAudio
      val iv = note.audioIv
      if (!encAudio.isNullOrBlank() && !iv.isNullOrBlank()) {
        viewModelScope.launch {
          val bytes =
            withContext(Dispatchers.Default) {
              cryptoManager.decryptBytes(encAudio, iv)
            }
          if (bytes != null) {
            audioManager.playBytes(note.id, bytes, note.audioDurationMs)
          } else {
            _bannerMessage.value = "Ses kaydı şifresi çözülemedi."
          }
        }
      }
      Unit
    }
    val cfg = vaultConfig.value
    if (note.locked && cfg?.pinEnabled == true && note.id !in _revealedLockedNoteIds.value) {
      _pinError.value = null
      _pendingPinProtectedAction.value = {
        _revealedLockedNoteIds.update { it + note.id }
        playAction()
      }
    } else {
      playAction()
    }
  }

  fun revealLockedCard(note: DecryptedNote) {
    val cfg = vaultConfig.value
    if (note.id in _revealedLockedNoteIds.value) {
      _revealedLockedNoteIds.update { it - note.id }
      return
    }
    if (cfg?.pinEnabled == true) {
      _pinError.value = null
      _pendingPinProtectedAction.value = {
        _revealedLockedNoteIds.update { it + note.id }
      }
    } else {
      _revealedLockedNoteIds.update { it + note.id }
    }
  }

  fun updateDraft(transform: (NoteEditorDraft) -> NoteEditorDraft) {
    _editorDraft.update { current -> current?.let(transform) }
  }

  fun closeEditor() {
    audioManager.clearRecordedAudio()
    _editorDraft.value = null
  }

  fun startRecording() {
    audioManager.startRecording()
  }

  fun stopRecording() {
    audioManager.stopRecording()
  }

  fun clearRecordedAudio() {
    audioManager.clearRecordedAudio()
  }

  fun toggleDraftAudioPlayback() {
    val rec = audioManager.recordingState.value
    val bytes = rec.recordedBytes ?: return
    audioManager.playBytes("draft_preview", bytes, rec.recordedDurationMs)
  }

  fun seekAudio(positionMs: Long) {
    audioManager.seekTo(positionMs)
  }

  fun saveDraftNote() {
    val draft = _editorDraft.value ?: return
    if (audioManager.recordingState.value.isRecording) {
      audioManager.stopRecording()
    }
    val recState = audioManager.recordingState.value
    val audioBytes = recState.recordedBytes
    val hasAudio = audioBytes != null && audioBytes.isNotEmpty()

    val cleanTitle = draft.title.trim().ifBlank {
      if (hasAudio) "Sesli Not" else ""
    }
    if (cleanTitle.isBlank()) {
      _bannerMessage.value = "Lütfen not için bir başlık girin."
      return
    }

    _editorDraft.update { it?.copy(isSaving = true) }

    viewModelScope.launch {
      val encryptedResult =
        withContext(Dispatchers.Default) {
          val encTitle = cryptoManager.encryptText(cleanTitle)
          val encBody =
            if (draft.body.isEmpty()) null else cryptoManager.encryptText(draft.body)
          val encAudio =
            if (hasAudio && audioBytes != null) cryptoManager.encryptBytes(audioBytes) else null
          Triple(encTitle, encBody, encAudio)
        }

      val (encTitle, encBody, encAudio) = encryptedResult
      if (encAudio != null && encAudio.ciphertextBase64.length > 740_000) {
        _editorDraft.update { it?.copy(isSaving = false) }
        _bannerMessage.value = "Ses kaydı bulut senkronizasyon boyut sınırını (750 KB) aşıyor. Daha kısa bir kayıt deneyin."
        return@launch
      }

      val result =
        repository.saveNote(
          existingNoteId = draft.noteId,
          categoryId = draft.categoryId,
          encryptedTitle = encTitle.ciphertextBase64,
          titleIv = encTitle.ivBase64,
          encryptedBody = encBody?.ciphertextBase64 ?: "",
          bodyIv = encBody?.ivBase64 ?: "",
          encryptedAudio = encAudio?.ciphertextBase64,
          audioIv = encAudio?.ivBase64,
          audioDurationMs = if (hasAudio) recState.recordedDurationMs else 0L,
          hasAudio = hasAudio,
          locked = draft.locked,
          pinned = draft.pinned,
          tags = draft.tags,
        )

      result.fold(
        onSuccess = {
          audioManager.clearRecordedAudio()
          _editorDraft.value = null
          _bannerMessage.value = "Not AES-256-GCM ile şifrelendi ve buluta senkronize edildi."
        },
        onFailure = { err ->
          _editorDraft.update { it?.copy(isSaving = false) }
          _bannerMessage.value = err.localizedMessage ?: "Not kaydedilemedi."
        },
      )
    }
  }

  fun togglePin(note: DecryptedNote) {
    viewModelScope.launch {
      repository.updateNoteFlags(noteId = note.id, pinned = !note.pinned).onFailure { err ->
        _bannerMessage.value = err.localizedMessage ?: "Sabitleme güncellenemedi."
      }
    }
  }

  fun toggleLock(note: DecryptedNote) {
    viewModelScope.launch {
      repository.updateNoteFlags(noteId = note.id, locked = !note.locked).onFailure { err ->
        _bannerMessage.value = err.localizedMessage ?: "Kilit durumu güncellenemedi."
      }
    }
  }

  fun deleteNote(noteId: String) {
    if (playbackState.value.activeId == noteId) {
      audioManager.stopPlayback()
    }
    viewModelScope.launch {
      repository.deleteNote(noteId).fold(
        onSuccess = {
          if (_editorDraft.value?.noteId == noteId) {
            _editorDraft.value = null
            audioManager.clearRecordedAudio()
          }
          _bannerMessage.value = "Şifreli not silindi."
        },
        onFailure = { err ->
          _bannerMessage.value = err.localizedMessage ?: "Not silinemedi."
        },
      )
    }
  }

  fun createCategory(name: String, colorHex: String, iconKey: String) {
    val currentCount = (categoriesState.value as? UiState.Success)?.data?.size?.toLong() ?: 5L
    viewModelScope.launch {
      repository.createCategory(name, colorHex, iconKey, currentCount).fold(
        onSuccess = { newId ->
          _selectedCategoryId.value = newId
          _bannerMessage.value = "\"$name\" kategorisi oluşturuldu."
        },
        onFailure = { err ->
          _bannerMessage.value = err.localizedMessage ?: "Kategori oluşturulamadı."
        },
      )
    }
  }

  fun deleteCategory(categoryId: String) {
    viewModelScope.launch {
      repository.deleteCategory(categoryId).fold(
        onSuccess = {
          if (_selectedCategoryId.value == categoryId) {
            _selectedCategoryId.value = null
          }
          _bannerMessage.value = "Kategori kaldırıldı."
        },
        onFailure = { err ->
          _bannerMessage.value = err.localizedMessage ?: "Kategori silinemedi."
        },
      )
    }
  }

  fun setVaultPin(pin: String, lockOnLaunch: Boolean) {
    viewModelScope.launch {
      val bundle =
        withContext(Dispatchers.Default) {
          cryptoManager.createPinProtectionBundle(pin)
        }
      val hasExisting = vaultConfig.value != null
      repository
        .saveVaultConfig(
          hasExistingDoc = hasExisting,
          pinEnabled = true,
          pinSalt = bundle.pinSaltBase64,
          pinHash = bundle.pinHashBase64,
          wrappedVaultKey = bundle.wrappedVaultKeyBase64,
          wrappedVaultKeyIv = bundle.wrappedVaultKeyIvBase64,
          lockOnLaunch = lockOnLaunch,
        )
        .fold(
          onSuccess = {
            _bannerMessage.value = "Kasa PIN'i ve bulut anahtar koruması etkinleştirildi."
          },
          onFailure = { err ->
            _bannerMessage.value = err.localizedMessage ?: "Kasa PIN'i kaydedilemedi."
          },
        )
    }
  }

  fun disableVaultPin() {
    val cfg = vaultConfig.value ?: return
    viewModelScope.launch {
      repository
        .saveVaultConfig(
          hasExistingDoc = true,
          pinEnabled = false,
          pinSalt = "",
          pinHash = "",
          wrappedVaultKey = null,
          wrappedVaultKeyIv = null,
          lockOnLaunch = false,
        )
        .fold(
          onSuccess = {
            _isVaultLocked.value = false
            _bannerMessage.value = "Kasa PIN kilidi devre dışı bırakıldı."
          },
          onFailure = { err ->
            _bannerMessage.value = err.localizedMessage ?: "Ayar güncellenemedi."
          },
        )
    }
  }

  fun toggleLockOnLaunch(enabled: Boolean) {
    val cfg = vaultConfig.value ?: return
    if (!cfg.pinEnabled) return
    viewModelScope.launch {
      repository
        .saveVaultConfig(
          hasExistingDoc = true,
          pinEnabled = true,
          pinSalt = cfg.pinSalt,
          pinHash = cfg.pinHash,
          wrappedVaultKey = cfg.wrappedVaultKey,
          wrappedVaultKeyIv = cfg.wrappedVaultKeyIv,
          lockOnLaunch = enabled,
        )
        .onFailure { err ->
          _bannerMessage.value = err.localizedMessage ?: "Ayar güncellenemedi."
        }
    }
  }

  override fun onCleared() {
    super.onCleared()
    audioManager.release()
  }

  companion object {
    private const val TAG = "VaultViewModel"
    private const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
