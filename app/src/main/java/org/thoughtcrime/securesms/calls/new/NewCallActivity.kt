package org.thoughtcrime.securesms.calls.new

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.ContactSelectionActivity
import org.thoughtcrime.securesms.ContactSelectionListFragment
import org.thoughtcrime.securesms.InviteActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.RecipientRepository
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog
import java.util.Optional
import java.util.function.Consumer

class NewCallActivity : ContactSelectionActivity(), ContactSelectionListFragment.NewCallCallback {

  override fun onCreate(icicle: Bundle?, ready: Boolean) {
    super.onCreate(icicle, ready)
    requireNotNull(supportActionBar)
    supportActionBar?.setTitle(R.string.NewCallActivity__new_call)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    addMenuProvider(NewCallMenuProvider())
  }

  override fun onSelectionChanged() = Unit

  override fun onBeforeContactSelected(isFromUnknownSearchKey: Boolean, recipientId: Optional<RecipientId?>, number: String?, callback: Consumer<Boolean?>) {
    if (recipientId.isPresent) {
      launch(Recipient.resolved(recipientId.get()))
    } else {
      Log.i(TAG, "[onContactSelected] Maybe creating a new recipient.")
      if (SignalStore.account.isRegistered) {
        Log.i(TAG, "[onContactSelected] Doing contact refresh.")

        val progress = SimpleProgressDialog.show(this)

        SimpleTask.run(lifecycle, { RecipientRepository.lookupNewE164(this, number!!) }, { result ->
          progress.dismiss()

          when (result) {
            is RecipientRepository.LookupResult.Success -> {
              val resolved = Recipient.resolved(result.recipientId)
              if (resolved.isRegistered && resolved.hasServiceId) {
                launch(resolved)
              }
            }

            is RecipientRepository.LookupResult.NotFound,
            is RecipientRepository.LookupResult.InvalidEntry -> {
              MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.NewConversationActivity__s_is_not_a_signal_user, number))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            }

            else -> {
              MaterialAlertDialogBuilder(this)
                .setMessage(R.string.NetworkFailure__network_error_check_your_connection_and_try_again)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            }
          }
        })
      }
    }
    callback.accept(true)
  }

  private fun launch(recipient: Recipient) {
    if (recipient.isGroup) {
      CommunicationActions.startVideoCall(this, recipient)
    } else {
      CommunicationActions.startVoiceCall(this, recipient)
    }
  }

  companion object {

    private val TAG = Log.tag(NewCallActivity::class.java)

    fun createIntent(context: Context): Intent {
      return Intent(context, NewCallActivity::class.java)
        .putExtra(
          ContactSelectionListFragment.DISPLAY_MODE,
          ContactSelectionDisplayMode.none()
            .withPush()
            .withActiveGroups()
            .withGroupMembers()
            .build()
        )
    }
  }

  override fun onInvite() {
    startActivity(Intent(this, InviteActivity::class.java))
  }

  private inner class NewCallMenuProvider : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
      menuInflater.inflate(R.menu.new_call_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
      when (menuItem.itemId) {
        android.R.id.home -> ActivityCompat.finishAfterTransition(this@NewCallActivity)
        R.id.menu_refresh -> onRefresh()
        R.id.menu_invite -> startActivity(Intent(this@NewCallActivity, InviteActivity::class.java))
      }

      return true
    }
  }
}
