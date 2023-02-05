package org.thoughtcrime.securesms.components.settings.app.subscription.errors

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.signal.core.util.PendingIntentFlags
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds

/**
 * Donation-related push notifications.
 */
object DonationErrorNotifications {
  fun displayErrorNotification(context: Context, donationError: DonationError) {
    val parameters = DonationErrorParams.create(context, donationError, NotificationCallback)
    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(context.getString(parameters.title))
      .setContentText(context.getString(parameters.message)).apply {
        if (parameters.positiveAction != null) {
          addAction(context, parameters.positiveAction)
        }

        if (parameters.negativeAction != null) {
          addAction(context, parameters.negativeAction)
        }
      }
      .build()

    NotificationManagerCompat
      .from(context)
      .notify(NotificationIds.DONOR_BADGE_FAILURE, notification)
  }

  private fun NotificationCompat.Builder.addAction(context: Context, errorAction: DonationErrorParams.ErrorAction<PendingIntent>) {
    addAction(
      NotificationCompat.Action.Builder(
        null,
        context.getString(errorAction.label),
        errorAction.action.invoke()
      ).build()
    )
  }

  private object NotificationCallback : DonationErrorParams.Callback<PendingIntent> {

    override fun onCancel(context: Context): DonationErrorParams.ErrorAction<PendingIntent>? = null

    override fun onOk(context: Context): DonationErrorParams.ErrorAction<PendingIntent>? = null

    override fun onLearnMore(context: Context): DonationErrorParams.ErrorAction<PendingIntent> {
      return createAction(
        context = context,
        label = R.string.DeclineCode__learn_more,
        actionIntent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.donation_decline_code_error_url)))
      )
    }

    override fun onTryCreditCardAgain(context: Context): DonationErrorParams.ErrorAction<PendingIntent>? = null

    override fun onGoToGooglePay(context: Context): DonationErrorParams.ErrorAction<PendingIntent> {
      return createAction(
        context = context,
        label = R.string.DeclineCode__go_to_google_pay,
        actionIntent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.google_pay_url)))
      )
    }

    override fun onContactSupport(context: Context): DonationErrorParams.ErrorAction<PendingIntent> {
      return createAction(
        context = context,
        label = R.string.Subscription__contact_support,
        actionIntent = AppSettingsActivity.help(context, HelpFragment.DONATION_INDEX)
      )
    }

    private fun createAction(
      context: Context,
      label: Int,
      actionIntent: Intent
    ): DonationErrorParams.ErrorAction<PendingIntent> {
      return DonationErrorParams.ErrorAction(
        label = label,
        action = {
          PendingIntent.getActivity(
            context,
            0,
            actionIntent,
            if (Build.VERSION.SDK_INT >= 23) PendingIntentFlags.oneShot() else PendingIntentFlags.mutable()
          )
        }
      )
    }
  }
}
