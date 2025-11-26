package org.thoughtcrime.securesms.notifications;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.signal.core.util.CursorUtil;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.concurrent.TimeUnit;

public final class DoNotDisturbUtil {

  private static final String TAG = Log.tag(DoNotDisturbUtil.class);

  private DoNotDisturbUtil() {
  }

  @WorkerThread
  @SuppressLint("SwitchIntDef")
  public static boolean shouldDisturbUserWithCall(@NonNull Context context) {
    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);

    switch (notificationManager.getCurrentInterruptionFilter()) {
      case NotificationManager.INTERRUPTION_FILTER_ALL:
      case NotificationManager.INTERRUPTION_FILTER_UNKNOWN:
        return true;
      default:
        return false;
    }
  }

  @WorkerThread
  @SuppressLint("SwitchIntDef")
  public static boolean shouldDisturbUserWithCall(@NonNull Context context, @NonNull Recipient recipient) {
    NotificationManager notificationManager = ServiceUtil.getNotificationManager(context);

    switch (notificationManager.getCurrentInterruptionFilter()) {
      case NotificationManager.INTERRUPTION_FILTER_ALL:
        return true;
      case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
        return handlePriority(context, notificationManager, recipient);
      case NotificationManager.INTERRUPTION_FILTER_NONE:
      case NotificationManager.INTERRUPTION_FILTER_ALARMS:
        return false;
      default:
        Log.w(TAG, "Unknown interruption filter, will interrupt user: " + notificationManager.getCurrentInterruptionFilter());
        return true;
    }
  }

  private static boolean handlePriority(@NonNull Context context, @NonNull NotificationManager notificationManager, @NonNull Recipient recipient) {
    if (Build.VERSION.SDK_INT < 28 && !notificationManager.isNotificationPolicyAccessGranted()) {
      Log.w(TAG, "Notification Policy is not granted");
      return true;
    }

    final NotificationManager.Policy policy                = notificationManager.getNotificationPolicy();
    final boolean                    areCallsPrioritized   = (policy.priorityCategories & NotificationManager.Policy.PRIORITY_CATEGORY_CALLS) != 0;
    final boolean                    isRepeatCallerEnabled = (policy.priorityCategories & NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS) != 0;

    if (!areCallsPrioritized && !isRepeatCallerEnabled) {
      return false;
    }

    final boolean isContactPriority = isContactPriority(context, recipient, policy.priorityCallSenders);
    final boolean isRepeatCaller    = isRepeatCaller(context, recipient);
    Log.i(TAG, "Handling priority - Contact priority: " + isContactPriority + ", Repeat caller: " + isRepeatCaller);

    if (areCallsPrioritized && !isRepeatCallerEnabled) {
      return isContactPriority;
    }

    if (!areCallsPrioritized) {
      return isRepeatCaller;
    }

    return isContactPriority || isRepeatCaller;
  }

  private static boolean isContactPriority(@NonNull Context context, @NonNull Recipient recipient, int priority) {
    if (!Permissions.hasAny(context, Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)) {
      return false;
    }

    boolean isSystemContact  = recipient.resolve().isSystemContact();
    boolean isContactStarred = isContactStarred(context, recipient);
    Log.i(TAG, "Checking contact priority - Priority " + priority + ", System contact: " + isSystemContact + ", Starred contact: " + isContactStarred);

    switch (priority) {
      case NotificationManager.Policy.PRIORITY_SENDERS_ANY:
        return true;
      case NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS:
        return isSystemContact;
      case NotificationManager.Policy.PRIORITY_SENDERS_STARRED:
        return isContactStarred;
    }

    Log.w(TAG, "Unknown priority " + priority);
    return true;
  }

  private static boolean isContactStarred(@NonNull Context context, @NonNull Recipient recipient) {
    if (!recipient.resolve().isSystemContact()) return false;

    //noinspection ConstantConditions
    try (Cursor cursor = context.getContentResolver().query(recipient.resolve().getContactUri(), new String[]{ContactsContract.Contacts.STARRED}, null, null, null)) {
      if (cursor == null || !cursor.moveToFirst()) return false;
      return CursorUtil.requireInt(cursor, ContactsContract.Contacts.STARRED) == 1;
    }
  }

  private static boolean isRepeatCaller(@NonNull Context context, @NonNull Recipient recipient) {
    return SignalDatabase.threads().hasCalledSince(recipient, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15));
  }

}
