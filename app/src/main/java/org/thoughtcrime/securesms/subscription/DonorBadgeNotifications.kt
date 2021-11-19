package org.thoughtcrime.securesms.subscription

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.notifications.NotificationIds

sealed class DonorBadgeNotifications {
  object RedemptionFailed : DonorBadgeNotifications() {
    override fun show(context: Context) {
      val notification = NotificationCompat.Builder(context, NotificationChannels.FAILURES)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.DonationsErrors__couldnt_add_badge))
        .setContentText(context.getString(R.string.Subscription__please_contact_support_for_more_information))
        .addAction(
          NotificationCompat.Action.Builder(
            null,
            context.getString(R.string.Subscription__contact_support),
            PendingIntent.getActivity(
              context,
              0,
              AppSettingsActivity.help(context, HelpFragment.DONATION_INDEX),
              if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_ONE_SHOT else 0
            )
          ).build()
        )
        .build()

      NotificationManagerCompat
        .from(context)
        .notify(NotificationIds.SUBSCRIPTION_VERIFY_FAILED, notification)
    }
  }

  object PaymentFailed : DonorBadgeNotifications() {
    override fun show(context: Context) {
      val notification = NotificationCompat.Builder(context, NotificationChannels.FAILURES)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.DonationsErrors__error_processing_payment))
        .setContentText(context.getString(R.string.DonationsErrors__your_badge_could_not_be_added))
        .addAction(
          NotificationCompat.Action.Builder(
            null,
            context.getString(R.string.Subscription__contact_support),
            PendingIntent.getActivity(
              context,
              0,
              AppSettingsActivity.help(context, HelpFragment.DONATION_INDEX),
              if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_ONE_SHOT else 0
            )
          ).build()
        )
        .build()

      NotificationManagerCompat
        .from(context)
        .notify(NotificationIds.SUBSCRIPTION_VERIFY_FAILED, notification)
    }
  }

  abstract fun show(context: Context)
}
