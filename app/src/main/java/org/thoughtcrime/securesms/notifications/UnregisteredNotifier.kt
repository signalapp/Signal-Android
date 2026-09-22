/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity

/**
 * Notification shown when the service tells us our credentials are no longer valid, prompting the user to re-register or re-link.
 */
object UnregisteredNotifier {

  private val TAG = Log.tag(UnregisteredNotifier::class)

  fun notify(context: Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "Notification permission is not granted.")
      return
    }

    val registrationIntent = if (SignalStore.account.isLinkedDevice) {
      RegistrationActivity.newIntentForReLinkDevice(context)
    } else {
      RegistrationActivity.newIntentForReRegistration(context)
    }

    val reRegistrationIntent = PendingIntent.getActivity(
      context,
      0,
      registrationIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntentFlags.immutable()
    )

    val notification = NotificationCompat.Builder(context, NotificationChannels.getInstance().FAILURES)
      .setSmallIcon(R.drawable.ic_signal_logo_large)
      .setContentText(context.getString(R.string.LoggedOutNotification_you_have_been_logged_out))
      .setContentIntent(reRegistrationIntent)
      .setOnlyAlertOnce(true)
      .setAutoCancel(true)
      .build()

    NotificationManagerCompat.from(context).notify(NotificationIds.UNREGISTERED_NOTIFICATION_ID, notification)
  }

  fun cancel(context: Context) {
    NotificationManagerCompat.from(context).cancel(NotificationIds.UNREGISTERED_NOTIFICATION_ID)
  }
}
