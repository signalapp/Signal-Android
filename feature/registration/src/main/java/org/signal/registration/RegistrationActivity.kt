package org.signal.registration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.signal.core.ui.compose.theme.SignalTheme

/**
 * Activity entry point for the registration flow.
 *
 * This activity can be launched from the main app to start the registration process.
 * Upon successful completion, it will return RESULT_OK and, if provided via [createIntent], launch the next intent to
 * route the user back into the main app.
 */
class RegistrationActivity : ComponentActivity() {

  companion object {
    private const val NEXT_INTENT_EXTRA = "next_intent"
    private const val START_DESTINATION_EXTRA = "start_destination"
    private const val START_FRESH_EXTRA = "start_fresh"

    /**
     * @param nextIntent An optional intent to launch once registration completes successfully. This is how the caller
     *   (which lives outside this module) routes the user back into the main app, since the launching activity will
     *   typically have finished itself.
     * @param startDestination An optional route to open directly instead of resuming a previous flow. Used, for example,
     *   to send a deregistered linked device straight to the link-device screen.
     * @param startFresh When true, any persisted registration data is not restored and the user starts the flow fresh
     *   from the beginning.
     */
    @JvmStatic
    @JvmOverloads
    fun createIntent(context: Context, nextIntent: Intent? = null, startDestination: RegistrationRoute? = null, startFresh: Boolean = false): Intent {
      return Intent(context, RegistrationActivity::class.java).apply {
        if (nextIntent != null) {
          putExtra(NEXT_INTENT_EXTRA, nextIntent)
        }
        if (startDestination != null) {
          putExtra(START_DESTINATION_EXTRA, startDestination)
        }
        putExtra(START_FRESH_EXTRA, startFresh)
      }
    }
  }

  private val repository: RegistrationRepository by lazy {
    RegistrationRepository(
      context = this.application,
      networkController = RegistrationDependencies.get().networkController,
      storageController = RegistrationDependencies.get().storageController,
      isLinkAndSyncAvailable = RegistrationDependencies.get().isLinkAndSyncAvailable
    )
  }

  @OptIn(ExperimentalPermissionsApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    val startDestination = IntentCompat.getParcelableExtra(intent, START_DESTINATION_EXTRA, RegistrationRoute::class.java)
    val startFresh = intent.getBooleanExtra(START_FRESH_EXTRA, false)

    setContent {
      SignalTheme(incognitoKeyboardEnabled = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
          RegistrationNavHost(
            registrationRepository = repository,
            startDestination = startDestination,
            startFresh = startFresh,
            modifier = Modifier
              .fillMaxSize()
              .navigationBarsPadding(),
            onRegistrationComplete = {
              setResult(RESULT_OK)
              IntentCompat.getParcelableExtra(intent, NEXT_INTENT_EXTRA, Intent::class.java)?.let { startActivity(it) }
              finish()
            }
          )
        }
      }
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
