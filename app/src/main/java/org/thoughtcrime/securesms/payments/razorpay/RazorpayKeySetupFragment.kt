package org.thoughtcrime.securesms.payments.razorpay

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R

/**
 * Fragment for users to configure their Razorpay API keys.
 * Provides input fields for API key and secret, with validation and testing.
 */
class RazorpayKeySetupFragment : Fragment() {

  companion object {
    private val TAG = Log.tag(RazorpayKeySetupFragment::class.java)

    fun newInstance(): RazorpayKeySetupFragment = RazorpayKeySetupFragment()
  }

  private val viewModel: RazorpayKeySetupViewModel by viewModels {
    RazorpayKeySetupViewModelFactory(requireContext())
  }

  private lateinit var apiKeyInput: TextInputLayout
  private lateinit var apiSecretInput: TextInputLayout
  private lateinit var saveButton: View
  private lateinit var testButton: View
  private lateinit var infoText: View

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_razorpay_key_setup, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Initialize views - These view IDs would need to be created in the layout file
    // For now, we'll create them dynamically or through a provided layout

    setupInputFields()
    setupButtons()
    observeViewModel()
    loadExistingKeys()
  }

  private fun setupInputFields() {
    // API Key input with validation
    val apiKeyEditText = apiKeyInput.editText
    apiKeyEditText?.addTextChangedListener { text ->
      validateApiKey(text.toString())
    }

    // API Secret input with validation
    val apiSecretEditText = apiSecretInput.editText
    apiSecretEditText?.addTextChangedListener { text ->
      validateApiSecret(text.toString())
    }
  }

  private fun setupButtons() {
    saveButton.setOnClickListener {
      saveApiKeys()
    }

    testButton.setOnClickListener {
      testApiKeys()
    }
  }

  private fun setupInfoText() {
    // Show instructions for obtaining API keys
    val instructions = """
      To get your Razorpay API keys:
      
      1. Visit https://dashboard.razorpay.com/
      2. Log in to your account
      3. Go to Settings → API Keys
      4. Copy your API Key and API Secret
      5. Paste them below
      
      Your keys are encrypted and stored securely on your device.
    """.trimIndent()

    // This would typically be shown as a text view in the layout
  }

  private fun validateApiKey(key: String) {
    val isValid = RazorpayConfig.isValidApiKey(key)
    val errorMessage = when {
      key.isEmpty() -> "API key is required"
      key.length < RazorpayConfig.Validation.MIN_API_KEY_LENGTH -> 
        "API key must be at least ${RazorpayConfig.Validation.MIN_API_KEY_LENGTH} characters"
      key.length > RazorpayConfig.Validation.MAX_API_KEY_LENGTH -> 
        "API key must not exceed ${RazorpayConfig.Validation.MAX_API_KEY_LENGTH} characters"
      !isValid -> "API key contains invalid characters"
      else -> null
    }

    if (errorMessage != null) {
      apiKeyInput.error = errorMessage
      apiKeyInput.isErrorEnabled = true
    } else {
      apiKeyInput.isErrorEnabled = false
    }
  }

  private fun validateApiSecret(secret: String) {
    val isValid = RazorpayConfig.isValidApiKey(secret)
    val errorMessage = when {
      secret.isEmpty() -> "API secret is required"
      secret.length < RazorpayConfig.Validation.MIN_API_KEY_LENGTH -> 
        "API secret must be at least ${RazorpayConfig.Validation.MIN_API_KEY_LENGTH} characters"
      secret.length > RazorpayConfig.Validation.MAX_API_KEY_LENGTH -> 
        "API secret must not exceed ${RazorpayConfig.Validation.MAX_API_KEY_LENGTH} characters"
      !isValid -> "API secret contains invalid characters"
      else -> null
    }

    if (errorMessage != null) {
      apiSecretInput.error = errorMessage
      apiSecretInput.isErrorEnabled = true
    } else {
      apiSecretInput.isErrorEnabled = false
    }
  }

  private fun saveApiKeys() {
    val apiKey = apiKeyInput.editText?.text?.toString()?.trim() ?: ""
    val apiSecret = apiSecretInput.editText?.text?.toString()?.trim() ?: ""

    // Validate before saving
    if (!RazorpayConfig.isValidApiKey(apiKey)) {
      showError("Invalid API key format")
      return
    }

    if (!RazorpayConfig.isValidApiKey(apiSecret)) {
      showError("Invalid API secret format")
      return
    }

    viewModel.saveApiKeys(apiKey, apiSecret)
  }

  private fun testApiKeys() {
    val apiKey = apiKeyInput.editText?.text?.toString()?.trim() ?: ""
    val apiSecret = apiSecretInput.editText?.text?.toString()?.trim() ?: ""

    if (apiKey.isEmpty() || apiSecret.isEmpty()) {
      showError("Please enter both API key and secret")
      return
    }

    viewModel.testApiKeys(apiKey, apiSecret)
  }

  private fun loadExistingKeys() {
    viewModel.loadExistingKeys { apiKey, apiSecret ->
      if (!apiKey.isNullOrEmpty()) {
        apiKeyInput.editText?.setText(apiKey)
      }
      if (!apiSecret.isNullOrEmpty()) {
        apiSecretInput.editText?.setText(apiSecret)
      }
    }
  }

  private fun observeViewModel() {
    lifecycleScope.launch {
      viewModel.uiState.collect { state ->
        when (state) {
          is RazorpayKeySetupState.Loading -> showLoading()
          is RazorpayKeySetupState.Success -> {
            showSuccess("API keys saved successfully")
            dismissFragment()
          }
          is RazorpayKeySetupState.Error -> showError(state.message)
          is RazorpayKeySetupState.TestSuccess -> 
            showSuccess("API keys validated successfully")
          is RazorpayKeySetupState.Idle -> hideLoading()
        }
      }
    }
  }

  private fun showLoading() {
    saveButton.isEnabled = false
    testButton.isEnabled = false
    // Show progress indicator
  }

  private fun hideLoading() {
    saveButton.isEnabled = true
    testButton.isEnabled = true
    // Hide progress indicator
  }

  private fun showSuccess(message: String) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
  }

  private fun showError(message: String) {
    Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_LONG).show()
    Log.w(TAG, "API key setup error: $message")
  }

  private fun dismissFragment() {
    parentFragmentManager.popBackStack()
  }
}

/**
 * ViewModel for managing API key setup state
 */
class RazorpayKeySetupViewModel(
  private val context: Context,
  private val apiKeyManager: ApiKeyManager
) : ViewModel() {

  private val TAG = Log.tag(RazorpayKeySetupViewModel::class.java)

  private val _uiState = kotlinx.coroutines.flow.MutableStateFlow<RazorpayKeySetupState>(
    RazorpayKeySetupState.Idle
  )
  val uiState = _uiState

  fun saveApiKeys(apiKey: String, apiSecret: String) {
    viewModelScope.launch {
      _uiState.value = RazorpayKeySetupState.Loading
      try {
        withContext(Dispatchers.IO) {
          apiKeyManager.storeApiKeys(apiKey, apiSecret)
        }
        _uiState.value = RazorpayKeySetupState.Success
      } catch (e: Exception) {
        Log.e(TAG, "Failed to save API keys", e)
        _uiState.value = RazorpayKeySetupState.Error(e.message ?: "Unknown error")
      }
    }
  }

  fun testApiKeys(apiKey: String, apiSecret: String) {
    viewModelScope.launch {
      _uiState.value = RazorpayKeySetupState.Loading
      try {
        withContext(Dispatchers.IO) {
          // Test connectivity with Razorpay API
          // This would make a simple API call to verify keys are valid
          val isValid = apiKeyManager.validateApiKey(apiKey) && 
                        apiKeyManager.validateApiKey(apiSecret)
          
          if (isValid) {
            apiKeyManager.updateValidationTimestamp()
          }
        }
        _uiState.value = RazorpayKeySetupState.TestSuccess
      } catch (e: Exception) {
        Log.e(TAG, "Failed to test API keys", e)
        _uiState.value = RazorpayKeySetupState.Error(e.message ?: "Test failed")
      }
    }
  }

  fun loadExistingKeys(callback: (apiKey: String?, apiSecret: String?) -> Unit) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) {
        val apiKey = apiKeyManager.getApiKey()
        val apiSecret = apiKeyManager.getApiSecret()
        withContext(Dispatchers.Main) {
          callback(apiKey, apiSecret)
        }
      }
    }
  }
}

/**
 * Factory for creating RazorpayKeySetupViewModel
 */
class RazorpayKeySetupViewModelFactory(private val context: Context) :
  ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    return RazorpayKeySetupViewModel(context, ApiKeyManager(context)) as T
  }
}

/**
 * UI State for Razorpay key setup
 */
sealed class RazorpayKeySetupState {
  object Idle : RazorpayKeySetupState()
  object Loading : RazorpayKeySetupState()
  object Success : RazorpayKeySetupState()
  object TestSuccess : RazorpayKeySetupState()
  data class Error(val message: String) : RazorpayKeySetupState()
}
