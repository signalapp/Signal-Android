package org.signal.registration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Activity entry point for the registration flow.
 *
 * This activity can be launched from the main app to start the registration process.
 * Upon successful completion, it will return RESULT_OK.
 */
class RegistrationActivity : ComponentActivity() {

  private val repository: RegistrationRepository by lazy {
    RegistrationRepository(
      networkController = RegistrationDependencies.get().networkController,
      storageController = RegistrationDependencies.get().storageController
    )
  }

  private val viewModel: RegistrationViewModel by viewModels(factoryProducer = {
    RegistrationViewModel.Factory(
      repository = repository
    )
  })

  @OptIn(ExperimentalPermissionsApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      SignalTheme(incognitoKeyboardEnabled = false) {
        Surface {
          RegistrationNavHost(
            registrationRepository = repository,
            modifier = Modifier.fillMaxSize(),
            onRegistrationComplete = {
              setResult(RESULT_OK)
              finish()
            }
          )
        }
      }
    }
  }

  companion object {
    /**
     * Creates an intent to launch the RegistrationActivity.
     *
     * @param context The context used to create the intent.
     * @return An intent that can be used to start the RegistrationActivity.
     */
    fun createIntent(context: Context): Intent {
      return Intent(context, RegistrationActivity::class.java)
    }
  }

  /**
   * Activity result contract for launching the registration flow.
   *
   * Usage:
   * ```
   * val registrationLauncher = registerForActivityResult(RegistrationContract()) { success ->
   *   if (success) {
   *     // Registration completed successfully
   *   } else {
   *     // Registration was cancelled or failed
   *   }
   * }
   *
   * registrationLauncher.launch(Unit)
   * ```
   */
  class RegistrationContract : ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit): Intent {
      return createIntent(context)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
      return resultCode == RESULT_OK
    }
  }
}
