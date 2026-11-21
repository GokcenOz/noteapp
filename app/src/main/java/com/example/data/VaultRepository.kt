package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class NotesSnapshotBatch(
  val notes: List<NoteDoc>,
  val syncStatus: SyncStatus,
)

class VaultRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(
    context: Context,
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("Firestore erişimi için Google ile giriş yapmalısınız.")
  }

  fun observeNotes(userId: String): Flow<NotesSnapshotBatch> = flow {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val path = "users/$userId/notes"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("notes")
        .snapshots(MetadataChanges.INCLUDE)
        .map { snapshot ->
          val items =
            snapshot.toObjects(
              NoteDoc::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          val syncStatus =
            when {
              snapshot.metadata.hasPendingWrites() -> SyncStatus.SYNCING
              snapshot.metadata.isFromCache -> SyncStatus.OFFLINE_CACHE
              else -> SyncStatus.SYNCED
            }
          NotesSnapshotBatch(notes = items, syncStatus = syncStatus)
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeCategories(userId: String): Flow<List<CategoryDoc>> = flow {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val path = "users/$userId/categories"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("categories")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              CategoryDoc::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedBy { it.sortOrder }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeVaultConfig(userId: String): Flow<VaultConfigDoc?> = flow {
    require(userId.isNotBlank()) { "userId must not be blank" }
    val path = "users/$userId/vaultConfig/settings"
    emitAll(
      db.collection("users")
        .document(userId)
        .collection("vaultConfig")
        .document("settings")
        .snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              VaultConfigDoc::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  suspend fun ensureStarterCategories(userId: String): Result<Unit> {
    val path = "users/$userId/categories"
    return try {
      val uid = requireUserId()
      check(uid == userId) { "Signed-in user mismatch" }
      val colRef = db.collection("users").document(uid).collection("categories")
      val existing = colRef.limit(1).get().await()
      if (!existing.isEmpty) return Result.success(Unit)

      val starters =
        listOf(
          StarterCategory("cat_personal", "Kişisel", "#10B981", "person", 0L),
          StarterCategory("cat_work", "İş & Projeler", "#38BDF8", "work", 1L),
          StarterCategory("cat_finance", "Finans & Şifreler", "#F59E0B", "finance", 2L),
          StarterCategory("cat_ideas", "Fikirler", "#A78BFA", "idea", 3L),
          StarterCategory("cat_voice", "Sesli Kayıtlar", "#F43F5E", "mic", 4L),
        )
      val batch = db.batch()
      for (cat in starters) {
        val docRef = colRef.document(cat.id)
        batch.set(
          docRef,
          mapOf(
            "userId" to uid,
            "name" to cat.name,
            "colorHex" to cat.colorHex,
            "iconKey" to cat.iconKey,
            "sortOrder" to cat.sortOrder,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun createCategory(
    name: String,
    colorHex: String,
    iconKey: String,
    sortOrder: Long,
  ): Result<String> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories"
    return try {
      val uid = requireUserId()
      val trimmed = name.trim()
      require(trimmed.length in 1..40) { "Kategori adı 1 ile 40 karakter arasında olmalıdır." }
      val docRef = db.collection("users").document(uid).collection("categories").document()
      path = docRef.path
      docRef
        .set(
          mapOf(
            "userId" to uid,
            "name" to trimmed,
            "colorHex" to colorHex.take(9),
            "iconKey" to iconKey.take(32),
            "sortOrder" to sortOrder.coerceIn(0L, 1000L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteCategory(categoryId: String): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories/$categoryId"
    return try {
      val uid = requireUserId()
      require(categoryId.isNotBlank())
      val docRef = db.collection("users").document(uid).collection("categories").document(categoryId)
      path = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun saveNote(
    existingNoteId: String?,
    categoryId: String,
    encryptedTitle: String,
    titleIv: String,
    encryptedBody: String,
    bodyIv: String,
    encryptedAudio: String?,
    audioIv: String?,
    audioDurationMs: Long,
    hasAudio: Boolean,
    locked: Boolean,
    pinned: Boolean,
    tags: List<String>,
  ): Result<String> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notes"
    return try {
      val uid = requireUserId()
      require(categoryId.isNotBlank() && categoryId.length <= 128) { "Geçersiz kategori." }
      require(encryptedTitle.isNotBlank() && encryptedTitle.length <= 2000) {
        "Başlık şifrelenemedi veya sınırı aştı."
      }
      require(titleIv.length in 8..64) { "Geçersiz şifreleme IV." }
      require(encryptedBody.length <= 100_000) { "Not içeriği çok uzun." }
      if (hasAudio) {
        require(!encryptedAudio.isNullOrBlank() && encryptedAudio.length <= 750_000) {
          "Ses kaydı boyutu bulut sınırını aşıyor."
        }
        require(!audioIv.isNullOrBlank() && audioIv.length <= 64) { "Geçersiz ses IV." }
      }
      val cleanTags = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
      val safeDuration = if (hasAudio) audioDurationMs.coerceIn(0L, 180_000L) else 0L

      val notesCol = db.collection("users").document(uid).collection("notes")
      if (existingNoteId.isNullOrBlank()) {
        val docRef = notesCol.document()
        path = docRef.path
        val payload =
          mutableMapOf<String, Any?>(
              "userId" to uid,
              "categoryId" to categoryId,
              "encryptedTitle" to encryptedTitle,
              "titleIv" to titleIv,
              "encryptedBody" to encryptedBody,
              "bodyIv" to bodyIv,
              "encryptedAudio" to if (hasAudio) encryptedAudio else null,
              "audioIv" to if (hasAudio) audioIv else null,
              "audioDurationMs" to safeDuration,
              "hasAudio" to hasAudio,
              "locked" to locked,
              "pinned" to pinned,
              "encryptionVersion" to 1L,
              "tags" to cleanTags,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
            .filterValues { it != null }
        docRef.set(payload).await()
        Result.success(docRef.id)
      } else {
        val docRef = notesCol.document(existingNoteId)
        path = docRef.path
        val updates =
          mutableMapOf<String, Any>(
            "categoryId" to categoryId,
            "encryptedTitle" to encryptedTitle,
            "titleIv" to titleIv,
            "encryptedBody" to encryptedBody,
            "bodyIv" to bodyIv,
            "audioDurationMs" to safeDuration,
            "hasAudio" to hasAudio,
            "locked" to locked,
            "pinned" to pinned,
            "encryptionVersion" to 1L,
            "tags" to cleanTags,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        if (hasAudio && encryptedAudio != null && audioIv != null) {
          updates["encryptedAudio"] = encryptedAudio
          updates["audioIv"] = audioIv
        } else {
          updates["encryptedAudio"] = FieldValue.delete()
          updates["audioIv"] = FieldValue.delete()
        }
        docRef.update(updates).await()
        Result.success(existingNoteId)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (existingNoteId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
        path,
      )
      Result.failure(e)
    }
  }

  suspend fun updateNoteFlags(
    noteId: String,
    pinned: Boolean? = null,
    locked: Boolean? = null,
  ): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notes/$noteId"
    return try {
      val uid = requireUserId()
      require(noteId.isNotBlank())
      val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
      path = docRef.path
      val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
      if (pinned != null) updates["pinned"] = pinned
      if (locked != null) updates["locked"] = locked
      docRef.update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteNote(noteId: String): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/notes/$noteId"
    return try {
      val uid = requireUserId()
      require(noteId.isNotBlank())
      val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
      path = docRef.path
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun saveVaultConfig(
    hasExistingDoc: Boolean,
    pinEnabled: Boolean,
    pinSalt: String,
    pinHash: String,
    wrappedVaultKey: String?,
    wrappedVaultKeyIv: String?,
    lockOnLaunch: Boolean,
  ): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/vaultConfig/settings"
    return try {
      val uid = requireUserId()
      val docRef =
        db.collection("users").document(uid).collection("vaultConfig").document("settings")
      path = docRef.path
      if (!hasExistingDoc) {
        val payload =
          mutableMapOf<String, Any?>(
              "userId" to uid,
              "pinEnabled" to pinEnabled,
              "pinSalt" to pinSalt,
              "pinHash" to pinHash,
              "wrappedVaultKey" to wrappedVaultKey,
              "wrappedVaultKeyIv" to wrappedVaultKeyIv,
              "lockOnLaunch" to lockOnLaunch,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
            .filterValues { it != null }
        docRef.set(payload).await()
      } else {
        val updates =
          mutableMapOf<String, Any>(
            "pinEnabled" to pinEnabled,
            "pinSalt" to pinSalt,
            "pinHash" to pinHash,
            "lockOnLaunch" to lockOnLaunch,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        if (wrappedVaultKey != null && wrappedVaultKeyIv != null) {
          updates["wrappedVaultKey"] = wrappedVaultKey
          updates["wrappedVaultKeyIv"] = wrappedVaultKeyIv
        } else {
          updates["wrappedVaultKey"] = FieldValue.delete()
          updates["wrappedVaultKeyIv"] = FieldValue.delete()
        }
        docRef.update(updates).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (hasExistingDoc) OperationType.UPDATE else OperationType.CREATE,
        path,
      )
      Result.failure(e)
    }
  }

  private data class StarterCategory(
    val id: String,
    val name: String,
    val colorHex: String,
    val iconKey: String,
    val sortOrder: Long,
  )
}
