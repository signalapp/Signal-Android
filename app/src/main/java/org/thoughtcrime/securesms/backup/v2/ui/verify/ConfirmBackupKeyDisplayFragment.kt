package org.thoughtcrime.securesms.backup.v2.ui.verify

import android.app.Activity.RESULT_OK
import androidx.compose.runtime.Composable
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyVerifyScreen
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Fragment to confirm the backup key just shown after users forget it.
 */
class ConfirmBackupKeyDisplayFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    MessageBackupsKeyVerifyScreen(
      backupKey = SignalStore.account.accountEntropyPool.displayValue,
      onNavigationClick = {
        requireActivity().supportFragmentManager.popBackStack()
      },
      onNextClick = {
        SignalStore.backup.lastVerifyKeyTime = System.currentTimeMillis()
        SignalStore.backup.hasVerifiedBefore = true
        SignalStore.backup.hasSnoozedVerified = false
        requireActivity().setResult(RESULT_OK)
        requireActivity().finish()
      }
    )
  }
}
