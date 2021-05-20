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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Browser;
import android.provider.Telephony;
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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.loader.app.LoaderManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.session.libsession.messaging.mentions.MentionsManager;
import org.session.libsession.messaging.messages.control.ExpirationTimerUpdate;
import org.session.libsession.messaging.messages.signal.OutgoingMediaMessage;
import org.session.libsession.messaging.messages.signal.OutgoingSecureMediaMessage;
import org.session.libsession.messaging.messages.signal.OutgoingTextMessage;
import org.session.libsession.messaging.messages.visible.OpenGroupInvitation;
import org.session.libsession.messaging.messages.visible.VisibleMessage;
import org.session.libsession.messaging.open_groups.OpenGroup;
import org.session.libsession.messaging.open_groups.OpenGroupV2;
import org.session.libsession.messaging.sending_receiving.MessageSender;
import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview;
import org.session.libsession.messaging.sending_receiving.quotes.QuoteModel;
import org.session.libsession.utilities.Contact;
import org.session.libsession.utilities.Address;
import org.session.libsession.utilities.DistributionTypes;
import org.session.libsession.utilities.GroupRecord;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.recipients.RecipientFormattingException;
import org.session.libsession.utilities.recipients.RecipientModifiedListener;
import org.session.libsession.utilities.ExpirationUtil;
import org.session.libsession.utilities.GroupUtil;
import org.session.libsession.utilities.MediaTypes;
import org.session.libsession.utilities.ServiceUtil;
import org.session.libsession.utilities.TextSecurePreferences;
import org.session.libsession.utilities.Util;
import org.session.libsession.utilities.ViewUtil;
import org.session.libsession.utilities.concurrent.AssertedSuccessListener;
import org.session.libsession.utilities.Stub;
import org.session.libsignal.exceptions.InvalidMessageException;
import org.session.libsignal.utilities.guava.Optional;
import org.session.libsession.messaging.mentions.Mention;
import org.session.libsignal.utilities.HexEncodingKt;
import org.session.libsignal.utilities.PublicKeyValidation;
import org.session.libsignal.utilities.ListenableFuture;
import org.session.libsignal.utilities.SettableFuture;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.ExpirationDialog;
import org.thoughtcrime.securesms.MediaOverviewActivity;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.ShortcutLauncherActivity;
import org.thoughtcrime.securesms.audio.AudioRecorder;
import org.thoughtcrime.securesms.audio.AudioSlidePlayer;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.components.AttachmentTypeSelector;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.components.ConversationSearchBottomBar;
import org.thoughtcrime.securesms.components.HidingLinearLayout;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.InputPanel;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase.Draft;
import org.thoughtcrime.securesms.database.DraftDatabase.Drafts;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.MmsSmsColumns.Types;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.giph.ui.GiphyActivity;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel;
import org.thoughtcrime.securesms.loki.activities.EditClosedGroupActivity;
import org.thoughtcrime.securesms.loki.activities.HomeActivity;
import org.thoughtcrime.securesms.loki.activities.SelectContactsActivity;
import org.thoughtcrime.securesms.loki.api.PublicChatInfoUpdateWorker;
import org.thoughtcrime.securesms.loki.database.LokiThreadDatabase;
import org.thoughtcrime.securesms.loki.database.LokiUserDatabase;
import org.thoughtcrime.securesms.loki.protocol.SessionMetaProtocol;
import org.thoughtcrime.securesms.loki.utilities.GeneralUtilitiesKt;
import org.thoughtcrime.securesms.loki.utilities.MentionManagerUtilities;
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities;
import org.thoughtcrime.securesms.loki.views.MentionCandidateSelectionView;
import org.thoughtcrime.securesms.loki.views.ProfilePictureView;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaSendActivity;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AttachmentManager.MediaType;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.QuoteId;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.search.model.MessageResult;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.PushCharacterCalculator;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import kotlin.Unit;
import network.loki.messenger.R;

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
               ConversationSearchBottomBar.EventListener
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

//  private static final int PICK_GALLERY        = 1;
  private static final int PICK_DOCUMENT       = 2;
  private static final int PICK_AUDIO          = 3;
  private static final int PICK_CONTACT        = 4;
//  private static final int GET_CONTACT_DETAILS = 5;
//  private static final int GROUP_EDIT          = 6;
  private static final int TAKE_PHOTO          = 7;
  private static final int ADD_CONTACT         = 8;
  private static final int PICK_LOCATION       = 9;
  private static final int PICK_GIF            = 10;
  private static final int SMS_DEFAULT         = 11;
  private static final int MEDIA_SENDER        = 12;
  private static final int INVITE_CONTACTS     = 124;

  private GlideRequests glideRequests;
  protected ComposeText                 composeText;
  private   AnimatingToggle             buttonToggle;
  private   ImageButton                 sendButton;
  private   ImageButton                 attachButton;
  private   ProfilePictureView          profilePictureView;
  private   TextView                    titleTextView;
  private   ConversationFragment        fragment;
  private   Button                      unblockButton;
  private   Button                      makeDefaultSmsButton;
  private   InputAwareLayout            container;
  private   TypingStatusTextWatcher     typingTextWatcher;
  private   MentionTextWatcher          mentionTextWatcher;
  private   ConversationSearchBottomBar searchNav;
  private   MenuItem                    searchViewItem;
  private   ProgressBar                 messageStatusProgressBar;
  private   ImageView                   muteIndicatorImageView;
  private   TextView                    subtitleTextView;
  private   View                        homeButtonContainer;

  private   AttachmentTypeSelector attachmentTypeSelector;
  private   AttachmentManager      attachmentManager;
  private   AudioRecorder          audioRecorder;
  private   Handler                audioHandler;
  private   Runnable               stopRecordingTask;
  private   Stub<MediaKeyboard>    emojiDrawerStub;
  protected HidingLinearLayout     quickAttachmentToggle;
  protected HidingLinearLayout     inlineAttachmentToggle;
  private   InputPanel             inputPanel;

  private LinkPreviewViewModel         linkPreviewViewModel;
  private ConversationSearchViewModel  searchViewModel;

  private Recipient  recipient;
  private long       threadId;
  private int        distributionType;
  private boolean    isDefaultSms            = false;
  private boolean    isSecurityInitialized   = false;
  private int        expandedKeyboardHeight  = 0;
  private int        collapsedKeyboardHeight = Integer.MAX_VALUE;
  private int        keyboardHeight          = 0;

  // Message status bar
  private ArrayList<BroadcastReceiver> broadcastReceivers = new ArrayList<>();
  private String                       messageStatus      = null;

  // Mentions
  private View                          mentionCandidateSelectionViewContainer;
  private MentionCandidateSelectionView mentionCandidateSelectionView;
  private int                           currentMentionStartIndex               = -1;
  private ArrayList<Mention>            mentions                               = new ArrayList<>();
  private String                        oldText                                = "";

  private final PushCharacterCalculator characterCalculator = new PushCharacterCalculator();

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    Log.i(TAG, "onCreate()");

    setContentView(R.layout.conversation_activity);

    fragment = initFragment(R.id.fragment_content, new ConversationFragment(), Locale.getDefault());

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

    initializeActionBar();
    initializeViews();
    initializeResources();
    initializeLinkPreviewObserver();
    initializeSearchObserver();
    initializeSecurity(false, isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
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
              String newText = oldText.substring(0, currentMentionStartIndex) + "@" + mentionCandidate.getDisplayName() + " ";
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

    MentionManagerUtilities.INSTANCE.populateUserPublicKeyCacheIfNeeded(threadId, this);

    OpenGroup publicChat = DatabaseFactory.getLokiThreadDatabase(this).getPublicChat(threadId);
    OpenGroupV2 openGroupV2 = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadId);
    if (publicChat != null) {
      // Request open group info update and handle the successful result in #onOpenGroupInfoUpdated().
      PublicChatInfoUpdateWorker.scheduleInstant(this, publicChat.getServer(), publicChat.getChannel());
    } else if (openGroupV2 != null) {
      PublicChatInfoUpdateWorker.scheduleInstant(this, openGroupV2.getServer(), openGroupV2.getRoom());
      if (openGroupV2.getRoom().equals("session") || openGroupV2.getRoom().equals("oxen")
          || openGroupV2.getRoom().equals("lokinet") || openGroupV2.getRoom().equals("crypto")) {
        View openGroupGuidelinesView = findViewById(R.id.open_group_guidelines_view);
        openGroupGuidelinesView.setVisibility(View.VISIBLE);
      }
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

      // Use 300dp if the keyboard wasn't opened yet.
      if (keyboardHeight == 0) {
        keyboardHeight = (int)(300f * getResources().getDisplayMetrics().density);
      }
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
    super.onNewIntent(intent);
    Log.i(TAG, "onNewIntent()");
    
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    if (!org.thoughtcrime.securesms.util.Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent()) {
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

    EventBus.getDefault().register(this);
    initializeEnabledCheck();
    composeText.setTransport();

    updateTitleTextView(recipient);
    updateProfilePicture();
    updateSubtitleTextView();
    updateInputUI(recipient);

    ApplicationContext.getInstance(this).messageNotifier.setVisibleThread(threadId);
    markThreadAsRead();

    inputPanel.setHint(getResources().getString(R.string.ConversationActivity_message));

    Log.i(TAG, "onResume() Finished: " + (System.currentTimeMillis() - getIntent().getLongExtra(TIMING_EXTRA, 0)));
  }

  @Override
  protected void onPause() {
    super.onPause();
    ApplicationContext.getInstance(this).messageNotifier.setVisibleThread(-1L);
    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_right);
    inputPanel.onPause();

    fragment.setLastSeen(System.currentTimeMillis());
    markLastSeen();
    AudioSlidePlayer.stopAll();
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
    composeText.setTransport();

    if (emojiDrawerStub.resolved() && container.getCurrentInput() == emojiDrawerStub.get()) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (recipient != null)               recipient.removeListener(this);
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
      initializeSecurity(true, isDefaultSms);
      break;
    case MEDIA_SENDER:
      long            expiresIn      = recipient.getExpireMessages() * 1000L;
      int             subscriptionId = -1;
      boolean         initiating     = threadId == -1;
      String          message        = data.getStringExtra(MediaSendActivity.EXTRA_MESSAGE);
      SlideDeck       slideDeck      = new SlideDeck();

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

      sendMediaMessage(message,
                       slideDeck,
                       inputPanel.getQuote().orNull(),
                       Optional.absent(),
                       initiating).addListener(new AssertedSuccessListener<Void>() {
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
    case INVITE_CONTACTS:
      if (data.getExtras() == null || !data.hasExtra(SelectContactsActivity.Companion.getSelectedContactsKey())) return;
        String[] selectedContacts = data.getExtras().getStringArray(SelectContactsActivity.Companion.getSelectedContactsKey());
        sendOpenGroupInvitations(selectedContacts);
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

    boolean isOpenGroupOrRSSFeed = recipient.getAddress().isOpenGroup();

    if (!isOpenGroupOrRSSFeed) {
      if (recipient.getExpireMessages() > 0) {
        inflater.inflate(R.menu.conversation_expiring_on, menu);

        final MenuItem  item       = menu.findItem(R.id.menu_expiring_messages);
        final View      actionView = MenuItemCompat.getActionView(item);
        final ImageView iconView   = actionView.findViewById(R.id.menu_badge_icon);
        final TextView  badgeView  = actionView.findViewById(R.id.expiration_badge);

        @ColorInt int color = GeneralUtilitiesKt.getColorWithID(getResources(), R.color.text, getTheme());
        iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        badgeView.setText(ExpirationUtil.getExpirationAbbreviatedDisplayValue(this, recipient.getExpireMessages()));
        actionView.setOnClickListener(v -> onOptionsItemSelected(item));
      } else {
        inflater.inflate(R.menu.conversation_expiring_off, menu);
      }
    }

    if (isSingleConversation()) {
      if (recipient.isBlocked()) {
        inflater.inflate(R.menu.conversation_unblock, menu);
      } else {
        inflater.inflate(R.menu.conversation_block, menu);
      }
      inflater.inflate(R.menu.conversation_copy_session_id, menu);
    } else if (isGroupConversation() && !isOpenGroupOrRSSFeed) {
//      inflater.inflate(R.menu.conversation_group_options, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      } else if (isActiveGroup()) {
        inflater.inflate(R.menu.conversation_push_group_options, menu);
      }
    } else if (isOpenGroupOrRSSFeed) {
      inflater.inflate(R.menu.conversation_invite_open_group, menu);
    }

    inflater.inflate(R.menu.conversation, menu);

//    if (isSingleConversation()) {
//      inflater.inflate(R.menu.conversation_secure, menu);
//    }

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
        updateInputUI(recipient);
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
//    case R.id.menu_call_secure:               handleDial(getRecipient(), true);         return true;
//    case R.id.menu_call_insecure:             handleDial(getRecipient(), false);        return true;
    case R.id.menu_unblock:                   handleUnblock();                                   return true;
    case R.id.menu_block:                     handleBlock();                                     return true;
    case R.id.menu_copy_session_id:           handleCopySessionID();                             return true;
    case R.id.menu_view_media:                handleViewMedia();                                 return true;
    case R.id.menu_add_shortcut:              handleAddShortcut();                               return true;
    case R.id.menu_search:                    handleSearch();                                    return true;
//    case R.id.menu_add_to_contacts:           handleAddToContacts();                             return true;
//    case R.id.menu_reset_secure_session:      handleResetSecureSession();                        return true;
//    case R.id.menu_group_recipients:          handleDisplayGroupRecipients();                    return true;
    case R.id.menu_distribution_broadcast:    handleDistributionBroadcastEnabled(item);          return true;
    case R.id.menu_distribution_conversation: handleDistributionConversationEnabled(item);       return true;
    case R.id.menu_edit_group:                handleEditPushGroup();                             return true;
    case R.id.menu_leave:                     handleLeavePushGroup();                            return true;
    case R.id.menu_mute_notifications:        handleMuteNotifications();                         return true;
    case R.id.menu_unmute_notifications:      handleUnmuteNotifications();                       return true;
//    case R.id.menu_conversation_settings:     handleConversationSettings();                      return true;
    case R.id.menu_expiring_messages_off:
    case R.id.menu_expiring_messages:         handleSelectMessageExpiration();                   return true;
    case R.id.menu_invite_to_open_group:      handleInviteToOpenGroup();                         return true;
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

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
          ExpirationTimerUpdate message = new ExpirationTimerUpdate(expirationTime);
          message.setRecipient(recipient.getAddress().serialize()); // we need the recipient in ExpiringMessageManager.insertOutgoingExpirationTimerMessage
          message.setSentTimestamp(System.currentTimeMillis());
          ExpiringMessageManager expiringMessageManager = ApplicationContext.getInstance(getApplicationContext()).getExpiringMessageManager();
          expiringMessageManager.setExpirationTimer(message);
          MessageSender.send(message, recipient.getAddress());

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

  private void handleBlock() {
    int titleRes = R.string.RecipientPreferenceActivity_block_this_contact_question;
    int bodyRes  = R.string.RecipientPreferenceActivity_you_will_no_longer_receive_messages_and_calls_from_this_contact;

    new AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(bodyRes)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.RecipientPreferenceActivity_block, (dialog, which) -> {
              new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                  DatabaseFactory.getRecipientDatabase(ConversationActivity.this)
                          .setBlocked(recipient, true);

                  Util.runOnMain(() -> finish());

                  return null;
                }
              }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }).show();
  }

  private void handleCopySessionID() {
    if (recipient.isGroupRecipient()) { return; }
    String sessionID = recipient.getAddress().toString();
    ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = ClipData.newPlainText("Session ID", sessionID);
    clipboard.setPrimaryClip(clip);
    Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
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

    GroupRecord group = DatabaseFactory.getGroupDatabase(this).getGroup(getRecipient().getAddress().toGroupString()).orNull();
    List<Address> admins = group.getAdmins();
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    String message = getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group);
    for (Address admin : admins) {
      if (admin.toString().equals(userPublicKey)) {
        message = "Because you are the creator of this group it will be deleted for everyone. This cannot be undone.";
      }
    }

    builder.setMessage(message);
    builder.setPositiveButton(R.string.yes, (dialog, which) -> {
      Recipient groupRecipient = getRecipient();
      String groupPublicKey;
      boolean isClosedGroup;
      try {
        groupPublicKey = HexEncodingKt.toHexString(GroupUtil.doubleDecodeGroupID(groupRecipient.getAddress().toString()));
        isClosedGroup = DatabaseFactory.getLokiAPIDatabase(this).isClosedGroup(groupPublicKey);
      } catch (IOException e) {
        groupPublicKey = null;
        isClosedGroup = false;
      }
      try {
        if (isClosedGroup) {
          MessageSender.explicitLeave(groupPublicKey, true);
          initializeEnabledCheck();
        } else {
          Toast.makeText(this, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
        }
      } catch (Exception e) {
        Toast.makeText(this, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleEditPushGroup() {
    Intent intent = new Intent(this, EditClosedGroupActivity.class);
    String groupID = this.recipient.getAddress().toGroupString();
    intent.putExtra(EditClosedGroupActivity.Companion.getGroupIDKey(), groupID);
    startActivity(intent);
  }

  private void handleInviteToOpenGroup() {
    Intent intent = new Intent(this, SelectContactsActivity.class);
    startActivityForResult(intent, INVITE_CONTACTS);
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = DistributionTypes.BROADCAST;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, DistributionTypes.BROADCAST);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = DistributionTypes.CONVERSATION;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, DistributionTypes.CONVERSATION);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleAddAttachment() {
    if (attachmentTypeSelector == null) {
      attachmentTypeSelector = new AttachmentTypeSelector(
              this,
              LoaderManager.getInstance(this),
              new AttachmentTypeListener(),
              keyboardHeight);
    }
    attachmentTypeSelector.show(this, attachButton);
  }

  private void handleSecurityChange(boolean isSecureText, boolean isDefaultSms) {
    Log.i(TAG, "handleSecurityChange(" + isSecureText + ", " + isDefaultSms + ")");
    if (isSecurityInitialized && isSecureText == true && isDefaultSms == this.isDefaultSms) {
      return;
    }

    this.isDefaultSms          = isDefaultSms;
    this.isSecurityInitialized = true;

    if (recipient == null || attachmentManager == null) { return; }

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

    supportInvalidateOptionsMenu();
    updateInputUI(recipient);
  }

  ///// Initializers

  private ListenableFuture<Boolean> initializeDraft() {
    final SettableFuture<Boolean> result = new SettableFuture<>();

    final String         draftText      = getIntent().getStringExtra(TEXT_EXTRA);
    final Uri            draftMedia     = getIntent().getData();
    final MediaType      draftMediaType = MediaType.from(getIntent().getType());
    final List<Media>    mediaList      = getIntent().getParcelableArrayListExtra(MEDIA_EXTRA);

    if (!Util.isEmpty(mediaList)) {
      Intent sendIntent = MediaSendActivity.buildEditorIntent(this, mediaList, recipient, draftText);
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
          switch (draft.getType()) {
            case Draft.TEXT:
              composeText.setText(draft.getValue());
              listener.onSuccess(true);
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
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient);

    return future;
  }

  private void initializeViews() {
    profilePictureView                     = findViewById(R.id.profilePictureView);
    titleTextView                          = findViewById(R.id.titleTextView);
    buttonToggle                           = ViewUtil.findById(this, R.id.button_toggle);
    sendButton                             = ViewUtil.findById(this, R.id.send_button);
    attachButton                           = ViewUtil.findById(this, R.id.attach_button);
    composeText                            = ViewUtil.findById(this, R.id.embedded_text_editor);
    emojiDrawerStub                        = ViewUtil.findStubById(this, R.id.emoji_drawer_stub);
    unblockButton                          = ViewUtil.findById(this, R.id.unblock_button);
    makeDefaultSmsButton                   = ViewUtil.findById(this, R.id.make_default_sms_button);
    container                              = ViewUtil.findById(this, R.id.layout_container);
    quickAttachmentToggle                  = ViewUtil.findById(this, R.id.quick_attachment_toggle);
    inlineAttachmentToggle                 = ViewUtil.findById(this, R.id.inline_attachment_container);
    inputPanel                             = ViewUtil.findById(this, R.id.bottom_panel);
    searchNav                              = ViewUtil.findById(this, R.id.conversation_search_nav);
    mentionCandidateSelectionViewContainer = ViewUtil.findById(this, R.id.mentionCandidateSelectionViewContainer);
    mentionCandidateSelectionView          = ViewUtil.findById(this, R.id.userSelectionView);
    messageStatusProgressBar               = ViewUtil.findById(this, R.id.messageStatusProgressBar);
    muteIndicatorImageView                 = ViewUtil.findById(this, R.id.muteIndicatorImageView);
    subtitleTextView                       = ViewUtil.findById(this, R.id.subtitleTextView);
    homeButtonContainer                    = ViewUtil.findById(this, R.id.homeButtonContainer);

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

    unblockButton.setOnClickListener(v -> handleUnblock());
    makeDefaultSmsButton.setOnClickListener(v -> handleMakeDefaultSms());

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

    homeButtonContainer.setOnClickListener(v -> onSupportNavigateUp());
  }

  protected void initializeActionBar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar == null) throw new AssertionError();

//    supportActionBar.setDisplayHomeAsUpEnabled(true);
    supportActionBar.setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    if (recipient != null) recipient.removeListener(this);

    Address address  = getIntent().getParcelableExtra(ADDRESS_EXTRA);
    if (address == null) { finish(); return; }
    recipient        = Recipient.from(this, address, true);
    threadId         = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, DistributionTypes.DEFAULT);
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

  @Override
  public void onSearchMoveUpPressed() {
    searchViewModel.onMoveUp();
  }

  @Override
  public void onSearchMoveDownPressed() {
    searchViewModel.onMoveDown();
  }

  @Override
  public void onModified(final Recipient recipient) {
    Log.i(TAG, "onModified(" + recipient.getAddress().serialize() + ")");
    Util.runOnMain(() -> {
      Log.i(TAG, "onModifiedRun(): " + recipient.getRegistered());
      updateTitleTextView(recipient);
      updateProfilePicture();
      updateSubtitleTextView();
      updateInputUI(recipient);
      initializeSecurity(true, isDefaultSms);

      if (searchViewItem == null || !searchViewItem.isActionViewExpanded()) {
        invalidateOptionsMenu();
      }
    });
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onOpenGroupInfoUpdated(OpenGroupUtilities.GroupInfoUpdatedEvent event) {
    OpenGroup publicChat = DatabaseFactory.getLokiThreadDatabase(this).getPublicChat(threadId);
    OpenGroupV2 openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadId);
    if (publicChat != null &&
            publicChat.getChannel() == event.getChannel() &&
            publicChat.getServer().equals(event.getUrl())) {
      this.updateSubtitleTextView();
    }
    if (openGroup != null &&
            openGroup.getRoom().equals(event.getRoom()) &&
            openGroup.getServer().equals(event.getUrl())) {
      this.updateSubtitleTextView();
    }
  }

  //////// Helper Methods

  private void addAttachment(int type) {
    linkPreviewViewModel.onUserCancel();

    Log.i(TAG, "Selected: " + type);
    switch (type) {
    case AttachmentTypeSelector.ADD_GALLERY:
      AttachmentManager.selectGallery(this, MEDIA_SENDER, recipient, composeText.getTextTrimmed()); break;
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
      boolean hasSeenGIFMetaDataWarning = TextSecurePreferences.hasSeenGIFMetaDataWarning(this);
      if (!hasSeenGIFMetaDataWarning) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Search GIFs?");
        builder.setMessage("You will not have full metadata protection when sending GIFs.");
        builder.setPositiveButton("OK", (dialog, which) -> {
          AttachmentManager.selectGif(this, PICK_GIF);
          dialog.dismiss();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.create().show();
        TextSecurePreferences.setHasSeenGIFMetaDataWarning(this);
      } else {
        AttachmentManager.selectGif(this, PICK_GIF);
      }
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

    if (MediaType.VCARD.equals(mediaType)) {
      return new SettableFuture<>(false);
    } else if (MediaType.IMAGE.equals(mediaType) || MediaType.GIF.equals(mediaType) || MediaType.VIDEO.equals(mediaType)) {
      Media media = new Media(uri, MediaUtil.getMimeType(this, uri), 0, width, height, 0, Optional.absent(), Optional.absent());
      startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient, composeText.getTextTrimmed()), MEDIA_SENDER);
      return new SettableFuture<>(false);
    } else {
      return attachmentManager.setMedia(glideRequests, uri, mediaType, MediaConstraints.getPushMediaConstraints(), width, height);
    }
  }

  private void addAttachmentContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactData contactData = contactDataList.getContactData(this, contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
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

    if (!org.thoughtcrime.securesms.util.Util.isEmpty(composeText)) {
      drafts.add(new Draft(Draft.TEXT, composeText.getTextTrimmed()));
    }

    for (Slide slide : attachmentManager.buildSlideDeck().getSlides()) {
      if      (slide.hasAudio() && slide.getUri() != null)    drafts.add(new Draft(Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo() && slide.getUri() != null)    drafts.add(new Draft(Draft.VIDEO, slide.getUri().toString()));
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
          if (threadId == -1) threadId = threadDatabase.getOrCreateThreadIdFor(getRecipient(), thisDistributionType);

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

  private void updateInputUI(Recipient recipient) {
    if (recipient.isGroupRecipient() && !isActiveGroup()) {
      unblockButton.setVisibility(View.GONE);
      inputPanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
    } else if (recipient.isBlocked()) {
      unblockButton.setVisibility(View.VISIBLE);
      inputPanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
    } else {
      inputPanel.setVisibility(View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
    }
  }

  private void initializeMediaKeyboardProviders(@NonNull MediaKeyboard mediaKeyboard) {
    boolean isSystemEmojiPreferred   = TextSecurePreferences.isSystemEmojiPreferred(this);
    if (!isSystemEmojiPreferred) {
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

    return Util.isOwnNumber(this, recipient.getAddress().serialize());
  }

  private boolean isGroupConversation() {
    return getRecipient() != null && getRecipient().isGroupRecipient();
  }

  private boolean isPushGroupConversation() {
    return getRecipient() != null && getRecipient().isPushGroupRecipient();
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
        result = result.substring(0, startIndex) + "@" + mention.getPublicKey() + result.substring(endIndex);
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
                                     .withMimeType(MediaTypes.LONG_TEXT)
                                     .withFileName(filename)
                                     .createForSingleSessionInMemory();

      textSlide = Optional.of(new TextSlide(this, textUri, filename, textData.length));
    }

    return new Pair<>(bodyText, textSlide);
  }

  private void markThreadAsRead() {
    Recipient recipient = this.recipient;
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        Context                 context    = ConversationActivity.this;
        List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(params[0], false);

        if (!SessionMetaProtocol.shouldSendReadReceipt(recipient.getAddress())) {
          for (MarkedMessageInfo messageInfo : messageIds) {
            MarkReadReceiver.scheduleDeletion(context, messageInfo.getExpirationInfo());
          }
        } else {
          MarkReadReceiver.process(context, messageIds);
        }
        ApplicationContext.getInstance(context).messageNotifier.updateNotification(context);
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
      ApplicationContext.getInstance(this).messageNotifier.setVisibleThread(threadId);
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();

    updateLinkPreviewState();
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
      boolean         initiating     = threadId == -1;
      boolean         needsSplit     = message.length() > characterCalculator.calculateCharacters(message).maxPrimaryMessageSize;
      boolean         isMediaMessage = attachmentManager.isAttachmentPresent()        ||
//                                       recipient.isGroupRecipient()                   ||
                                       inputPanel.getQuote().isPresent()              ||
                                       linkPreviewViewModel.hasLinkPreview()          ||
                                       LinkPreviewUtil.isValidMediaUrl(message) || // Loki - Send GIFs as media messages
                                       needsSplit;

      if (isMediaMessage) {
        sendMediaMessage(initiating);
      } else {
        sendTextMessage(initiating);
      }
    } catch (RecipientFormattingException ex) {
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Log.w(TAG, ex);
    }

    if (messageStatus == null && !isGroupConversation() && !(TextSecurePreferences.getLocalNumber(this).equals(recipient.getAddress().serialize()))) {
      messageStatus = "calculatingPoW";
      updateSubtitleTextView();
      updateMessageStatusProgressBar();
    }
  }

  private void sendMediaMessage(boolean initiating)
      throws InvalidMessageException
  {
    Log.i(TAG, "Sending media message...");
    sendMediaMessage(getMessage(), attachmentManager.buildSlideDeck(), inputPanel.getQuote().orNull(), linkPreviewViewModel.getActiveLinkPreview(), initiating);
  }

  private ListenableFuture<Void> sendMediaMessage(String body,
                                                  SlideDeck slideDeck,
                                                  QuoteModel quote,
                                                  Optional<LinkPreview> linkPreview,
                                                  final boolean initiating)
  {

    Pair<String, Optional<Slide>> splitMessage = getSplitMessage(body, characterCalculator.calculateCharacters(body).maxPrimaryMessageSize);
    body = splitMessage.first;

    if (splitMessage.second.isPresent()) {
      slideDeck.addSlide(splitMessage.second.get());
    }

    List<Attachment> attachments = slideDeck.asAttachments();

    VisibleMessage message = new VisibleMessage();
    message.setSentTimestamp(System.currentTimeMillis());
    message.setText(body);
    OutgoingMediaMessage outgoingMessageCandidate = OutgoingMediaMessage.from(message, recipient, attachments, quote, linkPreview.orNull());

    final SettableFuture<Void> future  = new SettableFuture<>();
    final Context              context = getApplicationContext();

    final OutgoingMediaMessage outgoingMessage;

    outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessageCandidate);
    ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);

    inputPanel.clearQuote();
    attachmentManager.clear(glideRequests, false);
    silentlySetComposeText("");

    final long id = fragment.stageOutgoingMessage(outgoingMessage);

    if (initiating) {
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, true);
    }

    try {
      long allocatedThreadId = getAllocatedThreadId(context);
      message.setId(DatabaseFactory.getMmsDatabase(context).insertMessageOutbox(outgoingMessage, allocatedThreadId, false, ()->fragment.releaseOutgoingMessage(id)));
      MessageSender.send(message, recipient.getAddress(), attachments, quote, linkPreview.orNull());
      sendComplete(allocatedThreadId);
    } catch (MmsException e) {
      Log.w(TAG, e);
      sendComplete(threadId);
    }
    future.set(null);

    return future;
  }

  private void sendTextMessage(final boolean initiating)
      throws InvalidMessageException
  {
    final Context context     = getApplicationContext();
    final String  messageBody = getMessage();

    VisibleMessage message = new VisibleMessage();
    message.setSentTimestamp(System.currentTimeMillis());
    message.setText(messageBody);
    OutgoingTextMessage outgoingTextMessage = OutgoingTextMessage.from(message, recipient);
    ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);

    silentlySetComposeText("");
    final long id = fragment.stageOutgoingMessage(outgoingTextMessage);

    if (initiating) {
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient, true);
    }

    long allocatedThreadId = getAllocatedThreadId(context);
    message.setId(DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(allocatedThreadId, outgoingTextMessage, false, message.getSentTimestamp(), ()->fragment.releaseOutgoingMessage(id)));
    MessageSender.send(message, recipient.getAddress());

    sendComplete(allocatedThreadId);
  }

  private void sendOpenGroupInvitations(String[] contactIDs) {
    final Context context = getApplicationContext();
    OpenGroupV2 openGroup = DatabaseFactory.getLokiThreadDatabase(context).getOpenGroupChat(threadId);
    for (String contactID : contactIDs) {
      Recipient recipient = Recipient.from(context, Address.fromSerialized(contactID), true);
      VisibleMessage message = new VisibleMessage();
      message.setSentTimestamp(System.currentTimeMillis());
      OpenGroupInvitation openGroupInvitationMessage = new OpenGroupInvitation();
      openGroupInvitationMessage.setName(openGroup.getName());
      openGroupInvitationMessage.setUrl(openGroup.getJoinURL());
      message.setOpenGroupInvitation(openGroupInvitationMessage);
      OutgoingTextMessage outgoingTextMessage = OutgoingTextMessage.fromOpenGroupInvitation(openGroupInvitationMessage, recipient, message.getSentTimestamp());
      DatabaseFactory.getSmsDatabase(context).insertMessageOutbox(-1, outgoingTextMessage, message.getSentTimestamp());
      MessageSender.send(message, recipient.getAddress());
    }
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

      if (!attachmentManager.isAttachmentPresent() && !linkPreviewViewModel.hasLinkPreview()) {
        inlineAttachmentToggle.show();
      } else {
        inlineAttachmentToggle.hide();
      }
    }
  }

  private void updateLinkPreviewState() {
    if (TextSecurePreferences.isLinkPreviewsEnabled(this) && !attachmentManager.isAttachmentPresent()) {
      linkPreviewViewModel.onEnabled();
      linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed(), composeText.getSelectionStart(), composeText.getSelectionEnd());
    } else {
      linkPreviewViewModel.onUserCancel();
    }
  }

  @Override
  public void onRecorderPermissionRequired() {
    Permissions.with(this)
               .request(Manifest.permission.RECORD_AUDIO)
               .withRationaleDialog(getString(R.string.ConversationActivity_to_send_audio_messages_allow_signal_access_to_your_microphone), R.drawable.ic_baseline_mic_48)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_requires_the_microphone_permission_in_order_to_send_audio_messages))
               .execute();
  }

  @Override
  public void onRecorderStarted() {
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(20);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    audioRecorder.startRecording();

    audioHandler = new Handler();
    stopRecordingTask = () -> inputPanel.onRecordReleased();
    audioHandler.postDelayed(stopRecordingTask, 60000);
  }

  @Override
  public void onRecorderLocked() {
    updateToggleButtonState();
  }

  @Override
  public void onRecorderFinished() {
    if (audioHandler != null && stopRecordingTask != null) {
      audioHandler.removeCallbacks(stopRecordingTask);
    }
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(20);

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final @NonNull Pair<Uri, Long> result) {
        int        subscriptionId = -1;
        long       expiresIn      = recipient.getExpireMessages() * 1000L;
        boolean    initiating     = threadId == -1;
        AudioSlide audioSlide     = new AudioSlide(ConversationActivity.this, result.first, result.second, MediaTypes.AUDIO_AAC, true);
        SlideDeck  slideDeck      = new SlideDeck();
        slideDeck.addSlide(audioSlide);

        sendMediaMessage("", slideDeck, inputPanel.getQuote().orNull(), Optional.absent(), initiating).addListener(new AssertedSuccessListener<Void>() {
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
    if (audioHandler != null && stopRecordingTask != null) {
      audioHandler.removeCallbacks(stopRecordingTask);
    }
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
      initializeMediaKeyboardProviders(emojiDrawerStub.get());

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

  private void silentlySetComposeText(String text) {
    typingTextWatcher.setEnabled(false);
    composeText.setText(text);
    if (text.isEmpty()) { resetMentions(); }
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
      startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient, composeText.getTextTrimmed()), MEDIA_SENDER);
    }
  }

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      Permissions.with(ConversationActivity.this)
                 .request(Manifest.permission.CAMERA)
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_baseline_photo_camera_48)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> {
                   composeText.clearFocus();
                   startActivityForResult(MediaSendActivity.buildCameraIntent(ConversationActivity.this, recipient), MEDIA_SENDER);
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
      if (composeText.getTextTrimmed().length() == 0 || beforeLength == 0) {
        composeText.postDelayed(ConversationActivity.this::updateToggleButtonState, 50);
      }
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
      if (enabled && threadId > 0) {
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
          List<Mention> mentionCandidates = MentionsManager.shared.getMentionCandidates("", threadId);
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
            List<Mention> mentionCandidates = MentionsManager.shared.getMentionCandidates(query, threadId);
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
    if (recipient.isGroupRecipient() && !isActiveGroup()) { return; }

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
    handleSecurityChange(true, isDefaultSms);
    updateToggleButtonState();
    updateLinkPreviewState();
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
  private long getAllocatedThreadId(Context context) {
    long allocatedThreadId;
    if (threadId == -1) {
      allocatedThreadId = DatabaseFactory.getThreadDatabase(context).getOrCreateThreadIdFor(recipient);
    } else {
      allocatedThreadId = threadId;
    }
    return allocatedThreadId;
  }

  private void updateTitleTextView(Recipient recipient) {
    String userPublicKey = TextSecurePreferences.getLocalNumber(this);
    if (recipient == null) {
      titleTextView.setText("Compose");
    } else if (recipient.getAddress().toString().toLowerCase().equals(userPublicKey)) {
      titleTextView.setText(getResources().getString(R.string.note_to_self));
    } else {
      boolean hasName = (recipient.getName() != null && !recipient.getName().isEmpty());
      titleTextView.setText(hasName ? recipient.getName() : recipient.getAddress().toString());
    }
  }

  private void updateProfilePicture() {
    try {
      profilePictureView.glide = GlideApp.with(this);
      profilePictureView.update(recipient, threadId);
    } catch (Exception exception) {
      // Do nothing
    }
  }

  private void updateSubtitleTextView() {
    muteIndicatorImageView.setVisibility(View.GONE);
    subtitleTextView.setVisibility(View.VISIBLE);
    if (recipient.isMuted()) {
      muteIndicatorImageView.setVisibility(View.VISIBLE);
      subtitleTextView.setText("Muted until " + DateUtils.getFormattedDateTime(recipient.mutedUntil, "EEE, MMM d, yyyy HH:mm", Locale.getDefault()));
    } else if (recipient.isGroupRecipient() && recipient.getName() != null && !recipient.getName().equals("Session Updates") && !recipient.getName().equals("Loki News")) {
      OpenGroup publicChat = DatabaseFactory.getLokiThreadDatabase(this).getPublicChat(threadId);
      OpenGroupV2 openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadId);
      if (publicChat != null) {
        Integer userCount = DatabaseFactory.getLokiAPIDatabase(this).getUserCount(publicChat.getChannel(), publicChat.getServer());
        if (userCount == null) { userCount = 0; }
        subtitleTextView.setText(userCount + " members");
      } else if (openGroup != null) {
        Integer userCount = DatabaseFactory.getLokiAPIDatabase(this).getUserCount(openGroup.getRoom(),openGroup.getServer());
        if (userCount == null) { userCount = 0; }
        subtitleTextView.setText(userCount + " members");
      } else if (PublicKeyValidation.isValid(recipient.getAddress().toString())) {
        subtitleTextView.setText(recipient.getAddress().toString());
      } else {
        subtitleTextView.setVisibility(View.GONE);
      }
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
    if (timestamp == 0 || (TextSecurePreferences.getLocalNumber(this).equals(recipient.getAddress().serialize())) ) { return; }
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
  // endregion
}
