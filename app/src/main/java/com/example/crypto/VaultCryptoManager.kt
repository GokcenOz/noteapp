package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
  val ciphertextBase64: String,
  val ivBase64: String,
)

data class PinProtectionBundle(
  val pinSaltBase64: String,
  val pinHashBase64: String,
  val wrappedVaultKeyBase64: String,
  val wrappedVaultKeyIvBase64: String,
)

class VaultCryptoManager(
  context: Context,
  private val userId: String,
) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val secureRandom = SecureRandom()

  @Volatile private var cachedMasterKey: SecretKey? = null

  private val keyAlias: String
    get() = "notebank_device_wrap_$userId"

  private val masterKeyPrefKey: String
    get() = "wrapped_master_key_$userId"

  private val masterIvPrefKey: String
    get() = "wrapped_master_iv_$userId"

  @Synchronized
  fun getOrCreateMasterKey(): SecretKey {
    cachedMasterKey?.let { return it }

    val existingCipher = prefs.getString(masterKeyPrefKey, null)
    val existingIv = prefs.getString(masterIvPrefKey, null)
    if (!existingCipher.isNullOrBlank() && !existingIv.isNullOrBlank()) {
      try {
        val deviceKey = getOrCreateDeviceKeyStoreKey()
        val unwrapped = decryptBytesWithKey(deviceKey, existingCipher, existingIv)
        if (unwrapped != null && unwrapped.size == 32) {
          val key = SecretKeySpec(unwrapped, "AES")
          cachedMasterKey = key
          return key
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to unwrap stored master key, regenerating local key", e)
      }
    }

    val rawKey = ByteArray(32).also { secureRandom.nextBytes(it) }
    val newMasterKey = SecretKeySpec(rawKey, "AES")
    persistMasterKeyLocally(newMasterKey)
    cachedMasterKey = newMasterKey
    return newMasterKey
  }

  private fun persistMasterKeyLocally(masterKey: SecretKey) {
    try {
      val deviceKey = getOrCreateDeviceKeyStoreKey()
      val wrapped = encryptBytesWithKey(deviceKey, masterKey.encoded)
      prefs.edit()
        .putString(masterKeyPrefKey, wrapped.ciphertextBase64)
        .putString(masterIvPrefKey, wrapped.ivBase64)
        .apply()
    } catch (e: Exception) {
      Log.e(TAG, "Unable to persist wrapped master key in KeyStore", e)
    }
  }

  private fun getOrCreateDeviceKeyStoreKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(keyAlias, null) as? SecretKey
    if (existing != null) return existing

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          keyAlias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  fun encryptText(plaintext: String): EncryptedPayload {
    val key = getOrCreateMasterKey()
    return encryptBytesWithKey(key, plaintext.toByteArray(Charsets.UTF_8))
  }

  fun decryptText(ciphertextBase64: String, ivBase64: String): String? {
    if (ciphertextBase64.isBlank() || ivBase64.isBlank()) return ""
    val key = getOrCreateMasterKey()
    val plain = decryptBytesWithKey(key, ciphertextBase64, ivBase64) ?: return null
    return String(plain, Charsets.UTF_8)
  }

  fun encryptBytes(plainBytes: ByteArray): EncryptedPayload {
    val key = getOrCreateMasterKey()
    return encryptBytesWithKey(key, plainBytes)
  }

  fun decryptBytes(ciphertextBase64: String, ivBase64: String): ByteArray? {
    if (ciphertextBase64.isBlank() || ivBase64.isBlank()) return null
    val key = getOrCreateMasterKey()
    return decryptBytesWithKey(key, ciphertextBase64, ivBase64)
  }

  fun createPinProtectionBundle(pin: String): PinProtectionBundle {
    require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN 4 rakamdan oluşmalıdır." }
    val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
    val kek = deriveKeyFromPin(pin, salt)
    val pinHash = hashPinVerification(pin, salt)
    val masterKey = getOrCreateMasterKey()
    val wrapped = encryptBytesWithKey(kek, masterKey.encoded)
    return PinProtectionBundle(
      pinSaltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
      pinHashBase64 = Base64.encodeToString(pinHash, Base64.NO_WRAP),
      wrappedVaultKeyBase64 = wrapped.ciphertextBase64,
      wrappedVaultKeyIvBase64 = wrapped.ivBase64,
    )
  }

  fun verifyPinAndUnwrapKey(
    pin: String,
    pinSaltBase64: String,
    pinHashBase64: String,
    wrappedVaultKeyBase64: String?,
    wrappedVaultKeyIvBase64: String?,
  ): Boolean {
    if (pinSaltBase64.isBlank() || pinHashBase64.isBlank()) return false
    return try {
      val salt = Base64.decode(pinSaltBase64, Base64.NO_WRAP)
      val expectedHash = Base64.decode(pinHashBase64, Base64.NO_WRAP)
      val actualHash = hashPinVerification(pin, salt)
      if (!MessageDigest.isEqual(expectedHash, actualHash)) {
        return false
      }
      if (!wrappedVaultKeyBase64.isNullOrBlank() && !wrappedVaultKeyIvBase64.isNullOrBlank()) {
        val kek = deriveKeyFromPin(pin, salt)
        val unwrappedBytes =
          decryptBytesWithKey(kek, wrappedVaultKeyBase64, wrappedVaultKeyIvBase64)
        if (unwrappedBytes != null && unwrappedBytes.size == 32) {
          val restoredKey = SecretKeySpec(unwrappedBytes, "AES")
          cachedMasterKey = restoredKey
          persistMasterKeyLocally(restoredKey)
        }
      }
      true
    } catch (e: Exception) {
      Log.w(TAG, "PIN verification failed", e)
      false
    }
  }

  fun keyFingerprint(): String {
    return try {
      val digest = MessageDigest.getInstance("SHA-256").digest(getOrCreateMasterKey().encoded)
      digest.take(8).joinToString(":") { "%02X".format(it) }
    } catch (_: Exception) {
      "AES-256-GCM"
    }
  }

  private fun deriveKeyFromPin(pin: String, salt: ByteArray): SecretKey {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
    val bytes = factory.generateSecret(spec).encoded
    return SecretKeySpec(bytes, "AES")
  }

  private fun hashPinVerification(pin: String, salt: ByteArray): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    // Separate verification domain tag so verification hash != key-encryption key
    val taggedPin = "notebank_verify:$pin".toCharArray()
    val spec = PBEKeySpec(taggedPin, salt, PBKDF2_ITERATIONS, 256)
    return factory.generateSecret(spec).encoded
  }

  private fun encryptBytesWithKey(key: SecretKey, plainBytes: ByteArray): EncryptedPayload {
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(plainBytes)
    return EncryptedPayload(
      ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
      ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
    )
  }

  private fun decryptBytesWithKey(
    key: SecretKey,
    ciphertextBase64: String,
    ivBase64: String,
  ): ByteArray? {
    return try {
      val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
      val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
      val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
      val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
      cipher.init(Cipher.DECRYPT_MODE, key, spec)
      cipher.doFinal(ciphertext)
    } catch (e: Exception) {
      Log.w(TAG, "AES-GCM decryption failed", e)
      null
    }
  }

  companion object {
    private const val TAG = "VaultCryptoManager"
    private const val PREFS_NAME = "notebank_vault_keystore_prefs"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 50_000
  }
}
