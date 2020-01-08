package org.thoughtcrime.securesms.util;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.TaskStackBuilder;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.widget.Toast;

import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

public class CommunicationActions {

  public static void startVoiceCall(@NonNull Activity activity, @NonNull Recipient recipient) {
    if (TelephonyUtil.isAnyPstnLineBusy(activity)) {
      Toast.makeText(activity,
                     R.string.CommunicationActions_a_cellular_call_is_already_in_progress,
                     Toast.LENGTH_SHORT)
           .show();
      return;
    }

    WebRtcCallService.isCallActive(activity, new ResultReceiver(new Handler(Looper.getMainLooper())) {
      @Override
      protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (resultCode == 1) {
          startCallInternal(activity, recipient, false);
        } else {
          new AlertDialog.Builder(activity)
                         .setMessage(R.string.CommunicationActions_start_voice_call)
                         .setPositiveButton(R.string.CommunicationActions_call, (d, w) -> startCallInternal(activity, recipient, false))
                         .setNegativeButton(R.string.CommunicationActions_cancel, (d, w) -> d.dismiss())
                         .setCancelable(true)
                         .show();
        }
      }
    });
  }

  public static void startVideoCall(@NonNull Activity activity, @NonNull Recipient recipient) {
    if (TelephonyUtil.isAnyPstnLineBusy(activity)) {
      Toast.makeText(activity,
                     R.string.CommunicationActions_a_cellular_call_is_already_in_progress,
                     Toast.LENGTH_SHORT)
           .show();
      return;
    }

    WebRtcCallService.isCallActive(activity, new ResultReceiver(new Handler(Looper.getMainLooper())) {
      @Override
      protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (resultCode == 1) {
          startCallInternal(activity, recipient, false);
        } else {
          new AlertDialog.Builder(activity)
                         .setMessage(R.string.CommunicationActions_start_video_call)
                         .setPositiveButton(R.string.CommunicationActions_call, (d, w) -> startCallInternal(activity, recipient, true))
                         .setNegativeButton(R.string.CommunicationActions_cancel, (d, w) -> d.dismiss())
                         .setCancelable(true)
                         .show();
        }
      }
    });
  }

  public static void startConversation(@NonNull Context context, @NonNull Recipient recipient, @Nullable String text) {
    startConversation(context, recipient, text, null);
  }

  public static void startConversation(@NonNull  Context          context,
                                       @NonNull  Recipient        recipient,
                                       @Nullable String           text,
                                       @Nullable TaskStackBuilder backStack)
  {
    new AsyncTask<Void, Void, Long>() {
      @Override
      protected Long doInBackground(Void... voids) {
        return DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
      }

      @Override
      protected void onPostExecute(Long threadId) {
        Intent intent = new Intent(context, ConversationActivity.class);
        intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipient.getId());
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);

        if (!TextUtils.isEmpty(text)) {
          intent.putExtra(ConversationActivity.TEXT_EXTRA, text);
        }

        if (backStack != null) {
          backStack.addNextIntent(intent);
          backStack.startActivities();
        } else {
          context.startActivity(intent);
        }
      }
    }.execute();
  }

  public static void composeSmsThroughDefaultApp(@NonNull Context context, @NonNull Recipient recipient, @Nullable String text) {
    Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + recipient.requireSmsAddress()));
    if (text != null) {
      intent.putExtra("sms_body", text);
    }
    context.startActivity(intent);
  }

  public static void openBrowserLink(@NonNull Context context, @NonNull String link) {
    try {
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
      context.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(context, R.string.CommunicationActions_no_browser_found, Toast.LENGTH_SHORT).show();
    }
  }


  private static void startCallInternal(@NonNull Activity activity, @NonNull Recipient recipient, boolean isVideo) {
    Permissions.with(activity)
               .request(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
               .ifNecessary()
               .withRationaleDialog(activity.getString(R.string.ConversationActivity_to_call_s_signal_needs_access_to_your_microphone_and_camera, recipient.getDisplayName(activity)),
                                    R.drawable.ic_mic_solid_24,
                                    R.drawable.ic_video_solid_24_tinted)
               .withPermanentDenialDialog(activity.getString(R.string.ConversationActivity_signal_needs_the_microphone_and_camera_permissions_in_order_to_call_s, recipient.getDisplayName(activity)))
               .onAllGranted(() -> {
                 Intent intent = new Intent(activity, WebRtcCallService.class);
                 intent.setAction(WebRtcCallService.ACTION_OUTGOING_CALL);
                 intent.putExtra(WebRtcCallService.EXTRA_REMOTE_RECIPIENT, recipient.getId());
                 activity.startService(intent);

                 Intent activityIntent = new Intent(activity, WebRtcCallActivity.class);
                 activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                 if (isVideo) {
                   activityIntent.putExtra(WebRtcCallActivity.EXTRA_ENABLE_VIDEO_IF_AVAILABLE, true);
                 }

                 activity.startActivity(activityIntent);
               })
               .execute();
  }
}
