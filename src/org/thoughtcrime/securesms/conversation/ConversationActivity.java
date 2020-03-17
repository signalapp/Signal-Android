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
import android.arch.lifecycle.ViewModelProviders;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Browser;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.support.v4.content.pm.ShortcutManagerCompat;
import android.support.v4.graphics.drawable.IconCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.ExpirationDialog;
import org.thoughtcrime.securesms.GroupCreateActivity;
import org.thoughtcrime.securesms.GroupMembersDialog;
import org.thoughtcrime.securesms.MediaOverviewActivity;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.PromptMmsActivity;
import org.thoughtcrime.securesms.RegistrationActivity;
import org.thoughtcrime.securesms.ShortcutLauncherActivity;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.audio.AudioRecorder;
import org.thoughtcrime.securesms.audio.AudioSlidePlayer;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.components.AttachmentTypeSelector;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.components.ConversationSearchBottomBar;
import org.thoughtcrime.securesms.components.HidingLinearLayout;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.InputPanel;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import org.thoughtcrime.securesms.components.SendButton;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.components.identity.UntrustedSendDialog;
import org.thoughtcrime.securesms.components.identity.UnverifiedBannerView;
import org.thoughtcrime.securesms.components.identity.UnverifiedSendDialog;
import org.thoughtcrime.securesms.components.location.SignalPlace;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.InviteReminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactShareEditActivity;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase.Draft;
import org.thoughtcrime.securesms.database.DraftDatabase.Drafts;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.MmsSmsColumns.Types;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.giph.ui.GiphyActivity;
import org.thoughtcrime.securesms.jobs.MultiDeviceBlockedUpdateJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.loki.LokiMessageDatabase;
import org.thoughtcrime.securesms.loki.LokiThreadDatabase;
import org.thoughtcrime.securesms.loki.LokiThreadDatabaseDelegate;
import org.thoughtcrime.securesms.loki.MultiDeviceUtilities;
import org.thoughtcrime.securesms.loki.redesign.activities.HomeActivity;
import org.thoughtcrime.securesms.loki.redesign.messaging.LokiAPIUtilities;
import org.thoughtcrime.securesms.loki.redesign.messaging.LokiUserDatabase;
import org.thoughtcrime.securesms.loki.redesign.views.FriendRequestViewDelegate;
import org.thoughtcrime.securesms.loki.redesign.views.MentionCandidateSelectionView;
import org.thoughtcrime.securesms.loki.redesign.views.SessionRestoreBannerView;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaSendActivity;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AttachmentManager.MediaType;
import org.thoughtcrime.securesms.mms.AudioSlide;
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
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.GroupShareProfileView;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.RecipientModifiedListener;
import org.thoughtcrime.securesms.search.model.MessageResult;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerKeyboardProvider;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.stickers.StickerManagementActivity;
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent;
import org.thoughtcrime.securesms.stickers.StickerSearchRepository;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.TextSecurePreferences.MediaKeyboardMode;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.loki.api.DeviceLink;
import org.whispersystems.signalservice.loki.api.LokiAPI;
import org.whispersystems.signalservice.loki.api.LokiDeviceLinkUtilities;
import org.whispersystems.signalservice.loki.api.LokiPublicChat;
import org.whispersystems.signalservice.loki.messaging.LokiMessageFriendRequestStatus;
import org.whispersystems.signalservice.loki.messaging.LokiThreadFriendRequestStatus;
import org.whispersystems.signalservice.loki.messaging.Mention;
import org.whispersystems.signalservice.loki.utilities.PublicKeyValidation;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;
import network.loki.messenger.R;

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
public class ConversationActivity extends PassphraseRequiredActionBarActivity
    implements ConversationFragment.ConversationFragmentListener,
               AttachmentManager.AttachmentListener,
               RecipientModifiedListener,
               OnKeyboardShownListener,
               InputPanel.Listener,
               InputPanel.MediaListener,
               ComposeText.CursorPositionChangedListener,
               ConversationSearchBottomBar.EventListener,
               StickerKeyboardProvider.StickerEventListener,
               LokiThreadDatabaseDelegate,
               FriendRequestViewDelegate
{
  private static final String TAG = ConversationActivity.class.getSimpleName();

  public static final String ADDRESS_EXTRA           = "address";
  public static final String THREAD_ID_EXTRA         = "thread_id";
  public static final String IS_ARCHIVED_EXTRA       = "is_archived";
  public static final String TEXT_EXTRA              = "draft_text";
  public static final String MEDIA_EXTRA             = "media_list";
  public static final String STICKER_EXTRA           = "media_list";
  public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";
  public static final String TIMING_EXTRA            = "timing";
  public static final String LAST_SEEN_EXTRA         = "last_seen";
  public static final String STARTING_POSITION_EXTRA = "starting_position";

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

  private   GlideRequests               glideRequests;
  protected ComposeText                 composeText;
  private   AnimatingToggle             buttonToggle;
  private   SendButton                  sendButton;
  private   ImageButton                 attachButton;
  private   TextView                    titleTextView;
  private   TextView                    charactersLeft;
  private   ConversationFragment        fragment;
  private   Button                      unblockButton;
  private   Button                      makeDefaultSmsButton;
  private   Button                      registerButton;
  private   InputAwareLayout            container;
  private   View                        composePanel;
  protected Stub<ReminderView>          reminderView;
  private   Stub<UnverifiedBannerView>  unverifiedBannerView;
  private   Stub<GroupShareProfileView> groupShareProfileView;
  private   TypingStatusTextWatcher     typingTextWatcher;
  private   MentionTextWatcher          mentionTextWatcher;
  private   ConversationSearchBottomBar searchNav;
  private   MenuItem                    searchViewItem;
  private   ProgressBar                 messageStatusProgressBar;
  private   ImageView                   muteIndicatorImageView;
  private   TextView subtitleTextView;

  private   AttachmentTypeSelector attachmentTypeSelector;
  private   AttachmentManager      attachmentManager;
  private   AudioRecorder          audioRecorder;
  private   BroadcastReceiver      securityUpdateReceiver;
  private   Stub<MediaKeyboard>    emojiDrawerStub;
  protected HidingLinearLayout     quickAttachmentToggle;
  protected HidingLinearLayout     inlineAttachmentToggle;
  private   InputPanel             inputPanel;

  private LinkPreviewViewModel         linkPreviewViewModel;
  private ConversationSearchViewModel  searchViewModel;
  private ConversationStickerViewModel stickerViewModel;

  private Recipient  recipient;
  private long       threadId;
  private int        distributionType;
  private boolean    archived;
  private boolean    isSecureText;
  private boolean    isDefaultSms            = false;
  private boolean    isMmsEnabled            = false;
  private boolean    isSecurityInitialized   = false;
  private int        expandedKeyboardHeight  = 0;
  private int        collapsedKeyboardHeight = Integer.MAX_VALUE;
  private int        keyboardHeight          = 0;

  private final IdentityRecordList      identityRecords = new IdentityRecordList();
  private final DynamicNoActionBarTheme dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage         dynamicLanguage = new DynamicLanguage();

  // Message Status Bar
  private ArrayList<BroadcastReceiver> broadcastReceivers = new ArrayList<>();
  private String messageStatus = null;

  // Mentions
  private View mentionCandidateSelectionViewContainer;
  private MentionCandidateSelectionView mentionCandidateSelectionView;
  private int currentMentionStartIndex = -1;
  private ArrayList<Mention> mentions = new ArrayList<>();
  private String oldText = "";

  // Multi Device
  private   boolean isFriendsWithAnyDevice = false;

  // Restoration
  protected SessionRestoreBannerView sessionRestoreBannerView;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    Log.i(TAG, "onCreate()");

    setContentView(R.layout.conversation_activity);

    TypedArray typedArray = obtainStyledAttributes(new int[] {R.attr.conversation_background});
    int color = typedArray.getColor(0, Color.WHITE);
    typedArray.recycle();

    getWindow().getDecorView().setBackgroundColor(color);

    fragment = initFragment(R.id.fragment_content, new ConversationFragment(), dynamicLanguage.getCurrentLocale());
    fragment.friendRequestViewDelegate = this;

    registerMessageStatusObserver("calculatingPoW");
    registerMessageStatusObserver("contactingNetwork");
    registerMessageStatusObserver("sendingMessage");
    registerMessageStatusObserver("messageSent");
    registerMessageStatusObserver("messageFailed");
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

      @Override
      public void onReceive(Context context, Intent intent) {
        Toast.makeText(ConversationActivity.this, "Your clock is out of sync with the service node network.", Toast.LENGTH_LONG).show();
      }
    };
    broadcastReceivers.add(broadcastReceiver);
    LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter("clockOutOfSync"));

    initializeReceivers();
    initializeActionBar();
    initializeViews();
    initializeResources();
    initializeLinkPreviewObserver();
    initializeSearchObserver();
    initializeStickerObserver();
    initializeSecurity(false, isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeProfiles();
        initializeDraft().addListener(new AssertedSuccessListener<Boolean>() {
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
            composeText.addTextChangedListener(mentionTextWatcher);
            mentionCandidateSelectionView.setGlide(glideRequests);
            mentionCandidateSelectionView.setOnMentionCandidateSelected( mentionCandidate -> {
              mentions.add(mentionCandidate);
              String oldText = composeText.getText().toString();
              String newText = oldText.substring(0, currentMentionStartIndex) + "@" + mentionCandidate.getDisplayName();
              composeText.setText(newText);
              composeText.setSelection(newText.length());
              currentMentionStartIndex = -1;
              mentionCandidateSelectionView.hide();
              ConversationActivity.this.oldText = newText;
              return Unit.INSTANCE;
            });
          }
        });
      }
    });

    sessionRestoreBannerView.setOnRestore(() -> {
      this.restoreSession();
      return Unit.INSTANCE;
    });
    sessionRestoreBannerView.setOnDismiss(() -> {
      // TODO: Maybe silence for x minutes?
      DatabaseFactory.getLokiThreadDatabase(ConversationActivity.this).removeAllSessionRestoreDevices(threadId);
      updateSessionRestoreBanner();
      return Unit.INSTANCE;
    });

    LokiAPIUtilities.INSTANCE.populateUserHexEncodedPublicKeyCacheIfNeeded(threadId, this);

    LokiPublicChat publicChat = DatabaseFactory.getLokiThreadDatabase(this).getPublicChat(threadId);
    if (publicChat != null) {
      ApplicationContext.getInstance(this).getLokiPublicChatAPI().getUserCount(publicChat.getChannel(), publicChat.getServer()).success(integer -> {
        updateSubtitleTextView();
        return Unit.INSTANCE;
      });
    }

    View rootView = findViewById(R.id.rootView);
    rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
      int height = rootView.getRootView().getHeight() - rootView.getHeight();
      int thresholdInDP = 120;
      float scale = getResources().getDisplayMetrics().density;
      int thresholdInPX = (int)(thresholdInDP * scale);
      if (expandedKeyboardHeight == 0 || height > thresholdInPX) {
        expandedKeyboardHeight = height;
      }
      collapsedKeyboardHeight = Math.min(collapsedKeyboardHeight, height);
      keyboardHeight = expandedKeyboardHeight - collapsedKeyboardHeight;
    });
  }

  private void registerMessageStatusObserver(String status) {
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

      @Override
      public void onReceive(Context context, Intent intent) {
        long timestamp = intent.getLongExtra("long", 0);
        handleMessageStatusChanged(status, timestamp);
      }
    };
    broadcastReceivers.add(broadcastReceiver);
    LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(status));
  }

  @Override
  protected void onNewIntent(Intent intent) {
    Log.i(TAG, "onNewIntent()");
    
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent()) {
      saveDraft();
      attachmentManager.clear(glideRequests, false);
      silentlySetComposeText("");
    }

    setIntent(intent);
    initializeResources();
    initializeSecurity(false, isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeDraft();
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
    initializeEnabledCheck();
    initializeMmsEnabledCheck();
    initializeIdentityRecords();
    composeText.setTransport(sendButton.getSelectedTransport());

    updateTitleTextView(recipient);
    updateSubtitleTextView();
    setActionBarColor(recipient.getColor());
    setBlockedUserState(recipient, isSecureText, isDefaultSms);
    setGroupShareProfileReminder(recipient);
    calculateCharactersRemaining();

    MessageNotifier.setVisibleThread(threadId);
    markThreadAsRead();

    DatabaseFactory.getLokiThreadDatabase(this).setDelegate(this);
    updateInputPanel();

    updateSessionRestoreBanner();

    Log.i(TAG, "onResume() Finished: " + (System.currentTimeMillis() - getIntent().getLongExtra(TIMING_EXTRA, 0)));
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleThread(-1L);
    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_right);
    inputPanel.onPause();

    fragment.setLastSeen(System.currentTimeMillis());
    markLastSeen();
    AudioSlidePlayer.stopAll();
    EventBus.getDefault().unregister(this);

    DatabaseFactory.getLokiThreadDatabase(this).setDelegate(null);
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
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (recipient != null)               recipient.removeListener(this);
    if (securityUpdateReceiver != null)  unregisterReceiver(securityUpdateReceiver);
    for (BroadcastReceiver broadcastReceiver : broadcastReceivers) {
      LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
    }
    super.onDestroy();
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
      recipient = Recipient.from(this, data.getParcelableExtra(GroupCreateActivity.GROUP_ADDRESS_EXTRA), true);
      recipient.addListener(this);
      updateTitleTextView(recipient);
      updateSubtitleTextView();
      NotificationChannels.updateContactChannelName(this, recipient);
      setBlockedUserState(recipient, isSecureText, isDefaultSms);
      supportInvalidateOptionsMenu();
      break;
    case TAKE_PHOTO:
      if (attachmentManager.getCaptureUri() != null) {
        setMedia(attachmentManager.getCaptureUri(), MediaType.IMAGE);
      }
      break;
    case ADD_CONTACT:
      recipient = Recipient.from(this, recipient.getAddress(), true);
      recipient.addListener(this);
      fragment.reloadList();
      break;
      /*
    case PICK_LOCATION:
      SignalPlace place = new SignalPlace(PlacePicker.getPlace(data, this));
      attachmentManager.setLocation(place, getCurrentMediaConstraints());
      break;
       */
    case PICK_GIF:
      setMedia(data.getData(),
              MediaType.GIF,
              data.getIntExtra(GiphyActivity.EXTRA_WIDTH, 0),
              data.getIntExtra(GiphyActivity.EXTRA_HEIGHT, 0));
      break;
    case SMS_DEFAULT:
      initializeSecurity(isSecureText, isDefaultSms);
      break;
    case MEDIA_SENDER:
      long            expiresIn      = recipient.getExpireMessages() * 1000L;
      int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
      boolean         initiating     = threadId == -1;
      TransportOption transport      = data.getParcelableExtra(MediaSendActivity.EXTRA_TRANSPORT);
      String          message        = data.getStringExtra(MediaSendActivity.EXTRA_MESSAGE);
      SlideDeck       slideDeck      = new SlideDeck();

      if (transport == null) {
        throw new IllegalStateException("Received a null transport from the MediaSendActivity.");
      }

      sendButton.setTransport(transport);

      List<Media> mediaList = data.getParcelableArrayListExtra(MediaSendActivity.EXTRA_MEDIA);

      for (Media mediaItem : mediaList) {
        if (MediaUtil.isVideoType(mediaItem.getMimeType())) {
          slideDeck.addSlide(new VideoSlide(this, mediaItem.getUri(), 0, mediaItem.getCaption().orNull()));
        } else if (MediaUtil.isGif(mediaItem.getMimeType())) {
          slideDeck.addSlide(new GifSlide(this, mediaItem.getUri(), 0, mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.getCaption().orNull()));
        } else if (MediaUtil.isImageType(mediaItem.getMimeType())) {
          slideDeck.addSlide(new ImageSlide(this, mediaItem.getUri(), 0, mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.getCaption().orNull()));
        } else {
          Log.w(TAG, "Asked to send an unexpected mimeType: '" + mediaItem.getMimeType() + "'. Skipping.");
        }
      }

      final Context context = ConversationActivity.this.getApplicationContext();

      sendMediaMessage(transport.isSms(),
                       message,
                       slideDeck,
                       inputPanel.getQuote().orNull(),
                       Collections.emptyList(),
                       Collections.emptyList(),
                       expiresIn,
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
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    boolean isOpenGroupOrRSSFeed = recipient.getAddress().isPublicChat() || recipient.getAddress().isRSSFeed();

    if (isSecureText && !isOpenGroupOrRSSFeed) {
      if (recipient.getExpireMessages() > 0) {
        inflater.inflate(R.menu.conversation_expiring_on, menu);

        final MenuItem item       = menu.findItem(R.id.menu_expiring_messages);
        final View     actionView = MenuItemCompat.getActionView(item);
        final TextView badgeView  = actionView.findViewById(R.id.expiration_badge);

        badgeView.setText(ExpirationUtil.getExpirationAbbreviatedDisplayValue(this, recipient.getExpireMessages()));
        actionView.setOnClickListener(v -> onOptionsItemSelected(item));
      } else {
        inflater.inflate(R.menu.conversation_expiring_off, menu);
      }
    }

    if (isSingleConversation()) {
      /*
      if (isSecureText) inflater.inflate(R.menu.conversation_callable_secure, menu);
      else              inflater.inflate(R.menu.conversation_callable_insecure, menu);
       */
    } else if (isGroupConversation() && !isOpenGroupOrRSSFeed) {
      inflater.inflate(R.menu.conversation_group_options, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      } else if (isActiveGroup()) {
        inflater.inflate(R.menu.conversation_push_group_options, menu);
      }
    }

    inflater.inflate(R.menu.conversation, menu);

    if (isSingleConversation() && isSecureText) {
      inflater.inflate(R.menu.conversation_secure, menu);
    } else if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (recipient != null && recipient.isMuted()) inflater.inflate(R.menu.conversation_muted, menu);
    else                                          inflater.inflate(R.menu.conversation_unmuted, menu);

    /*
    if (isSingleConversation() && getRecipient().getContactUri() == null) {
      inflater.inflate(R.menu.conversation_add_to_contacts, menu);
    }


    if (recipient != null && recipient.isLocalNumber()) {
      if (isSecureText) menu.findItem(R.id.menu_call_secure).setVisible(false);
      else              menu.findItem(R.id.menu_call_insecure).setVisible(false);

      MenuItem muteItem = menu.findItem(R.id.menu_mute_notifications);

      if (muteItem != null) {
        muteItem.setVisible(false);
      }
    }
     */

    searchViewItem = menu.findItem(R.id.menu_search);

    SearchView                     searchView    = (SearchView)searchViewItem.getActionView();
    SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        searchViewModel.onQueryUpdated(query, threadId);
        searchNav.showLoading();
        fragment.onSearchQueryUpdated(query);
        return true;
      }

      @Override
      public boolean onQueryTextChange(String query) {
        searchViewModel.onQueryUpdated(query, threadId);
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
        invalidateOptionsMenu();
        return true;
      }
    });

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_call_secure:               handleDial(getRecipient(), true);                  return true;
    case R.id.menu_call_insecure:             handleDial(getRecipient(), false);                 return true;
    case R.id.menu_view_media:                handleViewMedia();                                 return true;
    case R.id.menu_add_shortcut:              handleAddShortcut();                               return true;
    case R.id.menu_search:                    handleSearch();                                    return true;
    case R.id.menu_add_to_contacts:           handleAddToContacts();                             return true;
    case R.id.menu_reset_secure_session:      handleResetSecureSession();                        return true;
    case R.id.menu_group_recipients:          handleDisplayGroupRecipients();                    return true;
    case R.id.menu_distribution_broadcast:    handleDistributionBroadcastEnabled(item);          return true;
    case R.id.menu_distribution_conversation: handleDistributionConversationEnabled(item);       return true;
    case R.id.menu_edit_group:                handleEditPushGroup();                             return true;
    case R.id.menu_leave:                     handleLeavePushGroup();                            return true;
    case R.id.menu_invite:                    handleInviteLink();                                return true;
    case R.id.menu_mute_notifications:        handleMuteNotifications();                         return true;
    case R.id.menu_unmute_notifications:      handleUnmuteNotifications();                       return true;
    // case R.id.menu_conversation_settings:     handleConversationSettings();                      return true;
    case R.id.menu_expiring_messages_off:
    case R.id.menu_expiring_messages:         handleSelectMessageExpiration();                   return true;
    case android.R.id.home:                   handleReturnToConversationList();                  return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    Log.d(TAG, "onBackPressed()");
    if (container.isInputOpen()) container.hideCurrentInput(composeText);
    else                         super.onBackPressed();
  }

  @Override
  public void onKeyboardShown() {
    inputPanel.onKeyboardShown();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {
    updateReminders(recipient.hasSeenInviteReminder());
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  //////// Event Handlers

  private void handleReturnToConversationList() {
    Intent intent = new Intent(this, HomeActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(intent);
    finish();
  }

  private void handleSelectMessageExpiration() {
    if (isPushGroupConversation() && !isActiveGroup()) {
      return;
    }

    //noinspection CodeBlock2Expr
    ExpirationDialog.show(this, recipient.getExpireMessages(), expirationTime -> {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getRecipientDatabase(ConversationActivity.this).setExpireMessages(recipient, expirationTime);
          OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(getRecipient(), System.currentTimeMillis(), expirationTime * 1000L);
          MessageSender.send(ConversationActivity.this, outgoingMessage, threadId, false, null);

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          invalidateOptionsMenu();
          if (fragment != null) fragment.setLastSeen(0);
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });
  }

  private void handleMuteNotifications() {
    MuteDialog.show(this, until -> {
      recipient.setMuted(until);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getRecipientDatabase(ConversationActivity.this)
                         .setMuted(recipient, until);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });
  }

  private void handleConversationSettings() {
    /*
    Intent intent = new Intent(ConversationActivity.this, RecipientPreferenceActivity.class);
    intent.putExtra(RecipientPreferenceActivity.ADDRESS_EXTRA, recipient.getAddress());
    intent.putExtra(RecipientPreferenceActivity.CAN_HAVE_SAFETY_NUMBER_EXTRA,
                    isSecureText && !isSelfConversation());

    startActivitySceneTransition(intent, titleView.findViewById(R.id.contact_photo_image), "avatar");
     */
  }

  private void handleUnmuteNotifications() {
    recipient.setMuted(0);

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientDatabase(ConversationActivity.this)
                       .setMuted(recipient, 0);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleUnblock() {
    int titleRes = R.string.ConversationActivity_unblock_this_contact_question;
    int bodyRes  = R.string.ConversationActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact;

    if (recipient.isGroupRecipient()) {
      titleRes = R.string.ConversationActivity_unblock_this_group_question;
      bodyRes  = R.string.ConversationActivity_unblock_this_group_description;
    }

    //noinspection CodeBlock2Expr
    new AlertDialog.Builder(this)
                   .setTitle(titleRes)
                   .setMessage(bodyRes)
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(R.string.ConversationActivity_unblock, (dialog, which) -> {
                     new AsyncTask<Void, Void, Void>() {
                       @Override
                       protected Void doInBackground(Void... params) {
                         DatabaseFactory.getRecipientDatabase(ConversationActivity.this)
                             .setBlocked(recipient, false);

                         ApplicationContext.getInstance(ConversationActivity.this)
                             .getJobManager()
                             .add(new MultiDeviceBlockedUpdateJob());

                         return null;
                       }
                     }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                   }).show();
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private void handleMakeDefaultSms() {
    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
    startActivityForResult(intent, SMS_DEFAULT);
  }

  private void handleRegisterForSignal() {
    Intent intent = new Intent(this, RegistrationActivity.class);
    intent.putExtra(RegistrationActivity.RE_REGISTRATION_EXTRA, true);
    startActivity(intent);
  }

  private void handleInviteLink() {
    String inviteText = getString(R.string.ConversationActivity_lets_switch_to_signal, getString(R.string.install_url));

    if (isDefaultSms) {
      composeText.appendInvite(inviteText);
    } else {
      Intent intent = new Intent(Intent.ACTION_SENDTO);
      intent.setData(Uri.parse("smsto:" + recipient.getAddress().serialize()));
      intent.putExtra("sms_body", inviteText);
      intent.putExtra(Intent.EXTRA_TEXT, inviteText);
      startActivity(intent);
    }
  }

  private void handleResetSecureSession() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_reset_secure_session_question);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_this_may_help_if_youre_having_encryption_problems);
    builder.setPositiveButton(R.string.ConversationActivity_reset, (dialog, which) -> {
      if (isSingleConversation()) {
        final Context context = getApplicationContext();

        OutgoingEndSessionMessage endSessionMessage =
            new OutgoingEndSessionMessage(new OutgoingTextMessage(getRecipient(), "TERMINATE", 0, -1));

        new AsyncTask<OutgoingEndSessionMessage, Void, Long>() {
          @Override
          protected Long doInBackground(OutgoingEndSessionMessage... messages) {
            return MessageSender.send(context, messages[0], threadId, false, null);
          }

          @Override
          protected void onPostExecute(Long result) {
            sendComplete(result);
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, endSessionMessage);
      }
    });
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleViewMedia() {
    Intent intent = new Intent(this, MediaOverviewActivity.class);
    intent.putExtra(MediaOverviewActivity.ADDRESS_EXTRA, recipient.getAddress());
    startActivity(intent);
  }

  private void handleAddShortcut() {
    Log.i(TAG, "Creating home screen shortcut for recipient " + recipient.getAddress());

    new AsyncTask<Void, Void, IconCompat>() {

      @Override
      protected IconCompat doInBackground(Void... voids) {
        Context    context = getApplicationContext();
        IconCompat icon    = null;

        if (recipient.getContactPhoto() != null) {
          try {
            Bitmap bitmap = BitmapFactory.decodeStream(recipient.getContactPhoto().openInputStream(context));
            bitmap = BitmapUtil.createScaledBitmap(bitmap, 300, 300);
            icon   = IconCompat.createWithAdaptiveBitmap(bitmap);
          } catch (IOException e) {
            Log.w(TAG, "Failed to decode contact photo during shortcut creation. Falling back to generic icon.", e);
          }
        }

        if (icon == null) {
          icon = IconCompat.createWithResource(context, recipient.isGroupRecipient() ? R.mipmap.ic_group_shortcut
                                                                                     : R.mipmap.ic_person_shortcut);
        }

        return icon;
      }

      @Override
      protected void onPostExecute(IconCompat icon) {
        Context context  = getApplicationContext();
        String  name     = Optional.fromNullable(recipient.getName())
                                  .or(Optional.fromNullable(recipient.getProfileName()))
                                  .or(recipient.toShortString());

        ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, recipient.getAddress().serialize() + '-' + System.currentTimeMillis())
                                                                .setShortLabel(name)
                                                                .setIcon(icon)
                                                                .setIntent(ShortcutLauncherActivity.createIntent(context, recipient.getAddress()))
                                                                .build();

        if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)) {
          Toast.makeText(context, getString(R.string.ConversationActivity_added_to_home_screen), Toast.LENGTH_LONG).show();
        }
      }
    }.execute();
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

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(getString(R.string.ConversationActivity_leave_group));
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setCancelable(true);
    builder.setMessage(getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group));
    builder.setPositiveButton(R.string.yes, (dialog, which) -> {
      Recipient                           groupRecipient = getRecipient();
      if (GroupUtil.leaveGroup(this, groupRecipient)) {
        initializeEnabledCheck();
      } else {
        Toast.makeText(this, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleEditPushGroup() {
    AlertDialog.Builder alert = new AlertDialog.Builder(this);
    alert.setMessage("The ability to add members to a closed group is coming soon.");
    alert.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
    alert.create().show();
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
      try {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                                       Uri.parse("tel:" + recipient.getAddress().serialize()));
        startActivity(dialIntent);
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, anfe);
        Dialogs.showAlertDialog(this,
                                getString(R.string.ConversationActivity_calls_not_supported),
                                getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
      }
    }
  }

  private void handleDisplayGroupRecipients() {
    new GroupMembersDialog(this, getRecipient()).display();
  }

  private void handleAddToContacts() {
    if (recipient.getAddress().isGroup()) return;

    try {
      startActivityForResult(RecipientExporter.export(recipient).asAddContactIntent(), ADD_CONTACT);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
    }
  }

  private boolean handleDisplayQuickContact() {
    return !recipient.getAddress().isGroup();

//    if (recipient.getContactUri() != null) {
//      ContactsContract.QuickContact.showQuickContact(ConversationActivity.this, titleView, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
//    } else {
//      handleAddToContacts();
//    }
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled || isSecureText) {
      if (attachmentTypeSelector == null) {
        attachmentTypeSelector = new AttachmentTypeSelector(this, getSupportLoaderManager(), new AttachmentTypeListener(), keyboardHeight);
      }
      attachmentTypeSelector.keyboardHeight = keyboardHeight;
      attachmentTypeSelector.show(this, attachButton);
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

  private void handleUnverifiedRecipients() {
    List<Recipient>      unverifiedRecipients = identityRecords.getUnverifiedRecipients(this);
    List<IdentityRecord> unverifiedRecords    = identityRecords.getUnverifiedRecords();
    String               message              = IdentityUtil.getUnverifiedSendDialogDescription(this, unverifiedRecipients);

    if (message == null) return;

    //noinspection CodeBlock2Expr
    new UnverifiedSendDialog(this, message, unverifiedRecords, () -> {
      initializeIdentityRecords().addListener(new ListenableFuture.Listener<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          sendMessage();
        }

        @Override
        public void onFailure(ExecutionException e) {
          throw new AssertionError(e);
        }
      });
    }).show();
  }

  private void handleUntrustedRecipients() {
    List<Recipient>      untrustedRecipients = identityRecords.getUntrustedRecipients(this);
    List<IdentityRecord> untrustedRecords    = identityRecords.getUntrustedRecords();
    String               untrustedMessage    = IdentityUtil.getUntrustedSendDialogDescription(this, untrustedRecipients);

    if (untrustedMessage == null) return;

    //noinspection CodeBlock2Expr
    new UntrustedSendDialog(this, untrustedMessage, untrustedRecords, () -> {
      initializeIdentityRecords().addListener(new ListenableFuture.Listener<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          sendMessage();
        }

        @Override
        public void onFailure(ExecutionException e) {
          throw new AssertionError(e);
        }
      });
    }).show();
  }

  private void handleSecurityChange(boolean isSecureText, boolean isDefaultSms) {
    Log.i(TAG, "handleSecurityChange(" + isSecureText + ", " + isDefaultSms + ")");
    if (isSecurityInitialized && isSecureText == this.isSecureText && isDefaultSms == this.isDefaultSms) {
      return;
    }

    this.isSecureText          = isSecureText;
    this.isDefaultSms          = isDefaultSms;
    this.isSecurityInitialized = true;

    boolean isMediaMessage = recipient.isMmsGroupRecipient() || attachmentManager.isAttachmentPresent();

    sendButton.resetAvailableTransports(isMediaMessage);
    sendButton.setDefaultTransport(Type.TEXTSECURE);

    /* Loki - We don't support SMS
    if (!isSecureText && !isPushGroupConversation()) sendButton.disableTransport(Type.TEXTSECURE);
    if (recipient.isPushGroupRecipient())            sendButton.disableTransport(Type.SMS);

    if (!recipient.isPushGroupRecipient() && recipient.isForceSmsSelection()) {
      sendButton.setDefaultTransport(Type.SMS);
    } else {
      if (isSecureText || isPushGroupConversation()) sendButton.setDefaultTransport(Type.TEXTSECURE);
      else                                           sendButton.setDefaultTransport(Type.SMS);
    }
    */

    calculateCharactersRemaining();
    supportInvalidateOptionsMenu();
    setBlockedUserState(recipient, isSecureText, isDefaultSms);
  }

  ///// Initializers

  private ListenableFuture<Boolean> initializeDraft() {
    final SettableFuture<Boolean> result = new SettableFuture<>();

    final String         draftText      = getIntent().getStringExtra(TEXT_EXTRA);
    final Uri            draftMedia     = getIntent().getData();
    final MediaType      draftMediaType = MediaType.from(getIntent().getType());
    final List<Media>    mediaList      = getIntent().getParcelableArrayListExtra(MEDIA_EXTRA);
    final StickerLocator stickerLocator = getIntent().getParcelableExtra(STICKER_EXTRA);

    if (stickerLocator != null && draftMedia != null) {
      sendSticker(stickerLocator, draftMedia, 0, true);
      return new SettableFuture<>(false);
    }

    if (!Util.isEmpty(mediaList)) {
      Intent sendIntent = MediaSendActivity.buildEditorIntent(this, mediaList, recipient, draftText, sendButton.getSelectedTransport());
      startActivityForResult(sendIntent, MEDIA_SENDER);
      return new SettableFuture<>(false);
    }

    if (draftText != null) {
      composeText.setText("");
      composeText.append(draftText);
      result.set(true);
    }

    if (draftMedia != null && draftMediaType != null) {
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
    boolean enabled = !(isPushGroupConversation() && !isActiveGroup());
    inputPanel.setEnabled(enabled);
    sendButton.setEnabled(enabled);
    attachButton.setEnabled(enabled);
  }

  private ListenableFuture<Boolean> initializeDraftFromDatabase() {
    SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Void, Void, List<Draft>>() {
      @Override
      protected List<Draft> doInBackground(Void... params) {
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        List<Draft> results         = draftDatabase.getDrafts(threadId);

        draftDatabase.clearDrafts(threadId);

        return results;
      }

      @Override
      protected void onPostExecute(List<Draft> drafts) {
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
                composeText.setText(draft.getValue());
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
        // Loki - Override the flag below
        boolean signalEnabled = true; // TextSecurePreferences.isPushRegistered(context);


        return new boolean[] { signalEnabled, false};
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
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient);

    return future;
  }

  private void onSecurityUpdated() {
    Log.i(TAG, "onSecurityUpdated()");
    updateReminders(recipient.hasSeenInviteReminder());
    updateDefaultSubscriptionId(recipient.getDefaultSubscriptionId());
  }

  protected void updateReminders(boolean seenInvite) {
    Log.i(TAG, "updateReminders(" + seenInvite + ")");

    if (UnauthorizedReminder.isEligible(this)) {
      reminderView.get().showReminder(new UnauthorizedReminder(this));
    } else if (ExpiredBuildReminder.isEligible()) {
      reminderView.get().showReminder(new ExpiredBuildReminder(this));
    } else if (ServiceOutageReminder.isEligible(this)) {
      ApplicationContext.getInstance(this).getJobManager().add(new ServiceOutageDetectionJob());
      reminderView.get().showReminder(new ServiceOutageReminder(this));
    } else if (TextSecurePreferences.isPushRegistered(this)      &&
               TextSecurePreferences.isShowInviteReminders(this) &&
               !isSecureText                                            &&
               !seenInvite                                              &&
               !recipient.isGroupRecipient())
    {
      InviteReminder reminder = new InviteReminder(this, recipient);
      reminder.setOkListener(v -> {
        handleInviteLink();
        reminderView.get().requestDismiss();
      });
      reminderView.get().showReminder(reminder);
    } else if (reminderView.resolved()) {
      reminderView.get().hide();
    }
  }

  protected void updateSessionRestoreBanner() {
    Set<String> devices = DatabaseFactory.getLokiThreadDatabase(this).getSessionRestoreDevices(threadId);
    if (devices.size() > 0) {
      sessionRestoreBannerView.update(recipient);
      sessionRestoreBannerView.show();
    } else {
      sessionRestoreBannerView.hide();
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
        IdentityDatabase   identityDatabase   = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);
        IdentityRecordList identityRecordList = new IdentityRecordList();
        List<Recipient>    recipients         = new LinkedList<>();

        if (params[0].isGroupRecipient()) {
          recipients.addAll(DatabaseFactory.getGroupDatabase(ConversationActivity.this)
                                           .getGroupMembers(params[0].getAddress().toGroupString(), false));
        } else {
          recipients.add(params[0]);
        }

        for (Recipient recipient : recipients) {
          Log.i(TAG, "Loading identity for: " + recipient.getAddress());
          identityRecordList.add(identityDatabase.getIdentity(recipient.getAddress()));
        }

        String message = null;

        if (identityRecordList.isUnverified()) {
          message = IdentityUtil.getUnverifiedBannerDescription(ConversationActivity.this, identityRecordList.getUnverifiedRecipients(ConversationActivity.this));
        }

        return new Pair<>(identityRecordList, message);
      }

      @Override
      protected void onPostExecute(@NonNull Pair<IdentityRecordList, String> result) {
        Log.i(TAG, "Got identity records: " + result.first.isUnverified());
        identityRecords.replaceWith(result.first);

        if (result.second != null) {
          Log.d(TAG, "Replacing banner...");
          unverifiedBannerView.get().display(result.second, result.first.getUnverifiedRecords(),
                                             new UnverifiedClickedListener(),
                                             new UnverifiedDismissedListener());
        } else if (unverifiedBannerView.resolved()) {
          Log.d(TAG, "Clearing banner...");
          unverifiedBannerView.get().hide();
        }

//        titleView.setVerified(isSecureText && identityRecords.isVerified());

        future.set(true);
      }

    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient);

    return future;
  }

  private void initializeViews() {
    titleTextView          = findViewById(R.id.titleTextView);
    buttonToggle           = ViewUtil.findById(this, R.id.button_toggle);
    sendButton             = ViewUtil.findById(this, R.id.send_button);
    attachButton           = ViewUtil.findById(this, R.id.attach_button);
    composeText            = ViewUtil.findById(this, R.id.embedded_text_editor);
    charactersLeft         = ViewUtil.findById(this, R.id.space_left);
    emojiDrawerStub        = ViewUtil.findStubById(this, R.id.emoji_drawer_stub);
    unblockButton          = ViewUtil.findById(this, R.id.unblock_button);
    makeDefaultSmsButton   = ViewUtil.findById(this, R.id.make_default_sms_button);
    registerButton         = ViewUtil.findById(this, R.id.register_button);
    composePanel           = ViewUtil.findById(this, R.id.bottom_panel);
    container              = ViewUtil.findById(this, R.id.layout_container);
    reminderView           = ViewUtil.findStubById(this, R.id.reminder_stub);
    unverifiedBannerView   = ViewUtil.findStubById(this, R.id.unverified_banner_stub);
    groupShareProfileView  = ViewUtil.findStubById(this, R.id.group_share_profile_view_stub);
    quickAttachmentToggle  = ViewUtil.findById(this, R.id.quick_attachment_toggle);
    inlineAttachmentToggle = ViewUtil.findById(this, R.id.inline_attachment_container);
    inputPanel             = ViewUtil.findById(this, R.id.bottom_panel);
    searchNav              = ViewUtil.findById(this, R.id.conversation_search_nav);
    mentionCandidateSelectionViewContainer = ViewUtil.findById(this, R.id.mentionCandidateSelectionViewContainer);
    mentionCandidateSelectionView = ViewUtil.findById(this, R.id.userSelectionView);
    sessionRestoreBannerView = ViewUtil.findById(this, R.id.sessionRestoreBannerView);
    messageStatusProgressBar = ViewUtil.findById(this, R.id.messageStatusProgressBar);
    muteIndicatorImageView = ViewUtil.findById(this, R.id.muteIndicatorImageView);
    subtitleTextView = ViewUtil.findById(this, R.id.subtitleTextView);

    ImageButton quickCameraToggle      = ViewUtil.findById(this, R.id.quick_camera_toggle);
    ImageButton inlineAttachmentButton = ViewUtil.findById(this, R.id.inline_attachment_button);

    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this);
    inputPanel.setMediaListener(this);

    attachmentTypeSelector = null;
    attachmentManager      = new AttachmentManager(this, this);
    audioRecorder          = new AudioRecorder(this);
    typingTextWatcher      = new TypingStatusTextWatcher();
    mentionTextWatcher     = new MentionTextWatcher();

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
      composeText.setTransport(newTransport);
      if (manuallySelected) recordTransportPreference(newTransport);
    });

    /*
    titleView.setOnClickListener(v -> handleConversationSettings());
    titleView.setOnLongClickListener(v -> handleDisplayQuickContact());
     */
    unblockButton.setOnClickListener(v -> handleUnblock());
    makeDefaultSmsButton.setOnClickListener(v -> handleMakeDefaultSms());
    registerButton.setOnClickListener(v -> handleRegisterForSignal());

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) && Camera.getNumberOfCameras() > 0) {
      quickCameraToggle.setVisibility(View.VISIBLE);
      quickCameraToggle.setOnClickListener(new QuickCameraToggleListener());
    } else {
      quickCameraToggle.setVisibility(View.GONE);
    }

    searchNav.setEventListener(this);

    inlineAttachmentButton.setOnClickListener(v -> handleAddAttachment());
  }

  protected void initializeActionBar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    toolbar.getOverflowIcon().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
    setSupportActionBar(toolbar);

    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar == null) throw new AssertionError();

//    supportActionBar.setDisplayHomeAsUpEnabled(true);
    supportActionBar.setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    if (recipient != null) recipient.removeListener(this);

    recipient        = Recipient.from(this, getIntent().getParcelableExtra(ADDRESS_EXTRA), true);
    threadId         = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    archived         = getIntent().getBooleanExtra(IS_ARCHIVED_EXTRA, false);
    distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    glideRequests    = GlideApp.with(this);

    recipient.addListener(this);
  }

  private void initializeLinkPreviewObserver() {
    linkPreviewViewModel = ViewModelProviders.of(this, new LinkPreviewViewModel.Factory(new LinkPreviewRepository(this))).get(LinkPreviewViewModel.class);

    if (!TextSecurePreferences.isLinkPreviewsEnabled(this)) {
      linkPreviewViewModel.onUserCancel();
      return;
    }

    linkPreviewViewModel.getLinkPreviewState().observe(this, previewState -> {
      if (previewState == null) return;

      if (previewState.isLoading()) {
        Log.d(TAG, "Loading link preview.");
        inputPanel.setLinkPreviewLoading();
      } else {
        Log.d(TAG, "Setting link preview: " + previewState.getLinkPreview().isPresent());
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
        fragment.jumpToMessage(messageResult.messageRecipient.getAddress(), messageResult.receivedTimestampMs, searchViewModel::onMissingResult);
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

  private void showStickerIntroductionTooltip() {
    TextSecurePreferences.setMediaKeyboardMode(this, MediaKeyboardMode.STICKER);
    inputPanel.setMediaKeyboardToggleMode(true);

    TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                .setBackgroundTint(getResources().getColor(R.color.core_blue))
                .setTextColor(getResources().getColor(R.color.core_white))
                .setText(R.string.ConversationActivity_new_say_it_with_stickers)
                .setOnDismissListener(() -> {
                  TextSecurePreferences.setHasSeenStickerIntroTooltip(this, true);
                  EventBus.getDefault().removeStickyEvent(StickerPackInstallEvent.class);
                })
                .show(TooltipPopup.POSITION_ABOVE);
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

    ApplicationContext.getInstance(this)
                      .getJobManager()
                      .add(new RetrieveProfileJob(recipient));
  }

  @Override
  public void onModified(final Recipient recipient) {
    Log.i(TAG, "onModified(" + recipient.getAddress().serialize() + ")");
    Util.runOnMain(() -> {
      Log.i(TAG, "onModifiedRun(): " + recipient.getRegistered());
      updateTitleTextView(recipient);
      updateSubtitleTextView();
//      titleView.setVerified(identityRecords.isVerified());
      setBlockedUserState(recipient, isSecureText, isDefaultSms);
      setActionBarColor(recipient.getColor());
      setGroupShareProfileReminder(recipient);
      updateReminders(recipient.hasSeenInviteReminder());
      updateDefaultSubscriptionId(recipient.getDefaultSubscriptionId());
      initializeSecurity(isSecureText, isDefaultSms);

      if (searchViewItem == null || !searchViewItem.isActionViewExpanded()) {
        invalidateOptionsMenu();
      }
    });
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onIdentityRecordUpdate(final IdentityRecord event) {
    initializeIdentityRecords();
  }

  @Subscribe(threadMode =  ThreadMode.MAIN, sticky = true)
  public void onStickerPackInstalled(final StickerPackInstallEvent event) {
    if (!TextSecurePreferences.hasSeenStickerIntroTooltip(this)) return;

    EventBus.getDefault().removeStickyEvent(event);
    TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                .setText(R.string.ConversationActivity_sticker_pack_installed)
                .setIconGlideModel(event.getIconGlideModel())
                .show(TooltipPopup.POSITION_ABOVE);
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

  private void addAttachment(int type) {
    linkPreviewViewModel.onUserCancel();

    Log.i(TAG, "Selected: " + type);
    switch (type) {
    case AttachmentTypeSelector.ADD_GALLERY:
      AttachmentManager.selectGallery(this, MEDIA_SENDER, recipient, composeText.getTextTrimmed(), sendButton.getSelectedTransport()); break;
    case AttachmentTypeSelector.ADD_DOCUMENT:
      AttachmentManager.selectDocument(this, PICK_DOCUMENT); break;
    case AttachmentTypeSelector.ADD_SOUND:
      AttachmentManager.selectAudio(this, PICK_AUDIO); break;
    case AttachmentTypeSelector.ADD_CONTACT_INFO:
      AttachmentManager.selectContactInfo(this, PICK_CONTACT); break;
    case AttachmentTypeSelector.ADD_LOCATION:
      AttachmentManager.selectLocation(this, PICK_LOCATION); break;
    case AttachmentTypeSelector.TAKE_PHOTO:
      attachmentManager.capturePhoto(this, TAKE_PHOTO); break;
    case AttachmentTypeSelector.ADD_GIF:
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setTitle("Search GIFs?");
      builder.setMessage("You will not have full metadata protection when sending GIFs.");
      builder.setPositiveButton("OK", (dialog, which) -> {
        AttachmentManager.selectGif(this, PICK_GIF, !isSecureText);
        dialog.dismiss();
      });
      builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss() );
      builder.create().show();
      break;
    }
  }

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType) {
    return setMedia(uri, mediaType, 0, 0);
  }

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType, int width, int height) {
    if (uri == null) {
      return new SettableFuture<>(false);
    }

    if (MediaType.VCARD.equals(mediaType) && isSecureText) {
      openContactShareEditor(uri);
      return new SettableFuture<>(false);
    } else if (MediaType.IMAGE.equals(mediaType) || MediaType.GIF.equals(mediaType) || MediaType.VIDEO.equals(mediaType)) {
      Media media = new Media(uri, MediaUtil.getMimeType(this, uri), 0, width, height, 0, Optional.absent(), Optional.absent());
      startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient, composeText.getTextTrimmed(), sendButton.getSelectedTransport()), MEDIA_SENDER);
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
    long       expiresIn      = recipient.getExpireMessages() * 1000L;
    boolean    initiating     = threadId == -1;

    sendMediaMessage(isSmsForced(), "", attachmentManager.buildSlideDeck(), null, contacts, Collections.emptyList(), expiresIn, subscriptionId, initiating, false);
  }

  private void selectContactInfo(ContactData contactData) {
    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i]     = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIconAttribute(R.attr.conversation_attach_contact_info);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, (dialog, which) -> composeText.append(numbers[which]));
    builder.show();
  }

  private Drafts getDraftsForCurrentState() {
    Drafts drafts = new Drafts();

    if (!Util.isEmpty(composeText)) {
      drafts.add(new Draft(Draft.TEXT, composeText.getTextTrimmed()));
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
    supportActionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.action_bar_background)));
    setStatusBarColor(getResources().getColor(R.color.action_bar_background));
  }

  private void setBlockedUserState(Recipient recipient, boolean isSecureText, boolean isDefaultSms) {
    if (recipient.isGroupRecipient() && recipient.getAddress().isRSSFeed()) {
      unblockButton.setVisibility(View.GONE);
      composePanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);
    } else if (recipient.isBlocked()) {
      unblockButton.setVisibility(View.VISIBLE);
      composePanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);
    } else if (!isSecureText && isPushGroupConversation()) {
      unblockButton.setVisibility(View.GONE);
      composePanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.VISIBLE);
    } else if (!isSecureText && !isDefaultSms) {
      unblockButton.setVisibility(View.GONE);
      composePanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.VISIBLE);
      registerButton.setVisibility(View.GONE);
    } else {
      composePanel.setVisibility(View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);
    }
  }

  private void setGroupShareProfileReminder(@NonNull Recipient recipient) {
    if (recipient.isPushGroupRecipient() && !recipient.isProfileSharing() && !recipient.getAddress().isPublicChat() && !recipient.getAddress().isRSSFeed()) {
      groupShareProfileView.get().setRecipient(recipient);
      groupShareProfileView.get().setVisibility(View.GONE); // Loki - Always hide for now
    } else if (groupShareProfileView.resolved()) {
      groupShareProfileView.get().setVisibility(View.GONE);
    }
  }

  private void calculateCharactersRemaining() {
    /*
    String          messageBody     = composeText.getTextTrimmed();
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
     */
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


  private boolean isSingleConversation() {
    return getRecipient() != null && !getRecipient().isGroupRecipient();
  }

  private boolean isActiveGroup() {
    if (!isGroupConversation()) return false;

    Optional<GroupRecord> record = DatabaseFactory.getGroupDatabase(this).getGroup(getRecipient().getAddress().toGroupString());
    return record.isPresent() && record.get().isActive();
  }

  @SuppressWarnings("SimplifiableIfStatement")
  private boolean isSelfConversation() {
    if (!TextSecurePreferences.isPushRegistered(this)) return false;
    if (recipient.isGroupRecipient())                         return false;

    return Util.isOwnNumber(this, recipient.getAddress());
  }

  private boolean isGroupConversation() {
    return getRecipient() != null && getRecipient().isGroupRecipient();
  }

  private boolean isPushGroupConversation() {
    return getRecipient() != null && getRecipient().isPushGroupRecipient();
  }

  private boolean isSmsForced() {
    return sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
  }

  protected Recipient getRecipient() {
    return this.recipient;
  }

  protected long getThreadId() {
    return this.threadId;
  }

  private String getMessage() throws InvalidMessageException {
    String result = composeText.getTextTrimmed();
    if (result.length() < 1 && !attachmentManager.isAttachmentPresent()) throw new InvalidMessageException();
    for (Mention mention : mentions) {
      try {
        int startIndex = result.indexOf("@" + mention.getDisplayName());
        int endIndex = startIndex + mention.getDisplayName().length() + 1; // + 1 to include the @
        result = result.substring(0, startIndex) + "@" + mention.getHexEncodedPublicKey() + result.substring(endIndex);
      } catch (Exception exception) {
        Log.d("Loki", "Couldn't process mention due to error: " + exception.toString() + ".");
      }
    }
    return result;
  }

  private Pair<String, Optional<Slide>> getSplitMessage(String rawText, int maxPrimaryMessageSize) {
    String          bodyText  = rawText;
    Optional<Slide> textSlide = Optional.absent();

    if (bodyText.length() > maxPrimaryMessageSize) {
      bodyText = rawText.substring(0, maxPrimaryMessageSize);

      byte[] textData  = rawText.getBytes();
      String timestamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(new Date());
      String filename  = String.format("signal-%s.txt", timestamp);
      Uri    textUri   = BlobProvider.getInstance()
                                     .forData(textData)
                                     .withMimeType(MediaUtil.LONG_TEXT)
                                     .withFileName(filename)
                                     .createForSingleSessionInMemory();

      textSlide = Optional.of(new TextSlide(this, textUri, filename, textData.length));
    }

    return new Pair<>(bodyText, textSlide);
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return sendButton.getSelectedTransport().getType() == Type.TEXTSECURE
           ? MediaConstraints.getPushMediaConstraints()
           : MediaConstraints.getMmsMediaConstraints(sendButton.getSelectedTransport().getSimSubscriptionId().or(-1));
  }

  private void markThreadAsRead() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        Context                 context    = ConversationActivity.this;
        List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(params[0], false);

        MarkReadReceiver.process(context, messageIds);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
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
      fragment.reload(recipient, threadId);
      MessageNotifier.setVisibleThread(threadId);
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();

    updateLinkPreviewState();
  }

  @Override
  public void handleThreadFriendRequestStatusChanged(long threadID) {
      if (threadID != this.threadId) {
        Recipient threadRecipient = DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID);
        if (threadRecipient != null && !threadRecipient.isGroupRecipient()) {
          LokiDeviceLinkUtilities.INSTANCE.getAllLinkedDeviceHexEncodedPublicKeys(threadRecipient.getAddress().serialize()).success(devices -> {
            // We should update our input if this thread is a part of the other threads device
            if (devices.contains(recipient.getAddress().serialize())) {
              this.updateInputPanel();
            }
            return Unit.INSTANCE;
          });
        }
        return;
      }

      this.updateInputPanel();
  }

  @Override
  public void handleSessionRestoreDevicesChanged(long threadId) {
    if (threadId == this.threadId) {
      runOnUiThread(this::updateSessionRestoreBanner);
    }
  }

  private void updateInputPanel() {
    /*
      isFriendsWithAnyDevice reflects whether we are friends with any of the other user's devices.

      This fixes the case where the input panel disables and enables rapidly, which can occur when we are
      not friends with the current thread BUT multi device tells us that we are friends with another one of their devices.
     */
    if (recipient.isGroupRecipient() || isNoteToSelf() || isFriendsWithAnyDevice) { setInputPanelEnabled(true); return; }

    // Disable the input panel if a friend request is pending
    LokiThreadFriendRequestStatus friendRequestStatus = DatabaseFactory.getLokiThreadDatabase(this).getFriendRequestStatus(threadId);
    boolean isPending = friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_SENDING || friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_SENT || friendRequestStatus == LokiThreadFriendRequestStatus.REQUEST_RECEIVED;
    setInputPanelEnabled(!isPending);

    // Always enable the input panel if we are friends with the current user
    isFriendsWithAnyDevice = (friendRequestStatus == LokiThreadFriendRequestStatus.FRIENDS);

    if (!isFriendsWithAnyDevice) {
      // Enable the input panel if we don't have any pending friend requests OR we are friends with one of the user's linked devices
      MultiDeviceUtilities.hasPendingFriendRequestWithAnyLinkedDevice(this, recipient).success( hasPendingRequests -> {
        if (!hasPendingRequests) {
          setInputPanelEnabled(true);
        } else {
          MultiDeviceUtilities.isFriendsWithAnyLinkedDevice(this, recipient).success( isFriends -> {
            // Enable the input panel if we're friends with any of the user's devices
            isFriendsWithAnyDevice = isFriends;
            setInputPanelEnabled(isFriends);
            return Unit.INSTANCE;
          });
        }
        return Unit.INSTANCE;
      });
    }
  }

  private void setInputPanelEnabled(boolean enabled) {
    Util.runOnMain(() -> {
      updateToggleButtonState();
      String hint = enabled ? "Message" : "Pending session request";
      inputPanel.setHint(hint);
      inputPanel.setEnabled(enabled);
      if (enabled) {
        inputPanel.composeText.requestFocus();
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.showSoftInput(inputPanel.composeText, 0);
      }
    });
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
      boolean         isMediaMessage = attachmentManager.isAttachmentPresent()        ||
                                       recipient.isGroupRecipient()                   ||
                                       recipient.getAddress().isEmail()               ||
                                       inputPanel.getQuote().isPresent()              ||
                                       linkPreviewViewModel.hasLinkPreview()          ||
                                       LinkPreviewUtil.isWhitelistedMediaUrl(message) || // Loki - Send GIFs as media messages
                                       needsSplit;

      Log.i(TAG, "isManual Selection: " + sendButton.isManualSelection());
      Log.i(TAG, "forceSms: " + forceSms);

      if ((recipient.isMmsGroupRecipient() || recipient.getAddress().isEmail()) && !isMmsEnabled) {
        handleManualMmsRequired();
      } else if (!forceSms && identityRecords.isUnverified()) {
        handleUnverifiedRecipients();
      }/* else if (!forceSms && identityRecords.isUntrusted()) {
        handleUntrustedRecipients();
      }*/ else if (isMediaMessage) {
        sendMediaMessage(forceSms, expiresIn, subscriptionId, initiating);
      } else {
        sendTextMessage(forceSms, expiresIn, subscriptionId, initiating);
      }
    } catch (RecipientFormattingException ex) {
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Log.w(TAG, ex);
    }

    if (messageStatus == null && !isGroupConversation()) {
      messageStatus = "calculatingPoW";
      updateSubtitleTextView();
      updateMessageStatusProgressBar();
    }
  }

  private void sendMediaMessage(final boolean forceSms, final long expiresIn, final int subscriptionId, boolean initiating)
      throws InvalidMessageException
  {
    Log.i(TAG, "Sending media message...");
    sendMediaMessage(forceSms, getMessage(), attachmentManager.buildSlideDeck(), inputPanel.getQuote().orNull(), Collections.emptyList(), linkPreviewViewModel.getActiveLinkPreviews(), expiresIn, subscriptionId, initiating, true);
  }

  private ListenableFuture<Void> sendMediaMessage(final boolean forceSms,
                                                  String body,
                                                  SlideDeck slideDeck,
                                                  QuoteModel quote,
                                                  List<Contact> contacts,
                                                  List<LinkPreview> previews,
                                                  final long expiresIn,
                                                  final int subscriptionId,
                                                  final boolean initiating,
                                                  final boolean clearComposeBox)
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return new SettableFuture<>(null);
    }

    if (isSecureText && !forceSms) {
      Pair<String, Optional<Slide>> splitMessage = getSplitMessage(body, sendButton.getSelectedTransport().calculateCharacters(body).maxPrimaryMessageSize);
      body = splitMessage.first;

      if (splitMessage.second.isPresent()) {
        slideDeck.addSlide(splitMessage.second.get());
      }
    }

    OutgoingMediaMessage outgoingMessageCandidate = new OutgoingMediaMessage(recipient, slideDeck, body, System.currentTimeMillis(), subscriptionId, expiresIn, distributionType, quote, contacts, previews);

    final SettableFuture<Void> future  = new SettableFuture<>();
    final Context              context = getApplicationContext();

    final OutgoingMediaMessage outgoingMessage;

    if (isSecureText && !forceSms) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessageCandidate);
      ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);
    } else {
      outgoingMessage = outgoingMessageCandidate;
    }

    // Loki - Send a friend request if we're not yet friends with the user in question
    LokiThreadFriendRequestStatus friendRequestStatus = DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadId);
    outgoingMessage.isFriendRequest = !isGroupConversation() && friendRequestStatus != LokiThreadFriendRequestStatus.FRIENDS; // Needed for stageOutgoingMessage(...)

    if (clearComposeBox) {
      inputPanel.clearQuote();
      attachmentManager.clear(glideRequests, false);
      silentlySetComposeText("");
    }

    final long id = fragment.stageOutgoingMessage(outgoingMessage);

    new AsyncTask<Void, Void, Long>() {
      @Override
      protected Long doInBackground(Void... param) {
        if (initiating) {
          DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, true);
        }

        return MessageSender.send(context, outgoingMessage, threadId, forceSms, () -> fragment.releaseOutgoingMessage(id));
      }

      @Override
      protected void onPostExecute(Long result) {
        sendComplete(result);
        future.set(null);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    return future;
  }

  private void sendTextMessage(final boolean forceSms, final long expiresIn, final int subscriptionId, final boolean initiatingConversation)
      throws InvalidMessageException
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return;
    }

    final Context context     = getApplicationContext();
    final String  messageBody = getMessage();

    OutgoingTextMessage message;

    if (isSecureText && !forceSms) {
      message = new OutgoingEncryptedMessage(recipient, messageBody, expiresIn);
      ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);
    } else {
      message = new OutgoingTextMessage(recipient, messageBody, expiresIn, subscriptionId);
    }

    // Loki - Send a friend request if we're not yet friends with the user in question
    LokiThreadFriendRequestStatus friendRequestStatus = DatabaseFactory.getLokiThreadDatabase(context).getFriendRequestStatus(threadId);
    message.isFriendRequest = !isGroupConversation() && friendRequestStatus != LokiThreadFriendRequestStatus.FRIENDS; // Needed for stageOutgoingMessage(...)

    silentlySetComposeText("");
    final long id = fragment.stageOutgoingMessage(message);

    new AsyncTask<OutgoingTextMessage, Void, Long>() {
      @Override
      protected Long doInBackground(OutgoingTextMessage... messages) {
        if (initiatingConversation) {
          DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, true);
        }

        return MessageSender.send(context, messages[0], threadId, forceSms, () -> fragment.releaseOutgoingMessage(id));
      }

      @Override
      protected void onPostExecute(Long result) {
         sendComplete(result);
       }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
  }

  private void showDefaultSmsPrompt() {
    new AlertDialog.Builder(this)
                   .setMessage(R.string.ConversationActivity_signal_cannot_sent_sms_mms_messages_because_it_is_not_your_default_sms_app)
                   .setNegativeButton(R.string.ConversationActivity_no, (dialog, which) -> dialog.dismiss())
                   .setPositiveButton(R.string.ConversationActivity_yes, (dialog, which) -> handleMakeDefaultSms())
                   .show();
  }

  private void updateToggleButtonState() {
    // Don't allow attachments if we're not friends with any of the user's devices
    if (!isNoteToSelf() && !recipient.isGroupRecipient() && !isFriendsWithAnyDevice) {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.hide();
      inlineAttachmentToggle.hide();
      return;
    }

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

      if (!attachmentManager.isAttachmentPresent() && !linkPreviewViewModel.hasLinkPreview()) {
        inlineAttachmentToggle.show();
      } else {
        inlineAttachmentToggle.hide();
      }
    }
  }

  private void updateLinkPreviewState() {
    if (TextSecurePreferences.isLinkPreviewsEnabled(this) && !sendButton.getSelectedTransport().isSms() && !attachmentManager.isAttachmentPresent()) {
      linkPreviewViewModel.onEnabled();
      linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed(), composeText.getSelectionStart(), composeText.getSelectionEnd());
    } else {
      linkPreviewViewModel.onUserCancel();
    }
  }

  private void recordTransportPreference(TransportOption transportOption) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(ConversationActivity.this);

        recipientDatabase.setDefaultSubscriptionId(recipient, transportOption.getSimSubscriptionId().or(-1));

        if (!recipient.isPushGroupRecipient()) {
          recipientDatabase.setForceSmsSelection(recipient, recipient.getRegistered() == RegisteredState.REGISTERED && transportOption.isSms());
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
               .withRationaleDialog(getString(R.string.ConversationActivity_to_send_audio_messages_allow_signal_access_to_your_microphone), R.drawable.ic_mic_white_48dp)
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
        int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
        long       expiresIn      = recipient.getExpireMessages() * 1000L;
        boolean    initiating     = threadId == -1;
        AudioSlide audioSlide     = new AudioSlide(ConversationActivity.this, result.first, result.second, MediaUtil.AUDIO_AAC, true);
        SlideDeck  slideDeck      = new SlideDeck();
        slideDeck.addSlide(audioSlide);

        sendMediaMessage(forceSms, "", slideDeck, inputPanel.getQuote().orNull(), Collections.emptyList(), Collections.emptyList(), expiresIn, subscriptionId, initiating, true).addListener(new AssertedSuccessListener<Void>() {
          @Override
          public void onSuccess(Void nothing) {
            new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                BlobProvider.getInstance().delete(ConversationActivity.this, result.first);
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
            BlobProvider.getInstance().delete(ConversationActivity.this, result.first);
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
    if (!TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif")) {
      setMedia(uri, MediaType.GIF);
    } else if (MediaUtil.isImageType(contentType)) {
      setMedia(uri, MediaType.IMAGE);
    } else if (MediaUtil.isVideoType(contentType)) {
      setMedia(uri, MediaType.VIDEO);
    } else if (MediaUtil.isAudioType(contentType)) {
      setMedia(uri, MediaType.AUDIO);
    }
  }

  @Override
  public void onCursorPositionChanged(int start, int end) {
    linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed(), start, end);
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
    sendSticker(new StickerLocator(stickerRecord.getPackId(), stickerRecord.getPackKey(), stickerRecord.getStickerId()), stickerRecord.getUri(), stickerRecord.getSize(), clearCompose);

    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
      DatabaseFactory.getStickerDatabase(this).updateStickerLastUsedTime(stickerRecord.getRowId(), System.currentTimeMillis());
    });
  }

  private void sendSticker(@NonNull StickerLocator stickerLocator, @NonNull Uri uri, long size, boolean clearCompose) {
    if (sendButton.getSelectedTransport().isSms()) {
      Media  media  = new Media(uri, MediaUtil.IMAGE_WEBP, System.currentTimeMillis(), StickerSlide.WIDTH, StickerSlide.HEIGHT, size, Optional.absent(), Optional.absent());
      Intent intent = MediaSendActivity.buildEditorIntent(this, Collections.singletonList(media), recipient, composeText.getTextTrimmed(), sendButton.getSelectedTransport());
      startActivityForResult(intent, MEDIA_SENDER);
      return;
    }

    long            expiresIn      = recipient.getExpireMessages() * 1000L;
    int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    boolean         initiating     = threadId == -1;
    TransportOption transport      = sendButton.getSelectedTransport();
    SlideDeck       slideDeck      = new SlideDeck();
    Slide           stickerSlide   = new StickerSlide(this, uri, size, stickerLocator);

    slideDeck.addSlide(stickerSlide);

    sendMediaMessage(transport.isSms(), "", slideDeck, null, Collections.emptyList(), Collections.emptyList(), expiresIn, subscriptionId, initiating, clearCompose);

  }

  private void silentlySetComposeText(String text) {
    typingTextWatcher.setEnabled(false);
    composeText.setText(text);
    if (text.isEmpty()) resetMentions();
    typingTextWatcher.setEnabled(true);
  }

  // Listeners

  private class AttachmentTypeListener implements AttachmentTypeSelector.AttachmentClickedListener {
    @Override
    public void onClick(int type) {
      addAttachment(type);
    }

    @Override
    public void onQuickAttachment(Uri uri, String mimeType, String bucketId, long dateTaken, int width, int height, long size) {
      linkPreviewViewModel.onUserCancel();
      Media media = new Media(uri, mimeType, dateTaken, width, height, size, Optional.of(Media.ALL_MEDIA_BUCKET_ID), Optional.absent());
      startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient, composeText.getTextTrimmed(), sendButton.getSelectedTransport()), MEDIA_SENDER);
    }
  }

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      Permissions.with(ConversationActivity.this)
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_photo_camera_white_48dp)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> {
                   composeText.clearFocus();
                   startActivityForResult(MediaSendActivity.buildCameraIntent(ConversationActivity.this, recipient, sendButton.getSelectedTransport()), MEDIA_SENDER);
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

    @Override
    public void onTextChanged(String text) {
      if (enabled && threadId > 0 && isSecureText && !isSmsForced()) {
        ApplicationContext.getInstance(ConversationActivity.this).getTypingStatusSender().onTypingStarted(threadId);
      }
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  private class MentionTextWatcher extends SimpleTextWatcher {

    @Override
    public void onTextChanged(String text) {
      boolean isBackspace = text.length() < oldText.length();
      if (isBackspace) {
        currentMentionStartIndex = -1;
        mentionCandidateSelectionView.hide();
        mentionCandidateSelectionViewContainer.setVisibility(View.GONE);
        ArrayList<Mention> mentionsToRemove = new ArrayList<>();
        for (Mention mention : mentions) {
          if (!text.contains(mention.getDisplayName())) {
            mentionsToRemove.add(mention);
          }
        }
        mentions.removeAll(mentionsToRemove);
      }
      if (text.length() > 0) {
        if (currentMentionStartIndex > text.length()) {
            resetMentions(); // Should never occur
        }
        int lastCharacterIndex = text.length() - 1;
        char lastCharacter = text.charAt(lastCharacterIndex);
        char secondToLastCharacter = ' ';
        if (lastCharacterIndex > 0) {
          secondToLastCharacter = text.charAt(lastCharacterIndex - 1);
        }
        String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(ConversationActivity.this);
        LokiThreadDatabase threadDatabase = DatabaseFactory.getLokiThreadDatabase(ConversationActivity.this);
        LokiUserDatabase userDatabase = DatabaseFactory.getLokiUserDatabase(ConversationActivity.this);
        if (lastCharacter == '@' && Character.isWhitespace(secondToLastCharacter)) {
          List<Mention> mentionCandidates = LokiAPI.Companion.getMentionCandidates("", threadId, userHexEncodedPublicKey, threadDatabase, userDatabase);
          currentMentionStartIndex = lastCharacterIndex;
          mentionCandidateSelectionViewContainer.setVisibility(View.VISIBLE);
          mentionCandidateSelectionView.show(mentionCandidates, threadId);
        } else if (Character.isWhitespace(lastCharacter)) {
          currentMentionStartIndex = -1;
          mentionCandidateSelectionView.hide();
          mentionCandidateSelectionViewContainer.setVisibility(View.GONE);
        } else {
          if (currentMentionStartIndex != -1) {
            String query = text.substring(currentMentionStartIndex + 1); // + 1 to get rid of the @
            List<Mention> mentionCandidates = LokiAPI.Companion.getMentionCandidates(query, threadId, userHexEncodedPublicKey, threadDatabase, userDatabase);
            mentionCandidateSelectionViewContainer.setVisibility(View.VISIBLE);
            mentionCandidateSelectionView.show(mentionCandidates, threadId);
          }
        }
      }
      ConversationActivity.this.oldText = text;
    }
  }

  private void resetMentions() {
    oldText = "";
    currentMentionStartIndex = -1;
    mentions.clear();
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
  }

  @Override
  public void handleReplyMessage(MessageRecord messageRecord) {
    Recipient author;

    if (messageRecord.isOutgoing()) {
      author = Recipient.from(this, Address.fromSerialized(TextSecurePreferences.getLocalNumber(this)), true);
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
              slideDeck,
              recipient,
              threadId);

    } else if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getLinkPreviews().isEmpty()) {
      LinkPreview linkPreview = ((MmsMessageRecord) messageRecord).getLinkPreviews().get(0);
      SlideDeck   slideDeck   = new SlideDeck();

      if (linkPreview.getThumbnail().isPresent()) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, linkPreview.getThumbnail().get()));
      }

      inputPanel.setQuote(GlideApp.with(this),
              messageRecord.getDateSent(),
              author,
              messageRecord.getBody(),
              slideDeck,
              recipient,
              threadId);
    } else {
      inputPanel.setQuote(GlideApp.with(this),
              messageRecord.getDateSent(),
              author,
              messageRecord.getBody(),
              messageRecord.isMms() ? ((MmsMessageRecord) messageRecord).getSlideDeck() : new SlideDeck(),
              recipient,
              threadId);
    }
  }

  @Override
  public void onMessageActionToolbarOpened() {
     searchViewItem.collapseActionView();
  }

  @Override
  public void onForwardClicked() {
    inputPanel.clearQuote();
  }

  @Override
  public void onAttachmentChanged() {
    handleSecurityChange(isSecureText, isDefaultSms);
    updateToggleButtonState();
    updateLinkPreviewState();
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
              identityDatabase.setVerified(identityRecord.getAddress(),
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
        Intent intent = new Intent(ConversationActivity.this, VerifyIdentityActivity.class);
        intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, unverifiedIdentities.get(0).getAddress());
        intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(unverifiedIdentities.get(0).getIdentityKey()));
        intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, false);

        startActivity(intent);
      } else {
        String[] unverifiedNames = new String[unverifiedIdentities.size()];

        for (int i=0;i<unverifiedIdentities.size();i++) {
          unverifiedNames[i] = Recipient.from(ConversationActivity.this, unverifiedIdentities.get(i).getAddress(), false).toShortString();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setTitle("No longer verified");
        builder.setItems(unverifiedNames, (dialog, which) -> {
          Intent intent = new Intent(ConversationActivity.this, VerifyIdentityActivity.class);
          intent.putExtra(VerifyIdentityActivity.ADDRESS_EXTRA, unverifiedIdentities.get(which).getAddress());
          intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(unverifiedIdentities.get(which).getIdentityKey()));
          intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, false);

          startActivity(intent);
        });
        builder.show();
      }
    }
  }

  private class QuoteRestorationTask extends AsyncTask<Void, Void, MessageRecord> {

    private final String                  serialized;
    private final SettableFuture<Boolean> future;

    QuoteRestorationTask(@NonNull String serialized, @NonNull SettableFuture<Boolean> future) {
      this.serialized = serialized;
      this.future     = future;
    }

    @Override
    protected MessageRecord doInBackground(Void... voids) {
      QuoteId quoteId = QuoteId.deserialize(serialized);

      if (quoteId != null) {
        return DatabaseFactory.getMmsSmsDatabase(getApplicationContext()).getMessageFor(quoteId.getId(), quoteId.getAuthor());
      }

      return null;
    }

    @Override
    protected void onPostExecute(MessageRecord messageRecord) {
      if (messageRecord != null) {
        handleReplyMessage(messageRecord);
        future.set(true);
      } else {
        Log.e(TAG, "Failed to restore a quote from a draft. No matching message record.");
        future.set(false);
      }
    }
  }

  // region Loki
  private void updateTitleTextView(Recipient recipient) {
    String userHexEncodedPublicKey = TextSecurePreferences.getLocalNumber(this);
    Set<DeviceLink> deviceLinks = DatabaseFactory.getLokiAPIDatabase(this).getDeviceLinks(userHexEncodedPublicKey);
    HashSet<String> userLinkedDeviceHexEncodedPublicKeys = new HashSet<>();
    for (DeviceLink deviceLink : deviceLinks) {
      userLinkedDeviceHexEncodedPublicKeys.add(deviceLink.getMasterHexEncodedPublicKey().toLowerCase());
      userLinkedDeviceHexEncodedPublicKeys.add(deviceLink.getSlaveHexEncodedPublicKey().toLowerCase());
    }
    userLinkedDeviceHexEncodedPublicKeys.add(userHexEncodedPublicKey.toLowerCase());
    if (recipient == null) {
      titleTextView.setText("Compose");
    } else if (userLinkedDeviceHexEncodedPublicKeys.contains(recipient.getAddress().toString().toLowerCase())) {
      titleTextView.setText("Note to Self");
    } else {
      boolean hasName = (recipient.getName() != null && !recipient.getName().isEmpty());
      titleTextView.setText(hasName ? recipient.getName() : recipient.getAddress().toString());
    }
  }

  private void updateSubtitleTextView() {
    muteIndicatorImageView.setVisibility(View.GONE);
    subtitleTextView.setVisibility(View.VISIBLE);
    if (messageStatus != null) {
      switch (messageStatus) {
        case "calculatingPoW": subtitleTextView.setText("Encrypting message"); break;
        case "contactingNetwork": subtitleTextView.setText("Tracing a path"); break;
        case "sendingMessage": subtitleTextView.setText("Sending message"); break;
        case "messageSent": subtitleTextView.setText("Message sent securely"); break;
        case "messageFailed": subtitleTextView.setText("Message failed to send"); break;
      }
    } else if (recipient.isMuted()) {
      muteIndicatorImageView.setVisibility(View.VISIBLE);
      subtitleTextView.setText("Muted until " + DateUtils.getFormattedDateTime(recipient.mutedUntil, "EEE, MMM d, yyyy HH:mm", Locale.getDefault()));
    } else if (recipient.isGroupRecipient() && recipient.getName() != null && !recipient.getName().equals("Session Updates") && !recipient.getName().equals("Loki News")) {
      LokiPublicChat publicChat = DatabaseFactory.getLokiThreadDatabase(this).getPublicChat(threadId);
      if (publicChat != null) {
        Integer userCount = DatabaseFactory.getLokiAPIDatabase(this).getUserCount(publicChat.getChannel(), publicChat.getServer());
        if (userCount == null) { userCount = 0; }
        if (userCount >= 200) {
          subtitleTextView.setText("200+ members");
        } else {
          subtitleTextView.setText(userCount + " members");
        }
      } else if (PublicKeyValidation.isValid(recipient.getAddress().toString())) {
        subtitleTextView.setText(recipient.getAddress().toString());
      } else {
        subtitleTextView.setVisibility(View.GONE);
      }
    } else if (PublicKeyValidation.isValid(recipient.getAddress().toString())) {
      subtitleTextView.setText(recipient.getAddress().toString());
    } else {
      subtitleTextView.setVisibility(View.GONE);
    }
    titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension((subtitleTextView.getVisibility() == View.GONE) ? R.dimen.very_large_font_size : R.dimen.large_font_size));
  }

  private void setMessageStatusProgressAnimatedIfPossible(int progress) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      messageStatusProgressBar.setProgress(progress, true);
    } else {
      messageStatusProgressBar.setProgress(progress);
    }
  }

  private void updateMessageStatusProgressBar() {
    if (messageStatus != null) {
        messageStatusProgressBar.setAlpha(1.0f);
      switch (messageStatus) {
        case "calculatingPoW": setMessageStatusProgressAnimatedIfPossible(25); break;
        case "contactingNetwork": setMessageStatusProgressAnimatedIfPossible(50); break;
        case "sendingMessage": setMessageStatusProgressAnimatedIfPossible(75); break;
        case "messageSent":
          setMessageStatusProgressAnimatedIfPossible(100);
          new Handler().postDelayed(() -> messageStatusProgressBar.animate().alpha(0).setDuration(250).start(), 250);
          new Handler().postDelayed(() -> messageStatusProgressBar.setProgress(0), 500);
          break;
        case "messageFailed":
          messageStatusProgressBar.animate().alpha(0).setDuration(250).start();
          new Handler().postDelayed(() -> messageStatusProgressBar.setProgress(0), 250);
          break;
      }
    }
  }

  private void handleMessageStatusChanged(String newMessageStatus, long timestamp) {
    if (timestamp == 0) { return; }
    updateForNewMessageStatusIfNeeded(newMessageStatus, timestamp);
    if (newMessageStatus.equals("messageFailed") || newMessageStatus.equals("messageSent")) {
      new Handler().postDelayed(() -> clearMessageStatusIfNeeded(timestamp), 1000);
    }
  }

  private int precedence(String messageStatus) {
    if (messageStatus != null) {
      switch (messageStatus) {
        case "calculatingPoW": return 0;
        case "contactingNetwork": return 1;
        case "sendingMessage": return 2;
        case "messageSent": return 3;
        case "messageFailed": return 4;
        default: return -1;
      }
    } else {
        return -1;
    }
  }

  private void updateForNewMessageStatusIfNeeded(String newMessageStatus, long timestamp) {
    if (!DatabaseFactory.getSmsDatabase(this).isOutgoingMessage(timestamp) && !DatabaseFactory.getMmsDatabase(this).isOutgoingMessage(timestamp)) { return; }
    if (precedence(newMessageStatus) > precedence(messageStatus)) {
      messageStatus = newMessageStatus;
      updateSubtitleTextView();
      updateMessageStatusProgressBar();
    }
  }

  private void clearMessageStatusIfNeeded(long timestamp) {
    if (!DatabaseFactory.getSmsDatabase(this).isOutgoingMessage(timestamp) && !DatabaseFactory.getMmsDatabase(this).isOutgoingMessage(timestamp)) { return; }
    messageStatus = null;
    updateSubtitleTextView();
    updateMessageStatusProgressBar();
  }

  @Override
  public void acceptFriendRequest(@NotNull MessageRecord friendRequest) {
    // Send the accept to the original friend request thread ID
    LokiMessageDatabase lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(this);
    long originalThreadID = lokiMessageDatabase.getOriginalThreadID(friendRequest.id);
    long threadID = originalThreadID < 0 ? this.threadId : originalThreadID;
    Recipient contact = DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID);
    Address address = contact.getAddress();
    String contactHexEncodedPublicKey = address.serialize();
    DatabaseFactory.getLokiThreadDatabase(this).setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.FRIENDS);
    lokiMessageDatabase.setFriendRequestStatus(friendRequest.id, LokiMessageFriendRequestStatus.REQUEST_ACCEPTED);
    DatabaseFactory.getRecipientDatabase(this).setProfileSharing(contact, true);
    MessageSender.sendBackgroundMessageToAllDevices(this, contactHexEncodedPublicKey);
    MessageSender.syncContact(this, address);
    updateInputPanel();
  }

  @Override
  public void rejectFriendRequest(@NotNull MessageRecord friendRequest) {
    LokiMessageDatabase lokiMessageDatabase = DatabaseFactory.getLokiMessageDatabase(this);
    long originalThreadID = lokiMessageDatabase.getOriginalThreadID(friendRequest.id);
    long threadID = originalThreadID < 0 ? this.threadId : originalThreadID;
    DatabaseFactory.getLokiThreadDatabase(this).setFriendRequestStatus(threadID, LokiThreadFriendRequestStatus.NONE);
    String contactID = DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID).getAddress().toString();
    DatabaseFactory.getLokiPreKeyBundleDatabase(this).removePreKeyBundle(contactID);
    updateInputPanel();
  }

  public boolean isNoteToSelf() {
    return TextSecurePreferences.getLocalNumber(this).equals(recipient.getAddress().serialize());
  }

  public void restoreSession() {
    if (recipient.isGroupRecipient()) { return; }
    LokiThreadDatabase lokiThreadDatabase = DatabaseFactory.getLokiThreadDatabase(this);
    SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(this);
    Set<String> devices = lokiThreadDatabase.getSessionRestoreDevices(threadId);
    for (String device : devices) { MessageSender.sendRestoreSessionMessage(this, device); }
    long messageID = smsDatabase.insertMessageOutbox(threadId, new OutgoingTextMessage(recipient,"", 0, 0), false, System.currentTimeMillis(), null);
    if (messageID > -1) {
      smsDatabase.markAsLokiSessionRestoreSent(messageID);
    }
    lokiThreadDatabase.removeAllSessionRestoreDevices(threadId);
    updateSessionRestoreBanner();
  }
  // endregion
}
