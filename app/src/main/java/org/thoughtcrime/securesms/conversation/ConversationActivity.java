/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.conversation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ShortcutManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Browser;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.ViewModelProviders;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BlockUnblockDialog;
import org.thoughtcrime.securesms.ExpirationDialog;
import org.thoughtcrime.securesms.GroupMembersDialog;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.PromptMmsActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.ShortcutLauncherActivity;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.TombstoneAttachment;
import org.thoughtcrime.securesms.audio.AudioRecorder;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.components.ConversationSearchBottomBar;
import org.thoughtcrime.securesms.components.HidingLinearLayout;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.InputPanel;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import org.thoughtcrime.securesms.components.SendButton;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.components.TypingStatusSender;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.components.identity.UnverifiedBannerView;
import org.thoughtcrime.securesms.components.location.SignalPlace;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.GroupsV1MigrationInitiationReminder;
import org.thoughtcrime.securesms.components.reminder.GroupsV1MigrationSuggestionsReminder;
import org.thoughtcrime.securesms.components.reminder.PendingGroupJoinRequestsReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactShareEditActivity;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.conversation.ConversationGroupViewModel.GroupActiveState;
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeDialog;
import org.thoughtcrime.securesms.conversation.ui.groupcall.GroupCallViewModel;
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerViewModel;
import org.thoughtcrime.securesms.conversationlist.model.MessageResult;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase.Draft;
import org.thoughtcrime.securesms.database.DraftDatabase.Drafts;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.database.MentionUtil;
import org.thoughtcrime.securesms.database.MentionUtil.UpdatedBodyAndMentions;
import org.thoughtcrime.securesms.database.MmsSmsColumns.Types;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.GroupCallPeekEvent;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.giph.ui.GiphyActivity;
import org.thoughtcrime.securesms.groups.GroupChangeException;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.GroupChangeResult;
import org.thoughtcrime.securesms.groups.ui.GroupErrors;
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.ManagePendingAndRequestingMembersActivity;
import org.thoughtcrime.securesms.groups.ui.managegroup.ManageGroupActivity;
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInitiationBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationSuggestionsDialog;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.invites.InviteReminderModel;
import org.thoughtcrime.securesms.invites.InviteReminderRepository;
import org.thoughtcrime.securesms.jobs.GroupV1MigrationJob;
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel;
import org.thoughtcrime.securesms.maps.PlacePickerActivity;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaSendActivity;
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult;
import org.thoughtcrime.securesms.messagedetails.MessageDetailsActivity;
import org.thoughtcrime.securesms.messagerequests.MessageRequestState;
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel;
import org.thoughtcrime.securesms.messagerequests.MessageRequestsBottomView;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AttachmentManager.MediaType;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.LocationSlide;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.QuoteId;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewBannerView;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewCardDialogFragment;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.recipients.ui.managerecipient.ManageRecipientActivity;
import org.thoughtcrime.securesms.registration.RegistrationNavigationActivity;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerKeyboardProvider;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.stickers.StickerManagementActivity;
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent;
import org.thoughtcrime.securesms.stickers.StickerSearchRepository;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.DynamicDarkToolbarTheme;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MessageUtil;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SmsUtil;
import org.thoughtcrime.securesms.util.SpanUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.TextSecurePreferences.MediaKeyboardMode;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.thoughtcrime.securesms.TransportOption.Type;
import static org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
@SuppressLint("StaticFieldLeak")
public class ConversationActivity extends PassphraseRequiredActivity
    implements ConversationFragment.ConversationFragmentListener,
               AttachmentManager.AttachmentListener,
               OnKeyboardShownListener,
               InputPanel.Listener,
               InputPanel.MediaListener,
               ComposeText.CursorPositionChangedListener,
               ConversationSearchBottomBar.EventListener,
               StickerKeyboardProvider.StickerEventListener,
               AttachmentKeyboard.Callback,
               ConversationReactionOverlay.OnReactionSelectedListener,
               ReactWithAnyEmojiBottomSheetDialogFragment.Callback,
               SafetyNumberChangeDialog.Callback,
               ReactionsBottomSheetDialogFragment.Callback
{

  private static final int SHORTCUT_ICON_SIZE = Build.VERSION.SDK_INT >= 26 ? ViewUtil.dpToPx(72) : ViewUtil.dpToPx(48 + 16 * 2);

  private static final String TAG = ConversationActivity.class.getSimpleName();

  private static final String STATE_REACT_WITH_ANY_PAGE = "STATE_REACT_WITH_ANY_PAGE";

  private static final int PICK_GALLERY        = 1;
  private static final int PICK_DOCUMENT       = 2;
  private static final int PICK_AUDIO          = 3;
  private static final int PICK_CONTACT        = 4;
  private static final int GET_CONTACT_DETAILS = 5;
  private static final int GROUP_EDIT          = 6;
  private static final int TAKE_PHOTO          = 7;
  private static final int ADD_CONTACT         = 8;
  private static final int PICK_LOCATION       = 9;
  private static final int PICK_GIF            = 10;
  private static final int SMS_DEFAULT         = 11;
  private static final int MEDIA_SENDER        = 12;

  private   GlideRequests              glideRequests;
  protected ComposeText                composeText;
  private   AnimatingToggle            buttonToggle;
  private   SendButton                 sendButton;
  private   ImageButton                attachButton;
  protected ConversationTitleView      titleView;
  private   TextView                   charactersLeft;
  private   ConversationFragment       fragment;
  private   Button                     unblockButton;
  private   Button                     makeDefaultSmsButton;
  private   Button                     registerButton;
  private   InputAwareLayout           container;
  protected Stub<ReminderView>         reminderView;
  private   Stub<UnverifiedBannerView> unverifiedBannerView;
  private   Stub<ReviewBannerView>      reviewBanner;
  private   TypingStatusTextWatcher     typingTextWatcher;
  private   ConversationSearchBottomBar searchNav;
  private   MenuItem                    searchViewItem;
  private   MessageRequestsBottomView   messageRequestBottomView;
  private   ConversationReactionOverlay reactionOverlay;

  private   AttachmentManager        attachmentManager;
  private   AudioRecorder            audioRecorder;
  private   BroadcastReceiver        securityUpdateReceiver;
  private   Stub<MediaKeyboard>      emojiDrawerStub;
  private   Stub<AttachmentKeyboard> attachmentKeyboardStub;
  protected HidingLinearLayout       quickAttachmentToggle;
  protected HidingLinearLayout       inlineAttachmentToggle;
  private   InputPanel               inputPanel;
  private   View                     panelParent;
  private   View                     noLongerMemberBanner;
  private   View                     requestingMemberBanner;
  private   View                     cancelJoinRequest;
  private   Stub<View>               mentionsSuggestions;
  private   MaterialButton           joinGroupCallButton;
  private   boolean                  callingTooltipShown;

  private LinkPreviewViewModel         linkPreviewViewModel;
  private ConversationSearchViewModel  searchViewModel;
  private ConversationStickerViewModel stickerViewModel;
  private ConversationViewModel        viewModel;
  private InviteReminderModel          inviteReminderModel;
  private ConversationGroupViewModel   groupViewModel;
  private MentionsPickerViewModel      mentionsViewModel;
  private GroupCallViewModel           groupCallViewModel;

  private LiveRecipient recipient;
  private long          threadId;
  private int           distributionType;
  private int           reactWithAnyEmojiStartPage;
  private boolean       isSecureText;
  private boolean       isDefaultSms                  = true;
  private boolean       isMmsEnabled                  = true;
  private boolean       isSecurityInitialized         = false;

  private       IdentityRecordList identityRecords = new IdentityRecordList(Collections.emptyList());
  private final DynamicTheme       dynamicTheme    = new DynamicDarkToolbarTheme();
  private final DynamicLanguage    dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    if (ConversationIntents.isInvalid(getIntent())) {
      Log.w(TAG, "[onCreate] Missing recipientId!");
      // TODO [greyson] Navigation
      startActivity(MainActivity.clearTop(this));
      finish();
      return;
    }

    ConversationIntents.Args args = ConversationIntents.Args.from(getIntent());

    reportShortcutLaunch(args.getRecipientId());
    setContentView(R.layout.conversation_activity);

    getWindow().getDecorView().setBackgroundResource(R.color.signal_background_primary);

    fragment = initFragment(R.id.fragment_content, new ConversationFragment(), dynamicLanguage.getCurrentLocale());

    initializeReceivers();
    initializeActionBar();
    initializeViews();
    initializeResources(args);
    initializeLinkPreviewObserver();
    initializeSearchObserver();
    initializeStickerObserver();
    initializeViewModel(args);
    initializeGroupViewModel();
    initializeMentionsViewModel();
    initializeGroupCallViewModel();
    initializeEnabledCheck();
    initializePendingRequestsBanner();
    initializeGroupV1MigrationsBanners();
    initializeSecurity(recipient.get().isRegistered(), isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeProfiles();
        initializeGv1Migration();
        initializeDraft(args).addListener(new AssertedSuccessListener<Boolean>() {
          @Override
          public void onSuccess(Boolean loadedDraft) {
            if (loadedDraft != null && loadedDraft) {
              Log.i(TAG, "Finished loading draft");
              Util.runOnMain(() -> {
                if (fragment != null && fragment.isResumed()) {
                  fragment.moveToLastSeen();
                } else {
                  Log.w(TAG, "Wanted to move to the last seen position, but the fragment was in an invalid state");
                }
              });
            }

            if (TextSecurePreferences.isTypingIndicatorsEnabled(ConversationActivity.this)) {
              composeText.addTextChangedListener(typingTextWatcher);
            }
            composeText.setSelection(composeText.length(), composeText.length());
          }
        });
      }
    });
    initializeInsightObserver();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.i(TAG, "onNewIntent()");
    
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    reactWithAnyEmojiStartPage = 0;
    if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent() || inputPanel.getQuote().isPresent()) {
      saveDraft();
      attachmentManager.clear(glideRequests, false);
      inputPanel.clearQuote();
      silentlySetComposeText("");
    }

    if (ConversationIntents.isInvalid(intent)) {
      Log.w(TAG, "[onNewIntent] Missing recipientId!");
      // TODO [greyson] Navigation
      startActivity(MainActivity.clearTop(this));
      finish();
      return;
    }

    setIntent(intent);

    viewModel.setArgs(ConversationIntents.Args.from(intent));

    reportShortcutLaunch(viewModel.getArgs().getRecipientId());
    initializeResources(viewModel.getArgs());
    initializeSecurity(recipient.get().isRegistered(), isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeDraft(viewModel.getArgs());
      }
    });

    if (fragment != null) {
      fragment.onNewIntent();
    }

    searchNav.setVisibility(View.GONE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    EventBus.getDefault().register(this);
    initializeMmsEnabledCheck();
    initializeIdentityRecords();
    composeText.setTransport(sendButton.getSelectedTransport());

    Recipient recipientSnapshot = recipient.get();

    titleView.setTitle(glideRequests, recipientSnapshot);
    setActionBarColor(recipientSnapshot.getColor());
    setBlockedUserState(recipientSnapshot, isSecureText, isDefaultSms);
    calculateCharactersRemaining();

    if (recipientSnapshot.getGroupId().isPresent() && recipientSnapshot.getGroupId().get().isV2()) {
      GroupId.V2 groupId = recipientSnapshot.getGroupId().get().requireV2();

      ApplicationDependencies.getJobManager()
                             .startChain(new RequestGroupV2InfoJob(groupId))
                             .then(new GroupV2UpdateSelfProfileKeyJob(groupId))
                             .enqueue();

      if (viewModel.getArgs().isFirstTimeInSelfCreatedGroup()) {
        groupViewModel.inviteFriendsOneTimeIfJustSelfInGroup(getSupportFragmentManager(), groupId);
      }
    }

    if (groupCallViewModel != null) {
      groupCallViewModel.peekGroupCall(this);
    }

    setVisibleThread(threadId);
    ConversationUtil.refreshRecipientShortcuts();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (!isInBubble()) {
      ApplicationDependencies.getMessageNotifier().clearVisibleThread();
    }

    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_end);
    inputPanel.onPause();

    fragment.setLastSeen(System.currentTimeMillis());
    markLastSeen();
    EventBus.getDefault().unregister(this);
  }

  @Override
  protected void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.i(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    composeText.setTransport(sendButton.getSelectedTransport());

    if (emojiDrawerStub.resolved() && container.getCurrentInput() == emojiDrawerStub.get()) {
      container.hideAttachedInput(true);
    }

    if (reactionOverlay != null && reactionOverlay.isShowing()) {
      reactionOverlay.hide();
    }
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (securityUpdateReceiver != null)  unregisterReceiver(securityUpdateReceiver);
    super.onDestroy();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    return reactionOverlay.applyTouchEvent(ev) || super.dispatchTouchEvent(ev);
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.i(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if ((data == null && reqCode != TAKE_PHOTO && reqCode != SMS_DEFAULT) ||
        (resultCode != RESULT_OK && reqCode != SMS_DEFAULT))
    {
      updateLinkPreviewState();
      return;
    }

    switch (reqCode) {
    case PICK_DOCUMENT:
      setMedia(data.getData(), MediaType.DOCUMENT);
      break;
    case PICK_AUDIO:
      setMedia(data.getData(), MediaType.AUDIO);
      break;
    case PICK_CONTACT:
      if (isSecureText && !isSmsForced()) {
        openContactShareEditor(data.getData());
      } else {
        addAttachmentContactInfo(data.getData());
      }
      break;
    case GET_CONTACT_DETAILS:
      sendSharedContact(data.getParcelableArrayListExtra(ContactShareEditActivity.KEY_CONTACTS));
      break;
    case GROUP_EDIT:
      Recipient recipientSnapshot = recipient.get();

      onRecipientChanged(recipientSnapshot);
      titleView.setTitle(glideRequests, recipientSnapshot);
      NotificationChannels.updateContactChannelName(this, recipientSnapshot);
      setBlockedUserState(recipientSnapshot, isSecureText, isDefaultSms);
      supportInvalidateOptionsMenu();
      break;
    case TAKE_PHOTO:
      handleImageFromDeviceCameraApp();
      break;
    case ADD_CONTACT:
      SimpleTask.run(() -> {
        try {
          DirectoryHelper.refreshDirectoryFor(this, recipient.get(), false);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh user after adding to contacts.");
        }
        return null;
      }, nothing -> onRecipientChanged(recipient.get()));
      break;
    case PICK_LOCATION:
      SignalPlace place = new SignalPlace(PlacePickerActivity.addressFromData(data));
      attachmentManager.setLocation(place, getCurrentMediaConstraints());
      break;
    case PICK_GIF:
      setMedia(data.getData(),
               MediaType.GIF,
               data.getIntExtra(GiphyActivity.EXTRA_WIDTH, 0),
               data.getIntExtra(GiphyActivity.EXTRA_HEIGHT, 0),
               data.getBooleanExtra(GiphyActivity.EXTRA_BORDERLESS, false));
      break;
    case SMS_DEFAULT:
      initializeSecurity(isSecureText, isDefaultSms);
      break;
    case MEDIA_SENDER:
      MediaSendActivityResult result = data.getParcelableExtra(MediaSendActivity.EXTRA_RESULT);
      sendButton.setTransport(result.getTransport());

      if (result.isPushPreUpload()) {
        sendMediaMessage(result);
        return;
      }

      long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
      int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
      boolean    initiating     = threadId == -1;
      QuoteModel quote          = result.isViewOnce() ? null : inputPanel.getQuote().orNull();
      SlideDeck  slideDeck      = new SlideDeck();
      List<Mention> mentions    = new ArrayList<>(result.getMentions());

      for (Media mediaItem : result.getNonUploadedMedia()) {
        if (MediaUtil.isVideoType(mediaItem.getMimeType())) {
          slideDeck.addSlide(new VideoSlide(this, mediaItem.getUri(), mediaItem.getSize(), mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.getCaption().orNull(), mediaItem.getTransformProperties().orNull()));
        } else if (MediaUtil.isGif(mediaItem.getMimeType())) {
          slideDeck.addSlide(new GifSlide(this, mediaItem.getUri(), mediaItem.getSize(), mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.isBorderless(), mediaItem.getCaption().orNull()));
        } else if (MediaUtil.isImageType(mediaItem.getMimeType())) {
          slideDeck.addSlide(new ImageSlide(this, mediaItem.getUri(), mediaItem.getMimeType(), mediaItem.getSize(), mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.isBorderless(), mediaItem.getCaption().orNull(), null));
        } else {
          Log.w(TAG, "Asked to send an unexpected mimeType: '" + mediaItem.getMimeType() + "'. Skipping.");
        }
      }

      final Context context = ConversationActivity.this.getApplicationContext();

      sendMediaMessage(result.getTransport().isSms(),
                       result.getBody(),
                       slideDeck,
                       quote,
                       Collections.emptyList(),
                       Collections.emptyList(),
                       mentions,
                       expiresIn,
                       result.isViewOnce(),
                       subscriptionId,
                       initiating,
                       true).addListener(new AssertedSuccessListener<Void>() {
        @Override
        public void onSuccess(Void result) {
          AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            Stream.of(slideDeck.getSlides())
                  .map(Slide::getUri)
                  .withoutNulls()
                  .filter(BlobProvider::isAuthority)
                  .forEach(uri -> BlobProvider.getInstance().delete(context, uri));
          });
        }
      });

      break;
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(STATE_REACT_WITH_ANY_PAGE, reactWithAnyEmojiStartPage);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);

    reactWithAnyEmojiStartPage = savedInstanceState.getInt(STATE_REACT_WITH_ANY_PAGE, 0);
  }

  private void setVisibleThread(long threadId) {
    if (!isInBubble()) {
      ApplicationDependencies.getMessageNotifier().setVisibleThread(threadId);
    }
  }

  private void reportShortcutLaunch(@NonNull RecipientId recipientId) {
    if (Build.VERSION.SDK_INT < ConversationUtil.CONVERSATION_SUPPORT_VERSION) {
      return;
    }

    ShortcutManager shortcutManager = ServiceUtil.getShortcutManager(this);
    if (shortcutManager != null) {
      shortcutManager.reportShortcutUsed(ConversationUtil.getShortcutId(recipientId));
    }
  }

  private void handleImageFromDeviceCameraApp() {
    if (attachmentManager.getCaptureUri() == null) {
      Log.w(TAG, "No image available.");
      return;
    }

    try {
      Uri mediaUri = BlobProvider.getInstance()
                                 .forData(getContentResolver().openInputStream(attachmentManager.getCaptureUri()), 0L)
                                 .withMimeType(MediaUtil.IMAGE_JPEG)
                                 .createForSingleSessionOnDisk(this);

      getContentResolver().delete(attachmentManager.getCaptureUri(), null, null);

      setMedia(mediaUri, MediaType.IMAGE);
    } catch (IOException ioe) {
      Log.w(TAG, "Could not handle public image", ioe);
    }
  }

  @Override
  public void startActivity(Intent intent) {
    if (intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null) {
      intent.removeExtra(Browser.EXTRA_APPLICATION_ID);
    }

    try {
      super.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
      Toast.makeText(this, R.string.ConversationActivity_there_is_no_app_available_to_handle_this_link_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    GroupActiveState groupActiveState = groupViewModel.getGroupActiveState().getValue();
    boolean isActiveGroup             = groupActiveState != null && groupActiveState.isActiveGroup();
    boolean isActiveV2Group           = groupActiveState != null && groupActiveState.isActiveV2Group();
    boolean isInActiveGroup           = groupActiveState != null && !groupActiveState.isActiveGroup();

    if (isInMessageRequest()) {
      if (isActiveGroup) {
        inflater.inflate(R.menu.conversation_message_requests_group, menu);
      }

      inflater.inflate(R.menu.conversation_message_requests, menu);

      if (recipient != null && recipient.get().isMuted()) inflater.inflate(R.menu.conversation_muted, menu);
      else                                                inflater.inflate(R.menu.conversation_unmuted, menu);

      super.onCreateOptionsMenu(menu);
      return true;
    }

    if (isSecureText) {
      if (recipient.get().getExpireMessages() > 0) {
        if (!isInActiveGroup) {
          inflater.inflate(R.menu.conversation_expiring_on, menu);
        }
        titleView.showExpiring(recipient);
      } else {
        if (!isInActiveGroup) {
          inflater.inflate(R.menu.conversation_expiring_off, menu);
        }
        titleView.clearExpiring();
      }
    }

    if (isSingleConversation()) {
      if (isSecureText) inflater.inflate(R.menu.conversation_callable_secure, menu);
      else              inflater.inflate(R.menu.conversation_callable_insecure, menu);
    } else if (isGroupConversation()) {
      if (isActiveV2Group && FeatureFlags.groupCalling()) {
        inflater.inflate(R.menu.conversation_callable_groupv2, menu);
        if (groupCallViewModel != null && Boolean.TRUE.equals(groupCallViewModel.hasActiveGroupCall().getValue())) {
          hideMenuItem(menu, R.id.menu_video_secure);
        }
        showGroupCallingTooltip();
      }

      inflater.inflate(R.menu.conversation_group_options, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      }

      inflater.inflate(R.menu.conversation_active_group_options, menu);
    }

    inflater.inflate(R.menu.conversation, menu);

    if (isSingleConversation() && !isSecureText) {
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (recipient != null && recipient.get().isMuted()) inflater.inflate(R.menu.conversation_muted, menu);
    else                                                inflater.inflate(R.menu.conversation_unmuted, menu);

    if (isSingleConversation() && getRecipient().getContactUri() == null) {
      inflater.inflate(R.menu.conversation_add_to_contacts, menu);
    }

    if (recipient != null && recipient.get().isSelf()) {
      if (isSecureText) {
        hideMenuItem(menu, R.id.menu_call_secure);
        hideMenuItem(menu, R.id.menu_video_secure);
      } else {
        hideMenuItem(menu, R.id.menu_call_insecure);
      }

      hideMenuItem(menu, R.id.menu_mute_notifications);
    }

    if (recipient != null && recipient.get().isBlocked()) {
      if (isSecureText) {
        hideMenuItem(menu, R.id.menu_call_secure);
        hideMenuItem(menu, R.id.menu_video_secure);
        hideMenuItem(menu, R.id.menu_expiring_messages);
        hideMenuItem(menu, R.id.menu_expiring_messages_off);
      } else {
        hideMenuItem(menu, R.id.menu_call_insecure);
      }

      hideMenuItem(menu, R.id.menu_mute_notifications);
    }

    hideMenuItem(menu, R.id.menu_group_recipients);

    if (isActiveV2Group) {
      hideMenuItem(menu, R.id.menu_mute_notifications);
      hideMenuItem(menu, R.id.menu_conversation_settings);
    } else if (isGroupConversation()) {
      hideMenuItem(menu, R.id.menu_conversation_settings);
    }

    hideMenuItem(menu, R.id.menu_create_bubble);
    viewModel.canShowAsBubble().observe(this, canShowAsBubble -> {
      MenuItem item = menu.findItem(R.id.menu_create_bubble);

      if (item != null) {
        item.setVisible(canShowAsBubble && !isInBubble());
      }
    });

    searchViewItem = menu.findItem(R.id.menu_search);

    SearchView                     searchView    = (SearchView) searchViewItem.getActionView();
    SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        searchViewModel.onQueryUpdated(query, threadId, true);
        searchNav.showLoading();
        fragment.onSearchQueryUpdated(query);
        return true;
      }

      @Override
      public boolean onQueryTextChange(String query) {
        searchViewModel.onQueryUpdated(query, threadId, false);
        searchNav.showLoading();
        fragment.onSearchQueryUpdated(query);
        return true;
      }
    };

    searchViewItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem item) {
        searchView.setOnQueryTextListener(queryListener);
        searchViewModel.onSearchOpened();
        searchNav.setVisibility(View.VISIBLE);
        searchNav.setData(0, 0);
        inputPanel.setVisibility(View.GONE);

        for (int i = 0; i < menu.size(); i++) {
          if (!menu.getItem(i).equals(searchViewItem)) {
            menu.getItem(i).setVisible(false);
          }
        }
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem item) {
        searchView.setOnQueryTextListener(null);
        searchViewModel.onSearchClosed();
        searchNav.setVisibility(View.GONE);
        inputPanel.setVisibility(View.VISIBLE);
        fragment.onSearchQueryUpdated(null);
        setBlockedUserState(recipient.get(), isSecureText, isDefaultSms);
        invalidateOptionsMenu();
        return true;
      }
    });

    super.onCreateOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_call_secure:               handleDial(getRecipient(), true);                  return true;
    case R.id.menu_video_secure:              handleVideo(getRecipient());                       return true;
    case R.id.menu_call_insecure:             handleDial(getRecipient(), false);                 return true;
    case R.id.menu_view_media:                handleViewMedia();                                 return true;
    case R.id.menu_add_shortcut:              handleAddShortcut();                               return true;
    case R.id.menu_search:                    handleSearch();                                    return true;
    case R.id.menu_add_to_contacts:           handleAddToContacts();                             return true;
    case R.id.menu_group_recipients:          handleDisplayGroupRecipients();                    return true;
    case R.id.menu_distribution_broadcast:    handleDistributionBroadcastEnabled(item);          return true;
    case R.id.menu_distribution_conversation: handleDistributionConversationEnabled(item);       return true;
    case R.id.menu_group_settings:            handleManageGroup();                               return true;
    case R.id.menu_leave:                     handleLeavePushGroup();                            return true;
    case R.id.menu_invite:                    handleInviteLink();                                return true;
    case R.id.menu_mute_notifications:        handleMuteNotifications();                         return true;
    case R.id.menu_unmute_notifications:      handleUnmuteNotifications();                       return true;
    case R.id.menu_conversation_settings:     handleConversationSettings();                      return true;
    case R.id.menu_expiring_messages_off:
    case R.id.menu_expiring_messages:         handleSelectMessageExpiration();                   return true;
    case R.id.menu_create_bubble:             handleCreateBubble();                              return true;
    case android.R.id.home:                   onNavigateUp();                                    return true;
    }

    return false;
  }

  @Override
  public boolean onMenuOpened(int featureId, Menu menu) {
    if (menu == null) {
      return super.onMenuOpened(featureId, null);
    }

    if (!SignalStore.uiHints().hasSeenGroupSettingsMenuToast()) {
      MenuItem settingsMenuItem = menu.findItem(R.id.menu_group_settings);

      if (settingsMenuItem != null && settingsMenuItem.isVisible()) {
        Toast toast = Toast.makeText(this, R.string.ConversationActivity__more_options_now_in_group_settings, Toast.LENGTH_SHORT);

        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        SignalStore.uiHints().markHasSeenGroupSettingsMenuToast();
      }
    }

    return super.onMenuOpened(featureId, menu);
  }

  @Override
  public void onBackPressed() {
    Log.d(TAG, "onBackPressed()");
    if (reactionOverlay.isShowing()) {
      reactionOverlay.hide();
    } else if (container.isInputOpen()) {
      container.hideCurrentInput(composeText);
    } else {
      super.onBackPressed();
    }
  }

  @Override
  public void onKeyboardShown() {
    inputPanel.onKeyboardShown();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {
    updateReminders();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onAttachmentMediaClicked(@NonNull Media media) {
    linkPreviewViewModel.onUserCancel();
    startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport()), MEDIA_SENDER);
    container.hideCurrentInput(composeText);
  }

  @Override
  public void onAttachmentSelectorClicked(@NonNull AttachmentKeyboardButton button) {
    switch (button) {
      case GALLERY:
        AttachmentManager.selectGallery(this, MEDIA_SENDER, recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport());
        break;
      case GIF:
        AttachmentManager.selectGif(this, PICK_GIF, !isSecureText, recipient.get().getColor().toConversationColor(this));
        break;
      case FILE:
        AttachmentManager.selectDocument(this, PICK_DOCUMENT);
        break;
      case CONTACT:
        AttachmentManager.selectContactInfo(this, PICK_CONTACT);
        break;
      case LOCATION:
        AttachmentManager.selectLocation(this, PICK_LOCATION);
        break;
    }

    container.hideCurrentInput(composeText);
  }

  @Override
  public void onAttachmentPermissionsRequested() {
    Permissions.with(this)
               .request(Manifest.permission.READ_EXTERNAL_STORAGE)
               .onAllGranted(() -> viewModel.onAttachmentKeyboardOpen())
               .withPermanentDenialDialog(getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
               .execute();
  }

//////// Event Handlers

  private void handleSelectMessageExpiration() {
    boolean activeGroup = isActiveGroup();

    if (isPushGroupConversation() && !activeGroup) {
      return;
    }

    final long thread = this.threadId;

    ExpirationDialog.show(this, recipient.get().getExpireMessages(),
      expirationTime ->
        SimpleTask.run(
          getLifecycle(),
          () -> {
            if (activeGroup) {
              try {
                GroupManager.updateGroupTimer(ConversationActivity.this, getRecipient().requireGroupId().requirePush(), expirationTime);
                } catch (GroupChangeException | IOException e) {
                Log.w(TAG, e);
                return GroupChangeResult.failure(GroupChangeFailureReason.fromException(e));
              }
            } else {
              DatabaseFactory.getRecipientDatabase(ConversationActivity.this).setExpireMessages(recipient.getId(), expirationTime);
              OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(getRecipient(), System.currentTimeMillis(), expirationTime * 1000L);
              MessageSender.send(ConversationActivity.this, outgoingMessage, thread, false, null);
            }
            return GroupChangeResult.SUCCESS;
          },
          (changeResult) -> {
            if (!changeResult.isSuccess()) {
              Toast.makeText(ConversationActivity.this, GroupErrors.getUserDisplayMessage(changeResult.getFailureReason()), Toast.LENGTH_SHORT).show();
            } else {
              invalidateOptionsMenu();
              if (fragment != null) fragment.setLastSeen(0);
            }
          })
    );
  }

  private void handleMuteNotifications() {
    MuteDialog.show(this, until -> {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getRecipientDatabase(ConversationActivity.this)
                         .setMuted(recipient.getId(), until);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });
  }

  private void handleConversationSettings() {
    if (isGroupConversation()) {
      handleManageGroup();
      return;
    }

    if (isInMessageRequest()) return;

    Intent intent = ManageRecipientActivity.newIntentFromConversation(this, recipient.getId());
    startActivitySceneTransition(intent, titleView.findViewById(R.id.contact_photo_image), "avatar");
  }

  private void handleUnmuteNotifications() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientDatabase(ConversationActivity.this)
                       .setMuted(recipient.getId(), 0);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleUnblock() {
    BlockUnblockDialog.showUnblockFor(this, getLifecycle(), recipient.get(), () -> {
      SignalExecutors.BOUNDED.execute(() -> {
        RecipientUtil.unblock(ConversationActivity.this, recipient.get());
      });
    });
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private void handleMakeDefaultSms() {
    startActivityForResult(SmsUtil.getSmsRoleIntent(this), SMS_DEFAULT);
  }

  private void handleRegisterForSignal() {
    startActivity(RegistrationNavigationActivity.newIntentForReRegistration(this));
  }

  private void handleInviteLink() {
    String inviteText = getString(R.string.ConversationActivity_lets_switch_to_signal, getString(R.string.install_url));

    if (isDefaultSms) {
      composeText.appendInvite(inviteText);
    } else {
      Intent intent = new Intent(Intent.ACTION_SENDTO);
      intent.setData(Uri.parse("smsto:" + recipient.get().requireSmsAddress()));
      intent.putExtra("sms_body", inviteText);
      intent.putExtra(Intent.EXTRA_TEXT, inviteText);
      startActivity(intent);
    }
  }

  private void handleViewMedia() {
    startActivity(MediaOverviewActivity.forThread(this, threadId));
  }

  private void handleAddShortcut() {
    Log.i(TAG, "Creating home screen shortcut for recipient " + recipient.get().getId());

    final Context context = getApplicationContext();
    final Recipient recipient = this.recipient.get();

    GlideApp.with(this)
            .asBitmap()
            .load(recipient.getContactPhoto())
            .error(recipient.getFallbackContactPhoto().asDrawable(this, recipient.getColor().toAvatarColor(this), false))
            .into(new CustomTarget<Bitmap>() {
              @Override
              public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (errorDrawable == null) {
                  throw new AssertionError();
                }

                Log.w(TAG, "Utilizing fallback photo for shortcut for recipient " + recipient.getId());

                SimpleTask.run(() -> DrawableUtil.toBitmap(errorDrawable, SHORTCUT_ICON_SIZE, SHORTCUT_ICON_SIZE),
                               bitmap -> addIconToHomeScreen(context, bitmap, recipient));
              }

              @Override
              public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                SimpleTask.run(() -> BitmapUtil.createScaledBitmap(resource, SHORTCUT_ICON_SIZE, SHORTCUT_ICON_SIZE),
                               bitmap -> addIconToHomeScreen(context, bitmap, recipient));
              }

              @Override
              public void onLoadCleared(@Nullable Drawable placeholder) {
              }
            });

  }

  private void handleCreateBubble() {
    ConversationIntents.Args args = viewModel.getArgs();

    BubbleUtil.displayAsBubble(this, args.getRecipientId(), args.getThreadId());
    finish();
  }

  private static void addIconToHomeScreen(@NonNull Context context,
                                          @NonNull Bitmap bitmap,
                                          @NonNull Recipient recipient)
  {
    IconCompat icon = IconCompat.createWithAdaptiveBitmap(bitmap);
    String     name = recipient.isSelf() ? context.getString(R.string.note_to_self)
                                                  : recipient.getDisplayName(context);

    ShortcutInfoCompat shortcutInfoCompat = new ShortcutInfoCompat.Builder(context, recipient.getId().serialize() + '-' + System.currentTimeMillis())
                                                                  .setShortLabel(name)
                                                                  .setIcon(icon)
                                                                  .setIntent(ShortcutLauncherActivity.createIntent(context, recipient.getId()))
                                                                  .build();

    if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfoCompat, null)) {
      Toast.makeText(context, context.getString(R.string.ConversationActivity_added_to_home_screen), Toast.LENGTH_LONG).show();
    }

    bitmap.recycle();
  }

  private void handleSearch() {
    searchViewModel.onSearchOpened();
  }

  private void handleLeavePushGroup() {
    if (getRecipient() == null) {
      Toast.makeText(this, getString(R.string.ConversationActivity_invalid_recipient),
                     Toast.LENGTH_LONG).show();
      return;
    }

    LeaveGroupDialog.handleLeavePushGroup(this, getRecipient().requireGroupId().requirePush(), this::finish);
  }

  private void handleManageGroup() {
    startActivityForResult(ManageGroupActivity.newIntent(ConversationActivity.this, recipient.get().requireGroupId()),
                           GROUP_EDIT,
                           ManageGroupActivity.createTransitionBundle(this, titleView.findViewById(R.id.contact_photo_image)));
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.BROADCAST;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.BROADCAST);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.CONVERSATION);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleDial(final Recipient recipient, boolean isSecure) {
    if (recipient == null) return;

    if (isSecure) {
      CommunicationActions.startVoiceCall(this, recipient);
    } else {
      CommunicationActions.startInsecureCall(this, recipient);
    }
  }

  private void handleVideo(final Recipient recipient) {
    if (recipient == null) return;

    CommunicationActions.startVideoCall(this, recipient);
  }

  private void handleDisplayGroupRecipients() {
    new GroupMembersDialog(this, getRecipient()).display();
  }

  private void handleAddToContacts() {
    if (recipient.get().isGroup()) return;

    try {
      startActivityForResult(RecipientExporter.export(recipient.get()).asAddContactIntent(), ADD_CONTACT);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
    }
  }

  private boolean handleDisplayQuickContact() {
    if (isInMessageRequest() || recipient.get().isGroup()) return false;

    if (recipient.get().getContactUri() != null) {
      ContactsContract.QuickContact.showQuickContact(ConversationActivity.this, titleView, recipient.get().getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
    } else {
      handleAddToContacts();
    }

    return true;
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled || isSecureText) {
      viewModel.getRecentMedia().removeObservers(this);

      if (attachmentKeyboardStub.resolved() && container.isInputOpen() && container.getCurrentInput() == attachmentKeyboardStub.get()) {
        container.showSoftkey(composeText);
      } else {
        viewModel.getRecentMedia().observe(this, media -> attachmentKeyboardStub.get().onMediaChanged(media));
        attachmentKeyboardStub.get().setCallback(this);
        container.show(composeText, attachmentKeyboardStub.get());

        viewModel.onAttachmentKeyboardOpen();
      }
    } else {
      handleManualMmsRequired();
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Bundle extras = getIntent().getExtras();
    Intent intent = new Intent(this, PromptMmsActivity.class);
    if (extras != null) intent.putExtras(extras);
    startActivity(intent);
  }

  private void handleRecentSafetyNumberChange() {
    List<IdentityRecord> records = identityRecords.getUnverifiedRecords();
    records.addAll(identityRecords.getUntrustedRecords());
    SafetyNumberChangeDialog.show(getSupportFragmentManager(), records);
  }

  @Override
  public void onSendAnywayAfterSafetyNumberChange(@NonNull List<RecipientId> changedRecipients) {
    initializeIdentityRecords().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        sendMessage();
      }
    });
  }

  @Override
  public void onMessageResentAfterSafetyNumberChange() {
    initializeIdentityRecords().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) { }
    });
  }

  @Override
  public void onCanceled() { }

  private void handleSecurityChange(boolean isSecureText, boolean isDefaultSms) {
    Log.i(TAG, "handleSecurityChange(" + isSecureText + ", " + isDefaultSms + ")");

    this.isSecureText          = isSecureText;
    this.isDefaultSms          = isDefaultSms;
    this.isSecurityInitialized = true;

    boolean isMediaMessage = recipient.get().isMmsGroup() || attachmentManager.isAttachmentPresent();

    sendButton.resetAvailableTransports(isMediaMessage);

    if (!isSecureText && !isPushGroupConversation()) sendButton.disableTransport(Type.TEXTSECURE);
    if (recipient.get().isPushGroup())            sendButton.disableTransport(Type.SMS);

    if (!recipient.get().isPushGroup() && recipient.get().isForceSmsSelection()) {
      sendButton.setDefaultTransport(Type.SMS);
    } else {
      if (isSecureText || isPushGroupConversation()) sendButton.setDefaultTransport(Type.TEXTSECURE);
      else                                           sendButton.setDefaultTransport(Type.SMS);
    }

    calculateCharactersRemaining();
    supportInvalidateOptionsMenu();
    setBlockedUserState(recipient.get(), isSecureText, isDefaultSms);
  }

  ///// Initializers

  private ListenableFuture<Boolean> initializeDraft(@NonNull ConversationIntents.Args args) {
    final SettableFuture<Boolean> result = new SettableFuture<>();

    final CharSequence   draftText        = args.getDraftText();
    final Uri            draftMedia       = getIntent().getData();
    final String         draftContentType = getIntent().getType();
    final MediaType      draftMediaType   = MediaType.from(draftContentType);
    final List<Media>    mediaList        = args.getMedia();
    final StickerLocator stickerLocator   = args.getStickerLocator();
    final boolean        borderless       = args.isBorderless();

    if (stickerLocator != null && draftMedia != null) {
      Log.d(TAG, "Handling shared sticker.");
      sendSticker(stickerLocator, Objects.requireNonNull(draftContentType), draftMedia, 0, true);
      return new SettableFuture<>(false);
    }

    if (draftMedia != null && draftContentType != null && borderless) {
      SimpleTask.run(getLifecycle(),
                     () -> getKeyboardImageDetails(draftMedia),
                     details -> sendKeyboardImage(draftMedia, draftContentType, details));
      return new SettableFuture<>(false);
    }

    if (!Util.isEmpty(mediaList)) {
      Log.d(TAG, "Handling shared Media.");
      Intent sendIntent = MediaSendActivity.buildEditorIntent(this, mediaList, recipient.get(), draftText, sendButton.getSelectedTransport());
      startActivityForResult(sendIntent, MEDIA_SENDER);
      return new SettableFuture<>(false);
    }

    if (draftText != null) {
      composeText.setText("");
      composeText.append(draftText);
      result.set(true);
    }

    if (draftMedia != null && draftMediaType != null) {
      Log.d(TAG, "Handling shared Data.");
      return setMedia(draftMedia, draftMediaType);
    }

    if (draftText == null && draftMedia == null && draftMediaType == null) {
      return initializeDraftFromDatabase();
    } else {
      updateToggleButtonState();
      result.set(false);
    }

    return result;
  }

  private void initializeEnabledCheck() {
    groupViewModel.getSelfMemberLevel().observe(this, selfMemberShip -> {
      boolean canSendMessages;
      boolean leftGroup;
      boolean canCancelRequest;

      if (selfMemberShip == null) {
        leftGroup        = false;
        canSendMessages  = true;
        canCancelRequest = false;
      } else {
        switch (selfMemberShip) {
          case NOT_A_MEMBER:
            leftGroup        = true;
            canSendMessages  = false;
            canCancelRequest = false;
            break;
          case PENDING_MEMBER:
            leftGroup        = false;
            canSendMessages  = false;
            canCancelRequest = false;
            break;
          case REQUESTING_MEMBER:
            leftGroup        = false;
            canSendMessages  = false;
            canCancelRequest = true;
            break;
          case FULL_MEMBER:
          case ADMINISTRATOR:
            leftGroup        = false;
            canSendMessages  = true;
            canCancelRequest = false;
            break;
          default:
            throw new AssertionError();
        }
      }

      noLongerMemberBanner.setVisibility(leftGroup ? View.VISIBLE : View.GONE);
      requestingMemberBanner.setVisibility(canCancelRequest ? View.VISIBLE : View.GONE);
      if (canCancelRequest) {
        cancelJoinRequest.setOnClickListener(v -> ConversationGroupViewModel.onCancelJoinRequest(getRecipient(), new AsynchronousCallback.MainThread<Void, GroupChangeFailureReason>() {
          @Override
          public void onComplete(@Nullable Void result) {
            Log.d(TAG, "Cancel request complete");
          }

          @Override
          public void onError(@Nullable GroupChangeFailureReason error) {
            Log.d(TAG, "Cancel join request failed " + error);
            Toast.makeText(ConversationActivity.this, GroupErrors.getUserDisplayMessage(error), Toast.LENGTH_SHORT).show();
          }
        }.toWorkerCallback()));
      }

      inputPanel.setVisibility(canSendMessages ? View.VISIBLE : View.GONE);
      inputPanel.setEnabled(canSendMessages);
      sendButton.setEnabled(canSendMessages);
      attachButton.setEnabled(canSendMessages);
    });
  }

  private void initializePendingRequestsBanner() {
    groupViewModel.getActionableRequestingMembers()
                  .observe(this, actionablePendingGroupRequests -> updateReminders());
  }

  private void initializeGroupV1MigrationsBanners() {
    groupViewModel.getGroupV1MigrationSuggestions()
                  .observe(this, s -> updateReminders());
    groupViewModel.getShowGroupsV1MigrationBanner()
                  .observe(this, b -> updateReminders());
  }


  private ListenableFuture<Boolean> initializeDraftFromDatabase() {
    SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Void, Void, Pair<Drafts, CharSequence>>() {
      @Override
      protected Pair<Drafts, CharSequence> doInBackground(Void... params) {
        Context       context       = ConversationActivity.this;
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(context);
        Drafts        results       = draftDatabase.getDrafts(threadId);
        Draft         mentionsDraft = results.getDraftOfType(Draft.MENTION);
        Spannable     updatedText   = null;

        if (mentionsDraft != null) {
          String                 text     = results.getDraftOfType(Draft.TEXT).getValue();
          List<Mention>          mentions = MentionUtil.bodyRangeListToMentions(context, Base64.decodeOrThrow(mentionsDraft.getValue()));
          UpdatedBodyAndMentions updated  = MentionUtil.updateBodyAndMentionsWithDisplayNames(context, text, mentions);

          updatedText = new SpannableString(updated.getBody());
          MentionAnnotation.setMentionAnnotations(updatedText, updated.getMentions());
        }

        draftDatabase.clearDrafts(threadId);

        return new Pair<>(results, updatedText);
      }

      @Override
      protected void onPostExecute(Pair<Drafts, CharSequence> draftsWithUpdatedMentions) {
        Drafts       drafts      = Objects.requireNonNull(draftsWithUpdatedMentions.first());
        CharSequence updatedText = draftsWithUpdatedMentions.second();

        if (drafts.isEmpty()) {
          future.set(false);
          updateToggleButtonState();
          return;
        }

        AtomicInteger                      draftsRemaining = new AtomicInteger(drafts.size());
        AtomicBoolean                      success         = new AtomicBoolean(false);
        ListenableFuture.Listener<Boolean> listener        = new AssertedSuccessListener<Boolean>() {
          @Override
          public void onSuccess(Boolean result) {
            success.compareAndSet(false, result);

            if (draftsRemaining.decrementAndGet() <= 0) {
              future.set(success.get());
            }
          }
        };

        for (Draft draft : drafts) {
          try {
            switch (draft.getType()) {
              case Draft.TEXT:
                composeText.setText(updatedText == null ? draft.getValue() : updatedText);
                listener.onSuccess(true);
                break;
              case Draft.LOCATION:
                attachmentManager.setLocation(SignalPlace.deserialize(draft.getValue()), getCurrentMediaConstraints()).addListener(listener);
                break;
              case Draft.IMAGE:
                setMedia(Uri.parse(draft.getValue()), MediaType.IMAGE).addListener(listener);
                break;
              case Draft.AUDIO:
                setMedia(Uri.parse(draft.getValue()), MediaType.AUDIO).addListener(listener);
                break;
              case Draft.VIDEO:
                setMedia(Uri.parse(draft.getValue()), MediaType.VIDEO).addListener(listener);
                break;
              case Draft.QUOTE:
                SettableFuture<Boolean> quoteResult = new SettableFuture<>();
                new QuoteRestorationTask(draft.getValue(), quoteResult).execute();
                quoteResult.addListener(listener);
                break;
            }
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        updateToggleButtonState();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    return future;
  }

  private ListenableFuture<Boolean> initializeSecurity(final boolean currentSecureText,
                                                       final boolean currentIsDefaultSms)
  {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    handleSecurityChange(currentSecureText || isPushGroupConversation(), currentIsDefaultSms);

    new AsyncTask<Recipient, Void, boolean[]>() {
      @Override
      protected boolean[] doInBackground(Recipient... params) {
        Context           context         = ConversationActivity.this;
        Recipient         recipient       = params[0].resolve();
        Log.i(TAG, "Resolving registered state...");
        RegisteredState registeredState;

        if (recipient.isPushGroup()) {
          Log.i(TAG, "Push group recipient...");
          registeredState = RegisteredState.REGISTERED;
        } else {
          Log.i(TAG, "Checking through resolved recipient");
          registeredState = recipient.resolve().getRegistered();
        }

        Log.i(TAG, "Resolved registered state: " + registeredState);
        boolean           signalEnabled   = TextSecurePreferences.isPushRegistered(context);

        if (registeredState == RegisteredState.UNKNOWN) {
          try {
            Log.i(TAG, "Refreshing directory for user: " + recipient.getId().serialize());
            registeredState = DirectoryHelper.refreshDirectoryFor(context, recipient, false);
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        Log.i(TAG, "Returning registered state...");
        return new boolean[] {registeredState == RegisteredState.REGISTERED && signalEnabled,
                              Util.isDefaultSmsProvider(context)};
      }

      @Override
      protected void onPostExecute(boolean[] result) {
        if (result[0] != currentSecureText || result[1] != currentIsDefaultSms) {
          Log.i(TAG, "onPostExecute() handleSecurityChange: " + result[0] + " , " + result[1]);
          handleSecurityChange(result[0], result[1]);
        }
        future.set(true);
        onSecurityUpdated();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient.get());

    return future;
  }

  private void onSecurityUpdated() {
    Log.i(TAG, "onSecurityUpdated()");
    updateReminders();
    updateDefaultSubscriptionId(recipient.get().getDefaultSubscriptionId());
  }

  private void initializeInsightObserver() {
    inviteReminderModel = new InviteReminderModel(this, new InviteReminderRepository(this));
    inviteReminderModel.loadReminder(recipient, this::updateReminders);
  }

  protected void updateReminders() {
    Optional<Reminder> inviteReminder              = inviteReminderModel.getReminder();
    Integer            actionableRequestingMembers = groupViewModel.getActionableRequestingMembers().getValue();
    List<RecipientId>  gv1MigrationSuggestions     = groupViewModel.getGroupV1MigrationSuggestions().getValue();
    Boolean            gv1MigrationBanner          = groupViewModel.getShowGroupsV1MigrationBanner().getValue();

    if (UnauthorizedReminder.isEligible(this)) {
      reminderView.get().showReminder(new UnauthorizedReminder(this));
    } else if (ExpiredBuildReminder.isEligible()) {
      reminderView.get().showReminder(new ExpiredBuildReminder(this));
      reminderView.get().setOnActionClickListener(this::handleReminderAction);
    } else if (ServiceOutageReminder.isEligible(this)) {
      ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
      reminderView.get().showReminder(new ServiceOutageReminder(this));
    } else if (TextSecurePreferences.isPushRegistered(this)      &&
               TextSecurePreferences.isShowInviteReminders(this) &&
               !isSecureText                                     &&
               inviteReminder.isPresent()                        &&
               !recipient.get().isGroup()) {
      reminderView.get().setOnActionClickListener(this::handleReminderAction);
      reminderView.get().setOnDismissListener(() -> inviteReminderModel.dismissReminder());
      reminderView.get().showReminder(inviteReminder.get());
    } else if (actionableRequestingMembers != null && actionableRequestingMembers > 0) {
      reminderView.get().showReminder(PendingGroupJoinRequestsReminder.create(this, actionableRequestingMembers));
      reminderView.get().setOnActionClickListener(id -> {
        if (id == R.id.reminder_action_review_join_requests) {
          startActivity(ManagePendingAndRequestingMembersActivity.newIntent(this, getRecipient().getGroupId().get().requireV2()));
        }
      });
    } else if (gv1MigrationBanner == Boolean.TRUE && recipient.get().isPushV1Group()) {
      reminderView.get().showReminder(new GroupsV1MigrationInitiationReminder(this));
      reminderView.get().setOnActionClickListener(actionId -> {
        if (actionId == R.id.reminder_action_gv1_initiation_update_group) {
          GroupsV1MigrationInitiationBottomSheetDialogFragment.showForInitiation(getSupportFragmentManager(), recipient.getId());
        } else if (actionId == R.id.reminder_action_gv1_initiation_not_now) {
          groupViewModel.onMigrationInitiationReminderBannerDismissed(recipient.getId());
        }
      });
    } else if (gv1MigrationSuggestions != null && gv1MigrationSuggestions.size() > 0 && recipient.get().isPushV2Group()) {
      reminderView.get().showReminder(new GroupsV1MigrationSuggestionsReminder(this, gv1MigrationSuggestions));
      reminderView.get().setOnActionClickListener(actionId -> {
        if (actionId == R.id.reminder_action_gv1_suggestion_add_members) {
          GroupsV1MigrationSuggestionsDialog.show(this, recipient.get().requireGroupId().requireV2(), gv1MigrationSuggestions);
        } else if (actionId == R.id.reminder_action_gv1_suggestion_no_thanks) {
          groupViewModel.onSuggestedMembersBannerDismissed(recipient.get().requireGroupId(), gv1MigrationSuggestions);
        }
      });
      reminderView.get().setOnDismissListener(() -> {
      });
    } else if (reminderView.resolved()) {
      reminderView.get().hide();
    }
  }

  private void handleReminderAction(@IdRes int reminderActionId) {
    if (reminderActionId == R.id.reminder_action_invite) {
      handleInviteLink();
      reminderView.get().requestDismiss();
    } else if (reminderActionId == R.id.reminder_action_view_insights) {
      InsightsLauncher.showInsightsDashboard(getSupportFragmentManager());
    } else if (reminderActionId == R.id.reminder_action_update_now) {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(this);
    } else {
      throw new IllegalArgumentException("Unknown ID: " + reminderActionId);
    }
  }

  private void updateDefaultSubscriptionId(Optional<Integer> defaultSubscriptionId) {
    Log.i(TAG, "updateDefaultSubscriptionId(" + defaultSubscriptionId.orNull() + ")");
    sendButton.setDefaultSubscriptionId(defaultSubscriptionId);
  }

  private void initializeMmsEnabledCheck() {
    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        return Util.isMmsCapable(ConversationActivity.this);
      }

      @Override
      protected void onPostExecute(Boolean isMmsEnabled) {
        ConversationActivity.this.isMmsEnabled = isMmsEnabled;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private ListenableFuture<Boolean> initializeIdentityRecords() {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Pair<IdentityRecordList, String>>() {
      @Override
      protected @NonNull Pair<IdentityRecordList, String> doInBackground(Recipient... params) {
        IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);
        List<Recipient>                     recipients;

        if (params[0].isGroup()) {
          recipients = DatabaseFactory.getGroupDatabase(ConversationActivity.this)
                                      .getGroupMembers(params[0].requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
        } else {
          recipients = Collections.singletonList(params[0]);
        }

        long               startTime          =  System.currentTimeMillis();
        IdentityRecordList identityRecordList = identityDatabase.getIdentities(recipients);

        Log.i(TAG, String.format(Locale.US, "Loaded %d identities in %d ms", recipients.size(), System.currentTimeMillis() - startTime));

        String message = null;

        if (identityRecordList.isUnverified()) {
          message = IdentityUtil.getUnverifiedBannerDescription(ConversationActivity.this, identityRecordList.getUnverifiedRecipients());
        }

        return new Pair<>(identityRecordList, message);
      }

      @Override
      protected void onPostExecute(@NonNull Pair<IdentityRecordList, String> result) {
        Log.i(TAG, "Got identity records: " + result.first().isUnverified());
        identityRecords = result.first();

        if (result.second() != null) {
          Log.d(TAG, "Replacing banner...");
          unverifiedBannerView.get().display(result.second(), result.first().getUnverifiedRecords(),
                                             new UnverifiedClickedListener(),
                                             new UnverifiedDismissedListener());
        } else if (unverifiedBannerView.resolved()) {
          Log.d(TAG, "Clearing banner...");
          unverifiedBannerView.get().hide();
        }

        titleView.setVerified(isSecureText && identityRecords.isVerified());

        future.set(true);
      }

    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient.get());

    return future;
  }

  private void initializeViews() {
    titleView                = findViewById(R.id.conversation_title_view);
    buttonToggle             = findViewById(R.id.button_toggle);
    sendButton               = findViewById(R.id.send_button);
    attachButton             = findViewById(R.id.attach_button);
    composeText              = findViewById(R.id.embedded_text_editor);
    charactersLeft           = findViewById(R.id.space_left);
    emojiDrawerStub          = ViewUtil.findStubById(this, R.id.emoji_drawer_stub);
    attachmentKeyboardStub   = ViewUtil.findStubById(this, R.id.attachment_keyboard_stub);
    unblockButton            = findViewById(R.id.unblock_button);
    makeDefaultSmsButton     = findViewById(R.id.make_default_sms_button);
    registerButton           = findViewById(R.id.register_button);
    container                = findViewById(R.id.layout_container);
    reminderView             = ViewUtil.findStubById(this, R.id.reminder_stub);
    unverifiedBannerView     = ViewUtil.findStubById(this, R.id.unverified_banner_stub);
    reviewBanner             = ViewUtil.findStubById(this, R.id.review_banner_stub);
    quickAttachmentToggle    = findViewById(R.id.quick_attachment_toggle);
    inlineAttachmentToggle   = findViewById(R.id.inline_attachment_container);
    inputPanel               = findViewById(R.id.bottom_panel);
    panelParent              = findViewById(R.id.conversation_activity_panel_parent);
    searchNav                = findViewById(R.id.conversation_search_nav);
    messageRequestBottomView = findViewById(R.id.conversation_activity_message_request_bottom_bar);
    reactionOverlay          = findViewById(R.id.conversation_reaction_scrubber);
    mentionsSuggestions      = ViewUtil.findStubById(this, R.id.conversation_mention_suggestions_stub);

    ImageButton quickCameraToggle      = findViewById(R.id.quick_camera_toggle);
    ImageButton inlineAttachmentButton = findViewById(R.id.inline_attachment_button);

    noLongerMemberBanner   = findViewById(R.id.conversation_no_longer_member_banner);
    requestingMemberBanner = findViewById(R.id.conversation_requesting_banner);
    cancelJoinRequest      = findViewById(R.id.conversation_cancel_request);
    joinGroupCallButton    = findViewById(R.id.conversation_group_cal_join);

    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this);
    inputPanel.setMediaListener(this);

    attachmentManager = new AttachmentManager(this, this);
    audioRecorder     = new AudioRecorder(this);
    typingTextWatcher = new TypingStatusTextWatcher();

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setCursorPositionChangedListener(this);
    attachButton.setOnClickListener(new AttachButtonListener());
    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    sendButton.addOnTransportChangedListener((newTransport, manuallySelected) -> {
      calculateCharactersRemaining();
      updateLinkPreviewState();
      linkPreviewViewModel.onTransportChanged(newTransport.isSms());
      composeText.setTransport(newTransport);

      buttonToggle.getBackground().setColorFilter(newTransport.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
      buttonToggle.getBackground().invalidateSelf();

      if (manuallySelected) recordTransportPreference(newTransport);
    });

    titleView.setOnClickListener(v -> handleConversationSettings());
    titleView.setOnLongClickListener(v -> handleDisplayQuickContact());
    unblockButton.setOnClickListener(v -> handleUnblock());
    makeDefaultSmsButton.setOnClickListener(v -> handleMakeDefaultSms());
    registerButton.setOnClickListener(v -> handleRegisterForSignal());

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    if (Camera.getNumberOfCameras() > 0) {
      quickCameraToggle.setVisibility(View.VISIBLE);
      quickCameraToggle.setOnClickListener(new QuickCameraToggleListener());
    } else {
      quickCameraToggle.setVisibility(View.GONE);
    }

    searchNav.setEventListener(this);

    inlineAttachmentButton.setOnClickListener(v -> handleAddAttachment());

    reactionOverlay.setOnReactionSelectedListener(this);

    joinGroupCallButton.setOnClickListener(v -> handleVideo(getRecipient()));
  }

  protected void initializeActionBar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar == null) throw new AssertionError();

    supportActionBar.setDisplayHomeAsUpEnabled(true);
    supportActionBar.setDisplayShowTitleEnabled(false);

    if (isInBubble()) {
      supportActionBar.setHomeAsUpIndicator(ContextCompat.getDrawable(this, R.drawable.ic_notification));
      toolbar.setNavigationOnClickListener(unused -> startActivity(MainActivity.clearTop(this)));
    }
  }

  private boolean isInBubble() {
    if (Build.VERSION.SDK_INT >= ConversationUtil.CONVERSATION_SUPPORT_VERSION) {
      Display display = getDisplay();

      return display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY;
    } else {
      return false;
    }
  }

  private void initializeResources(@NonNull ConversationIntents.Args args) {
    if (recipient != null) {
      recipient.removeObservers(this);
    }

    recipient        = Recipient.live(args.getRecipientId());
    threadId         = args.getThreadId();
    distributionType = args.getDistributionType();
    glideRequests    = GlideApp.with(this);

    recipient.observe(this, this::onRecipientChanged);
  }

  private void initializeLinkPreviewObserver() {
    linkPreviewViewModel = ViewModelProviders.of(this, new LinkPreviewViewModel.Factory(new LinkPreviewRepository())).get(LinkPreviewViewModel.class);

    linkPreviewViewModel.getLinkPreviewState().observe(this, previewState -> {
      if (previewState == null) return;

      if (previewState.isLoading()) {
        inputPanel.setLinkPreviewLoading();
      } else if (previewState.hasLinks() && !previewState.getLinkPreview().isPresent()) {
        inputPanel.setLinkPreviewNoPreview(previewState.getError());
      } else {
        inputPanel.setLinkPreview(glideRequests, previewState.getLinkPreview());
      }

      updateToggleButtonState();
    });
  }

  private void initializeSearchObserver() {
    searchViewModel = ViewModelProviders.of(this).get(ConversationSearchViewModel.class);

    searchViewModel.getSearchResults().observe(this, result -> {
      if (result == null) return;

      if (!result.getResults().isEmpty()) {
        MessageResult messageResult = result.getResults().get(result.getPosition());
        fragment.jumpToMessage(messageResult.messageRecipient.getId(), messageResult.receivedTimestampMs, searchViewModel::onMissingResult);
      }

      searchNav.setData(result.getPosition(), result.getResults().size());
    });
  }

  private void initializeStickerObserver() {
    StickerSearchRepository repository = new StickerSearchRepository(this);

    stickerViewModel = ViewModelProviders.of(this, new ConversationStickerViewModel.Factory(getApplication(), repository))
                                         .get(ConversationStickerViewModel.class);

    stickerViewModel.getStickerResults().observe(this, stickers -> {
      if (stickers == null) return;

      inputPanel.setStickerSuggestions(stickers);
    });

    stickerViewModel.getStickersAvailability().observe(this, stickersAvailable -> {
      if (stickersAvailable == null) return;

      boolean           isSystemEmojiPreferred = TextSecurePreferences.isSystemEmojiPreferred(this);
      MediaKeyboardMode keyboardMode           = TextSecurePreferences.getMediaKeyboardMode(this);
      boolean           stickerIntro           = !TextSecurePreferences.hasSeenStickerIntroTooltip(this);

      if (stickersAvailable) {
        inputPanel.showMediaKeyboardToggle(true);
        inputPanel.setMediaKeyboardToggleMode(isSystemEmojiPreferred || keyboardMode == MediaKeyboardMode.STICKER);
        if (stickerIntro) showStickerIntroductionTooltip();
      }

      if (emojiDrawerStub.resolved()) {
        initializeMediaKeyboardProviders(emojiDrawerStub.get(), stickersAvailable);
      }
    });
  }

  private void initializeViewModel(@NonNull ConversationIntents.Args args) {
    this.viewModel = ViewModelProviders.of(this, new ConversationViewModel.Factory()).get(ConversationViewModel.class);

    this.viewModel.setArgs(args);
  }

  private void initializeGroupViewModel() {
    groupViewModel = ViewModelProviders.of(this, new ConversationGroupViewModel.Factory()).get(ConversationGroupViewModel.class);
    recipient.observe(this, groupViewModel::onRecipientChange);
    groupViewModel.getGroupActiveState().observe(this, unused -> invalidateOptionsMenu());
    groupViewModel.getReviewState().observe(this, this::presentGroupReviewBanner);
  }

  private void initializeMentionsViewModel() {
    mentionsViewModel = ViewModelProviders.of(this, new MentionsPickerViewModel.Factory()).get(MentionsPickerViewModel.class);

    recipient.observe(this, r -> {
      if (r.isPushV2Group() && !mentionsSuggestions.resolved()) {
        mentionsSuggestions.get();
      }
      mentionsViewModel.onRecipientChange(r);
    });

    composeText.setMentionQueryChangedListener(query -> {
      if (getRecipient().isPushV2Group() && getRecipient().isActiveGroup()) {
        if (!mentionsSuggestions.resolved()) {
          mentionsSuggestions.get();
        }
        mentionsViewModel.onQueryChange(query);
      }
    });

    composeText.setMentionValidator(annotations -> {
      if (!getRecipient().isPushV2Group() || !getRecipient().isActiveGroup()) {
        return annotations;
      }

      Set<String> validRecipientIds = Stream.of(getRecipient().getParticipants())
                                            .map(r -> MentionAnnotation.idToMentionAnnotationValue(r.getId()))
                                            .collect(Collectors.toSet());

      return Stream.of(annotations)
                   .filterNot(a -> validRecipientIds.contains(a.getValue()))
                   .toList();
    });

    mentionsViewModel.getSelectedRecipient().observe(this, recipient -> {
      composeText.replaceTextWithMention(recipient.getDisplayName(this), recipient.getId());
    });
  }

  public void initializeGroupCallViewModel() {
    groupCallViewModel = ViewModelProviders.of(this, new GroupCallViewModel.Factory()).get(GroupCallViewModel.class);

    recipient.observe(this, r -> {
      groupCallViewModel.onRecipientChange(this, r);
    });

    groupCallViewModel.hasActiveGroupCall().observe(this, hasActiveCall -> {
      invalidateOptionsMenu();
      joinGroupCallButton.setVisibility(hasActiveCall ? View.VISIBLE : View.GONE);
    });

    groupCallViewModel.groupCallHasCapacity().observe(this, hasCapacity -> joinGroupCallButton.setText(hasCapacity ? R.string.ConversationActivity_join : R.string.ConversationActivity_full));
  }

  private void showGroupCallingTooltip() {
    if (!FeatureFlags.groupCalling() || !SignalStore.tooltips().shouldShowGroupCallingTooltip() || callingTooltipShown) {
      return;
    }

    View anchor = findViewById(R.id.menu_video_secure);
    if (anchor == null) {
      Log.w(TAG, "Video Call tooltip anchor is null. Skipping tooltip...");
      return;
    }

    callingTooltipShown = true;

    SignalStore.tooltips().markGroupCallSpeakerViewSeen();
    TooltipPopup.forTarget(anchor)
                .setBackgroundTint(ContextCompat.getColor(this, R.color.signal_accent_green))
                .setTextColor(getResources().getColor(R.color.core_white))
                .setText(R.string.ConversationActivity__tap_here_to_start_a_group_call)
                .setOnDismissListener(() -> SignalStore.tooltips().markGroupCallingTooltipSeen())
                .show(TooltipPopup.POSITION_BELOW);
  }

  private void showStickerIntroductionTooltip() {
    TextSecurePreferences.setMediaKeyboardMode(this, MediaKeyboardMode.STICKER);
    inputPanel.setMediaKeyboardToggleMode(true);

    TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                .setBackgroundTint(getResources().getColor(R.color.core_ultramarine))
                .setTextColor(getResources().getColor(R.color.core_white))
                .setText(R.string.ConversationActivity_new_say_it_with_stickers)
                .setOnDismissListener(() -> {
                  TextSecurePreferences.setHasSeenStickerIntroTooltip(this, true);
                  EventBus.getDefault().removeStickyEvent(StickerPackInstallEvent.class);
                })
                .show(TooltipPopup.POSITION_ABOVE);
  }

  @Override
  public void onReactionSelected(MessageRecord messageRecord, String emoji) {
    final Context context = getApplicationContext();

    reactionOverlay.hide();

    SignalExecutors.BOUNDED.execute(() -> {
      ReactionRecord oldRecord = Stream.of(messageRecord.getReactions())
                                       .filter(record -> record.getAuthor().equals(Recipient.self().getId()))
                                       .findFirst()
                                       .orElse(null);

      if (oldRecord != null && oldRecord.getEmoji().equals(emoji)) {
        MessageSender.sendReactionRemoval(context, messageRecord.getId(), messageRecord.isMms(), oldRecord);
      } else {
        MessageSender.sendNewReaction(context, messageRecord.getId(), messageRecord.isMms(), emoji);
      }
    });
  }

  @Override
  public void onCustomReactionSelected(@NonNull MessageRecord messageRecord, boolean hasAddedCustomEmoji) {
    ReactionRecord oldRecord = Stream.of(messageRecord.getReactions())
                                     .filter(record -> record.getAuthor().equals(Recipient.self().getId()))
                                     .findFirst()
                                     .orElse(null);

    if (oldRecord != null && hasAddedCustomEmoji) {
      final Context context = getApplicationContext();

      reactionOverlay.hide();

      SignalExecutors.BOUNDED.execute(() -> MessageSender.sendReactionRemoval(context,
                                                                              messageRecord.getId(),
                                                                              messageRecord.isMms(),
                                                                              oldRecord));
    } else {
      reactionOverlay.hideAllButMask();

      ReactWithAnyEmojiBottomSheetDialogFragment.createForMessageRecord(messageRecord, reactWithAnyEmojiStartPage)
                                                .show(getSupportFragmentManager(), "BOTTOM");
    }
  }

  @Override
  public void onReactWithAnyEmojiDialogDismissed() {
    reactionOverlay.hideMask();
  }

  @Override
  public void onReactWithAnyEmojiPageChanged(int page) {
    reactWithAnyEmojiStartPage = page;
  }

  @Override
  public void onSearchMoveUpPressed() {
    searchViewModel.onMoveUp();
  }

  @Override
  public void onSearchMoveDownPressed() {
    searchViewModel.onMoveDown();
  }

  private void initializeProfiles() {
    if (!isSecureText) {
      Log.i(TAG, "SMS contact, no profile fetch needed.");
      return;
    }

    RetrieveProfileJob.enqueueAsync(recipient.getId());
  }

  private void initializeGv1Migration() {
    GroupV1MigrationJob.enqueuePossibleAutoMigrate(recipient.getId());
  }

  private void onRecipientChanged(@NonNull Recipient recipient) {
    Log.i(TAG, "onModified(" + recipient.getId() + ") " + recipient.getRegistered());
    titleView.setTitle(glideRequests, recipient);
    titleView.setVerified(identityRecords.isVerified());
    setBlockedUserState(recipient, isSecureText, isDefaultSms);
    setActionBarColor(recipient.getColor());
    updateReminders();
    updateDefaultSubscriptionId(recipient.getDefaultSubscriptionId());
    initializeSecurity(isSecureText, isDefaultSms);

    if (searchViewItem == null || !searchViewItem.isActionViewExpanded()) {
      invalidateOptionsMenu();
    }

    if (groupViewModel != null) {
      groupViewModel.onRecipientChange(recipient);
    }

    if (mentionsViewModel != null) {
      mentionsViewModel.onRecipientChange(recipient);
    }

    if (groupCallViewModel != null) {
      groupCallViewModel.onRecipientChange(this, recipient);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onIdentityRecordUpdate(final IdentityRecord event) {
    initializeIdentityRecords();
  }

  @Subscribe(threadMode =  ThreadMode.MAIN, sticky = true)
  public void onStickerPackInstalled(final StickerPackInstallEvent event) {
    if (!TextSecurePreferences.hasSeenStickerIntroTooltip(this)) return;

    EventBus.getDefault().removeStickyEvent(event);

    if (!inputPanel.isStickerMode()) {
      TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                  .setText(R.string.ConversationActivity_sticker_pack_installed)
                  .setIconGlideModel(event.getIconGlideModel())
                  .show(TooltipPopup.POSITION_ABOVE);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  public void onGroupCallPeekEvent(@NonNull GroupCallPeekEvent event) {
    if (groupCallViewModel != null) {
      groupCallViewModel.onGroupCallPeekEvent(event);
    }
  }

  private void initializeReceivers() {
    securityUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        initializeSecurity(isSecureText, isDefaultSms);
        calculateCharactersRemaining();
      }
    };

    registerReceiver(securityUpdateReceiver,
                     new IntentFilter(SecurityEvent.SECURITY_UPDATE_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);
  }

  //////// Helper Methods

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType) {
    return setMedia(uri, mediaType, 0, 0, false);
  }

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType, int width, int height, boolean borderless) {
    if (uri == null) {
      return new SettableFuture<>(false);
    }

    if (MediaType.VCARD.equals(mediaType) && isSecureText) {
      openContactShareEditor(uri);
      return new SettableFuture<>(false);
    } else if (MediaType.IMAGE.equals(mediaType) || MediaType.GIF.equals(mediaType) || MediaType.VIDEO.equals(mediaType)) {
      String mimeType = MediaUtil.getMimeType(this, uri);
      if (mimeType == null) {
        mimeType = mediaType.toFallbackMimeType();
      }

      Media media = new Media(uri, mimeType, 0, width, height, 0, 0, borderless, Optional.absent(), Optional.absent(), Optional.absent());
      startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport()), MEDIA_SENDER);
      return new SettableFuture<>(false);
    } else {
      return attachmentManager.setMedia(glideRequests, uri, mediaType, getCurrentMediaConstraints(), width, height);
    }
  }

  private void openContactShareEditor(Uri contactUri) {
    Intent intent = ContactShareEditActivity.getIntent(this, Collections.singletonList(contactUri));
    startActivityForResult(intent, GET_CONTACT_DETAILS);
  }

  private void addAttachmentContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactData contactData = contactDataList.getContactData(this, contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
  }

  private void sendSharedContact(List<Contact> contacts) {
    int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
    boolean    initiating     = threadId == -1;

    sendMediaMessage(isSmsForced(), "", attachmentManager.buildSlideDeck(), null, contacts, Collections.emptyList(), Collections.emptyList(), expiresIn, false, subscriptionId, initiating, false);
  }

  private void selectContactInfo(ContactData contactData) {
    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i]     = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIcon(R.drawable.ic_account_box);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, (dialog, which) -> composeText.append(numbers[which]));
    builder.show();
  }

  private Drafts getDraftsForCurrentState() {
    Drafts drafts = new Drafts();

    if (recipient.get().isGroup() && !recipient.get().isActiveGroup()) {
      return drafts;
    }

    if (!Util.isEmpty(composeText)) {
      drafts.add(new Draft(Draft.TEXT, composeText.getTextTrimmed().toString()));
      List<Mention> draftMentions = composeText.getMentions();
      if (!draftMentions.isEmpty()) {
        drafts.add(new Draft(Draft.MENTION, Base64.encodeBytes(MentionUtil.mentionsToBodyRangeList(draftMentions).toByteArray())));
      }
    }

    for (Slide slide : attachmentManager.buildSlideDeck().getSlides()) {
      if      (slide.hasAudio() && slide.getUri() != null)    drafts.add(new Draft(Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo() && slide.getUri() != null)    drafts.add(new Draft(Draft.VIDEO, slide.getUri().toString()));
      else if (slide.hasLocation())                           drafts.add(new Draft(Draft.LOCATION, ((LocationSlide)slide).getPlace().serialize()));
      else if (slide.hasImage() && slide.getUri() != null)    drafts.add(new Draft(Draft.IMAGE, slide.getUri().toString()));
    }

    Optional<QuoteModel> quote = inputPanel.getQuote();

    if (quote.isPresent()) {
      drafts.add(new Draft(Draft.QUOTE, new QuoteId(quote.get().getId(), quote.get().getAuthor()).serialize()));
    }

    return drafts;
  }

  protected ListenableFuture<Long> saveDraft() {
    final SettableFuture<Long> future = new SettableFuture<>();

    if (this.recipient == null) {
      future.set(threadId);
      return future;
    }

    final Drafts       drafts               = getDraftsForCurrentState();
    final long         thisThreadId         = this.threadId;
    final int          thisDistributionType = this.distributionType;

    new AsyncTask<Long, Void, Long>() {
      @Override
      protected Long doInBackground(Long... params) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(ConversationActivity.this);
        DraftDatabase  draftDatabase  = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        long           threadId       = params[0];

        if (drafts.size() > 0) {
          if (threadId == -1) threadId = threadDatabase.getThreadIdFor(getRecipient(), thisDistributionType);

          draftDatabase.insertDrafts(threadId, drafts);
          threadDatabase.updateSnippet(threadId, drafts.getSnippet(ConversationActivity.this),
                                       drafts.getUriSnippet(),
                                       System.currentTimeMillis(), Types.BASE_DRAFT_TYPE, true);
        } else if (threadId > 0) {
          threadDatabase.update(threadId, false);
        }

        return threadId;
      }

      @Override
      protected void onPostExecute(Long result) {
        future.set(result);
      }

    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, thisThreadId);

    return future;
  }

  private void setActionBarColor(MaterialColor color) {
    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar == null) throw new AssertionError();
    int actionBarColor = color.toActionBarColor(this);
    supportActionBar.setBackgroundDrawable(new ColorDrawable(actionBarColor));
    WindowUtil.setStatusBarColor(getWindow(), color.toStatusBarColor(this));

    joinGroupCallButton.setTextColor(actionBarColor);
    joinGroupCallButton.setIconTint(ColorStateList.valueOf(actionBarColor));
    joinGroupCallButton.setRippleColor(ColorStateList.valueOf(actionBarColor));
  }

  private void setBlockedUserState(Recipient recipient, boolean isSecureText, boolean isDefaultSms) {
    if (!isSecureText && isPushGroupConversation()) {
      unblockButton.setVisibility(View.GONE);
      inputPanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.VISIBLE);
    } else if (!isSecureText && !isDefaultSms) {
      unblockButton.setVisibility(View.GONE);
      inputPanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.VISIBLE);
      registerButton.setVisibility(View.GONE);
    } else {
      boolean inactivePushGroup = isPushGroupConversation() && !recipient.isActiveGroup();
      inputPanel.setVisibility(inactivePushGroup ? View.GONE : View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);
    }
  }

  private void calculateCharactersRemaining() {
    String          messageBody     = composeText.getTextTrimmed().toString();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState  characterState  = transportOption.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(String.format(dynamicLanguage.getCurrentLocale(),
                                           "%d/%d (%d)",
                                           characterState.charactersRemaining,
                                           characterState.maxTotalMessageSize,
                                           characterState.messagesSpent));
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }

  private void initializeMediaKeyboardProviders(@NonNull MediaKeyboard mediaKeyboard, boolean stickersAvailable) {
    boolean isSystemEmojiPreferred   = TextSecurePreferences.isSystemEmojiPreferred(this);

    if (stickersAvailable) {
      if (isSystemEmojiPreferred) {
        mediaKeyboard.setProviders(0, new StickerKeyboardProvider(this, this));
      } else {
        MediaKeyboardMode keyboardMode = TextSecurePreferences.getMediaKeyboardMode(this);
        int               index        = keyboardMode == MediaKeyboardMode.STICKER ? 1 : 0;

        mediaKeyboard.setProviders(index,
                                   new EmojiKeyboardProvider(this, inputPanel),
                                   new StickerKeyboardProvider(this, this));
      }
    } else if (!isSystemEmojiPreferred) {
      mediaKeyboard.setProviders(0, new EmojiKeyboardProvider(this, inputPanel));
    }
  }

  private boolean isInMessageRequest() {
    return messageRequestBottomView.getVisibility() == View.VISIBLE;
  }

  private boolean isSingleConversation() {
    return getRecipient() != null && !getRecipient().isGroup();
  }

  private boolean isActiveGroup() {
    if (!isGroupConversation()) return false;

    Optional<GroupRecord> record = DatabaseFactory.getGroupDatabase(this).getGroup(getRecipient().getId());
    return record.isPresent() && record.get().isActive();
  }

  @SuppressWarnings("SimplifiableIfStatement")
  private boolean isSelfConversation() {
    if (!TextSecurePreferences.isPushRegistered(this)) return false;
    if (recipient.get().isGroup())                     return false;

    return recipient.get().isSelf();
  }

  private boolean isGroupConversation() {
    return getRecipient() != null && getRecipient().isGroup();
  }

  private boolean isPushGroupConversation() {
    return getRecipient() != null && getRecipient().isPushGroup();
  }

  private boolean isPushGroupV1Conversation() {
    return getRecipient() != null && getRecipient().isPushV1Group();
  }

  private boolean isSmsForced() {
    return sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
  }

  protected Recipient getRecipient() {
    return this.recipient.get();
  }

  protected long getThreadId() {
    return this.threadId;
  }

  private String getMessage() throws InvalidMessageException {
    String rawText = composeText.getTextTrimmed().toString();

    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
      throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

    return rawText;
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return sendButton.getSelectedTransport().getType() == Type.TEXTSECURE
           ? MediaConstraints.getPushMediaConstraints()
           : MediaConstraints.getMmsMediaConstraints(sendButton.getSelectedTransport().getSimSubscriptionId().or(-1));
  }

  private void markLastSeen() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        DatabaseFactory.getThreadDatabase(ConversationActivity.this).setLastSeen(params[0]);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  protected void sendComplete(long threadId) {
    boolean refreshFragment = (threadId != this.threadId);
    this.threadId = threadId;

    if (fragment == null || !fragment.isVisible() || isFinishing()) {
      return;
    }

    fragment.setLastSeen(0);

    if (refreshFragment) {
      fragment.reload(recipient.get(), threadId);
      setVisibleThread(threadId);
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();

    updateLinkPreviewState();
    linkPreviewViewModel.onSend();
  }

  private void sendMessage() {
    if (inputPanel.isRecordingInLockedMode()) {
      inputPanel.releaseRecordingLock();
      return;
    }

    try {
      Recipient recipient = getRecipient();

      if (recipient == null) {
        throw new RecipientFormattingException("Badly formatted");
      }

      String          message        = getMessage();
      TransportOption transport      = sendButton.getSelectedTransport();
      boolean         forceSms       = (recipient.isForceSmsSelection() || sendButton.isManualSelection()) && transport.isSms();
      int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
      long            expiresIn      = recipient.getExpireMessages() * 1000L;
      boolean         initiating     = threadId == -1;
      boolean         needsSplit     = !transport.isSms() && message.length() > transport.calculateCharacters(message).maxPrimaryMessageSize;
      boolean         isMediaMessage = attachmentManager.isAttachmentPresent() ||
                                       recipient.isGroup()                     ||
                                       recipient.getEmail().isPresent()        ||
                                       inputPanel.getQuote().isPresent()       ||
                                       composeText.hasMentions()               ||
                                       linkPreviewViewModel.hasLinkPreview()   ||
                                       needsSplit;

      Log.i(TAG, "isManual Selection: " + sendButton.isManualSelection());
      Log.i(TAG, "forceSms: " + forceSms);

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !isMmsEnabled) {
        handleManualMmsRequired();
      } else if (!forceSms && (identityRecords.isUnverified(true) || identityRecords.isUntrusted(true))) {
        handleRecentSafetyNumberChange();
      } else if (isMediaMessage) {
        sendMediaMessage(forceSms, expiresIn, false, subscriptionId, initiating);
      } else {
        sendTextMessage(forceSms, expiresIn, subscriptionId, initiating);
      }
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ConversationActivity.this,
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void sendMediaMessage(@NonNull MediaSendActivityResult result) {
    long                 thread        = this.threadId;
    long                 expiresIn     = recipient.get().getExpireMessages() * 1000L;
    QuoteModel           quote         = result.isViewOnce() ? null : inputPanel.getQuote().orNull();
    List<Mention>        mentions      = new ArrayList<>(result.getMentions());
    OutgoingMediaMessage message       = new OutgoingMediaMessage(recipient.get(), new SlideDeck(), result.getBody(), System.currentTimeMillis(), -1, expiresIn, result.isViewOnce(), distributionType, quote, Collections.emptyList(), Collections.emptyList(), mentions);
    OutgoingMediaMessage secureMessage = new OutgoingSecureMediaMessage(message);

    ApplicationDependencies.getTypingStatusSender().onTypingStopped(thread);

    inputPanel.clearQuote();
    attachmentManager.clear(glideRequests, false);
    silentlySetComposeText("");

    long id = fragment.stageOutgoingMessage(message);

    SimpleTask.run(() -> {
      long resultId = MessageSender.sendPushWithPreUploadedMedia(this, secureMessage, result.getPreUploadResults(), thread, () -> fragment.releaseOutgoingMessage(id));

      int deleted = DatabaseFactory.getAttachmentDatabase(this).deleteAbandonedPreuploadedAttachments();
      Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");

      return resultId;
    }, this::sendComplete);
  }

  private void sendMediaMessage(final boolean forceSms, final long expiresIn, final boolean viewOnce, final int subscriptionId, final boolean initiating)
      throws InvalidMessageException
  {
    Log.i(TAG, "Sending media message...");
    sendMediaMessage(forceSms,
                     getMessage(),
                     attachmentManager.buildSlideDeck(),
                     inputPanel.getQuote().orNull(),
                     Collections.emptyList(),
                     linkPreviewViewModel.getActiveLinkPreviews(),
                     composeText.getMentions(),
                     expiresIn,
                     viewOnce,
                     subscriptionId,
                     initiating,
                     true);
  }

  private ListenableFuture<Void> sendMediaMessage(final boolean forceSms,
                                                  @NonNull String body,
                                                  SlideDeck slideDeck,
                                                  QuoteModel quote,
                                                  List<Contact> contacts,
                                                  List<LinkPreview> previews,
                                                  List<Mention> mentions,
                                                  final long expiresIn,
                                                  final boolean viewOnce,
                                                  final int subscriptionId,
                                                  final boolean initiating,
                                                  final boolean clearComposeBox)
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return new SettableFuture<>(null);
    }

    final long thread = this.threadId;

    if (isSecureText && !forceSms) {
      MessageUtil.SplitResult splitMessage = MessageUtil.getSplitMessage(this, body, sendButton.getSelectedTransport().calculateCharacters(body).maxPrimaryMessageSize);
      body = splitMessage.getBody();

      if (splitMessage.getTextSlide().isPresent()) {
        slideDeck.addSlide(splitMessage.getTextSlide().get());
      }
    }

    OutgoingMediaMessage outgoingMessageCandidate = new OutgoingMediaMessage(recipient.get(), slideDeck, body, System.currentTimeMillis(), subscriptionId, expiresIn, viewOnce, distributionType, quote, contacts, previews, mentions);

    final SettableFuture<Void> future  = new SettableFuture<>();
    final Context              context = getApplicationContext();

    final OutgoingMediaMessage outgoingMessage;

    if (isSecureText && !forceSms) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessageCandidate);
      ApplicationDependencies.getTypingStatusSender().onTypingStopped(thread);
    } else {
      outgoingMessage = outgoingMessageCandidate;
    }

    Permissions.with(this)
               .request(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
               .ifNecessary(!isSecureText || forceSms)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
               .onAllGranted(() -> {
                 if (clearComposeBox) {
                   inputPanel.clearQuote();
                   attachmentManager.clear(glideRequests, false);
                   silentlySetComposeText("");
                 }

                 final long id = fragment.stageOutgoingMessage(outgoingMessage);

                 SimpleTask.run(() -> {
                   return MessageSender.send(context, outgoingMessage, thread, forceSms, () -> fragment.releaseOutgoingMessage(id));
                 }, result -> {
                   sendComplete(result);
                   future.set(null);
                 });
               })
               .onAnyDenied(() -> future.set(null))
               .execute();

    return future;
  }

  private void sendTextMessage(final boolean forceSms, final long expiresIn, final int subscriptionId, final boolean initiating)
      throws InvalidMessageException
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return;
    }

    final long    thread      = this.threadId;
    final Context context     = getApplicationContext();
    final String  messageBody = getMessage();

    OutgoingTextMessage message;

    if (isSecureText && !forceSms) {
      message = new OutgoingEncryptedMessage(recipient.get(), messageBody, expiresIn);
      ApplicationDependencies.getTypingStatusSender().onTypingStopped(thread);
    } else {
      message = new OutgoingTextMessage(recipient.get(), messageBody, expiresIn, subscriptionId);
    }

    Permissions.with(this)
               .request(Manifest.permission.SEND_SMS)
               .ifNecessary(forceSms || !isSecureText)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
               .onAllGranted(() -> {
                 silentlySetComposeText("");
                 final long id = fragment.stageOutgoingMessage(message);

                 new AsyncTask<OutgoingTextMessage, Void, Long>() {
                   @Override
                   protected Long doInBackground(OutgoingTextMessage... messages) {
                     return MessageSender.send(context, messages[0], thread, forceSms, () -> fragment.releaseOutgoingMessage(id));
                   }

                   @Override
                   protected void onPostExecute(Long result) {
                     sendComplete(result);
                   }
                 }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);

               })
               .execute();
  }

  private void showDefaultSmsPrompt() {
    new AlertDialog.Builder(this)
                   .setMessage(R.string.ConversationActivity_signal_cannot_sent_sms_mms_messages_because_it_is_not_your_default_sms_app)
                   .setNegativeButton(R.string.ConversationActivity_no, (dialog, which) -> dialog.dismiss())
                   .setPositiveButton(R.string.ConversationActivity_yes, (dialog, which) -> handleMakeDefaultSms())
                   .show();
  }

  private void updateToggleButtonState() {
    if (inputPanel.isRecordingInLockedMode()) {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.show();
      inlineAttachmentToggle.hide();
      return;
    }

    if (composeText.getText().length() == 0 && !attachmentManager.isAttachmentPresent()) {
      buttonToggle.display(attachButton);
      quickAttachmentToggle.show();
      inlineAttachmentToggle.hide();
    } else {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.hide();

      if (!attachmentManager.isAttachmentPresent() && !linkPreviewViewModel.hasLinkPreviewUi()) {
        inlineAttachmentToggle.show();
      } else {
        inlineAttachmentToggle.hide();
      }
    }
  }

  private void updateLinkPreviewState() {
    if (SignalStore.settings().isLinkPreviewsEnabled() && isSecureText && !sendButton.getSelectedTransport().isSms() && !attachmentManager.isAttachmentPresent()) {
      linkPreviewViewModel.onEnabled();
      linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed().toString(), composeText.getSelectionStart(), composeText.getSelectionEnd());
    } else {
      linkPreviewViewModel.onUserCancel();
    }
  }

  private void recordTransportPreference(TransportOption transportOption) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(ConversationActivity.this);

        recipientDatabase.setDefaultSubscriptionId(recipient.getId(), transportOption.getSimSubscriptionId().or(-1));

        if (!recipient.resolve().isPushGroup()) {
          recipientDatabase.setForceSmsSelection(recipient.getId(), recipient.get().getRegistered() == RegisteredState.REGISTERED && transportOption.isSms());
        }

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @Override
  public void onRecorderPermissionRequired() {
    Permissions.with(this)
               .request(Manifest.permission.RECORD_AUDIO)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.ConversationActivity_to_send_audio_messages_allow_signal_access_to_your_microphone), R.drawable.ic_mic_solid_24)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_requires_the_microphone_permission_in_order_to_send_audio_messages))
               .execute();
  }

  @Override
  public void onRecorderStarted() {
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(20);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    audioRecorder.startRecording();
  }

  @Override
  public void onRecorderLocked() {
    updateToggleButtonState();
  }

  @Override
  public void onRecorderFinished() {
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(20);

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final @NonNull Pair<Uri, Long> result) {
        boolean    forceSms       = sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
        boolean    initiating     = threadId == -1;
        int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
        long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
        AudioSlide audioSlide     = new AudioSlide(ConversationActivity.this, result.first(), result.second(), MediaUtil.AUDIO_AAC, true);
        SlideDeck  slideDeck      = new SlideDeck();
        slideDeck.addSlide(audioSlide);

        ListenableFuture<Void> sendResult = sendMediaMessage(forceSms,
                                                             "",
                                                             slideDeck,
                                                             inputPanel.getQuote().orNull(),
                                                             Collections.emptyList(),
                                                             Collections.emptyList(),
                                                             composeText.getMentions(),
                                                             expiresIn,
                                                             false,
                                                             subscriptionId,
                                                             initiating,
                                                             true);

        sendResult.addListener(new AssertedSuccessListener<Void>() {
          @Override
          public void onSuccess(Void nothing) {
            new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                BlobProvider.getInstance().delete(ConversationActivity.this, result.first());
                return null;
              }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
          }
        });
      }

      @Override
      public void onFailure(ExecutionException e) {
        Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public void onRecorderCanceled() {
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(50);

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final Pair<Uri, Long> result) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            BlobProvider.getInstance().delete(ConversationActivity.this, result.first());
            return null;
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }

      @Override
      public void onFailure(ExecutionException e) {}
    });
  }

  @Override
  public void onEmojiToggle() {
    if (!emojiDrawerStub.resolved()) {
      Boolean stickersAvailable = stickerViewModel.getStickersAvailability().getValue();

      initializeMediaKeyboardProviders(emojiDrawerStub.get(), stickersAvailable == null ? false : stickersAvailable);

      inputPanel.setMediaKeyboard(emojiDrawerStub.get());
    }

    if (container.getCurrentInput() == emojiDrawerStub.get()) {
      container.showSoftkey(composeText);
    } else {
      container.show(composeText, emojiDrawerStub.get());
    }
  }

  @Override
  public void onLinkPreviewCanceled() {
    linkPreviewViewModel.onUserCancel();
  }

  @Override
  public void onStickerSuggestionSelected(@NonNull StickerRecord sticker) {
    sendSticker(sticker, true);
  }

  @Override
  public void onMediaSelected(@NonNull Uri uri, String contentType) {
    if (MediaUtil.isGif(contentType) || MediaUtil.isImageType(contentType)) {
      SimpleTask.run(getLifecycle(),
                     () -> getKeyboardImageDetails(uri),
                     details -> sendKeyboardImage(uri, contentType, details));
    } else if (MediaUtil.isVideoType(contentType)) {
      setMedia(uri, MediaType.VIDEO);
    } else if (MediaUtil.isAudioType(contentType)) {
      setMedia(uri, MediaType.AUDIO);
    }
  }

  @Override
  public void onCursorPositionChanged(int start, int end) {
    linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed().toString(), start, end);
  }

  @Override
  public void onStickerSelected(@NonNull StickerRecord stickerRecord) {
    sendSticker(stickerRecord, false);
  }

  @Override
  public void onStickerManagementClicked() {
    startActivity(StickerManagementActivity.getIntent(this));
    container.hideAttachedInput(true);
  }

  private void sendSticker(@NonNull StickerRecord stickerRecord, boolean clearCompose) {
    sendSticker(new StickerLocator(stickerRecord.getPackId(), stickerRecord.getPackKey(), stickerRecord.getStickerId(), stickerRecord.getEmoji()), stickerRecord.getContentType(), stickerRecord.getUri(), stickerRecord.getSize(), clearCompose);

    SignalExecutors.BOUNDED.execute(() ->
     DatabaseFactory.getStickerDatabase(getApplicationContext())
                    .updateStickerLastUsedTime(stickerRecord.getRowId(), System.currentTimeMillis())
    );
  }

  private void sendSticker(@NonNull StickerLocator stickerLocator, @NonNull String contentType, @NonNull Uri uri, long size, boolean clearCompose) {
    if (sendButton.getSelectedTransport().isSms()) {
      Media  media  = new Media(uri, contentType, System.currentTimeMillis(), StickerSlide.WIDTH, StickerSlide.HEIGHT, size, 0, false, Optional.absent(), Optional.absent(), Optional.absent());
      Intent intent = MediaSendActivity.buildEditorIntent(this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport());
      startActivityForResult(intent, MEDIA_SENDER);
      return;
    }

    long            expiresIn      = recipient.get().getExpireMessages() * 1000L;
    int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    boolean         initiating     = threadId == -1;
    TransportOption transport      = sendButton.getSelectedTransport();
    SlideDeck       slideDeck      = new SlideDeck();
    Slide           stickerSlide   = new StickerSlide(this, uri, size, stickerLocator, contentType);

    slideDeck.addSlide(stickerSlide);

    sendMediaMessage(transport.isSms(), "", slideDeck, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), expiresIn, false, subscriptionId, initiating, clearCompose);
  }

  private void silentlySetComposeText(String text) {
    typingTextWatcher.setEnabled(false);
    composeText.setText(text);
    typingTextWatcher.setEnabled(true);
  }

  @Override
  public void onReactionsDialogDismissed() {
    reactionOverlay.hideMask();
  }

  // Listeners

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      Permissions.with(ConversationActivity.this)
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_24)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> {
                   composeText.clearFocus();
                   startActivityForResult(MediaSendActivity.buildCameraIntent(ConversationActivity.this, recipient.get(), sendButton.getSelectedTransport()), MEDIA_SENDER);
                   overridePendingTransition(R.anim.camera_slide_from_bottom, R.anim.stationary);
                 })
                 .onAnyDenied(() -> Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
                 .execute();
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      sendMessage();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        return true;
      }
      return false;
    }
  }

  private class AttachButtonListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      handleAddAttachment();
    }
  }

  private class AttachButtonLongClickListener implements View.OnLongClickListener {
    @Override
    public boolean onLongClick(View v) {
      return sendButton.performLongClick();
    }
  }

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(ConversationActivity.this)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      container.showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getTextTrimmed().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      calculateCharactersRemaining();

      if (composeText.getTextTrimmed().length() == 0 || beforeLength == 0) {
        composeText.postDelayed(ConversationActivity.this::updateToggleButtonState, 50);
      }

      stickerViewModel.onInputTextUpdated(s.toString());
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  private class TypingStatusTextWatcher extends SimpleTextWatcher {

    private boolean enabled = true;

    private String previousText = "";

    @Override
    public void onTextChanged(String text) {
      if (enabled && threadId > 0 && isSecureText && !isSmsForced() && !recipient.get().isBlocked()) {
        TypingStatusSender typingStatusSender = ApplicationDependencies.getTypingStatusSender();

        if (text.length() == 0) {
          typingStatusSender.onTypingStoppedWithNotify(threadId);
        } else if (text.length() < previousText.length() && previousText.contains(text)) {
          typingStatusSender.onTypingStopped(threadId);
        } else {
          typingStatusSender.onTypingStarted(threadId);
        }

        previousText = text;
      }
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  @Override
  public void onMessageRequest(@NonNull MessageRequestViewModel viewModel) {
    messageRequestBottomView.setAcceptOnClickListener(v -> viewModel.onAccept());
    messageRequestBottomView.setDeleteOnClickListener(v -> onMessageRequestDeleteClicked(viewModel));
    messageRequestBottomView.setBlockOnClickListener(v -> onMessageRequestBlockClicked(viewModel));
    messageRequestBottomView.setUnblockOnClickListener(v -> onMessageRequestUnblockClicked(viewModel));
    messageRequestBottomView.setGroupV1MigrationContinueListener(v -> GroupsV1MigrationInitiationBottomSheetDialogFragment.showForInitiation(getSupportFragmentManager(), recipient.getId()));

    viewModel.getRequestReviewDisplayState().observe(this, this::presentRequestReviewBanner);
    viewModel.getMessageData().observe(this, this::presentMessageRequestState);
    viewModel.getFailures().observe(this, this::showGroupChangeErrorToast);
    viewModel.getMessageRequestStatus().observe(this, status -> {
      switch (status) {
        case IDLE:
          hideMessageRequestBusy();
          break;
        case ACCEPTING:
        case BLOCKING:
        case DELETING:
          showMessageRequestBusy();
          break;
        case ACCEPTED:
          hideMessageRequestBusy();
          break;
        case DELETED:
        case BLOCKED:
          hideMessageRequestBusy();
          finish();
      }
    });
  }

  private void presentRequestReviewBanner(@NonNull MessageRequestViewModel.RequestReviewDisplayState state) {
    switch (state) {
      case SHOWN:
        reviewBanner.get().setVisibility(View.VISIBLE);

        CharSequence message = new SpannableStringBuilder().append(SpanUtil.bold(getString(R.string.ConversationFragment__review_requests_carefully)))
                                                           .append(" ")
                                                           .append(getString(R.string.ConversationFragment__signal_found_another_contact_with_the_same_name));

        reviewBanner.get().setBannerMessage(message);

        Drawable drawable = ContextUtil.requireDrawable(this, R.drawable.ic_info_white_24).mutate();
        DrawableCompat.setTint(drawable, ContextCompat.getColor(this, R.color.signal_icon_tint_primary));

        reviewBanner.get().setBannerIcon(drawable);
        reviewBanner.get().setOnClickListener(unused -> handleReviewRequest(recipient.getId()));
        break;
      case HIDDEN:
        reviewBanner.get().setVisibility(View.GONE);
        break;
      default:
        break;
    }
  }

  private void presentGroupReviewBanner(@NonNull ConversationGroupViewModel.ReviewState groupReviewState) {
    if (groupReviewState.getCount() > 0) {
      reviewBanner.get().setVisibility(View.VISIBLE);
      reviewBanner.get().setBannerMessage(getString(R.string.ConversationFragment__d_group_members_have_the_same_name, groupReviewState.getCount()));
      reviewBanner.get().setBannerRecipient(groupReviewState.getRecipient());
      reviewBanner.get().setOnClickListener(unused -> handleReviewGroupMembers(groupReviewState.getGroupId()));
    } else if (reviewBanner.resolved()) {
      reviewBanner.get().setVisibility(View.GONE);
    }
  }

  private void showMessageRequestBusy() {
    messageRequestBottomView.showBusy();
  }

  private void hideMessageRequestBusy() {
    messageRequestBottomView.hideBusy();
  }

  private void handleReviewGroupMembers(@Nullable GroupId.V2 groupId) {
    if (groupId == null) {
      return;
    }

    ReviewCardDialogFragment.createForReviewMembers(groupId)
                            .show(getSupportFragmentManager(), null);
  }

  private void handleReviewRequest(@NonNull RecipientId recipientId) {
    if (recipientId == Recipient.UNKNOWN.getId()) {
      return;
    }

    ReviewCardDialogFragment.createForReviewRequest(recipientId)
                            .show(getSupportFragmentManager(), null);
  }

  private void showGroupChangeErrorToast(@NonNull GroupChangeFailureReason e) {
    Toast.makeText(this, GroupErrors.getUserDisplayMessage(e), Toast.LENGTH_LONG).show();
  }

  @Override
  public void handleReaction(@NonNull View maskTarget,
                             @NonNull MessageRecord messageRecord,
                             @NonNull Toolbar.OnMenuItemClickListener toolbarListener,
                             @NonNull ConversationReactionOverlay.OnHideListener onHideListener)
  {
    reactionOverlay.setOnToolbarItemClickedListener(toolbarListener);
    reactionOverlay.setOnHideListener(onHideListener);
    reactionOverlay.show(this, maskTarget, recipient.get(), messageRecord, inputAreaHeight());
  }

  @Override
  public void onListVerticalTranslationChanged(float translationY) {
    reactionOverlay.setListVerticalTranslation(translationY);
  }

  @Override
  public void onMessageWithErrorClicked(@NonNull MessageRecord messageRecord) {
    if (messageRecord.hasFailedWithNetworkFailures()) {
      new AlertDialog.Builder(this)
                     .setMessage(R.string.conversation_activity__message_could_not_be_sent)
                     .setNegativeButton(android.R.string.cancel, null)
                     .setPositiveButton(R.string.conversation_activity__send, (dialog, which) -> MessageSender.resend(this, messageRecord))
                     .show();
    } else if (messageRecord.isIdentityMismatchFailure()) {
      SafetyNumberChangeDialog.show(this, messageRecord);
    } else {
      startActivity(MessageDetailsActivity.getIntentForMessageDetails(this, messageRecord, messageRecord.getRecipient().getId(), messageRecord.getThreadId()));
    }
  }

  @Override
  public void handleReactionDetails(@NonNull View maskTarget) {
    reactionOverlay.showMask(maskTarget, titleView.getMeasuredHeight(), inputAreaHeight());
  }

  @Override
  public void onCursorChanged() {
    if (!reactionOverlay.isShowing()) {
      return;
    }

    SimpleTask.run(() -> {
          //noinspection CodeBlock2Expr
          return DatabaseFactory.getMmsSmsDatabase(this)
                                .checkMessageExists(reactionOverlay.getMessageRecord());
        }, messageExists -> {
          if (!messageExists) {
            reactionOverlay.hide();
          }
        });
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
  }

  @Override
  public void handleReplyMessage(ConversationMessage conversationMessage) {
    MessageRecord messageRecord = conversationMessage.getMessageRecord();

    Recipient author;

    if (messageRecord.isOutgoing()) {
      author = Recipient.self();
    } else {
      author = messageRecord.getIndividualRecipient();
    }

    if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getSharedContacts().isEmpty()) {
      Contact   contact     = ((MmsMessageRecord) messageRecord).getSharedContacts().get(0);
      String    displayName = ContactUtil.getDisplayName(contact);
      String    body        = getString(R.string.ConversationActivity_quoted_contact_message, EmojiStrings.BUST_IN_SILHOUETTE, displayName);
      SlideDeck slideDeck   = new SlideDeck();

      if (contact.getAvatarAttachment() != null) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, contact.getAvatarAttachment()));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          body,
                          slideDeck);

    } else if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getLinkPreviews().isEmpty()) {
      LinkPreview linkPreview = ((MmsMessageRecord) messageRecord).getLinkPreviews().get(0);
      SlideDeck   slideDeck   = new SlideDeck();

      if (linkPreview.getThumbnail().isPresent()) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, linkPreview.getThumbnail().get()));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          conversationMessage.getDisplayBody(this),
                          slideDeck);
    } else {
      SlideDeck slideDeck = messageRecord.isMms() ? ((MmsMessageRecord) messageRecord).getSlideDeck() : new SlideDeck();

      if (messageRecord.isMms() && ((MmsMessageRecord) messageRecord).isViewOnce()) {
        Attachment attachment = new TombstoneAttachment(MediaUtil.VIEW_ONCE, true);
        slideDeck = new SlideDeck();
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, attachment));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          conversationMessage.getDisplayBody(this),
                          slideDeck);
    }

    inputPanel.clickOnComposeInput();
  }

  @Override
  public void onMessageActionToolbarOpened() {
    searchViewItem.collapseActionView();
  }

  @Override
  public void onForwardClicked()  {
    inputPanel.clearQuote();
  }

  @Override
  public void onAttachmentChanged() {
    handleSecurityChange(isSecureText, isDefaultSms);
    updateToggleButtonState();
    updateLinkPreviewState();
  }

  private int inputAreaHeight() {
    int height = panelParent.getMeasuredHeight();

    if (attachmentKeyboardStub.resolved()) {
      View keyboard = attachmentKeyboardStub.get();
      if (keyboard.getVisibility() == View.VISIBLE) {
        return height + keyboard.getMeasuredHeight();
      }
    }

    return height;
  }

  private void onMessageRequestDeleteClicked(@NonNull MessageRequestViewModel requestModel) {
    Recipient recipient = requestModel.getRecipient().getValue();
    if (recipient == null) {
      Log.w(TAG, "[onMessageRequestDeleteClicked] No recipient!");
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this)
                                                 .setNeutralButton(R.string.ConversationActivity_cancel, (d, w) -> d.dismiss());

    if (recipient.isGroup() && recipient.isBlocked()) {
      builder.setTitle(R.string.ConversationActivity_delete_conversation);
      builder.setMessage(R.string.ConversationActivity_this_conversation_will_be_deleted_from_all_of_your_devices);
      builder.setPositiveButton(R.string.ConversationActivity_delete, (d, w) -> requestModel.onDelete());
    } else if (recipient.isGroup()) {
      builder.setTitle(R.string.ConversationActivity_delete_and_leave_group);
      builder.setMessage(R.string.ConversationActivity_you_will_leave_this_group_and_it_will_be_deleted_from_all_of_your_devices);
      builder.setNegativeButton(R.string.ConversationActivity_delete_and_leave, (d, w) -> requestModel.onDelete());
    } else {
      builder.setTitle(R.string.ConversationActivity_delete_conversation);
      builder.setMessage(R.string.ConversationActivity_this_conversation_will_be_deleted_from_all_of_your_devices);
      builder.setNegativeButton(R.string.ConversationActivity_delete, (d, w) -> requestModel.onDelete());
    }

    builder.show();
  }

  private void onMessageRequestBlockClicked(@NonNull MessageRequestViewModel requestModel) {
    Recipient recipient = requestModel.getRecipient().getValue();
    if (recipient == null) {
      Log.w(TAG, "[onMessageRequestBlockClicked] No recipient!");
      return;
    }

    BlockUnblockDialog.showBlockAndDeleteFor(this, getLifecycle(), recipient, requestModel::onBlock, requestModel::onBlockAndDelete);
  }

  private void onMessageRequestUnblockClicked(@NonNull MessageRequestViewModel requestModel) {
    Recipient recipient = requestModel.getRecipient().getValue();
    if (recipient == null) {
      Log.w(TAG, "[onMessageRequestUnblockClicked] No recipient!");
      return;
    }

    BlockUnblockDialog.showUnblockFor(this, getLifecycle(), recipient, requestModel::onUnblock);
  }

  private static void hideMenuItem(@NonNull Menu menu, @IdRes int menuItem) {
    if (menu.findItem(menuItem) != null) {
      menu.findItem(menuItem).setVisible(false);
    }
  }

  @WorkerThread
  private @Nullable KeyboardImageDetails getKeyboardImageDetails(@NonNull Uri uri) {
    try {
      Bitmap bitmap = glideRequests.asBitmap()
                                   .load(new DecryptableStreamUriLoader.DecryptableUri(uri))
                                   .skipMemoryCache(true)
                                   .diskCacheStrategy(DiskCacheStrategy.NONE)
                                   .submit()
                                   .get(1000, TimeUnit.MILLISECONDS);
      int topLeft = bitmap.getPixel(0, 0);
      return new KeyboardImageDetails(bitmap.getWidth(), bitmap.getHeight(), Color.alpha(topLeft) < 255);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return null;
    }
  }

  private void sendKeyboardImage(@NonNull Uri uri, @NonNull String contentType, @Nullable KeyboardImageDetails details) {
    if (details == null || !details.hasTransparency) {
      setMedia(uri, Objects.requireNonNull(MediaType.from(contentType)));
      return;
    }

    long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
    int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    boolean    initiating     = threadId == -1;
    SlideDeck  slideDeck      = new SlideDeck();

    if (MediaUtil.isGif(contentType)) {
      slideDeck.addSlide(new GifSlide(this, uri, 0, details.width, details.height, details.hasTransparency, null));
    } else if (MediaUtil.isImageType(contentType)) {
      slideDeck.addSlide(new ImageSlide(this, uri, contentType, 0, details.width, details.height, details.hasTransparency, null, null));
    } else {
      throw new AssertionError("Only images are supported!");
    }

    sendMediaMessage(isSmsForced(),
                     "",
                     slideDeck,
                     null,
                     Collections.emptyList(),
                     Collections.emptyList(),
                     composeText.getMentions(),
                     expiresIn,
                     false,
                     subscriptionId,
                     initiating,
                     false);
  }

  private class UnverifiedDismissedListener implements UnverifiedBannerView.DismissListener {
    @Override
    public void onDismissed(final List<IdentityRecord> unverifiedIdentities) {
      final IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          synchronized (SESSION_LOCK) {
            for (IdentityRecord identityRecord : unverifiedIdentities) {
              identityDatabase.setVerified(identityRecord.getRecipientId(),
                                           identityRecord.getIdentityKey(),
                                           VerifiedStatus.DEFAULT);
            }
          }

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          initializeIdentityRecords();
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private class UnverifiedClickedListener implements UnverifiedBannerView.ClickListener {
    @Override
    public void onClicked(final List<IdentityRecord> unverifiedIdentities) {
      Log.i(TAG, "onClicked: " + unverifiedIdentities.size());
      if (unverifiedIdentities.size() == 1) {
        startActivity(VerifyIdentityActivity.newIntent(ConversationActivity.this, unverifiedIdentities.get(0), false));
      } else {
        String[] unverifiedNames = new String[unverifiedIdentities.size()];

        for (int i=0;i<unverifiedIdentities.size();i++) {
          unverifiedNames[i] = Recipient.resolved(unverifiedIdentities.get(i).getRecipientId()).getDisplayName(ConversationActivity.this);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
        builder.setIcon(R.drawable.ic_warning);
        builder.setTitle("No longer verified");
        builder.setItems(unverifiedNames, (dialog, which) -> {
          startActivity(VerifyIdentityActivity.newIntent(ConversationActivity.this, unverifiedIdentities.get(which), false));
        });
        builder.show();
      }
    }
  }

  private class QuoteRestorationTask extends AsyncTask<Void, Void, ConversationMessage> {

    private final String                  serialized;
    private final SettableFuture<Boolean> future;

    QuoteRestorationTask(@NonNull String serialized, @NonNull SettableFuture<Boolean> future) {
      this.serialized = serialized;
      this.future     = future;
    }

    @Override
    protected ConversationMessage doInBackground(Void... voids) {
      QuoteId quoteId = QuoteId.deserialize(ConversationActivity.this, serialized);

      if (quoteId == null) {
        return null;
      }

      Context context = getApplicationContext();

      MessageRecord messageRecord = DatabaseFactory.getMmsSmsDatabase(context).getMessageFor(quoteId.getId(), quoteId.getAuthor());
      if (messageRecord == null) {
        return null;
      }

      return ConversationMessageFactory.createWithUnresolvedData(context, messageRecord);
    }

    @Override
    protected void onPostExecute(ConversationMessage conversationMessage) {
      if (conversationMessage != null) {
        handleReplyMessage(conversationMessage);
        future.set(true);
      } else {
        Log.e(TAG, "Failed to restore a quote from a draft. No matching message record.");
        future.set(false);
      }
    }
  }

  private void presentMessageRequestState(@Nullable MessageRequestViewModel.MessageData messageData) {
    if (!Util.isEmpty(viewModel.getArgs().getDraftText()) ||
        viewModel.getArgs().getMedia() != null            ||
        viewModel.getArgs().getStickerLocator() != null)
    {
      Log.d(TAG, "[presentMessageRequestState] Have extra, so ignoring provided state.");
      messageRequestBottomView.setVisibility(View.GONE);
    } else if (isPushGroupV1Conversation() && !isActiveGroup()) {
      Log.d(TAG, "[presentMessageRequestState] Inactive push group V1, so ignoring provided state.");
      messageRequestBottomView.setVisibility(View.GONE);
    } else if (messageData == null) {
      Log.d(TAG, "[presentMessageRequestState] Null messageData. Ignoring.");
    } else if (messageData.getMessageState() == MessageRequestState.NONE) {
      Log.d(TAG, "[presentMessageRequestState] No message request necessary.");
      messageRequestBottomView.setVisibility(View.GONE);
    } else {
      Log.d(TAG, "[presentMessageRequestState] " + messageData.getMessageState());
      messageRequestBottomView.setMessageData(messageData);
      messageRequestBottomView.setVisibility(View.VISIBLE);
    }

    invalidateOptionsMenu();
  }

  private static class KeyboardImageDetails {
    private final int     width;
    private final int     height;
    private final boolean hasTransparency;

    private KeyboardImageDetails(int width, int height, boolean hasTransparency) {
      this.width           = width;
      this.height          = height;
      this.hasTransparency = hasTransparency;
    }
  }
}
