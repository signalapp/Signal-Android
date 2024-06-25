package org.thoughtcrime.securesms.util;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.TaskStackBuilder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.concurrent.RxExtensions;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallLinkRootKey;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.WebRtcCallActivity;
import org.thoughtcrime.securesms.calls.links.CallLinks;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.database.CallLinkTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.model.GroupRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.joining.GroupJoinBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.joining.GroupJoinUpdateRequiredBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.v2.GroupInviteLinkUrl;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository;
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.UsernameLinkConversionResult;
import org.thoughtcrime.securesms.proxy.ProxyBottomSheetFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.signalservice.api.push.UsernameLinkComponents;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class CommunicationActions {

  private static final String TAG = Log.tag(CommunicationActions.class);

  /**
   * Start a voice call. Assumes that permission request results will be routed to a handler on the Fragment.
   */
  public static void startVoiceCall(@NonNull Fragment fragment, @NonNull Recipient recipient) {
    startVoiceCall(new FragmentCallContext(fragment), recipient);
  }

  /**
   * Start a voice call. Assumes that permission request results will be routed to a handler on the Activity.
   */
  public static void startVoiceCall(@NonNull FragmentActivity activity, @NonNull Recipient recipient) {
    startVoiceCall(new ActivityCallContext(activity), recipient);
  }

  private static void startVoiceCall(@NonNull CallContext callContext, @NonNull Recipient recipient) {
    if (TelephonyUtil.isAnyPstnLineBusy(callContext.getContext())) {
      Toast.makeText(callContext.getContext(),
                     R.string.CommunicationActions_a_cellular_call_is_already_in_progress,
                     Toast.LENGTH_SHORT)
           .show();
      return;
    }

    if (recipient.isRegistered()) {
      AppDependencies.getSignalCallManager().isCallActive(new ResultReceiver(new Handler(Looper.getMainLooper())) {
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
          if (resultCode == 1) {
            startCallInternal(callContext, recipient, false, false);
          } else {
            new MaterialAlertDialogBuilder(callContext.getContext())
                .setMessage(R.string.CommunicationActions_start_voice_call)
                .setPositiveButton(R.string.CommunicationActions_call, (d, w) -> startCallInternal(callContext, recipient, false, false))
                .setNegativeButton(R.string.CommunicationActions_cancel, (d, w) -> d.dismiss())
                .setCancelable(true)
                .show();
          }
        }
      });
    } else {
      startInsecureCall(callContext, recipient);
    }
  }

  /**
   * Start a video call. Assumes that permission request results will be routed to a handler on the Fragment.
   */
  public static void startVideoCall(@NonNull Fragment fragment, @NonNull Recipient recipient) {
    startVideoCall(new FragmentCallContext(fragment), recipient, false);
  }

  /**
   * Start a video call. Assumes that permission request results will be routed to a handler on the Activity.
   */
  public static void startVideoCall(@NonNull FragmentActivity activity, @NonNull Recipient recipient) {
    startVideoCall(new ActivityCallContext(activity), recipient, false);
  }

  private static void startVideoCall(@NonNull CallContext callContext, @NonNull Recipient recipient, boolean fromCallLink) {
    if (TelephonyUtil.isAnyPstnLineBusy(callContext.getContext())) {
      Toast.makeText(callContext.getContext(),
                     R.string.CommunicationActions_a_cellular_call_is_already_in_progress,
                     Toast.LENGTH_SHORT)
           .show();
      return;
    }

    AppDependencies.getSignalCallManager().isCallActive(new ResultReceiver(new Handler(Looper.getMainLooper())) {
      @Override
      protected void onReceiveResult(int resultCode, Bundle resultData) {
        startCallInternal(callContext, recipient, resultCode != 1, fromCallLink);
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
        return SignalDatabase.threads().getOrCreateThreadIdFor(recipient);
      }

      @Override
      protected void onPostExecute(@NonNull Long threadId) {
        ConversationIntents.Builder builder = ConversationIntents.createBuilderSync(context, recipient.getId(), Objects.requireNonNull(threadId));
        if (!TextUtils.isEmpty(text)) {
          builder.withDraftText(text);
        }

        Intent intent = builder.build();
        if (backStack != null) {
          backStack.addNextIntent(intent);
          backStack.startActivities();
        } else {
          context.startActivity(intent);
        }
      }
    }.execute();
  }

  public static void startInsecureCall(@NonNull FragmentActivity activity, @NonNull Recipient recipient) {
    startInsecureCall(new ActivityCallContext(activity), recipient);
  }

  public static void startInsecureCall(@NonNull Fragment fragment, @NonNull Recipient recipient) {
    startInsecureCall(new FragmentCallContext(fragment), recipient);
  }

  public static void startInsecureCall(@NonNull CallContext callContext, @NonNull Recipient recipient) {
    new MaterialAlertDialogBuilder(callContext.getContext())
                   .setTitle(R.string.CommunicationActions_insecure_call)
                   .setMessage(R.string.CommunicationActions_carrier_charges_may_apply)
                   .setPositiveButton(R.string.CommunicationActions_call, (d, w) -> {
                     d.dismiss();
                     startInsecureCallInternal(callContext, recipient);
                   })
                   .setNegativeButton(R.string.CommunicationActions_cancel, (d, w) -> d.dismiss())
                   .show();
  }

  public static @NonNull Intent createIntentToShareTextViaShareSheet(@NonNull String text) {
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_TEXT, text);

    return intent;
  }

  public static @NonNull Intent createIntentToComposeSmsThroughDefaultApp(@NonNull Recipient recipient, @Nullable String text) {
    Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + recipient.requireSmsAddress()));
    if (text != null) {
      intent.putExtra("sms_body", text);
    }

    return intent;
  }

  public static void composeSmsThroughDefaultApp(@NonNull Context context, @NonNull Recipient recipient, @Nullable String text) {
    Intent intent = createIntentToComposeSmsThroughDefaultApp(recipient, text);
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

  public static void openEmail(@NonNull Context context, @NonNull String address, @Nullable String subject, @Nullable String body) {
    Intent intent = new Intent(Intent.ACTION_SENDTO);
    intent.setData(Uri.parse("mailto:"));
    intent.putExtra(Intent.EXTRA_EMAIL, new String[]{ address });
    intent.putExtra(Intent.EXTRA_SUBJECT, Util.emptyIfNull(subject));
    intent.putExtra(Intent.EXTRA_TEXT, Util.emptyIfNull(body));

    context.startActivity(Intent.createChooser(intent, context.getString(R.string.CommunicationActions_send_email)));
  }

  /**
   * If the url is a group link it will handle it.
   * If the url is a malformed group link, it will assume Signal needs to update.
   * Otherwise returns false, indicating was not a group link.
   */
  public static boolean handlePotentialGroupLinkUrl(@NonNull FragmentActivity activity, @NonNull String potentialGroupLinkUrl) {
    try {
      GroupInviteLinkUrl groupInviteLinkUrl = GroupInviteLinkUrl.fromUri(potentialGroupLinkUrl);

      if (groupInviteLinkUrl == null) {
        return false;
      }

      handleGroupLinkUrl(activity, groupInviteLinkUrl);
      return true;
    } catch (GroupInviteLinkUrl.InvalidGroupLinkException e) {
      Log.w(TAG, "Could not parse group URL", e);
      Toast.makeText(activity, R.string.GroupJoinUpdateRequiredBottomSheetDialogFragment_group_link_is_not_valid, Toast.LENGTH_SHORT).show();
      return true;
    } catch (GroupInviteLinkUrl.UnknownGroupLinkVersionException e) {
      Log.w(TAG, "Group link is for an advanced version", e);
      GroupJoinUpdateRequiredBottomSheetDialogFragment.show(activity.getSupportFragmentManager());
      return true;
    }
  }

  public static void handleGroupLinkUrl(@NonNull FragmentActivity activity,
                                        @NonNull GroupInviteLinkUrl groupInviteLinkUrl)
  {
    GroupId.V2 groupId = GroupId.v2(groupInviteLinkUrl.getGroupMasterKey());

    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      GroupRecord group = SignalDatabase.groups().getGroup(groupId).orElse(null);

      return group != null && group.isActive() ? Recipient.resolved(group.getRecipientId())
                                               : null;
    },
    recipient -> {
      if (recipient != null) {
        CommunicationActions.startConversation(activity, recipient, null);
        Toast.makeText(activity, R.string.GroupJoinBottomSheetDialogFragment_you_are_already_a_member, Toast.LENGTH_SHORT).show();
      } else {
        GroupJoinBottomSheetDialogFragment.show(activity.getSupportFragmentManager(), groupInviteLinkUrl);
      }
    });
  }

  /**
   * If the url is a proxy link it will handle it.
   * Otherwise returns false, indicating was not a proxy link.
   */
  public static boolean handlePotentialProxyLinkUrl(@NonNull FragmentActivity activity, @NonNull String potentialProxyLinkUrl) {
    String proxy = SignalProxyUtil.parseHostFromProxyDeepLink(potentialProxyLinkUrl);

    if (proxy != null) {
      ProxyBottomSheetFragment.showForProxy(activity.getSupportFragmentManager(), proxy);
      return true;
    } else {
      return false;
    }
  }

  /**
   * If the url is a signal.me link it will handle it.
   */
  public static void handlePotentialSignalMeUrl(@NonNull FragmentActivity activity, @NonNull String potentialUrl) {
    String                 e164     = SignalMeUtil.parseE164FromLink(activity, potentialUrl);
    UsernameLinkComponents username = UsernameRepository.parseLink(potentialUrl);

    if (e164 != null) {
      handleE164Link(activity, e164);
    } else if (username != null) {
      handleUsernameLink(activity, potentialUrl);
    }
  }

  public static void handlePotentialCallLinkUrl(@NonNull FragmentActivity activity, @NonNull String potentialUrl) {
    if (!CallLinks.isCallLink(potentialUrl)) {
      return;
    }

    if (!RemoteConfig.adHocCalling()) {
      Toast.makeText(activity, R.string.CommunicationActions_cant_join_call, Toast.LENGTH_SHORT).show();
      return;
    }

    CallLinkRootKey rootKey = CallLinks.parseUrl(potentialUrl);
    if (rootKey == null) {
      Log.w(TAG, "Failed to parse root key from call link");
      new MaterialAlertDialogBuilder(activity)
          .setTitle(R.string.CommunicationActions_invalid_link)
          .setMessage(R.string.CommunicationActions_this_is_not_a_valid_call_link)
          .setPositiveButton(android.R.string.ok, null)
          .show();
      return;
    }

    startVideoCall(new ActivityCallContext(activity), rootKey);
  }

  /**
   * Attempts to start a video call for the given call link via root key. This will insert a call link into
   * the user's database if one does not already exist.
   *
   * @param fragment The fragment, which will be used for context and permissions routing.
   */
  public static void startVideoCall(@NonNull Fragment fragment, @NonNull CallLinkRootKey rootKey) {
    startVideoCall(new FragmentCallContext(fragment), rootKey);
  }

  private static void startVideoCall(@NonNull CallContext callContext, @NonNull CallLinkRootKey rootKey) {
    if (!RemoteConfig.adHocCalling()) {
      Toast.makeText(callContext.getContext(), R.string.CommunicationActions_cant_join_call, Toast.LENGTH_SHORT).show();
      return;
    }

    SimpleTask.run(() -> {
      CallLinkRoomId         roomId   = CallLinkRoomId.fromBytes(rootKey.deriveRoomId());
      CallLinkTable.CallLink callLink = SignalDatabase.callLinks().getOrCreateCallLinkByRootKey(rootKey);

      if (callLink.getState().hasBeenRevoked()) {
        return Optional.<Recipient>empty();
      }

      return SignalDatabase.recipients().getByCallLinkRoomId(roomId).map(Recipient::resolved);
    }, callLinkRecipient -> {
      if (callLinkRecipient.isEmpty()) {
        new MaterialAlertDialogBuilder(callContext.getContext())
            .setTitle(R.string.CommunicationActions_cant_join_call)
            .setMessage(R.string.CommunicationActions_this_call_link_is_no_longer_valid)
            .setPositiveButton(android.R.string.ok, null)
            .show();
      } else {
        startVideoCall(callContext, callLinkRecipient.get(), true);
      }
    });
  }

  private static void startInsecureCallInternal(@NonNull CallContext callContext, @NonNull Recipient recipient) {
    try {
      Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + recipient.requireSmsAddress()));
      callContext.startActivity(dialIntent);
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, anfe);
      Dialogs.showAlertDialog(callContext.getContext(),
                              callContext.getContext().getString(R.string.ConversationActivity_calls_not_supported),
                              callContext.getContext().getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
    }
  }

  private static void startCallInternal(@NonNull CallContext callContext, @NonNull Recipient recipient, boolean isVideo, boolean fromCallLink) {
    if (isVideo) startVideoCallInternal(callContext, recipient, fromCallLink);
    else         startAudioCallInternal(callContext, recipient);
  }

  private static void startAudioCallInternal(@NonNull CallContext callContext, @NonNull Recipient recipient) {
    callContext.getPermissionsBuilder()
               .request(Manifest.permission.RECORD_AUDIO)
               .ifNecessary()
               .withRationaleDialog(callContext.getContext().getString(R.string.ConversationActivity_allow_access_microphone), callContext.getContext().getString(R.string.ConversationActivity__to_call_signal_needs_access_to_your_microphone), R.drawable.symbol_phone_24)
               .withPermanentDenialDialog(callContext.getContext().getString(R.string.ConversationActivity__to_call_signal_needs_access_to_your_microphone), null, R.string.ConversationActivity_allow_access_microphone, R.string.ConversationActivity__to_start_call, callContext.getFragmentManager())
               .onAnyDenied(() -> Toast.makeText(callContext.getContext(), R.string.ConversationActivity_signal_needs_microphone_access_voice_call, Toast.LENGTH_LONG).show())
               .onAllGranted(() -> {
                 AppDependencies.getSignalCallManager().startOutgoingAudioCall(recipient);

                 MessageSender.onMessageSent();

                 Intent activityIntent = new Intent(callContext.getContext(), WebRtcCallActivity.class);

                 activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                 callContext.startActivity(activityIntent);
               })
               .execute();
  }

  private static void startVideoCallInternal(@NonNull CallContext callContext, @NonNull Recipient recipient, boolean fromCallLink) {
    AppDependencies.getSignalCallManager().startPreJoinCall(recipient);

    Intent activityIntent = new Intent(callContext.getContext(), WebRtcCallActivity.class);

    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  .putExtra(WebRtcCallActivity.EXTRA_ENABLE_VIDEO_IF_AVAILABLE, true)
                  .putExtra(WebRtcCallActivity.EXTRA_STARTED_FROM_CALL_LINK, fromCallLink);

    callContext.startActivity(activityIntent);
  }

  private static void handleE164Link(Activity activity, String e164) {
    SimpleProgressDialog.DismissibleDialog dialog = SimpleProgressDialog.showDelayed(activity, 500, 500);

    SimpleTask.run(() -> {
      Recipient recipient = Recipient.external(activity, e164);

      if (!recipient.isRegistered() || !recipient.getHasServiceId()) {
        try {
          ContactDiscovery.refresh(activity, recipient, false, TimeUnit.SECONDS.toMillis(10));
          recipient = Recipient.resolved(recipient.getId());
        } catch (IOException e) {
          Log.w(TAG, "[handlePotentialSignalMeUrl] Failed to refresh directory for new contact.");
        }
      }

      return recipient;
    }, recipient -> {
      dialog.dismiss();

      if (recipient.isRegistered() && recipient.getHasServiceId()) {
        startConversation(activity, recipient, null);
      } else {
        new MaterialAlertDialogBuilder(activity)
            .setMessage(activity.getString(R.string.NewConversationActivity__s_is_not_a_signal_user, e164))
            .setPositiveButton(android.R.string.ok, null)
            .show();
      }
    });
  }

  private static void handleUsernameLink(Activity activity, String link) {
    SimpleProgressDialog.DismissibleDialog dialog = SimpleProgressDialog.showDelayed(activity, 500, 500);

    SimpleTask.run(() -> {
      try {
        UsernameLinkConversionResult result = RxExtensions.safeBlockingGet(UsernameRepository.fetchUsernameAndAciFromLink(link));

        // TODO we could be better here and report different types of errors to the UI
        if (result instanceof UsernameLinkConversionResult.Success success) {
          return Recipient.externalUsername(success.getAci(), success.getUsername().getUsername());
        } else {
          return null;
        }
      } catch (InterruptedException e) {
        Log.w(TAG, "Interrupted?", e);
        return null;
      }
    }, recipient -> {
      dialog.dismiss();

      if (recipient != null && recipient.isRegistered() && recipient.getHasServiceId()) {
        startConversation(activity, recipient, null);
      } else {
        new MaterialAlertDialogBuilder(activity)
            .setMessage(activity.getString(R.string.UsernameLinkSettings_qr_result_not_found_no_username))
            .setPositiveButton(android.R.string.ok, null)
            .show();
      }
    });
  }

  private interface CallContext {
    @NonNull Permissions.PermissionsBuilder getPermissionsBuilder();
    void startActivity(@NonNull Intent intent);
    @NonNull Context getContext();
    @NonNull FragmentManager getFragmentManager();
  }

  private static class ActivityCallContext implements CallContext {
    private final FragmentActivity activity;

    private ActivityCallContext(FragmentActivity activity) {
      this.activity = activity;
    }

    @Override
    public @NonNull Permissions.PermissionsBuilder getPermissionsBuilder() {
      return Permissions.with(activity);
    }

    @Override
    public void startActivity(@NonNull Intent intent) {
      activity.startActivity(intent);
    }

    @Override
    public @NonNull Context getContext() {
      return activity;
    }

    @Override
    public @NonNull FragmentManager getFragmentManager() {
      return activity.getSupportFragmentManager();
    }
  }

  private static class FragmentCallContext implements CallContext {
    private final Fragment fragment;

    private FragmentCallContext(Fragment fragment) {
      this.fragment = fragment;
    }

    @Override
    public @NonNull Permissions.PermissionsBuilder getPermissionsBuilder() {
      return Permissions.with(fragment);
    }

    @Override
    public void startActivity(@NonNull Intent intent) {
      fragment.startActivity(intent);
    }

    @Override
    public @NonNull Context getContext() {
      return fragment.requireContext();
    }

    @Override
    public @NonNull FragmentManager getFragmentManager() {
      return fragment.getParentFragmentManager();
    }
  }
}
