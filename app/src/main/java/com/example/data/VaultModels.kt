package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class VaultConfigDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val pinEnabled: Boolean = false,
  val pinSalt: String = "",
  val pinHash: String = "",
  val wrappedVaultKey: String? = null,
  val wrappedVaultKeyIv: String? = null,
  val lockOnLaunch: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CategoryDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val colorHex: String = "#10B981",
  val iconKey: String = "folder",
  val sortOrder: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class NoteDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val categoryId: String = "",
  val encryptedTitle: String = "",
  val titleIv: String = "",
  val encryptedBody: String = "",
  val bodyIv: String = "",
  val encryptedAudio: String? = null,
  val audioIv: String? = null,
  val audioDurationMs: Long = 0L,
  val hasAudio: Boolean = false,
  val locked: Boolean = false,
  val pinned: Boolean = false,
  val encryptionVersion: Long = 1L,
  val tags: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class DecryptedNote(
  val id: String,
  val categoryId: String,
  val title: String,
  val body: String,
  val encryptedAudio: String?,
  val audioIv: String?,
  val audioDurationMs: Long,
  val hasAudio: Boolean,
  val locked: Boolean,
  val pinned: Boolean,
  val tags: List<String>,
  val decryptionFailed: Boolean,
  val createdAt: Timestamp?,
  val updatedAt: Timestamp?,
)

enum class SyncStatus {
  SYNCED,
  SYNCING,
  OFFLINE_CACHE,
}

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}
