package org.thoughtcrime.securesms.backup.v2.ui.verify

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.subscription.EnterKeyScreen
import org.thoughtcrime.securesms.components.compose.rememberBiometricsAuthentication
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.CommunicationActions
import kotlin.random.Random
import kotlin.random.nextInt

/**
 * Screen to verify the backup key
 */
class VerifyBackupKeyActivity : PassphraseRequiredActivity() {

  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, VerifyBackupKeyActivity::class.java)
    }

    const val REQUEST_CODE = 1204
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()

    setContent {
      SignalTheme {
        val context = LocalContext.current
        val biometrics = rememberBiometricsAuthentication(
          promptTitle = stringResource(R.string.RemoteBackupsSettingsFragment__unlock_to_view_backup_key),
          educationSheetMessage = stringResource(R.string.RemoteBackupsSettingsFragment__to_view_your_key),
          onAuthenticationFailed = {
            // Matches existing behavior: show a generic "authentication required" toast.
            Toast.makeText(
              context,
              R.string.RemoteBackupsSettingsFragment__authenticatino_required,
              Toast.LENGTH_SHORT
            ).show()
          }
        )

        VerifyBackupPinScreen(
          backupKey = SignalStore.account.accountEntropyPool.displayValue,
          onForgotKeyClick = {
            biometrics.withBiometricsAuthentication {
              displayBackupKey()
            }
          },
          onNextClick = {
            SignalStore.backup.lastVerifyKeyTime = System.currentTimeMillis()
            SignalStore.backup.hasVerifiedBefore = true
            SignalStore.backup.hasSnoozedVerified = false
            setResult(RESULT_OK)
            finish()
          }
        )
      }
    }
  }

  private fun displayBackupKey() {
    supportFragmentManager
      .beginTransaction()
      .add(android.R.id.content, ForgotBackupKeyFragment())
      .addToBackStack(null)
      .commit()
  }
}

@Composable
fun VerifyBackupPinScreen(
  backupKey: String,
  onForgotKeyClick: () -> Unit = {},
  onNextClick: () -> Unit = {}
) {
  val context = LocalContext.current
  val keyboardController = LocalSoftwareKeyboardController.current

  val text = buildAnnotatedString {
    append(stringResource(id = R.string.VerifyBackupPinScreen__enter_the_backup_key_that_you_recorded))
    append(" ")

    withLink(
      LinkAnnotation.Clickable(tag = "learn-more") {
        CommunicationActions.openBrowserLink(context, context.getString(R.string.backup_failed_support_url))
      }
    ) {
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
        append(stringResource(id = R.string.BackupAlertBottomSheet__learn_more))
      }
    }
  }

  Scaffold { paddingValues ->
    EnterKeyScreen(
      paddingValues = paddingValues,
      backupKey = backupKey,
      onNextClick = onNextClick,
      captionContent = {
        Text(
          text = stringResource(R.string.VerifyBackupPinScreen__enter_your_backup_key),
          style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
          modifier = Modifier.padding(top = 40.dp, bottom = 16.dp)
        )

        Text(
          text = text,
          style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
      },
      seeKeyButton = {
        TextButton(
          onClick = {
            keyboardController?.hide()
            onForgotKeyClick()
          }
        ) {
          Text(text = stringResource(id = R.string.VerifyBackupPinScreen__forgot_key))
        }
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun VerifyBackupKeyScreen() {
  Previews.Preview {
    VerifyBackupPinScreen(
      backupKey = (0 until 64).map { Random.nextInt(65..90).toChar() }.joinToString("").uppercase()
    )
  }
}
