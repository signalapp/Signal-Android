package org.thoughtcrime.securesms.payments.razorpay

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.signal.core.util.logging.Log
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Manages secure storage and retrieval of Razorpay API keys.
 * Uses Android's EncryptedSharedPreferences for AES-256 encryption.
 */
class ApiKeyManager(context: Context) {

  companion object {
    private val TAG = Log.tag(ApiKeyManager::class.java)
    private const val PREFS_NAME = "razorpay_api_keys"
    private const val KEY_API_KEY = "api_key_encrypted"
    private const val KEY_API_SECRET = "api_secret_encrypted"
    private const val KEY_LAST_VALIDATED = "last_validated_timestamp"
  }

  private val encryptedPrefs: SharedPreferences = try {
    val masterKey = MasterKey.Builder(context)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()

    EncryptedSharedPreferences.create(
      context,
      PREFS_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
  } catch (e: GeneralSecurityException) {
    Log.e(TAG, "Failed to initialize encrypted preferences", e)
    throw ApiKeyException("Unable to initialize secure storage", e)
  } catch (e: IOException) {
    Log.e(TAG, "Failed to initialize encrypted preferences", e)
    throw ApiKeyException("Unable to initialize secure storage", e)
  }

  /**
   * Stores Razorpay API key securely
   * @param apiKey The Razorpay API key
   * @param apiSecret The Razorpay API secret key
   * @throws ApiKeyException if validation fails
   */
  fun storeApiKeys(apiKey: String, apiSecret: String) {
    // Validate API key format
    if (!RazorpayConfig.isValidApiKey(apiKey)) {
      throw ApiKeyException("Invalid API key format")
    }

    if (!RazorpayConfig.isValidApiKey(apiSecret)) {
      throw ApiKeyException("Invalid API secret format")
    }

    try {
      encryptedPrefs.edit().apply {
        putString(KEY_API_KEY, apiKey)
        putString(KEY_API_SECRET, apiSecret)
        putLong(KEY_LAST_VALIDATED, System.currentTimeMillis())
        apply()
      }
      Log.i(TAG, "API keys stored securely")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to store API keys", e)
      throw ApiKeyException("Failed to store API keys", e)
    }
  }

  /**
   * Retrieves the stored Razorpay API key
   * @return The API key or null if not configured
   */
  fun getApiKey(): String? {
    return try {
      encryptedPrefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to retrieve API key", e)
      null
    }
  }

  /**
   * Retrieves the stored Razorpay API secret
   * @return The API secret or null if not configured
   */
  fun getApiSecret(): String? {
    return try {
      encryptedPrefs.getString(KEY_API_SECRET, null)?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to retrieve API secret", e)
      null
    }
  }

  /**
   * Checks if API keys are configured
   */
  fun hasApiKeys(): Boolean {
    return getApiKey() != null && getApiSecret() != null
  }

  /**
   * Get last validation timestamp
   */
  fun getLastValidationTime(): Long {
    return encryptedPrefs.getLong(KEY_LAST_VALIDATED, 0)
  }

  /**
   * Updates validation timestamp (called after successful API connection test)
   */
  fun updateValidationTimestamp() {
    try {
      encryptedPrefs.edit().apply {
        putLong(KEY_LAST_VALIDATED, System.currentTimeMillis())
        apply()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to update validation timestamp", e)
    }
  }

  /**
   * Clears all stored API keys
   */
  fun clearApiKeys() {
    try {
      encryptedPrefs.edit().apply {
        remove(KEY_API_KEY)
        remove(KEY_API_SECRET)
        remove(KEY_LAST_VALIDATED)
        apply()
      }
      Log.i(TAG, "API keys cleared")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to clear API keys", e)
    }
  }

  /**
   * Validates API key format and connectivity
   * @param apiKey The API key to validate
   * @return true if valid, false otherwise
   */
  fun validateApiKey(apiKey: String): Boolean {
    return try {
      if (!RazorpayConfig.isValidApiKey(apiKey)) {
        Log.w(TAG, "API key failed format validation")
        return false
      }

      // Additional validation could be added here to test connectivity
      // For now, we validate format and will test connectivity when actually used
      true
    } catch (e: Exception) {
      Log.e(TAG, "Error validating API key", e)
      false
    }
  }

  /**
   * Custom exception for API key operations
   */
  class ApiKeyException(message: String, cause: Exception? = null) :
    Exception(message, cause)
}
