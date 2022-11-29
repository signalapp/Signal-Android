package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import android.content.Context
import android.content.DialogInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.util.CommunicationActions

/**
 * Donation Error Dialogs.
 */
object DonationErrorDialogs {
  /**
   * Displays a dialog, and returns a handle to it for dismissal.
   */
  fun show(context: Context, throwable: Throwable?, callback: DialogCallback): DialogInterface {
    val builder = MaterialAlertDialogBuilder(context)

    builder.setOnDismissListener { callback.onDialogDismissed() }

    val params = DonationErrorParams.create(context, throwable, callback)

    builder.setTitle(params.title)
      .setMessage(params.message)

    if (params.positiveAction != null) {
      builder.setPositiveButton(params.positiveAction.label) { _, _ -> params.positiveAction.action() }
    }

    if (params.negativeAction != null) {
      builder.setNegativeButton(params.negativeAction.label) { _, _ -> params.negativeAction.action() }
    }

    return builder.show()
  }

  abstract class DialogCallback : DonationErrorParams.Callback<Unit> {

    override fun onCancel(context: Context): DonationErrorParams.ErrorAction<Unit>? {
      return DonationErrorParams.ErrorAction(
        label = android.R.string.cancel,
        action = {}
      )
    }

    override fun onOk(context: Context): DonationErrorParams.ErrorAction<Unit>? {
      return DonationErrorParams.ErrorAction(
        label = android.R.string.ok,
        action = {}
      )
    }

    override fun onGoToGooglePay(context: Context): DonationErrorParams.ErrorAction<Unit>? {
      return DonationErrorParams.ErrorAction(
        label = R.string.DeclineCode__go_to_google_pay,
        action = {
          CommunicationActions.openBrowserLink(context, context.getString(R.string.google_pay_url))
        }
      )
    }

    override fun onTryCreditCardAgain(context: Context): DonationErrorParams.ErrorAction<Unit>? {
      return DonationErrorParams.ErrorAction(
        label = R.string.DeclineCode__try,
        action = {}
      )
    }

    override fun onLearnMore(context: Context): DonationErrorParams.ErrorAction<Unit>? {
      return DonationErrorParams.ErrorAction(
        label = R.string.DeclineCode__learn_more,
        action = {
          CommunicationActions.openBrowserLink(context, context.getString(R.string.donation_decline_code_error_url))
        }
      )
    }

    override fun onContactSupport(context: Context): DonationErrorParams.ErrorAction<Unit> {
      return DonationErrorParams.ErrorAction(
        label = R.string.Subscription__contact_support,
        action = {
          context.startActivity(AppSettingsActivity.help(context, HelpFragment.DONATION_INDEX))
        }
      )
    }

    open fun onDialogDismissed() = Unit
  }
}
