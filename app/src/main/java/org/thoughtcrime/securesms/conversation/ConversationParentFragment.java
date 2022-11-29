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
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Browser;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.SimpleColorFilter;
import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.PendingIntentFlags;
import org.signal.core.util.StringUtil;
import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.util.Pair;
import org.thoughtcrime.securesms.BlockUnblockDialog;
import org.thoughtcrime.securesms.GroupMembersDialog;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.PromptMmsActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.ShortcutLauncherActivity;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.TombstoneAttachment;
import org.thoughtcrime.securesms.audio.AudioRecorder;
import org.thoughtcrime.securesms.badges.gifts.thanks.GiftThanksSheet;
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
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel;
import org.thoughtcrime.securesms.components.identity.UnverifiedBannerView;
import org.thoughtcrime.securesms.components.location.SignalPlace;
import org.thoughtcrime.securesms.components.mention.MentionAnnotation;
import org.thoughtcrime.securesms.components.reminder.BubbleOptOutReminder;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.GroupsV1MigrationSuggestionsReminder;
import org.thoughtcrime.securesms.components.reminder.PendingGroupJoinRequestsReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity;
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlayerView;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactShareEditActivity;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.conversation.ConversationGroupViewModel.GroupActiveState;
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import org.thoughtcrime.securesms.conversation.drafts.DraftViewModel;
import org.thoughtcrime.securesms.conversation.ui.groupcall.GroupCallViewModel;
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery;
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryChangedListener;
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryResultsController;
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryViewModel;
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerViewModel;
import org.thoughtcrime.securesms.crypto.ReentrantSessionLock;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.database.DraftTable.Draft;
import org.thoughtcrime.securesms.database.DraftTable.Drafts;
import org.thoughtcrime.securesms.database.GroupTable;
import org.thoughtcrime.securesms.database.IdentityTable.VerifiedStatus;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.RecipientTable.RegisteredState;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.GroupCallPeekEvent;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.exporter.flow.SmsExportActivity;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason;
import org.thoughtcrime.securesms.groups.ui.GroupErrors;
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.ManagePendingAndRequestingMembersActivity;
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInitiationBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationSuggestionsDialog;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.invites.InviteReminderModel;
import org.thoughtcrime.securesms.invites.InviteReminderRepository;
import org.thoughtcrime.securesms.jobs.ForceUpdateGroupV2Job;
import org.thoughtcrime.securesms.jobs.GroupV1MigrationJob;
import org.thoughtcrime.securesms.jobs.GroupV2UpdateSelfProfileKeyJob;
import org.thoughtcrime.securesms.jobs.RequestGroupV2InfoJob;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.keyboard.KeyboardPage;
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel;
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment;
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment;
import org.thoughtcrime.securesms.keyboard.gif.GifKeyboardPageFragment;
import org.thoughtcrime.securesms.keyboard.sticker.StickerKeyboardPageFragment;
import org.thoughtcrime.securesms.keyboard.sticker.StickerSearchDialogFragment;
import org.thoughtcrime.securesms.keyvalue.PaymentsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.keyvalue.SmsExportPhase;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel;
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder;
import org.thoughtcrime.securesms.maps.PlacePickerActivity;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult;
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity;
import org.thoughtcrime.securesms.messagedetails.MessageDetailsFragment;
import org.thoughtcrime.securesms.messagerequests.MessageRequestState;
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel;
import org.thoughtcrime.securesms.messagerequests.MessageRequestsBottomView;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.QuoteId;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.SlideFactory.MediaType;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.notifications.v2.ConversationId;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewBannerView;
import org.thoughtcrime.securesms.profiles.spoofing.ReviewCardDialogFragment;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment;
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.recipients.ui.disappearingmessages.RecipientDisappearingMessagesActivity;
import org.thoughtcrime.securesms.registration.RegistrationNavigationActivity;
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet;
import org.thoughtcrime.securesms.search.MessageResult;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerEventListener;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.stickers.StickerManagementActivity;
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent;
import org.thoughtcrime.securesms.stickers.StickerSearchRepository;
import org.thoughtcrime.securesms.stories.StoryViewerArgs;
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.BubbleUtil;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.ContextUtil;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.DrawableUtil;
import org.thoughtcrime.securesms.util.FullscreenHelper;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.Material3OnScrollHelper;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.MessageUtil;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
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
import org.thoughtcrime.securesms.util.views.Stub;
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperDimLevelUtil;
import org.whispersystems.signalservice.api.SignalSessionLock;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static org.thoughtcrime.securesms.database.GroupTable.GroupRecord;

/**
 * Fragment for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
@SuppressLint("StaticFieldLeak")
public class ConversationParentFragment extends Fragment
    implements ConversationFragment.ConversationFragmentListener,
               AttachmentManager.AttachmentListener,
               OnKeyboardShownListener,
               InputPanel.Listener,
               InputPanel.MediaListener,
               ComposeText.CursorPositionChangedListener,
               ConversationSearchBottomBar.EventListener,
               StickerEventListener,
               AttachmentKeyboard.Callback,
               ConversationReactionOverlay.OnReactionSelectedListener,
               ReactWithAnyEmojiBottomSheetDialogFragment.Callback,
               SafetyNumberBottomSheet.Callbacks,
               ReactionsBottomSheetDialogFragment.Callback,
               MediaKeyboard.MediaKeyboardListener,
               EmojiEventListener,
               GifKeyboardPageFragment.Host,
               EmojiKeyboardPageFragment.Callback,
               EmojiSearchFragment.Callback,
               StickerKeyboardPageFragment.Callback,
               Material3OnScrollHelperBinder,
               MessageDetailsFragment.Callback
{

  private static final int SHORTCUT_ICON_SIZE = Build.VERSION.SDK_INT >= 26 ? ViewUtil.dpToPx(72) : ViewUtil.dpToPx(48 + 16 * 2);

  private static final String TAG = Log.tag(ConversationParentFragment.class);

  private static final String STATE_REACT_WITH_ANY_PAGE = "STATE_REACT_WITH_ANY_PAGE";
  private static final String STATE_IS_SEARCH_REQUESTED = "STATE_IS_SEARCH_REQUESTED";

  private static final String ARG_INTENT_DATA = "arg.intent.data";

  private static final int REQUEST_CODE_SETTINGS = 1000;

  private static final int PICK_GALLERY        = 1;
  private static final int PICK_DOCUMENT       = 2;
  private static final int PICK_AUDIO          = 3;
  private static final int PICK_CONTACT        = 4;
  private static final int GET_CONTACT_DETAILS = 5;
  private static final int GROUP_EDIT          = 6;
  private static final int TAKE_PHOTO          = 7;
  private static final int ADD_CONTACT         = 8;
  private static final int PICK_LOCATION       = 9;
  public static  final int PICK_GIF            = 10;
  private static final int SMS_DEFAULT         = 11;
  private static final int MEDIA_SENDER        = 12;

  private static final int     REQUEST_CODE_PIN_SHORTCUT = 902;
  private static final String  ACTION_PINNED_SHORTCUT    = "action_pinned_shortcut";

  private   GlideRequests                glideRequests;
  protected ComposeText                  composeText;
  private   AnimatingToggle              buttonToggle;
  private   SendButton                   sendButton;
  private   ImageButton                  attachButton;
  protected ConversationTitleView        titleView;
  private   TextView                     charactersLeft;
  private   ConversationFragment         fragment;
  private   Button                       unblockButton;
  private   Stub<View>                   smsExportStub;
  private   Button                       registerButton;
  private   InputAwareLayout             container;
  protected Stub<ReminderView>           reminderView;
  private   Stub<UnverifiedBannerView>   unverifiedBannerView;
  private   Stub<ReviewBannerView>       reviewBanner;
  private   ComposeTextWatcher           typingTextWatcher;
  private   ConversationSearchBottomBar  searchNav;
  private   MenuItem                     searchViewItem;
  private   MessageRequestsBottomView    messageRequestBottomView;
  private   ConversationReactionDelegate reactionDelegate;
  private   Stub<FrameLayout>            voiceNotePlayerViewStub;
  private   View                         navigationBarBackground;

  private   AttachmentManager        attachmentManager;
  private   AudioRecorder            audioRecorder;
  private   BroadcastReceiver        securityUpdateReceiver;
  private   Stub<MediaKeyboard>      emojiDrawerStub;
  private   Stub<AttachmentKeyboard> attachmentKeyboardStub;
  protected HidingLinearLayout       quickAttachmentToggle;
  protected HidingLinearLayout       inlineAttachmentToggle;
  private   InputPanel               inputPanel;
  private   View                     noLongerMemberBanner;
  private   Stub<TextView>           cannotSendInAnnouncementGroupBanner;
  private   View                     requestingMemberBanner;
  private   View                     cancelJoinRequest;
  private   Stub<View>               releaseChannelUnmute;
  private   Stub<View>               mentionsSuggestions;
  private   MaterialButton           joinGroupCallButton;
  private   boolean                  callingTooltipShown;
  private   ImageView                wallpaper;
  private   View                     wallpaperDim;
  private   Toolbar                  toolbar;
  private   View                     toolbarBackground;
  private   BroadcastReceiver        pinnedShortcutReceiver;

  private LinkPreviewViewModel         linkPreviewViewModel;
  private ConversationSearchViewModel  searchViewModel;
  private ConversationStickerViewModel stickerViewModel;
  private ConversationViewModel        viewModel;
  private InviteReminderModel          inviteReminderModel;
  private ConversationGroupViewModel   groupViewModel;
  private MentionsPickerViewModel      mentionsViewModel;
  private InlineQueryViewModel         inlineQueryViewModel;
  private GroupCallViewModel           groupCallViewModel;
  private VoiceRecorderWakeLock        voiceRecorderWakeLock;
  private DraftViewModel               draftViewModel;
  private VoiceNoteMediaController     voiceNoteMediaController;
  private VoiceNotePlayerView          voiceNotePlayerView;
  private Material3OnScrollHelper      material3OnScrollHelper;
  private InlineQueryResultsController inlineQueryResultsController;
  private OnBackPressedCallback        backPressedCallback;

  private LiveRecipient recipient;
  private long          threadId;
  private int           distributionType;
  private int           reactWithAnyEmojiStartPage = -1;
  private boolean       isSearchRequested          = false;

  private final LifecycleDisposable disposables            = new LifecycleDisposable();
  private final Debouncer           optionsMenuDebouncer   = new Debouncer(50);
  private final Debouncer           textDraftSaveDebouncer = new Debouncer(500);

  private IdentityRecordList   identityRecords = new IdentityRecordList(Collections.emptyList());
  private Callback             callback;
  private RecentEmojiPageModel recentEmojis;

  public static ConversationParentFragment create(Intent intent) {
    ConversationParentFragment fragment = new ConversationParentFragment();
    Bundle                     bundle   = new Bundle();

    bundle.putAll(ConversationIntents.createParentFragmentArguments(intent));
    fragment.setArguments(bundle);

    return fragment;
  }

  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.conversation_activity, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    disposables.bindTo(getViewLifecycleOwner());

    if (requireActivity() instanceof Callback) {
      callback = (Callback) requireActivity();
    } else if (getParentFragment() instanceof Callback) {
      callback = (Callback) getParentFragment();
    } else {
      throw new ClassCastException("Cannot cast activity or parent fragment into a Callback object");
    }

    // TODO [alex] LargeScreenSupport -- This check will no longer be valid / necessary
    if (ConversationIntents.isInvalid(requireArguments())) {
      Log.w(TAG, "[onCreate] Missing recipientId!");
      // TODO [greyson] Navigation
      startActivity(MainActivity.clearTop(requireContext()));
      requireActivity().finish();
      return;
    }

    voiceNoteMediaController = new VoiceNoteMediaController(requireActivity());
    voiceRecorderWakeLock    = new VoiceRecorderWakeLock(requireActivity());

    // TODO [alex] LargeScreenSupport -- Should be removed once we move to multi-pane layout.
    new FullscreenHelper(requireActivity()).showSystemUI();

    ConversationIntents.Args args = ConversationIntents.Args.from(requireArguments());
    if (savedInstanceState == null && args.getGiftBadge() != null) {
      GiftThanksSheet.show(getChildFragmentManager(), args.getRecipientId(), args.getGiftBadge());
    }

    isSearchRequested = args.isWithSearchOpen();

    reportShortcutLaunch(args.getRecipientId());

    requireActivity().getWindow().getDecorView().setBackgroundResource(R.color.signal_background_primary);

    fragment = (ConversationFragment) getChildFragmentManager().findFragmentById(R.id.fragment_content);
    if (fragment == null) {
      fragment = new ConversationFragment();
      getChildFragmentManager().beginTransaction()
                               .replace(R.id.fragment_content, fragment)
                               .commitNow();
    }

    initializeReceivers();
    initializeViews(view);
    updateWallpaper(args.getWallpaper());
    initializeResources(args);
    initializeLinkPreviewObserver();
    initializeSearchObserver();
    initializeStickerObserver();
    initializeViewModel(args);
    initializeGroupViewModel();
    initializeMentionsViewModel();
    initializeGroupCallViewModel();
    initializeDraftViewModel();
    initializeEnabledCheck();
    initializePendingRequestsBanner();
    initializeGroupV1MigrationsBanners();

    Flowable<ConversationSecurityInfo> observableSecurityInfo = viewModel.getConversationSecurityInfo(args.getRecipientId());

    disposables.add(observableSecurityInfo.subscribe(this::handleSecurityChange));
    disposables.add(observableSecurityInfo.firstOrError().subscribe(unused -> onInitialSecurityConfigurationLoaded()));

    initializeInsightObserver();
    initializeActionBar();

    disposables.add(viewModel.getStoryViewState().subscribe(titleView::setStoryRingFromState));

    backPressedCallback = new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        onBackPressed();
      }
    };
    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);

    if (isSearchRequested && savedInstanceState == null) {
      onCreateOptionsMenu(toolbar.getMenu(), requireActivity().getMenuInflater());
    }

    sendButton.post(() -> sendButton.triggerSelectedChangedEvent());
  }

  @Override
  public void onResume() {
    super.onResume();

    // TODO [alex] LargeScreenSupport -- Remove these lines.
    WindowUtil.setLightNavigationBarFromTheme(requireActivity());
    WindowUtil.setLightStatusBarFromTheme(requireActivity());

    EventBus.getDefault().register(this);
    backPressedCallback.setEnabled(true);
    viewModel.checkIfMmsIsEnabled();
    initializeIdentityRecords();
    composeText.setMessageSendType(sendButton.getSelectedSendType());

    Recipient recipientSnapshot = recipient.get();

    titleView.setTitle(glideRequests, recipientSnapshot);
    setBlockedUserState(recipientSnapshot, viewModel.getConversationStateSnapshot().getSecurityInfo());
    calculateCharactersRemaining();

    if (recipientSnapshot.getGroupId().isPresent() && recipientSnapshot.getGroupId().get().isV2() && !recipientSnapshot.isBlocked()) {
      GroupId.V2 groupId = recipientSnapshot.getGroupId().get().requireV2();

      ApplicationDependencies.getJobManager()
                             .startChain(new RequestGroupV2InfoJob(groupId))
                             .then(GroupV2UpdateSelfProfileKeyJob.withoutLimits(groupId))
                             .enqueue();

      ForceUpdateGroupV2Job.enqueueIfNecessary(groupId);

      if (viewModel.getArgs().isFirstTimeInSelfCreatedGroup()) {
        groupViewModel.inviteFriendsOneTimeIfJustSelfInGroup(getChildFragmentManager(), groupId);
      }
    }

    if (groupCallViewModel != null) {
      groupCallViewModel.peekGroupCall();
    }

    setVisibleThread(threadId);
    ConversationUtil.refreshRecipientShortcuts();

    if (SignalStore.rateLimit().needsRecaptcha()) {
      RecaptchaProofBottomSheetFragment.show(getChildFragmentManager());
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (!isInBubble()) {
      ApplicationDependencies.getMessageNotifier().clearVisibleThread();
    }

    if (requireActivity().isFinishing()) requireActivity().overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_end);
    inputPanel.onPause();

    fragment.setLastSeen(System.currentTimeMillis());
    markLastSeen();
    EventBus.getDefault().unregister(this);
    material3OnScrollHelper.setColorImmediate();
  }

  @Override
  public void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.i(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    composeText.setMessageSendType(sendButton.getSelectedSendType());

    if (emojiDrawerStub.resolved() && container.getCurrentInput() == emojiDrawerStub.get()) {
      container.hideAttachedInput(true);
    }

    if (reactionDelegate.isShowing()) {
     reactionDelegate.hide();
    }

    if (inlineQueryResultsController != null) {
      inlineQueryResultsController.onOrientationChange(newConfig.orientation == ORIENTATION_LANDSCAPE);
    }
  }

  @Override
  public void onDestroy() {
    if (securityUpdateReceiver != null)  requireActivity().unregisterReceiver(securityUpdateReceiver);
    if (pinnedShortcutReceiver != null)  requireActivity().unregisterReceiver(pinnedShortcutReceiver);
    super.onDestroy();
  }

  // TODO [alex] LargeScreenSupport -- Pipe in events from activity
  public boolean dispatchTouchEvent(MotionEvent ev) {
    return reactionDelegate.applyTouchEvent(ev);
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.i(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if ((data == null && reqCode != TAKE_PHOTO && reqCode != SMS_DEFAULT) ||
        (resultCode != Activity.RESULT_OK && reqCode != SMS_DEFAULT))
    {
      updateLinkPreviewState();
      SignalStore.settings().setDefaultSms(Util.isDefaultSmsProvider(requireContext()));
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
      if (viewModel.isPushAvailable() && !isSmsForced()) {
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
      NotificationChannels.getInstance().updateContactChannelName(recipientSnapshot);
      setBlockedUserState(recipientSnapshot, viewModel.getConversationStateSnapshot().getSecurityInfo());
      invalidateOptionsMenu();
      break;
    case TAKE_PHOTO:
      handleImageFromDeviceCameraApp();
      break;
    case ADD_CONTACT:
      SimpleTask.run(() -> {
        try {
          ContactDiscovery.refresh(requireContext(), recipient.get(), false);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh user after adding to contacts.");
        }
        return null;
      }, nothing -> onRecipientChanged(recipient.get()));
      break;
    case PICK_LOCATION:
      SignalPlace place = new SignalPlace(PlacePickerActivity.addressFromData(data));
      attachmentManager.setLocation(place, getCurrentMediaConstraints());
      draftViewModel.setLocationDraft(place);
      break;
    case SMS_DEFAULT:
      viewModel.updateSecurityInfo();
      break;
    case PICK_GIF:
    case MEDIA_SENDER:
      MediaSendActivityResult result = MediaSendActivityResult.fromData(data);

      if (!Objects.equals(result.getRecipientId(), recipient.getId())) {
        Log.w(TAG, "Result's recipientId did not match ours! Result: " + result.getRecipientId() + ", Activity: " + recipient.getId());
        Toast.makeText(requireContext(), R.string.ConversationActivity_error_sending_media, Toast.LENGTH_SHORT).show();
        return;
      }

      sendButton.setSendType(result.getMessageSendType());

      if (result.isPushPreUpload()) {
        sendMediaMessage(result);
        return;
      }

      long       expiresIn      = TimeUnit.SECONDS.toMillis(recipient.get().getExpiresInSeconds());
      boolean    initiating     = threadId == -1;
      QuoteModel quote          = result.isViewOnce() ? null : inputPanel.getQuote().orElse(null);
      SlideDeck  slideDeck      = new SlideDeck();
      List<Mention> mentions    = new ArrayList<>(result.getMentions());

      for (Media mediaItem : result.getNonUploadedMedia()) {
        if (MediaUtil.isVideoType(mediaItem.getMimeType())) {
          slideDeck.addSlide(new VideoSlide(requireContext(), mediaItem.getUri(), mediaItem.getSize(), mediaItem.isVideoGif(), mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.getCaption().orElse(null), mediaItem.getTransformProperties().orElse(null)));
        } else if (MediaUtil.isGif(mediaItem.getMimeType())) {
          slideDeck.addSlide(new GifSlide(requireContext(), mediaItem.getUri(), mediaItem.getSize(), mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.isBorderless(), mediaItem.getCaption().orElse(null)));
        } else if (MediaUtil.isImageType(mediaItem.getMimeType())) {
          slideDeck.addSlide(new ImageSlide(requireContext(), mediaItem.getUri(), mediaItem.getMimeType(), mediaItem.getSize(), mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.isBorderless(), mediaItem.getCaption().orElse(null), null, mediaItem.getTransformProperties().orElse(null)));
        } else {
          Log.w(TAG, "Asked to send an unexpected mimeType: '" + mediaItem.getMimeType() + "'. Skipping.");
        }
      }

      final Context context = requireContext().getApplicationContext();

      sendMediaMessage(result.getRecipientId(),
                       result.getMessageSendType(),
                       result.getBody(),
                       slideDeck,
                       quote,
                       Collections.emptyList(),
                       Collections.emptyList(),
                       mentions,
                       expiresIn,
                       result.isViewOnce(),
                       initiating,
                       true,
                       null).addListener(new AssertedSuccessListener<Void>() {
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
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(STATE_REACT_WITH_ANY_PAGE, reactWithAnyEmojiStartPage);
    outState.putBoolean(STATE_IS_SEARCH_REQUESTED, isSearchRequested);
  }

  @Override
  public void onViewStateRestored(Bundle savedInstanceState) {
    super.onViewStateRestored(savedInstanceState);

    if (savedInstanceState != null) {
      reactWithAnyEmojiStartPage = savedInstanceState.getInt(STATE_REACT_WITH_ANY_PAGE, -1);
      isSearchRequested          = savedInstanceState.getBoolean(STATE_IS_SEARCH_REQUESTED, false);
    }
  }

  private void onInitialSecurityConfigurationLoaded() {
    Log.d(TAG, "Initial security configuration loaded.");
    if (getContext() == null) {
      Log.w(TAG, "Fragment has become detached from context. Ignoring configuration call.");
      return;
    }

    initializeProfiles();
    initializeGv1Migration();

    Log.d(TAG, "Initializing draft from initial security configuration load...");
    initializeDraft(viewModel.getArgs()).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean loadedDraft) {
        Log.d(TAG, "Initial security configuration loaded.");
        if (getContext() == null) {
          Log.w(TAG, "Fragment has become detached from context. Ignoring draft load.");
          return;
        }

        if (loadedDraft != null && loadedDraft) {
          Log.i(TAG, "Finished loading draft");
          ThreadUtil.runOnMain(() -> {
            if (fragment != null && fragment.isResumed()) {
              fragment.moveToLastSeen();
            } else {
              Log.w(TAG, "Wanted to move to the last seen position, but the fragment was in an invalid state");
            }
          });
        }

        composeText.addTextChangedListener(typingTextWatcher);
        composeText.setSelection(composeText.length(), composeText.length());
      }
    });
  }

  private void setVisibleThread(long threadId) {
    if (!isInBubble()) {
      // TODO [alex] LargeScreenSupport -- Inform MainActivityViewModel that the conversation was opened.
      ApplicationDependencies.getMessageNotifier().setVisibleThread(ConversationId.forConversation(threadId));
    }
  }

  private void reportShortcutLaunch(@NonNull RecipientId recipientId) {
    ShortcutManagerCompat.reportShortcutUsed(requireContext(), ConversationUtil.getShortcutId(recipientId));
  }

  private void handleImageFromDeviceCameraApp() {
    if (attachmentManager.getCaptureUri() == null) {
      Log.w(TAG, "No image available.");
      return;
    }

    try {
      Uri mediaUri = BlobProvider.getInstance()
                                 .forData(requireContext().getContentResolver().openInputStream(attachmentManager.getCaptureUri()), 0L)
                                 .withMimeType(MediaUtil.IMAGE_JPEG)
                                 .createForSingleSessionOnDisk(requireContext());

      requireContext().getContentResolver().delete(attachmentManager.getCaptureUri(), null, null);

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
      Toast.makeText(requireContext(), R.string.ConversationActivity_there_is_no_app_available_to_handle_this_link_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    menu.clear();

    GroupActiveState groupActiveState = groupViewModel.getGroupActiveState().getValue();
    boolean isActiveGroup             = groupActiveState != null && groupActiveState.isActiveGroup();
    boolean isActiveV2Group           = groupActiveState != null && groupActiveState.isActiveV2Group();
    boolean isInActiveGroup           = groupActiveState != null && !groupActiveState.isActiveGroup();

    if (isInMessageRequest() && recipient != null && !recipient.get().isBlocked()) {
      if (isActiveGroup) {
        inflater.inflate(R.menu.conversation_message_requests_group, menu);
      }

      super.onCreateOptionsMenu(menu, inflater);
    }

    if (viewModel.isPushAvailable()) {
      if (recipient.get().getExpiresInSeconds() > 0) {
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
      if (viewModel.isPushAvailable())   {
        inflater.inflate(R.menu.conversation_callable_secure, menu);
      } else if (!recipient.get().isReleaseNotes() && SignalStore.misc().getSmsExportPhase().allowSmsFeatures()) {
        inflater.inflate(R.menu.conversation_callable_insecure, menu);
      }
    } else if (isGroupConversation()) {
      if (isActiveV2Group && Build.VERSION.SDK_INT > 19) {
        inflater.inflate(R.menu.conversation_callable_groupv2, menu);
        if (groupCallViewModel != null && Boolean.TRUE.equals(groupCallViewModel.hasActiveGroupCall().getValue())) {
          hideMenuItem(menu, R.id.menu_video_secure);
        }
        showGroupCallingTooltip();
      }

      inflater.inflate(R.menu.conversation_group_options, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == ThreadTable.DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      }

      inflater.inflate(R.menu.conversation_active_group_options, menu);
    }

    inflater.inflate(R.menu.conversation, menu);

    if (isInMessageRequest() && !recipient.get().isBlocked()) {
      hideMenuItem(menu, R.id.menu_conversation_settings);
    }

    if (isSingleConversation() && !viewModel.isPushAvailable() && !recipient.get().isReleaseNotes()) {
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (recipient != null && recipient.get().isMuted()) inflater.inflate(R.menu.conversation_muted, menu);
    else                                                inflater.inflate(R.menu.conversation_unmuted, menu);

    if (isSingleConversation() && getRecipient().getContactUri() == null && !recipient.get().isReleaseNotes() && !recipient.get().isSelf()) {
      inflater.inflate(R.menu.conversation_add_to_contacts, menu);
    }

    if (recipient != null && recipient.get().isSelf()) {
      if (viewModel.isPushAvailable()) {
        hideMenuItem(menu, R.id.menu_call_secure);
        hideMenuItem(menu, R.id.menu_video_secure);
      } else {
        hideMenuItem(menu, R.id.menu_call_insecure);
      }

      hideMenuItem(menu, R.id.menu_mute_notifications);
    }

    if (recipient != null && recipient.get().isBlocked()) {
      if (viewModel.isPushAvailable()) {
        hideMenuItem(menu, R.id.menu_call_secure);
        hideMenuItem(menu, R.id.menu_video_secure);
        hideMenuItem(menu, R.id.menu_expiring_messages);
        hideMenuItem(menu, R.id.menu_expiring_messages_off);
      } else {
        hideMenuItem(menu, R.id.menu_call_insecure);
      }

      hideMenuItem(menu, R.id.menu_mute_notifications);
    }

    if (recipient != null && recipient.get().isReleaseNotes()) {
      hideMenuItem(menu, R.id.menu_add_shortcut);
    }

    hideMenuItem(menu, R.id.menu_group_recipients);

    if (isActiveV2Group) {
      hideMenuItem(menu, R.id.menu_mute_notifications);
      hideMenuItem(menu, R.id.menu_conversation_settings);
    } else if (isGroupConversation()) {
      hideMenuItem(menu, R.id.menu_conversation_settings);
    }

    hideMenuItem(menu, R.id.menu_create_bubble);
    disposables.add(viewModel.canShowAsBubble().subscribe(canShowAsBubble -> {
      MenuItem item = menu.findItem(R.id.menu_create_bubble);

      if (item != null) {
        item.setVisible(canShowAsBubble && !isInBubble());
      }
    }));

    if (threadId == -1L) {
      hideMenuItem(menu, R.id.menu_view_media);
    }

    searchViewItem = menu.findItem(R.id.menu_search);

    SearchView                     searchView    = (SearchView) searchViewItem.getActionView();
    SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        searchViewModel.onQueryUpdated(query, threadId, true);
        searchNav.showLoading();
        viewModel.setSearchQuery(query);
        return true;
      }

      @Override
      public boolean onQueryTextChange(String query) {
        searchViewModel.onQueryUpdated(query, threadId, false);
        searchNav.showLoading();
        viewModel.setSearchQuery(query);
        return true;
      }
    };

    searchViewItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem item) {
        searchView.setOnQueryTextListener(queryListener);
        isSearchRequested = true;
        searchViewModel.onSearchOpened();
        searchNav.setVisibility(View.VISIBLE);
        searchNav.setData(0, 0);
        inputPanel.setHideForSearch(true);

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
        isSearchRequested = false;
        searchViewModel.onSearchClosed();
        searchNav.setVisibility(View.GONE);
        inputPanel.setHideForSearch(false);
        viewModel.setSearchQuery(null);
        setBlockedUserState(recipient.get(), viewModel.getConversationStateSnapshot().getSecurityInfo());
        invalidateOptionsMenu();
        return true;
      }
    });

    searchView.setMaxWidth(Integer.MAX_VALUE);

    if (isSearchRequested) {
      if (searchViewItem.expandActionView()) {
          searchViewModel.onSearchOpened();
        }
    }

    super.onCreateOptionsMenu(menu, inflater);

    int toolbarTextAndIconColor = getResources().getColor(wallpaper.getDrawable() != null ? R.color.signal_colorNeutralInverse : R.color.signal_colorOnSurface);
    setToolbarActionItemTint(toolbar, toolbarTextAndIconColor);
  }

  public void invalidateOptionsMenu() {
    if (!isSearchRequested && getActivity() != null) {
      optionsMenuDebouncer.publish(() -> {
        if (getActivity() != null) {
          onCreateOptionsMenu(toolbar.getMenu(), requireActivity().getMenuInflater());
        }
      });
    }
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);
    int itemId = item.getItemId();

    if (itemId == R.id.menu_call_secure) {
      handleDial(getRecipient(), true);
    } else if (itemId == R.id.menu_video_secure) {
      handleVideo(getRecipient());
    } else if (itemId == R.id.menu_call_insecure) {
      handleDial(getRecipient(), false);
    } else if (itemId == R.id.menu_view_media) {
      handleViewMedia();
    } else if (itemId == R.id.menu_add_shortcut) {
      handleAddShortcut();
    } else if (itemId == R.id.menu_search) {
      handleSearch();
    } else if (itemId == R.id.menu_add_to_contacts) {
      handleAddToContacts();
    } else if (itemId == R.id.menu_group_recipients) {
      handleDisplayGroupRecipients();
    } else if (itemId == R.id.menu_distribution_broadcast) {
      handleDistributionBroadcastEnabled(item);
    } else if (itemId == R.id.menu_distribution_conversation) {
      handleDistributionConversationEnabled(item);
    } else if (itemId == R.id.menu_group_settings) {
      handleManageGroup();
    } else if (itemId == R.id.menu_leave) {
      handleLeavePushGroup();
    } else if (itemId == R.id.menu_invite) {
      handleInviteLink();
    } else if (itemId == R.id.menu_mute_notifications) {
      handleMuteNotifications();
    } else if (itemId == R.id.menu_unmute_notifications) {
      handleUnmuteNotifications();
    } else if (itemId == R.id.menu_conversation_settings) {
      handleConversationSettings();
    } else if (itemId == R.id.menu_expiring_messages_off || itemId == R.id.menu_expiring_messages) {
      handleSelectMessageExpiration();
    } else if (itemId == R.id.menu_create_bubble) {
      handleCreateBubble();
    } else if (itemId == android.R.id.home) {
      requireActivity().finish();
    } else {
      return false;
    }

    return true;
  }

  public void onBackPressed() {
    Log.d(TAG, "onBackPressed()");
    if (reactionDelegate.isShowing()) {
      reactionDelegate.hide();
    } else if (container.isInputOpen()) {
      container.hideCurrentInput(composeText);
      navigationBarBackground.setVisibility(View.GONE);
    } else if (isSearchRequested) {
      if (searchViewItem != null) {
        searchViewItem.collapseActionView();
      }
    } else if (isInBubble()) {
      backPressedCallback.setEnabled(false);
      requireActivity().onBackPressed();
    } else {
      requireActivity().finish();
    }
  }

  @Override
  public void onKeyboardShown() {
    inputPanel.onKeyboardShown();
    if (emojiDrawerStub.resolved() && emojiDrawerStub.get().isShowing() && !emojiDrawerStub.get().isEmojiSearchMode()) {
      emojiDrawerStub.get().hide(true);
    }
    if (attachmentKeyboardStub.resolved() && attachmentKeyboardStub.get().isShowing()) {
      navigationBarBackground.setVisibility(View.GONE);
      attachmentKeyboardStub.get().hide(true);
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {
    updateReminders();
  }

  @SuppressLint("MissingSuperCall")
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onAttachmentMediaClicked(@NonNull Media media) {
    linkPreviewViewModel.onUserCancel();
    startActivityForResult(MediaSelectionActivity.editor(requireActivity(), sendButton.getSelectedSendType(), Collections.singletonList(media), recipient.getId(), composeText.getTextTrimmed()), MEDIA_SENDER);
    container.hideCurrentInput(composeText);
  }

  @Override
  public void onAttachmentSelectorClicked(@NonNull AttachmentKeyboardButton button) {
    switch (button) {
      case GALLERY:
        AttachmentManager.selectGallery(this, MEDIA_SENDER, recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedSendType(), inputPanel.getQuote().isPresent());
        break;
      case FILE:
        AttachmentManager.selectDocument(this, PICK_DOCUMENT);
        break;
      case CONTACT:
        AttachmentManager.selectContactInfo(this, PICK_CONTACT);
        break;
      case LOCATION:
        AttachmentManager.selectLocation(this, PICK_LOCATION, getSendButtonColor(sendButton.getSelectedSendType()));
        break;
      case PAYMENT:
        AttachmentManager.selectPayment(this, recipient.get());
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
    if (isPushGroupConversation() && !isActiveGroup()) {
      return;
    }

    startActivity(RecipientDisappearingMessagesActivity.forRecipient(requireContext(), recipient.getId()));
  }

  private void handleMuteNotifications() {
    MuteDialog.show(requireActivity(), until -> {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
         SignalDatabase.recipients().setMuted(recipient.getId(), until);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });
  }

  private void handleStoryRingClick() {
    startActivity(StoryViewerActivity.createIntent(
                  requireContext(),
                  new StoryViewerArgs.Builder(recipient.getId(), recipient.get().shouldHideStory())
                                     .isFromQuote(true)
                                     .build()));
  }

  private void handleConversationSettings() {
    if (isGroupConversation()) {
      handleManageGroup();
      return;
    }

    if (isInMessageRequest() && !recipient.get().isBlocked()) return;

    Intent intent = ConversationSettingsActivity.forRecipient(requireContext(), recipient.getId());
    Bundle bundle = ConversationSettingsActivity.createTransitionBundle(requireActivity(), titleView.findViewById(R.id.contact_photo_image), toolbar);

    ActivityCompat.startActivity(requireActivity(), intent, bundle);
  }

  private void handleUnmuteNotifications() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        SignalDatabase.recipients().setMuted(recipient.getId(), 0);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleUnblock() {
    final Context context = requireContext().getApplicationContext();
    BlockUnblockDialog.showUnblockFor(requireContext(), getLifecycle(), recipient.get(), () -> {
      SignalExecutors.BOUNDED.execute(() -> {
        RecipientUtil.unblock(recipient.get());
      });
    });
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private void handleMakeDefaultSms() {
    startActivityForResult(SmsUtil.getSmsRoleIntent(requireContext()), SMS_DEFAULT);
  }

  private void handleRegisterForSignal() {
    startActivity(RegistrationNavigationActivity.newIntentForReRegistration(requireContext()));
  }

  private void handleInviteLink() {
    String inviteText = getString(R.string.ConversationActivity_lets_switch_to_signal, getString(R.string.install_url));

    if (viewModel.isDefaultSmsApplication() && SignalStore.misc().getSmsExportPhase().isSmsSupported()) {
      composeText.appendInvite(inviteText);
    } else if (recipient.get().hasSmsAddress()) {
      Intent intent = new Intent(Intent.ACTION_SENDTO);
      intent.setData(Uri.parse("smsto:" + recipient.get().requireSmsAddress()));
      intent.putExtra("sms_body", inviteText);
      intent.putExtra(Intent.EXTRA_TEXT, inviteText);
      startActivity(intent);
    } else {
      Intent sendIntent = new Intent();
      sendIntent.setAction(Intent.ACTION_SEND);
      sendIntent.putExtra(Intent.EXTRA_TEXT, inviteText);
      sendIntent.setType("text/plain");
      if (sendIntent.resolveActivity(requireContext().getPackageManager()) != null) {
        startActivity(Intent.createChooser(sendIntent, getString(R.string.InviteActivity_invite_to_signal)));
      } else {
        Toast.makeText(requireContext(), R.string.InviteActivity_no_app_to_share_to, Toast.LENGTH_LONG).show();
      }
    }
  }

  private void handleViewMedia() {
    startActivity(MediaOverviewActivity.forThread(requireContext(), threadId));
  }

  private void handleAddShortcut() {
    Log.i(TAG, "Creating home screen shortcut for recipient " + recipient.get().getId());

    final Context context = requireContext().getApplicationContext();
    final Recipient recipient = this.recipient.get();

    if (pinnedShortcutReceiver == null) {
      pinnedShortcutReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
          Toast.makeText(context, context.getString(R.string.ConversationActivity_added_to_home_screen), Toast.LENGTH_LONG).show();
        }
      };
      requireActivity().registerReceiver(pinnedShortcutReceiver, new IntentFilter(ACTION_PINNED_SHORTCUT));
    }

    GlideApp.with(this)
            .asBitmap()
            .load(recipient.getContactPhoto())
            .error(recipient.getFallbackContactPhoto().asDrawable(context, recipient.getAvatarColor(), false))
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

    BubbleUtil.displayAsBubble(requireContext(), args.getRecipientId(), args.getThreadId());
    requireActivity().finish();
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

    Intent callbackIntent                = new Intent(ACTION_PINNED_SHORTCUT);
    PendingIntent shortcutPinnedCallback = PendingIntent.getBroadcast(context, REQUEST_CODE_PIN_SHORTCUT, callbackIntent, PendingIntentFlags.mutable());

    ShortcutManagerCompat.requestPinShortcut(context, shortcutInfoCompat, shortcutPinnedCallback.getIntentSender());

    bitmap.recycle();
  }

  private void handleSearch() {
    searchViewModel.onSearchOpened();
  }

  private void handleLeavePushGroup() {
    if (getRecipient() == null) {
      Toast.makeText(requireContext(), getString(R.string.ConversationActivity_invalid_recipient),
                     Toast.LENGTH_LONG).show();
      return;
    }

    LeaveGroupDialog.handleLeavePushGroup(requireActivity(), getRecipient().requireGroupId().requirePush(), () -> requireActivity().finish());
  }

  private void handleManageGroup() {
    Intent intent = ConversationSettingsActivity.forGroup(requireContext(), recipient.get().requireGroupId());
    Bundle bundle = ConversationSettingsActivity.createTransitionBundle(requireContext(), titleView.findViewById(R.id.contact_photo_image), toolbar);

    ActivityCompat.startActivity(requireContext(), intent, bundle);
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = ThreadTable.DistributionTypes.BROADCAST;
    draftViewModel.setDistributionType(distributionType);
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          SignalDatabase.threads().setDistributionType(threadId, ThreadTable.DistributionTypes.BROADCAST);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = ThreadTable.DistributionTypes.CONVERSATION;
    draftViewModel.setDistributionType(distributionType);
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          SignalDatabase.threads().setDistributionType(threadId, ThreadTable.DistributionTypes.CONVERSATION);
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

    if (recipient.isPushV2Group() && groupCallViewModel.hasActiveGroupCall().getValue() == Boolean.FALSE && groupViewModel.isNonAdminInAnnouncementGroup()) {
      new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ConversationActivity_cant_start_group_call)
                                          .setMessage(R.string.ConversationActivity_only_admins_of_this_group_can_start_a_call)
                                          .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                                          .show();
    } else {
      CommunicationActions.startVideoCall(this, recipient);
    }
  }

  private void handleDisplayGroupRecipients() {
    new GroupMembersDialog(requireActivity(), getRecipient()).display();
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
      ContactsContract.QuickContact.showQuickContact(requireContext(), titleView, recipient.get().getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
    } else {
      handleAddToContacts();
    }

    return true;
  }

  private void handleAddAttachment() {
    if (viewModel.getConversationStateSnapshot().isMmsEnabled() || viewModel.isPushAvailable()) {
      viewModel.getRecentMedia().removeObservers(this);

      if (attachmentKeyboardStub.resolved() && container.isInputOpen() && container.getCurrentInput() == attachmentKeyboardStub.get()) {
        container.showSoftkey(composeText);
      } else {
        viewModel.getRecentMedia().observe(getViewLifecycleOwner(), media -> attachmentKeyboardStub.get().onMediaChanged(media));
        attachmentKeyboardStub.get().setCallback(this);
        attachmentKeyboardStub.get().setWallpaperEnabled(recipient.get().hasWallpaper());

        updatePaymentsAvailable();

        container.show(composeText, attachmentKeyboardStub.get());
        navigationBarBackground.setVisibility(View.VISIBLE);

        viewModel.onAttachmentKeyboardOpen();
      }
    } else {
      handleManualMmsRequired();
    }
  }

  private void updatePaymentsAvailable() {
    if (!attachmentKeyboardStub.resolved()) {
      return;
    }

    PaymentsValues paymentsValues = SignalStore.paymentsValues();

    if (paymentsValues.getPaymentsAvailability().isSendAllowed() &&
        !recipient.get().isSelf()                                &&
        !recipient.get().isGroup()                               &&
        recipient.get().isRegistered()                           &&
        !recipient.get().isForceSmsSelection())
    {
      attachmentKeyboardStub.get().filterAttachmentKeyboardButtons(null);
    } else {
      attachmentKeyboardStub.get().filterAttachmentKeyboardButtons(btn -> btn != AttachmentKeyboardButton.PAYMENT);
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(requireContext(), R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Bundle extras = requireArguments();
    Intent intent = new Intent(requireContext(), PromptMmsActivity.class);

    intent.putExtras(extras);
    startActivity(intent);
  }

  private void handleRecentSafetyNumberChange() {
    List<IdentityRecord> records = identityRecords.getUnverifiedRecords();
    records.addAll(identityRecords.getUntrustedRecords());
    SafetyNumberBottomSheet
        .forIdentityRecordsAndDestination(
            records,
            new ContactSearchKey.RecipientSearchKey.KnownRecipient(recipient.getId())
        )
        .show(getChildFragmentManager());
  }

  @Override
  public void onMessageResentAfterSafetyNumberChangeInBottomSheet() {
    Log.d(TAG, "onMessageResentAfterSafetyNumberChange");
    initializeIdentityRecords().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) { }
    });
  }

  @Override
  public void onCanceled() { }

  private void handleSecurityChange(@NonNull ConversationSecurityInfo conversationSecurityInfo) {
    Log.i(TAG, "handleSecurityChange(" + conversationSecurityInfo + ")");

    boolean isPushAvailable = conversationSecurityInfo.isPushAvailable();
    boolean isMediaMessage  = recipient.get().isMmsGroup() || attachmentManager.isAttachmentPresent();

    sendButton.resetAvailableTransports(isMediaMessage);

    boolean smsEnabled = true;

    if (recipient.get().isPushGroup() || (!recipient.get().isMmsGroup() && !recipient.get().hasSmsAddress())) {
      sendButton.disableTransportType(MessageSendType.TransportType.SMS);
      smsEnabled = false;
    }

    if (!isPushAvailable && !isPushGroupConversation() && !recipient.get().isServiceIdOnly() && !recipient.get().isReleaseNotes() && smsEnabled) {
      sendButton.disableTransportType(MessageSendType.TransportType.SIGNAL);
    }

    if (!recipient.get().isPushGroup() && recipient.get().isForceSmsSelection() && smsEnabled) {
      sendButton.setDefaultTransport(MessageSendType.TransportType.SMS);
      viewModel.insertSmsExportUpdateEvent(recipient.get());
    } else {
      if (isPushAvailable || isPushGroupConversation() || recipient.get().isServiceIdOnly() || recipient.get().isReleaseNotes() || !smsEnabled) {
        sendButton.setDefaultTransport(MessageSendType.TransportType.SIGNAL);
      } else {
        sendButton.setDefaultTransport(MessageSendType.TransportType.SMS);
        viewModel.insertSmsExportUpdateEvent(recipient.get());
      }
    }

    calculateCharactersRemaining();
    invalidateOptionsMenu();
    setBlockedUserState(recipient.get(), conversationSecurityInfo);
    onSecurityUpdated();
  }

  ///// Initializers

  private ListenableFuture<Boolean> initializeDraft(@NonNull ConversationIntents.Args args) {
    final SettableFuture<Boolean> result = new SettableFuture<>();

    long    sharedDataTimestamp   = args.getShareDataTimestamp();
    long    lastTimestamp         = callback.getShareDataTimestamp();
    boolean hasProcessedShareData = sharedDataTimestamp > 0 && sharedDataTimestamp <= lastTimestamp;

    Log.d(TAG, "Shared this data at " + sharedDataTimestamp + " and last processed share data at " + lastTimestamp);
    if (hasProcessedShareData) {
      Log.d(TAG, "Already processed this share data. Skipping.");
      result.set(false);
      return result;
    } else {
      Log.d(TAG, "Have not processed this share data. Proceeding.");
      callback.setShareDataTimestamp(sharedDataTimestamp);
    }

    final CharSequence   draftText        = args.getDraftText();
    final Uri            draftMedia       = ConversationIntents.getIntentData(requireArguments());
    final String         draftContentType = ConversationIntents.getIntentType(requireArguments());
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
      Log.d(TAG, "Handling borderless draft media with content type " + draftContentType);
      SimpleTask.run(getLifecycle(),
                     () -> getKeyboardImageDetails(draftMedia),
                     details -> sendKeyboardImage(draftMedia, draftContentType, details));
      return new SettableFuture<>(false);
    }

    if (!Util.isEmpty(mediaList)) {
      Log.d(TAG, "Handling shared Media.");
      Intent sendIntent = MediaSelectionActivity.editor(requireContext(), sendButton.getSelectedSendType(), mediaList, recipient.getId(), draftText);
      startActivityForResult(sendIntent, MEDIA_SENDER);
      return new SettableFuture<>(false);
    }

    if (draftText != null) {
      Log.d(TAG, "Handling shared text");
      composeText.setText("");
      composeText.append(draftText);
      result.set(true);
    }

    if (draftMedia != null && draftMediaType != null) {
      Log.d(TAG, "Handling shared Data.");
      return setMedia(draftMedia, draftMediaType);
    }

    if (draftText == null && (draftMedia == null || ConversationIntents.isBubbleIntentUri(draftMedia)) && draftMediaType == null) {
      Log.d(TAG, "Initializing draft from database");
      return initializeDraftFromDatabase();
    } else {
      updateToggleButtonState();
      result.set(false);
    }

    return result;
  }

  private void initializeEnabledCheck() {
    groupViewModel.getSelfMemberLevel().observe(getViewLifecycleOwner(), selfMembership -> {
      boolean canSendMessages;
      boolean leftGroup;
      boolean canCancelRequest;

      if (selfMembership == null) {
        leftGroup        = false;
        canSendMessages  = true;
        canCancelRequest = false;
        if (cannotSendInAnnouncementGroupBanner.resolved()) {
          cannotSendInAnnouncementGroupBanner.get().setVisibility(View.GONE);
        }
      } else {
        switch (selfMembership.getMemberLevel()) {
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

        if (!leftGroup && !canCancelRequest && selfMembership.isAnnouncementGroup() && selfMembership.getMemberLevel() != GroupTable.MemberLevel.ADMINISTRATOR) {
          canSendMessages = false;
          cannotSendInAnnouncementGroupBanner.get().setVisibility(View.VISIBLE);
          cannotSendInAnnouncementGroupBanner.get().setMovementMethod(LinkMovementMethod.getInstance());
          cannotSendInAnnouncementGroupBanner.get().setText(SpanUtil.clickSubstring(requireContext(), R.string.ConversationActivity_only_s_can_send_messages, R.string.ConversationActivity_admins, v -> {
            ShowAdminsBottomSheetDialog.show(getChildFragmentManager(), getRecipient().requireGroupId().requireV2());
          }));
        } else if (cannotSendInAnnouncementGroupBanner.resolved()) {
          cannotSendInAnnouncementGroupBanner.get().setVisibility(View.GONE);
        }
      }

      if (messageRequestBottomView.getVisibility() == View.GONE) {
        noLongerMemberBanner.setVisibility(leftGroup ? View.VISIBLE : View.GONE);
      }

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
            Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(error), Toast.LENGTH_SHORT).show();
          }
        }.toWorkerCallback()));
      }

      inputPanel.setHideForGroupState(!canSendMessages);
      inputPanel.setEnabled(canSendMessages);
      sendButton.setEnabled(canSendMessages);
      attachButton.setEnabled(canSendMessages);
    });
  }

  private void initializePendingRequestsBanner() {
    groupViewModel.getActionableRequestingMembers()
                  .observe(getViewLifecycleOwner(), actionablePendingGroupRequests -> updateReminders());
  }

  private void initializeGroupV1MigrationsBanners() {
    groupViewModel.getGroupV1MigrationSuggestions()
                  .observe(getViewLifecycleOwner(), s -> updateReminders());
  }

  private ListenableFuture<Boolean> initializeDraftFromDatabase() {
    SettableFuture<Boolean> future = new SettableFuture<>();

    Disposable disposable = draftViewModel
        .loadDrafts(threadId)
        .subscribe(databaseDrafts -> {
          Drafts       drafts      = databaseDrafts.getDrafts();
          CharSequence updatedText = databaseDrafts.getUpdatedText();

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
                case Draft.VOICE_NOTE:
                  listener.onSuccess(true);
                  break;
              }
            } catch (IOException e) {
              Log.w(TAG, e);
            }
          }

          updateToggleButtonState();
        });

    disposables.add(disposable);

    return future;
  }

  private void onSecurityUpdated() {
    Log.i(TAG, "onSecurityUpdated()");
    updateReminders();
    updateDefaultSubscriptionId(recipient.get().getDefaultSubscriptionId());
  }

  private void initializeInsightObserver() {
    inviteReminderModel = new InviteReminderModel(requireContext(), new InviteReminderRepository(requireContext()));
    inviteReminderModel.loadReminder(recipient, this::updateReminders);
  }

  protected void updateReminders() {
    Context context = getContext();
    if (callback.onUpdateReminders() || context == null) {
      return;
    }

    Optional<Reminder> inviteReminder              = inviteReminderModel.getReminder();
    Integer            actionableRequestingMembers = groupViewModel.getActionableRequestingMembers().getValue();
    List<RecipientId>  gv1MigrationSuggestions     = groupViewModel.getGroupV1MigrationSuggestions().getValue();

    if (UnauthorizedReminder.isEligible(context)) {
      reminderView.get().showReminder(new UnauthorizedReminder(context));
    } else if (ExpiredBuildReminder.isEligible()) {
      reminderView.get().showReminder(new ExpiredBuildReminder(context));
      reminderView.get().setOnActionClickListener(this::handleReminderAction);
    } else if (ServiceOutageReminder.isEligible(context)) {
      ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
      reminderView.get().showReminder(new ServiceOutageReminder(context));
    } else if (SignalStore.account().isRegistered()                 &&
               TextSecurePreferences.isShowInviteReminders(context) &&
               !viewModel.isPushAvailable()                         &&
               inviteReminder.isPresent()                           &&
               !recipient.get().isGroup()) {
      reminderView.get().setOnActionClickListener(this::handleReminderAction);
      reminderView.get().setOnDismissListener(() -> inviteReminderModel.dismissReminder());
      reminderView.get().showReminder(inviteReminder.get());
    } else if (actionableRequestingMembers != null && actionableRequestingMembers > 0) {
      reminderView.get().showReminder(PendingGroupJoinRequestsReminder.create(context, actionableRequestingMembers));
      reminderView.get().setOnActionClickListener(id -> {
        if (id == R.id.reminder_action_review_join_requests) {
          startActivity(ManagePendingAndRequestingMembersActivity.newIntent(context, getRecipient().getGroupId().get().requireV2()));
        }
      });
    } else if (gv1MigrationSuggestions != null && gv1MigrationSuggestions.size() > 0 && recipient.get().isPushV2Group()) {
      reminderView.get().showReminder(new GroupsV1MigrationSuggestionsReminder(context, gv1MigrationSuggestions));
      reminderView.get().setOnActionClickListener(actionId -> {
        if (actionId == R.id.reminder_action_gv1_suggestion_add_members) {
          GroupsV1MigrationSuggestionsDialog.show(requireActivity(), recipient.get().requireGroupId().requireV2(), gv1MigrationSuggestions);
        } else if (actionId == R.id.reminder_action_gv1_suggestion_no_thanks) {
          groupViewModel.onSuggestedMembersBannerDismissed(recipient.get().requireGroupId());
        }
      });
      reminderView.get().setOnDismissListener(() -> {
      });
    } else if (isInBubble() && !SignalStore.tooltips().hasSeenBubbleOptOutTooltip() && Build.VERSION.SDK_INT > 29) {
      reminderView.get().showReminder(new BubbleOptOutReminder(context));
      reminderView.get().setOnActionClickListener(actionId -> {
        SignalStore.tooltips().markBubbleOptOutTooltipSeen();
        reminderView.get().hide();

        if (actionId == R.id.reminder_action_turn_off) {
          Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
              .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
        }
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
      InsightsLauncher.showInsightsDashboard(getChildFragmentManager());
    } else if (reminderActionId == R.id.reminder_action_update_now) {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext());
    } else {
      throw new IllegalArgumentException("Unknown ID: " + reminderActionId);
    }
  }

  private void updateDefaultSubscriptionId(Optional<Integer> defaultSubscriptionId) {
    Log.i(TAG, "updateDefaultSubscriptionId(" + defaultSubscriptionId.orElse(null) + ")");
    sendButton.setDefaultSubscriptionId(defaultSubscriptionId.orElse(null));
  }

  private ListenableFuture<Boolean> initializeIdentityRecords() {
    final SettableFuture<Boolean> future  = new SettableFuture<>();
    final Context                 context = requireContext().getApplicationContext();

    if (SignalStore.account().getAci() == null || SignalStore.account().getPni() == null) {
      Log.w(TAG, "Not registered! Skipping initializeIdentityRecords()");
      future.set(false);
      return future;
    }

    new AsyncTask<Recipient, Void, Pair<IdentityRecordList, String>>() {
      @Override
      protected @NonNull Pair<IdentityRecordList, String> doInBackground(Recipient... params) {
        List<Recipient> recipients;

        if (params[0].isGroup()) {
          recipients = SignalDatabase.groups().getGroupMembers(params[0].requireGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);
        } else {
          recipients = Collections.singletonList(params[0]);
        }

        long               startTime          =  System.currentTimeMillis();
        IdentityRecordList identityRecordList = ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecords(recipients);

        Log.i(TAG, String.format(Locale.US, "Loaded %d identities in %d ms", recipients.size(), System.currentTimeMillis() - startTime));

        String message = null;

        if (identityRecordList.isUnverified()) {
          message = IdentityUtil.getUnverifiedBannerDescription(context, identityRecordList.getUnverifiedRecipients());
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

        titleView.setVerified(viewModel.isPushAvailable() && identityRecords.isVerified() && !recipient.get().isSelf());

        future.set(true);
      }

    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient.get());

    return future;
  }

  private void initializeViews(View view) {
    toolbar                  = view.findViewById(R.id.toolbar);
    toolbarBackground        = view.findViewById(R.id.toolbar_background);
    titleView                = view.findViewById(R.id.conversation_title_view);
    buttonToggle             = view.findViewById(R.id.button_toggle);
    sendButton               = view.findViewById(R.id.send_button);
    attachButton             = view.findViewById(R.id.attach_button);
    composeText              = view.findViewById(R.id.embedded_text_editor);
    charactersLeft           = view.findViewById(R.id.space_left);
    emojiDrawerStub          = ViewUtil.findStubById(view, R.id.emoji_drawer_stub);
    attachmentKeyboardStub   = ViewUtil.findStubById(view, R.id.attachment_keyboard_stub);
    unblockButton            = view.findViewById(R.id.unblock_button);
    smsExportStub            = ViewUtil.findStubById(view, R.id.sms_export_stub);
    registerButton           = view.findViewById(R.id.register_button);
    container                = view.findViewById(R.id.layout_container);
    reminderView             = ViewUtil.findStubById(view, R.id.reminder_stub);
    unverifiedBannerView     = ViewUtil.findStubById(view, R.id.unverified_banner_stub);
    reviewBanner             = ViewUtil.findStubById(view, R.id.review_banner_stub);
    quickAttachmentToggle    = view.findViewById(R.id.quick_attachment_toggle);
    inlineAttachmentToggle   = view.findViewById(R.id.inline_attachment_container);
    inputPanel               = view.findViewById(R.id.bottom_panel);
    searchNav                = view.findViewById(R.id.conversation_search_nav);
    messageRequestBottomView = view.findViewById(R.id.conversation_activity_message_request_bottom_bar);
    mentionsSuggestions      = ViewUtil.findStubById(view, R.id.conversation_mention_suggestions_stub);
    wallpaper                = view.findViewById(R.id.conversation_wallpaper);
    wallpaperDim             = view.findViewById(R.id.conversation_wallpaper_dim);
    voiceNotePlayerViewStub  = ViewUtil.findStubById(view, R.id.voice_note_player_stub);
    navigationBarBackground  = view.findViewById(R.id.navbar_background);

    ImageButton quickCameraToggle      = view.findViewById(R.id.quick_camera_toggle);
    ImageButton inlineAttachmentButton = view.findViewById(R.id.inline_attachment_button);

    Stub<ConversationReactionOverlay> reactionOverlayStub = ViewUtil.findStubById(view, R.id.conversation_reaction_scrubber_stub);
    reactionDelegate = new ConversationReactionDelegate(reactionOverlayStub);

    noLongerMemberBanner                = view.findViewById(R.id.conversation_no_longer_member_banner);
    cannotSendInAnnouncementGroupBanner = ViewUtil.findStubById(view, R.id.conversation_cannot_send_announcement_stub);
    requestingMemberBanner              = view.findViewById(R.id.conversation_requesting_banner);
    cancelJoinRequest                   = view.findViewById(R.id.conversation_cancel_request);
    releaseChannelUnmute                = ViewUtil.findStubById(view, R.id.conversation_release_notes_unmute_stub);
    joinGroupCallButton                 = view.findViewById(R.id.conversation_group_call_join);

    sendButton.setPopupContainer((ViewGroup) view);
    sendButton.setSnackbarContainer(view.findViewById(R.id.fragment_content));

    container.setIsBubble(isInBubble());
    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this);
    inputPanel.setMediaListener(this);

    attachmentManager = new AttachmentManager(requireContext(), view, this);
    audioRecorder     = new AudioRecorder(requireContext());
    typingTextWatcher = new ComposeTextWatcher();

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setCursorPositionChangedListener(this);
    attachButton.setOnClickListener(new AttachButtonListener());
    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    sendButton.addOnSelectionChangedListener((newMessageSendType, manuallySelected) -> {
      if (getContext() == null) {
        Log.w(TAG, "onSelectionChanged called in detached state. Ignoring.");
        return;
      }

      calculateCharactersRemaining();
      updateLinkPreviewState();
      linkPreviewViewModel.onTransportChanged(newMessageSendType.usesSmsTransport());
      composeText.setMessageSendType(newMessageSendType);

      updateSendButtonColor(newMessageSendType);

      if (manuallySelected) recordTransportPreference(newMessageSendType);
    });

    titleView.setOnStoryRingClickListener(v -> handleStoryRingClick());
    titleView.setOnClickListener(v -> handleConversationSettings());
    titleView.setOnLongClickListener(v -> handleDisplayQuickContact());
    unblockButton.setOnClickListener(v -> handleUnblock());
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

    reactionDelegate.setOnReactionSelectedListener(this);

    joinGroupCallButton.setOnClickListener(v -> handleVideo(getRecipient()));

    voiceNoteMediaController.getVoiceNotePlayerViewState().observe(getViewLifecycleOwner(), state -> {
      if (state.isPresent()) {
        requireVoiceNotePlayerView().show();
        requireVoiceNotePlayerView().setState(state.get());
      } else if (voiceNotePlayerViewStub.resolved()) {
        requireVoiceNotePlayerView().hide();
      }
    });

    voiceNoteMediaController.getVoiceNotePlaybackState().observe(getViewLifecycleOwner(), inputPanel.getPlaybackStateObserver());

    material3OnScrollHelper = new Material3OnScrollHelper(requireActivity(), Collections.singletonList(toolbarBackground), Collections.emptyList()) {
      @Override
      public @NonNull ColorSet getActiveColorSet() {
        return new ColorSet(getActiveToolbarColor(wallpaper.getDrawable() != null));
      }

      @Override
      public @NonNull ColorSet getInactiveColorSet() {
        return new ColorSet(getInactiveToolbarColor(wallpaper.getDrawable() != null));
      }
    };
  }

  private void updateSendButtonColor(MessageSendType newMessageSendType) {
    buttonToggle.getBackground().setColorFilter(getSendButtonColor(newMessageSendType), PorterDuff.Mode.MULTIPLY);
    buttonToggle.getBackground().invalidateSelf();
  }

  private @ColorInt int getSendButtonColor(MessageSendType newTransport) {
    if (newTransport.usesSmsTransport()) {
      return getResources().getColor(newTransport.getBackgroundColorRes());
    } else if (recipient != null) {
      return getRecipient().getChatColors().asSingleColor();
    } else {
      return getResources().getColor(newTransport.getBackgroundColorRes());
    }
  }

  private @NonNull VoiceNotePlayerView requireVoiceNotePlayerView() {
    if (voiceNotePlayerView == null) {
      voiceNotePlayerView = voiceNotePlayerViewStub.get().findViewById(R.id.voice_note_player_view);
      voiceNotePlayerView.setListener(new VoiceNotePlayerViewListener());
    }

    return voiceNotePlayerView;
  }

  private void updateWallpaper(@Nullable ChatWallpaper chatWallpaper) {
    Log.d(TAG, "Setting wallpaper.");
    if (chatWallpaper != null) {
      chatWallpaper.loadInto(wallpaper);
      ChatWallpaperDimLevelUtil.applyDimLevelForNightMode(wallpaperDim, chatWallpaper);
      inputPanel.setWallpaperEnabled(true);
      if (attachmentKeyboardStub.resolved()) {
        attachmentKeyboardStub.get().setWallpaperEnabled(true);
      }

      material3OnScrollHelper.setColorImmediate();
      int toolbarTextAndIconColor = getResources().getColor(R.color.signal_colorNeutralInverse);
      toolbar.setTitleTextColor(toolbarTextAndIconColor);
      setToolbarActionItemTint(toolbar, toolbarTextAndIconColor);
      if (!smsExportStub.resolved()) {
        WindowUtil.setNavigationBarColor(requireActivity(), getResources().getColor(R.color.conversation_navigation_wallpaper));
      }
    } else {
      wallpaper.setImageDrawable(null);
      wallpaperDim.setVisibility(View.GONE);
      inputPanel.setWallpaperEnabled(false);
      if (attachmentKeyboardStub.resolved()) {
        attachmentKeyboardStub.get().setWallpaperEnabled(false);
      }

      material3OnScrollHelper.setColorImmediate();
      int toolbarTextAndIconColor = getResources().getColor(R.color.signal_colorOnSurface);
      toolbar.setTitleTextColor(toolbarTextAndIconColor);
      setToolbarActionItemTint(toolbar, toolbarTextAndIconColor);
      if (!releaseChannelUnmute.resolved() && !smsExportStub.resolved()) {
        WindowUtil.setNavigationBarColor(requireActivity(), getResources().getColor(R.color.signal_colorBackground));
      }
    }
    fragment.onWallpaperChanged(chatWallpaper);
    messageRequestBottomView.setWallpaperEnabled(chatWallpaper != null);
  }

  private static @ColorRes int getActiveToolbarColor(boolean hasWallpaper) {
    return hasWallpaper ? R.color.conversation_toolbar_color_wallpaper_scrolled
                        : R.color.signal_colorSurface2;
  }

  private static @ColorRes int getInactiveToolbarColor(boolean hasWallpaper) {
    return hasWallpaper ? R.color.conversation_toolbar_color_wallpaper
                        : R.color.signal_colorBackground;
  }

  private void setToolbarActionItemTint(@NonNull Toolbar toolbar, @ColorInt int tint) {
    for (int i = 0; i < toolbar.getMenu().size(); i++) {
      MenuItem menuItem = toolbar.getMenu().getItem(i);
      MenuItemCompat.setIconTintList(menuItem, ColorStateList.valueOf(tint));
    }

    if (toolbar.getNavigationIcon() != null) {
      toolbar.getNavigationIcon().setColorFilter(new SimpleColorFilter(tint));
    }

    if (toolbar.getOverflowIcon() != null) {
      toolbar.getOverflowIcon().setColorFilter(new SimpleColorFilter(tint));
    }
  }

  protected void initializeActionBar() {
    invalidateOptionsMenu();
    toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

    if (isInBubble()) {
      toolbar.setNavigationIcon(DrawableUtil.tint(ContextUtil.requireDrawable(requireContext(), R.drawable.ic_notification),
                                                  ContextCompat.getColor(requireContext(), R.color.signal_accent_primary)));
      toolbar.setNavigationOnClickListener(unused -> startActivity(MainActivity.clearTop(requireContext())));
    }

    callback.onInitializeToolbar(toolbar);
  }

  protected boolean isInBubble() {
    return callback.isInBubble();
  }

  private void initializeResources(@NonNull ConversationIntents.Args args) {
    if (recipient != null) {
      recipient.removeObservers(this);
    }

    recipient        = Recipient.live(args.getRecipientId());
    threadId         = args.getThreadId();
    distributionType = args.getDistributionType();
    glideRequests    = GlideApp.with(this);

    Log.i(TAG, "[initializeResources] Recipient: " + recipient.getId() + ", Thread: " + threadId);

    recipient.observe(getViewLifecycleOwner(), this::onRecipientChanged);
  }

  private void initializeLinkPreviewObserver() {
    linkPreviewViewModel = new ViewModelProvider(this, (ViewModelProvider.Factory) new LinkPreviewViewModel.Factory(new LinkPreviewRepository())).get(LinkPreviewViewModel.class);

    linkPreviewViewModel.getLinkPreviewState().observe(getViewLifecycleOwner(), previewState -> {
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
    ConversationSearchViewModel.Factory viewModelFactory = new ConversationSearchViewModel.Factory(getString(R.string.note_to_self));

    searchViewModel = new ViewModelProvider(this, (ViewModelProvider.Factory) viewModelFactory).get(ConversationSearchViewModel.class);

    searchViewModel.getSearchResults().observe(getViewLifecycleOwner(), result -> {
      if (result == null) return;

      if (!result.getResults().isEmpty()) {
        MessageResult messageResult = result.getResults().get(result.getPosition());
        fragment.jumpToMessage(messageResult.getMessageRecipient().getId(), messageResult.getReceivedTimestampMs(), searchViewModel::onMissingResult);
      }

      searchNav.setData(result.getPosition(), result.getResults().size());
    });
  }

  private void initializeStickerObserver() {
    StickerSearchRepository repository = new StickerSearchRepository(requireContext());

    stickerViewModel = new ViewModelProvider(this, (ViewModelProvider.Factory) new ConversationStickerViewModel.Factory(requireActivity().getApplication(), repository))
                                         .get(ConversationStickerViewModel.class);

    stickerViewModel.getStickerResults().observe(getViewLifecycleOwner(), stickers -> {
      if (stickers == null) return;

      inputPanel.setStickerSuggestions(stickers);
    });

    stickerViewModel.getStickersAvailability().observe(getViewLifecycleOwner(), stickersAvailable -> {
      if (stickersAvailable == null) return;

      boolean           isSystemEmojiPreferred = SignalStore.settings().isPreferSystemEmoji();
      MediaKeyboardMode keyboardMode           = TextSecurePreferences.getMediaKeyboardMode(requireContext());
      boolean           stickerIntro           = !TextSecurePreferences.hasSeenStickerIntroTooltip(requireContext());

      if (stickersAvailable) {
        inputPanel.showMediaKeyboardToggle(true);
        switch (keyboardMode) {
          case EMOJI:
            inputPanel.setMediaKeyboardToggleMode(isSystemEmojiPreferred ? KeyboardPage.STICKER : KeyboardPage.EMOJI);
            break;
          case STICKER:
            inputPanel.setMediaKeyboardToggleMode(KeyboardPage.STICKER);
            break;
          case GIF:
            inputPanel.setMediaKeyboardToggleMode(KeyboardPage.GIF);
            break;
        }
        if (stickerIntro) showStickerIntroductionTooltip();
      }

      if (emojiDrawerStub.resolved()) {
        initializeMediaKeyboardProviders();
      }
    });
  }

  private void initializeViewModel(@NonNull ConversationIntents.Args args) {
    this.viewModel = new ViewModelProvider(this, (ViewModelProvider.Factory) new ConversationViewModel.Factory()).get(ConversationViewModel.class);

    this.viewModel.setArgs(args);
    this.viewModel.getEvents().observe(getViewLifecycleOwner(), this::onViewModelEvent);
    disposables.add(this.viewModel.getWallpaper().subscribe(w -> updateWallpaper(w.orElse(null))));
  }

  private void initializeGroupViewModel() {
    groupViewModel = new ViewModelProvider(this, (ViewModelProvider.Factory) new ConversationGroupViewModel.Factory()).get(ConversationGroupViewModel.class);
    recipient.observe(this, groupViewModel::onRecipientChange);
    groupViewModel.getGroupActiveState().observe(getViewLifecycleOwner(), unused -> invalidateOptionsMenu());
    groupViewModel.getReviewState().observe(getViewLifecycleOwner(), this::presentGroupReviewBanner);
  }

  private void initializeMentionsViewModel() {
    mentionsViewModel    = new ViewModelProvider(requireActivity(), new MentionsPickerViewModel.Factory()).get(MentionsPickerViewModel.class);
    inlineQueryViewModel = new ViewModelProvider(requireActivity()).get(InlineQueryViewModel.class);

    inlineQueryResultsController = new InlineQueryResultsController(
        requireContext(),
        inlineQueryViewModel,
        inputPanel,
        (ViewGroup) requireView(),
        composeText,
        getViewLifecycleOwner()
    );
    inlineQueryResultsController.onOrientationChange(getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE);

    recipient.observe(getViewLifecycleOwner(), r -> {
      if (r.isPushV2Group() && !mentionsSuggestions.resolved()) {
        mentionsSuggestions.get();
      }
      mentionsViewModel.onRecipientChange(r);
    });

    composeText.setInlineQueryChangedListener(new InlineQueryChangedListener() {
      @Override
      public void onQueryChanged(@NonNull InlineQuery inlineQuery) {
        if (inlineQuery instanceof InlineQuery.Mention) {
          if (getRecipient().isPushV2Group() && getRecipient().isActiveGroup()) {
            if (!mentionsSuggestions.resolved()) {
              mentionsSuggestions.get();
            }
            mentionsViewModel.onQueryChange(inlineQuery.getQuery());
          }
          inlineQueryViewModel.onQueryChange(inlineQuery);
        } else if (inlineQuery instanceof InlineQuery.Emoji) {
          inlineQueryViewModel.onQueryChange(inlineQuery);
          mentionsViewModel.onQueryChange(null);
        } else if (inlineQuery instanceof InlineQuery.NoQuery) {
          mentionsViewModel.onQueryChange(null);
          inlineQueryViewModel.onQueryChange(inlineQuery);
        }
      }

      @Override
      public void clearQuery() {
        onQueryChanged(InlineQuery.NoQuery.INSTANCE);
      }
    });

    composeText.setMentionValidator(annotations -> {
      if (!getRecipient().isPushV2Group() || !getRecipient().isActiveGroup()) {
        return annotations;
      }

      Set<String> validRecipientIds = Stream.of(getRecipient().getParticipantIds())
                                            .map(id -> MentionAnnotation.idToMentionAnnotationValue(id))
                                            .collect(Collectors.toSet());

      return Stream.of(annotations)
                   .filterNot(a -> validRecipientIds.contains(a.getValue()))
                   .toList();
    });

    mentionsViewModel.getSelectedRecipient().observe(getViewLifecycleOwner(), recipient -> {
      composeText.replaceTextWithMention(recipient.getDisplayName(requireContext()), recipient.getId());
    });

    Disposable disposable = inlineQueryViewModel
        .getSelection()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(r -> {
          composeText.replaceText(r);
        });

    disposables.add(disposable);
  }

  public void initializeGroupCallViewModel() {
    groupCallViewModel = new ViewModelProvider(this, new GroupCallViewModel.Factory()).get(GroupCallViewModel.class);

    recipient.observe(this, r -> {
      groupCallViewModel.onRecipientChange(r);
    });

    groupCallViewModel.hasActiveGroupCall().observe(getViewLifecycleOwner(), hasActiveCall -> {
      invalidateOptionsMenu();
      joinGroupCallButton.setVisibility(hasActiveCall ? View.VISIBLE : View.GONE);
    });

    groupCallViewModel.groupCallHasCapacity().observe(getViewLifecycleOwner(), hasCapacity -> joinGroupCallButton.setText(hasCapacity ? R.string.ConversationActivity_join : R.string.ConversationActivity_full));
  }

  public void initializeDraftViewModel() {
    draftViewModel = new ViewModelProvider(this).get(DraftViewModel.class);

    recipient.observe(getViewLifecycleOwner(), r -> {
      draftViewModel.onRecipientChanged(r);
    });

    draftViewModel.setThreadId(threadId);
    draftViewModel.setDistributionType(distributionType);

    disposables.add(
        draftViewModel
            .getState()
            .distinctUntilChanged(state -> state.getVoiceNoteDraft())
            .subscribe(state -> {
              inputPanel.setVoiceNoteDraft(state.getVoiceNoteDraft());
              updateToggleButtonState();
            })
    );
  }

  private void showGroupCallingTooltip() {
    if (Build.VERSION.SDK_INT == 19 || !SignalStore.tooltips().shouldShowGroupCallingTooltip() || callingTooltipShown) {
      return;
    }

    View anchor = requireView().findViewById(R.id.menu_video_secure);
    if (anchor == null) {
      Log.w(TAG, "Video Call tooltip anchor is null. Skipping tooltip...");
      return;
    }

    callingTooltipShown = true;

    SignalStore.tooltips().markGroupCallSpeakerViewSeen();
    TooltipPopup.forTarget(anchor)
                .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.signal_accent_green))
                .setTextColor(getResources().getColor(R.color.core_white))
                .setText(R.string.ConversationActivity__tap_here_to_start_a_group_call)
                .setOnDismissListener(() -> SignalStore.tooltips().markGroupCallingTooltipSeen())
                .show(TooltipPopup.POSITION_BELOW);
  }

  private void showStickerIntroductionTooltip() {
    TextSecurePreferences.setMediaKeyboardMode(requireContext(), MediaKeyboardMode.STICKER);
    inputPanel.setMediaKeyboardToggleMode(KeyboardPage.STICKER);

    TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                .setBackgroundTint(getResources().getColor(R.color.core_ultramarine))
                .setTextColor(getResources().getColor(R.color.core_white))
                .setText(R.string.ConversationActivity_new_say_it_with_stickers)
                .setOnDismissListener(() -> {
                  TextSecurePreferences.setHasSeenStickerIntroTooltip(requireContext(), true);
                  EventBus.getDefault().removeStickyEvent(StickerPackInstallEvent.class);
                })
                .show(TooltipPopup.POSITION_ABOVE);
  }

  @Override
  public void onReactionSelected(MessageRecord messageRecord, String emoji) {
    final Context context = requireContext().getApplicationContext();

    reactionDelegate.hide();

    SignalExecutors.BOUNDED.execute(() -> {
      ReactionRecord oldRecord = Stream.of(messageRecord.getReactions())
                                       .filter(record -> record.getAuthor().equals(Recipient.self().getId()))
                                       .findFirst()
                                       .orElse(null);

      if (oldRecord != null && oldRecord.getEmoji().equals(emoji)) {
        MessageSender.sendReactionRemoval(context, new MessageId(messageRecord.getId(), messageRecord.isMms()), oldRecord);
      } else {
        MessageSender.sendNewReaction(context, new MessageId(messageRecord.getId(), messageRecord.isMms()), emoji);
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
      final Context context = requireContext().getApplicationContext();

      reactionDelegate.hide();

      SignalExecutors.BOUNDED.execute(() -> MessageSender.sendReactionRemoval(context,
                                                                              new MessageId(messageRecord.getId(), messageRecord.isMms()),
                                                                              oldRecord));
    } else {
      reactionDelegate.hideForReactWithAny();

      ReactWithAnyEmojiBottomSheetDialogFragment.createForMessageRecord(messageRecord, reactWithAnyEmojiStartPage)
                                                .show(getChildFragmentManager(), "BOTTOM");
    }
  }

  @Override
  public void onReactWithAnyEmojiDialogDismissed() {
    reactionDelegate.hide();
  }

  @Override
  public void onReactWithAnyEmojiSelected(@NonNull String emoji) {
    reactionDelegate.hide();
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
    if (!viewModel.isPushAvailable()) {
      Log.i(TAG, "SMS contact, no profile fetch needed.");
      return;
    }

    RetrieveProfileJob.enqueueAsync(recipient.getId());
  }

  private void initializeGv1Migration() {
    GroupV1MigrationJob.enqueuePossibleAutoMigrate(recipient.getId());
  }

  private void onRecipientChanged(@NonNull Recipient recipient) {
    if (getContext() == null) {
      Log.w(TAG, "onRecipientChanged called in detached state. Ignoring.");
      return;
    }

    Log.i(TAG, "onModified(" + recipient.getId() + ") " + recipient.getRegistered());
    titleView.setTitle(glideRequests, recipient);
    titleView.setVerified(identityRecords.isVerified() && !recipient.isSelf());
    setBlockedUserState(recipient, viewModel.getConversationStateSnapshot().getSecurityInfo());
    updateReminders();
    updateDefaultSubscriptionId(recipient.getDefaultSubscriptionId());
    updatePaymentsAvailable();
    updateSendButtonColor(sendButton.getSelectedSendType());

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
      groupCallViewModel.onRecipientChange(recipient);
    }

    if (draftViewModel != null) {
      draftViewModel.onRecipientChanged(recipient);
    }

    if (this.threadId == -1) {
      SimpleTask.run(() -> SignalDatabase.threads().getThreadIdIfExistsFor(recipient.getId()), threadId -> {
        if (this.threadId != threadId) {
          Log.d(TAG, "Thread id changed via recipient change");
          this.threadId = threadId;
          fragment.reload(recipient, this.threadId);
          setVisibleThread(this.threadId);
          draftViewModel.setThreadId(this.threadId);
        }
      });
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onIdentityRecordUpdate(final IdentityRecord event) {
    initializeIdentityRecords();
  }

  @Subscribe(threadMode =  ThreadMode.MAIN, sticky = true)
  public void onStickerPackInstalled(final StickerPackInstallEvent event) {
    if (!TextSecurePreferences.hasSeenStickerIntroTooltip(requireContext())) return;

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
        viewModel.updateSecurityInfo();
        calculateCharactersRemaining();
      }
    };

    requireActivity().registerReceiver(securityUpdateReceiver,
                                       new IntentFilter(SecurityEvent.SECURITY_UPDATE_EVENT),
                                       KeyCachingService.KEY_PERMISSION, null);
  }

  //////// Helper Methods

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType) {
    return setMedia(uri, mediaType, 0, 0, false, false);
  }

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType, int width, int height, boolean borderless, boolean videoGif) {
    if (uri == null) {
      return new SettableFuture<>(false);
    }

    if (MediaType.VCARD.equals(mediaType) && viewModel.isPushAvailable()) {
      openContactShareEditor(uri);
      return new SettableFuture<>(false);
    } else if (MediaType.IMAGE.equals(mediaType) || MediaType.GIF.equals(mediaType) || MediaType.VIDEO.equals(mediaType)) {
      String mimeType = MediaUtil.getMimeType(requireContext(), uri);
      if (mimeType == null) {
        mimeType = mediaType.toFallbackMimeType();
      }

      Media media = new Media(uri, mimeType, 0, width, height, 0, 0, borderless, videoGif, Optional.empty(), Optional.empty(), Optional.empty());
      startActivityForResult(MediaSelectionActivity.editor(requireContext(), sendButton.getSelectedSendType(), Collections.singletonList(media), recipient.getId(), composeText.getTextTrimmed()), MEDIA_SENDER);
      return new SettableFuture<>(false);
    } else {
      return attachmentManager.setMedia(glideRequests, uri, mediaType, getCurrentMediaConstraints(), width, height);
    }
  }

  private void openContactShareEditor(Uri contactUri) {
    Intent intent = ContactShareEditActivity.getIntent(requireContext(), Collections.singletonList(contactUri), getSendButtonColor(sendButton.getSelectedSendType()));
    startActivityForResult(intent, GET_CONTACT_DETAILS);
  }

  private void addAttachmentContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactData contactData = contactDataList.getContactData(requireContext(), contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
  }

  private void sendSharedContact(List<Contact> contacts) {
    long       expiresIn      = TimeUnit.SECONDS.toMillis(recipient.get().getExpiresInSeconds());
    boolean    initiating     = threadId == -1;

    sendMediaMessage(recipient.getId(), sendButton.getSelectedSendType(), "", attachmentManager.buildSlideDeck(), null, contacts, Collections.emptyList(), Collections.emptyList(), expiresIn, false, initiating, false, null);
  }

  private void selectContactInfo(ContactData contactData) {
    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i]     = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
    builder.setIcon(R.drawable.ic_account_box);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, (dialog, which) -> composeText.append(numbers[which]));
    builder.show();
  }

  private void setBlockedUserState(Recipient recipient, @NonNull ConversationSecurityInfo conversationSecurityInfo) {
    if (!conversationSecurityInfo.isInitialized()) {
      Log.i(TAG, "Ignoring blocked state update for uninitialized security info.");
      return;
    }

    if (!conversationSecurityInfo.isPushAvailable() && isPushGroupConversation()) {
      unblockButton.setVisibility(View.GONE);
      inputPanel.setHideForBlockedState(true);
      smsExportStub.setVisibility(View.GONE);
      registerButton.setVisibility(View.VISIBLE);
    } else if (!conversationSecurityInfo.isPushAvailable() && !(SignalStore.misc().getSmsExportPhase().isSmsSupported() && conversationSecurityInfo.isDefaultSmsApplication()) && (recipient.hasSmsAddress() || recipient.isMmsGroup())) {
      unblockButton.setVisibility(View.GONE);
      inputPanel.setHideForBlockedState(true);
      smsExportStub.setVisibility(View.VISIBLE);
      registerButton.setVisibility(View.GONE);

      int color = ContextCompat.getColor(requireContext(), recipient.hasWallpaper() ? R.color.wallpaper_bubble_color : R.color.signal_colorBackground);
      smsExportStub.get().setBackgroundColor(color);
      WindowUtil.setNavigationBarColor(requireActivity(), color);

      TextView       message      = smsExportStub.get().findViewById(R.id.export_sms_message);
      MaterialButton actionButton = smsExportStub.get().findViewById(R.id.export_sms_button);
      boolean        isPhase1     = SignalStore.misc().getSmsExportPhase() == SmsExportPhase.PHASE_1;

      if (SignalStore.misc().getSmsExportPhase() == SmsExportPhase.PHASE_0) {
        message.setText(getString(R.string.NewConversationActivity__s_is_not_a_signal_user, recipient.getDisplayName(requireContext())));
        actionButton.setText(R.string.conversation_activity__enable_signal_for_sms);
        actionButton.setOnClickListener(v -> {
          handleMakeDefaultSms();
        });
      } else if (conversationSecurityInfo.getHasUnexportedInsecureMessages()) {
        message.setText(isPhase1 ? R.string.ConversationActivity__sms_messaging_is_currently_disabled_you_can_export_your_messages_to_another_app_on_your_phone
                                 : R.string.ConversationActivity__sms_messaging_is_no_longer_supported_in_signal_you_can_export_your_messages_to_another_app_on_your_phone);
        actionButton.setText(R.string.ConversationActivity__export_sms_messages);
        actionButton.setOnClickListener(v -> startActivity(SmsExportActivity.createIntent(requireContext())));
      } else {
        message.setText(requireContext().getString(isPhase1 ? R.string.ConversationActivity__sms_messaging_is_currently_disabled_invite_s_to_to_signal_to_keep_the_conversation_here
                                                            : R.string.ConversationActivity__sms_messaging_is_no_longer_supported_in_signal_invite_s_to_to_signal_to_keep_the_conversation_here,
                                                   recipient.getDisplayName(requireContext())));
        actionButton.setText(R.string.ConversationActivity__invite_to_signal);
        actionButton.setOnClickListener(v -> handleInviteLink());
      }
    } else if (recipient.isReleaseNotes() && !recipient.isBlocked()) {
      unblockButton.setVisibility(View.GONE);
      inputPanel.setHideForBlockedState(true);
      smsExportStub.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);

      if (recipient.isMuted()) {
        View unmuteBanner = releaseChannelUnmute.get();
        unmuteBanner.setVisibility(View.VISIBLE);
        unmuteBanner.findViewById(R.id.conversation_activity_unmute_button)
                    .setOnClickListener(v -> handleUnmuteNotifications());
        WindowUtil.setNavigationBarColor(requireActivity(), getResources().getColor(R.color.signal_colorSurface2));
      } else if (releaseChannelUnmute.resolved()) {
        releaseChannelUnmute.get().setVisibility(View.GONE);
        WindowUtil.setNavigationBarColor(requireActivity(), getResources().getColor(R.color.signal_colorBackground));
      }
    } else {
      boolean inactivePushGroup = isPushGroupConversation() && !recipient.isActiveGroup();
      inputPanel.setHideForBlockedState(inactivePushGroup);
      unblockButton.setVisibility(View.GONE);
      smsExportStub.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);
    }

    if (releaseChannelUnmute.resolved() && !recipient.isReleaseNotes()) {
      releaseChannelUnmute.get().setVisibility(View.GONE);
    }
  }

  private void calculateCharactersRemaining() {
    String          messageBody     = composeText.getTextTrimmed().toString();
    MessageSendType sendType        = sendButton.getSelectedSendType();
    CharacterState  characterState  = sendType.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(String.format(Locale.getDefault(),
                                           "%d/%d (%d)",
                                           characterState.charactersRemaining,
                                           characterState.maxTotalMessageSize,
                                           characterState.messagesSpent));
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }

  private void initializeMediaKeyboardProviders() {
    KeyboardPagerViewModel keyboardPagerViewModel = new ViewModelProvider(requireActivity()).get(KeyboardPagerViewModel.class);

    switch (TextSecurePreferences.getMediaKeyboardMode(requireContext())) {
      case EMOJI:
        keyboardPagerViewModel.switchToPage(KeyboardPage.EMOJI);
        break;
      case STICKER:
        keyboardPagerViewModel.switchToPage(KeyboardPage.STICKER);
        break;
      case GIF:
        keyboardPagerViewModel.switchToPage(KeyboardPage.GIF);
        break;
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

    Optional<GroupRecord> record = SignalDatabase.groups().getGroup(getRecipient().getId());
    return record.isPresent() && record.get().isActive();
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
    return sendButton.isManualSelection() && sendButton.getSelectedSendType().usesSmsTransport();
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
    return sendButton.getSelectedSendType().usesSignalTransport()
           ? MediaConstraints.getPushMediaConstraints()
           : MediaConstraints.getMmsMediaConstraints(sendButton.getSelectedSendType().getSimSubscriptionIdOr(-1));
  }

  private void markLastSeen() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        SignalDatabase.threads().setLastSeen(params[0]);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  protected void sendComplete(long threadId) {
    boolean refreshFragment = (threadId != this.threadId);
    this.threadId = threadId;

    if (fragment == null || !fragment.isVisible() || requireActivity().isFinishing()) {
      callback.onSendComplete(threadId);
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
    callback.onSendComplete(threadId);

    draftViewModel.onSendComplete(threadId);
  }

  private void sendMessage(@Nullable String metricId) {
    if (inputPanel.isRecordingInLockedMode()) {
      inputPanel.releaseRecordingLock();
      return;
    }

    Draft voiceNote = draftViewModel.getVoiceNoteDraft();
    if (voiceNote != null) {
      AudioSlide audioSlide = AudioSlide.createFromVoiceNoteDraft(requireContext(), voiceNote);

      sendVoiceNote(Objects.requireNonNull(audioSlide.getUri()), audioSlide.getFileSize());
      return;
    }

    try {
      Recipient recipient = getRecipient();

      if (recipient == null) {
        throw new RecipientFormattingException("Badly formatted");
      }

      String          message        = getMessage();
      MessageSendType sendType       = sendButton.getSelectedSendType();
      long            expiresIn      = TimeUnit.SECONDS.toMillis(recipient.getExpiresInSeconds());
      boolean         initiating     = threadId == -1;
      boolean         needsSplit     = !sendType.usesSmsTransport() && message.length() > sendType.calculateCharacters(message).maxPrimaryMessageSize;
      boolean         isMediaMessage = attachmentManager.isAttachmentPresent() ||
                                       recipient.isGroup()                     ||
                                       recipient.getEmail().isPresent()        ||
                                       inputPanel.getQuote().isPresent()       ||
                                       composeText.hasMentions()               ||
                                       linkPreviewViewModel.hasLinkPreview()   ||
                                       needsSplit;

      Log.i(TAG, "[sendMessage] recipient: " + recipient.getId() + ", threadId: " + threadId + ",  sendType: " + (sendType.usesSignalTransport() ? "signal" : "sms") + ", isManual: " + sendButton.isManualSelection());

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !viewModel.getConversationStateSnapshot().isMmsEnabled()) {
        handleManualMmsRequired();
      } else if (sendType.usesSignalTransport() && (identityRecords.isUnverified(true) || identityRecords.isUntrusted(true))) {
        handleRecentSafetyNumberChange();
      } else if (isMediaMessage) {
        sendMediaMessage(sendType, expiresIn, false, initiating, metricId);
      } else {
        sendTextMessage(sendType, expiresIn, initiating, metricId);
      }
    } catch (RecipientFormattingException ex) {
      Toast.makeText(requireContext(),
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(requireContext(), R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void sendMediaMessage(@NonNull MediaSendActivityResult result) {
    long                 thread        = this.threadId;
    long                 expiresIn     = TimeUnit.SECONDS.toMillis(recipient.get().getExpiresInSeconds());
    QuoteModel           quote         = result.isViewOnce() ? null : inputPanel.getQuote().orElse(null);
    List<Mention>        mentions      = new ArrayList<>(result.getMentions());
    OutgoingMediaMessage message       = new OutgoingMediaMessage(recipient.get(), new SlideDeck(), result.getBody(), System.currentTimeMillis(), -1, expiresIn, result.isViewOnce(), distributionType, result.getStoryType(), null, false, quote, Collections.emptyList(), Collections.emptyList(), mentions, null);
    OutgoingMediaMessage secureMessage = new OutgoingSecureMediaMessage(message);

    final Context context = requireContext().getApplicationContext();

    ApplicationDependencies.getTypingStatusSender().onTypingStopped(thread);

    inputPanel.clearQuote();
    attachmentManager.clear(glideRequests, false);
    silentlySetComposeText("");

    long id = fragment.stageOutgoingMessage(secureMessage);

    SimpleTask.run(() -> {
      long resultId = MessageSender.sendPushWithPreUploadedMedia(context, secureMessage, result.getPreUploadResults(), thread, null);

      int deleted = SignalDatabase.attachments().deleteAbandonedPreuploadedAttachments();
      Log.i(TAG, "Deleted " + deleted + " abandoned attachments.");

      return resultId;
    }, this::sendComplete);
  }

  private void sendMediaMessage(@NonNull MessageSendType sendType, final long expiresIn, final boolean viewOnce, final boolean initiating, @Nullable String metricId)
      throws InvalidMessageException
  {
    Log.i(TAG, "Sending media message...");
    List<LinkPreview> linkPreviews = linkPreviewViewModel.onSend();
    sendMediaMessage(recipient.getId(),
                     sendType,
                     getMessage(),
                     attachmentManager.buildSlideDeck(),
                     inputPanel.getQuote().orElse(null),
                     Collections.emptyList(),
                     linkPreviews,
                     composeText.getMentions(),
                     expiresIn,
                     viewOnce,
                     initiating,
                     true,
                     metricId);
  }

  private ListenableFuture<Void> sendMediaMessage(@NonNull RecipientId recipientId,
                                                  @NonNull MessageSendType sendType,
                                                  @NonNull String body,
                                                  SlideDeck slideDeck,
                                                  QuoteModel quote,
                                                  List<Contact> contacts,
                                                  List<LinkPreview> previews,
                                                  List<Mention> mentions,
                                                  final long expiresIn,
                                                  final boolean viewOnce,
                                                  final boolean initiating,
                                                  final boolean clearComposeBox,
                                                  final @Nullable String metricId)
  {
    if (!viewModel.isDefaultSmsApplication() && sendType.usesSmsTransport() && recipient.get().hasSmsAddress()) {
      showDefaultSmsPrompt();
      return new SettableFuture<>(null);
    }

    final boolean sendPush = sendType.usesSignalTransport();
    final long    thread   = this.threadId;

    if (sendPush) {
      MessageUtil.SplitResult splitMessage = MessageUtil.getSplitMessage(requireContext(), body, sendButton.getSelectedSendType().calculateCharacters(body).maxPrimaryMessageSize);
      body = splitMessage.getBody();

      if (splitMessage.getTextSlide().isPresent()) {
        slideDeck.addSlide(splitMessage.getTextSlide().get());
      }
    }

    OutgoingMediaMessage outgoingMessageCandidate = new OutgoingMediaMessage(Recipient.resolved(recipientId), slideDeck, body, System.currentTimeMillis(), sendType.getSimSubscriptionIdOr(-1), expiresIn, viewOnce, distributionType, StoryType.NONE, null, false, quote, contacts, previews, mentions, null);

    final SettableFuture<Void> future  = new SettableFuture<>();
    final Context              context = requireContext().getApplicationContext();

    final OutgoingMediaMessage outgoingMessage;

    if (sendPush) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessageCandidate);
      ApplicationDependencies.getTypingStatusSender().onTypingStopped(thread);
    } else {
      outgoingMessage = outgoingMessageCandidate.withExpiry(0);
    }

    Permissions.with(this)
               .request(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
               .ifNecessary(!sendPush)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
               .onAllGranted(() -> {
                 if (clearComposeBox) {
                   inputPanel.clearQuote();
                   attachmentManager.clear(glideRequests, false);
                   silentlySetComposeText("");
                 }

                 final long id = fragment.stageOutgoingMessage(outgoingMessage);

                 SimpleTask.run(() -> {
                   return MessageSender.send(context, outgoingMessage, thread, sendType.usesSmsTransport(), metricId, null);
                 }, result -> {
                   sendComplete(result);
                   future.set(null);
                 });
               })
               .onAnyDenied(() -> future.set(null))
               .execute();

    return future;
  }

  private void sendTextMessage(@NonNull MessageSendType sendType, final long expiresIn, final boolean initiating, final @Nullable String metricId)
      throws InvalidMessageException
  {
    if (!viewModel.isDefaultSmsApplication() && sendType.usesSmsTransport() && recipient.get().hasSmsAddress()) {
      showDefaultSmsPrompt();
      return;
    }

    final long    thread      = this.threadId;
    final Context context     = requireContext().getApplicationContext();
    final String  messageBody = getMessage();
    final boolean sendPush    = sendType.usesSignalTransport();

    OutgoingTextMessage message;

    if (sendPush) {
      message = new OutgoingEncryptedMessage(recipient.get(), messageBody, expiresIn);
      ApplicationDependencies.getTypingStatusSender().onTypingStopped(thread);
    } else {
      message = new OutgoingTextMessage(recipient.get(), messageBody, 0, sendType.getSimSubscriptionIdOr(-1));
    }

    Permissions.with(this)
               .request(Manifest.permission.SEND_SMS)
               .ifNecessary(!sendPush)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
               .onAllGranted(() -> {
                 final long id = new SecureRandom().nextLong();
                 SimpleTask.run(() -> {
                   return MessageSender.send(context, message, thread, sendType.usesSmsTransport(), metricId, null);
                 }, this::sendComplete);

                 silentlySetComposeText("");
                 fragment.stageOutgoingMessage(message, id);
               })
               .execute();
  }

  private void showDefaultSmsPrompt() {
    new MaterialAlertDialogBuilder(requireContext())
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

    if (draftViewModel.getVoiceNoteDraft() != null) {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.hide();
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

  private void onViewModelEvent(@NonNull ConversationViewModel.Event event) {
    if (event == ConversationViewModel.Event.SHOW_RECAPTCHA) {
      RecaptchaProofBottomSheetFragment.show(getChildFragmentManager());
    } else {
      throw new AssertionError("Unexpected event!");
    }
  }

  private void updateLinkPreviewState() {
    if (SignalStore.settings().isLinkPreviewsEnabled() && viewModel.isPushAvailable() && !sendButton.getSelectedSendType().usesSmsTransport() && !attachmentManager.isAttachmentPresent() && getContext() != null) {
      linkPreviewViewModel.onEnabled();
      linkPreviewViewModel.onTextChanged(requireContext(), composeText.getTextTrimmed().toString(), composeText.getSelectionStart(), composeText.getSelectionEnd());
    } else {
      linkPreviewViewModel.onUserCancel();
    }
  }

  private void recordTransportPreference(MessageSendType sendType) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        RecipientTable recipientTable = SignalDatabase.recipients();

        recipientTable.setDefaultSubscriptionId(recipient.getId(), sendType.getSimSubscriptionIdOr(-1));

        if (!recipient.resolve().isPushGroup()) {
          recipientTable.setForceSmsSelection(recipient.getId(), recipient.get().getRegistered() == RegisteredState.REGISTERED && sendType.usesSmsTransport());
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
    Vibrator vibrator = ServiceUtil.getVibrator(requireContext());
    vibrator.vibrate(20);

    requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

    voiceNoteMediaController.pausePlayback();
    audioRecorder.startRecording();
  }

  @Override
  public void onRecorderLocked() {
    voiceRecorderWakeLock.acquire();
    updateToggleButtonState();
    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
  }

  @Override
  public void onRecorderFinished() {
    voiceRecorderWakeLock.release();
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(requireContext());
    vibrator.vibrate(20);

    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

    ListenableFuture<VoiceNoteDraft> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<VoiceNoteDraft>() {
      @Override
      public void onSuccess(final @NonNull VoiceNoteDraft result) {
        sendVoiceNote(result.getUri(), result.getSize());
      }

      @Override
      public void onFailure(ExecutionException e) {
        Toast.makeText(requireContext(), R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public void onRecorderCanceled() {
    voiceRecorderWakeLock.release();
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(requireContext());
    vibrator.vibrate(50);

    requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

    ListenableFuture<VoiceNoteDraft> future = audioRecorder.stopRecording();
    if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
      future.addListener(new DeleteCanceledVoiceNoteListener());
    } else {
      draftViewModel.saveEphemeralVoiceNoteDraft(future);
    }
  }

  @Override
  public void onEmojiToggle() {
    if (!emojiDrawerStub.resolved()) {
      initializeMediaKeyboardProviders();
    }

    inputPanel.setMediaKeyboard(emojiDrawerStub.get());
    emojiDrawerStub.get().setFragmentManager(getChildFragmentManager());

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
  public void onQuoteChanged(long id, @NonNull RecipientId author) {
    draftViewModel.setQuoteDraft(id, author);
  }

  @Override
  public void onQuoteCleared() {
    draftViewModel.clearQuoteDraft();
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
    linkPreviewViewModel.onTextChanged(requireContext(), composeText.getTextTrimmed().toString(), start, end);
  }

  @Override
  public void onStickerSelected(@NonNull StickerRecord stickerRecord) {
    sendSticker(stickerRecord, false);
  }

  @Override
  public void onStickerManagementClicked() {
    startActivity(StickerManagementActivity.getIntent(requireContext()));
    container.hideAttachedInput(true);
  }

  private void sendVoiceNote(@NonNull Uri uri, long size) {
    boolean    initiating = threadId == -1;
    long       expiresIn  = TimeUnit.SECONDS.toMillis(recipient.get().getExpiresInSeconds());
    AudioSlide audioSlide = new AudioSlide(requireContext(), uri, size, MediaUtil.AUDIO_AAC, true);
    SlideDeck  slideDeck  = new SlideDeck();

    slideDeck.addSlide(audioSlide);

    sendMediaMessage(recipient.getId(),
                     sendButton.getSelectedSendType(),
                     "",
                     slideDeck,
                     inputPanel.getQuote().orElse(null),
                     Collections.emptyList(),
                     Collections.emptyList(),
                     composeText.getMentions(),
                     expiresIn,
                     false,
                     initiating,
                     true,
                     null);
  }

  private void sendSticker(@NonNull StickerRecord stickerRecord, boolean clearCompose) {
    sendSticker(new StickerLocator(stickerRecord.getPackId(), stickerRecord.getPackKey(), stickerRecord.getStickerId(), stickerRecord.getEmoji()), stickerRecord.getContentType(), stickerRecord.getUri(), stickerRecord.getSize(), clearCompose);

    SignalExecutors.BOUNDED.execute(() ->
     SignalDatabase.stickers()
                    .updateStickerLastUsedTime(stickerRecord.getRowId(), System.currentTimeMillis())
    );
  }

  private void sendSticker(@NonNull StickerLocator stickerLocator, @NonNull String contentType, @NonNull Uri uri, long size, boolean clearCompose) {
    if (sendButton.getSelectedSendType().usesSmsTransport()) {
      Media  media  = new Media(uri, contentType, System.currentTimeMillis(), StickerSlide.WIDTH, StickerSlide.HEIGHT, size, 0, false, false, Optional.empty(), Optional.empty(), Optional.empty());
      Intent intent = MediaSelectionActivity.editor(requireContext(), sendButton.getSelectedSendType(), Collections.singletonList(media), recipient.getId(), composeText.getTextTrimmed());
      startActivityForResult(intent, MEDIA_SENDER);
      return;
    }

    long            expiresIn      = TimeUnit.SECONDS.toMillis(recipient.get().getExpiresInSeconds());
    boolean         initiating     = threadId == -1;
    MessageSendType sendType       = sendButton.getSelectedSendType();
    SlideDeck       slideDeck      = new SlideDeck();
    Slide           stickerSlide   = new StickerSlide(requireContext(), uri, size, stickerLocator, contentType);

    slideDeck.addSlide(stickerSlide);

    sendMediaMessage(recipient.getId(), sendType, "", slideDeck, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), expiresIn, false, initiating, clearCompose, null);
  }

  private void silentlySetComposeText(String text) {
    typingTextWatcher.setTypingStatusEnabled(false);
    composeText.setText(text);
    typingTextWatcher.setTypingStatusEnabled(true);
  }

  @Override
  public void onReactionsDialogDismissed() {
    fragment.clearFocusedItem();
  }

  @Override
  public void onShown() {
    if (inputPanel != null) {
      inputPanel.getMediaKeyboardListener().onShown();
    }
  }

  @Override
  public void onHidden() {
    if (inputPanel != null) {
      inputPanel.getMediaKeyboardListener().onHidden();
    }
  }

  @Override
  public void onKeyboardChanged(@NonNull KeyboardPage page) {
    if (inputPanel != null) {
      inputPanel.getMediaKeyboardListener().onKeyboardChanged(page);
    }
  }

  @Override
  public void onEmojiSelected(String emoji) {
    if (inputPanel != null) {
      inputPanel.onEmojiSelected(emoji);
      if (recentEmojis == null) {
        recentEmojis = new RecentEmojiPageModel(ApplicationDependencies.getApplication(), TextSecurePreferences.RECENT_STORAGE_KEY);
      }
      recentEmojis.onCodePointSelected(emoji);
    }
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
    if (keyEvent != null) {
      inputPanel.onKeyEvent(keyEvent);
    }
  }

  @Override
  public void openGifSearch() {
    AttachmentManager.selectGif(this, ConversationParentFragment.PICK_GIF, recipient.getId(), sendButton.getSelectedSendType(), isMms(), composeText.getTextTrimmed());
  }

  @Override
  public void onGifSelectSuccess(@NonNull Uri blobUri, int width, int height) {
    setMedia(blobUri,
             Objects.requireNonNull(MediaType.from(BlobProvider.getMimeType(blobUri))),
             width,
             height,
             false,
             true);
  }

  @Override
  public boolean isMms() {
    return !viewModel.isPushAvailable();
  }

  @Override
  public void openEmojiSearch() {
    if (emojiDrawerStub.resolved()) {
      emojiDrawerStub.get().onOpenEmojiSearch();
    }
  }

  @Override public void closeEmojiSearch() {
    if (emojiDrawerStub.resolved()) {
      emojiDrawerStub.get().onCloseEmojiSearch();
    }
  }

  @Override
  public void onVoiceNoteDraftPlay(@NonNull Uri audioUri, double progress) {
    voiceNoteMediaController.startSinglePlaybackForDraft(audioUri, threadId, progress);
  }

  @Override
  public void onVoiceNoteDraftPause(@NonNull Uri audioUri) {
    voiceNoteMediaController.pausePlayback(audioUri);
  }

  @Override
  public void onVoiceNoteDraftSeekTo(@NonNull Uri audioUri, double progress) {
    voiceNoteMediaController.seekToPosition(audioUri, progress);
  }

  @Override
  public void onVoiceNoteDraftDelete(@NonNull Uri audioUri) {
    voiceNoteMediaController.stopPlaybackAndReset(audioUri);
    draftViewModel.deleteVoiceNoteDraft();
  }

  @Override
  public @NonNull VoiceNoteMediaController getVoiceNoteMediaController() {
    return voiceNoteMediaController;
  }

  @Override public void openStickerSearch() {
    StickerSearchDialogFragment.show(getChildFragmentManager());
  }

  @Override
  public void bindScrollHelper(@NonNull RecyclerView recyclerView) {
    material3OnScrollHelper.attach(recyclerView);
  }

  @Override
  public void onMessageDetailsFragmentDismissed() {
    material3OnScrollHelper.setColorImmediate();
  }

  @Override
  public void sendAnywayAfterSafetyNumberChangedInBottomSheet(@NonNull List<? extends ContactSearchKey.RecipientSearchKey> destinations) {
    Log.d(TAG, "onSendAnywayAfterSafetyNumberChange");
    initializeIdentityRecords().addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        sendMessage(null);
      }
    });
  }

  // Listeners

  private final class DeleteCanceledVoiceNoteListener implements ListenableFuture.Listener<VoiceNoteDraft> {
    @Override
    public void onSuccess(final VoiceNoteDraft result) {
      draftViewModel.cancelEphemeralVoiceNoteDraft(result.asDraft());
    }

    @Override
    public void onFailure(ExecutionException e) {}
  }

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      Permissions.with(ConversationParentFragment.this)
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_24)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> {
                   composeText.clearFocus();
                   startActivityForResult(MediaSelectionActivity.camera(requireActivity(), sendButton.getSelectedSendType(), recipient.getId(), inputPanel.getQuote().isPresent()), MEDIA_SENDER);
                   requireActivity().overridePendingTransition(R.anim.camera_slide_from_bottom, R.anim.stationary);
                 })
                 .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
                 .execute();
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      String metricId = recipient.get().isGroup() ? SignalLocalMetrics.GroupMessageSend.start()
                                                  : SignalLocalMetrics.IndividualMessageSend.start();
      sendMessage(metricId);
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
          if (SignalStore.settings().isEnterKeySends() || event.isCtrlPressed()) {
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
        composeText.postDelayed(ConversationParentFragment.this::updateToggleButtonState, 50);
      }

      stickerViewModel.onInputTextUpdated(s.toString());
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
      if (hasFocus && container.getCurrentInput() == emojiDrawerStub.get()) {
        container.showSoftkey(composeText);
      }
    }
  }

  private class ComposeTextWatcher extends SimpleTextWatcher {

    private boolean typingStatusEnabled = true;

    private String previousText = "";

    @Override
    public void onTextChanged(@NonNull CharSequence text) {
      handleSaveDraftOnTextChange(text);
      handleTypingIndicatorOnTextChange(text.toString());
    }

    private void handleSaveDraftOnTextChange(@NonNull CharSequence text) {
      textDraftSaveDebouncer.publish(() -> draftViewModel.setTextDraft(StringUtil.trimSequence(text).toString(), MentionAnnotation.getMentionsFromAnnotations(text)));
    }

    private void handleTypingIndicatorOnTextChange(@NonNull String text) {
      if (typingStatusEnabled && threadId > 0 && viewModel.isPushAvailable() && !isSmsForced() && !recipient.get().isBlocked() && !recipient.get().isSelf()) {
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

    public void setTypingStatusEnabled(boolean enabled) {
      this.typingStatusEnabled = enabled;
    }
  }

  @Override
  public void onMessageRequest(@NonNull MessageRequestViewModel viewModel) {
    messageRequestBottomView.setAcceptOnClickListener(v -> viewModel.onAccept());
    messageRequestBottomView.setDeleteOnClickListener(v -> onMessageRequestDeleteClicked(viewModel));
    messageRequestBottomView.setBlockOnClickListener(v -> onMessageRequestBlockClicked(viewModel));
    messageRequestBottomView.setUnblockOnClickListener(v -> onMessageRequestUnblockClicked(viewModel));
    messageRequestBottomView.setGroupV1MigrationContinueListener(v -> GroupsV1MigrationInitiationBottomSheetDialogFragment.showForInitiation(getChildFragmentManager(), recipient.getId()));

    viewModel.getRequestReviewDisplayState().observe(getViewLifecycleOwner(), this::presentRequestReviewBanner);
    viewModel.getMessageData().observe(getViewLifecycleOwner(), this::presentMessageRequestState);
    viewModel.getFailures().observe(getViewLifecycleOwner(), this::showGroupChangeErrorToast);
    viewModel.getMessageRequestStatus().observe(getViewLifecycleOwner(), status -> {
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
        case BLOCKED_AND_REPORTED:
          hideMessageRequestBusy();
          Toast.makeText(requireContext(), R.string.ConversationActivity__reported_as_spam_and_blocked, Toast.LENGTH_SHORT).show();
          break;
        case DELETED:
        case BLOCKED:
          hideMessageRequestBusy();
          requireActivity().finish();
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

        Drawable drawable = ContextUtil.requireDrawable(requireContext(), R.drawable.ic_info_white_24).mutate();
        DrawableCompat.setTint(drawable, ContextCompat.getColor(requireContext(), R.color.signal_icon_tint_primary));

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
                            .show(getChildFragmentManager(), null);
  }

  private void handleReviewRequest(@NonNull RecipientId recipientId) {
    if (recipientId == Recipient.UNKNOWN.getId()) {
      return;
    }

    ReviewCardDialogFragment.createForReviewRequest(recipientId)
                            .show(getChildFragmentManager(), null);
  }

  private void showGroupChangeErrorToast(@NonNull GroupChangeFailureReason e) {
    Toast.makeText(requireContext(), GroupErrors.getUserDisplayMessage(e), Toast.LENGTH_LONG).show();
  }

  @Override
  public void handleReaction(@NonNull ConversationMessage conversationMessage,
                             @NonNull ConversationReactionOverlay.OnActionSelectedListener onActionSelectedListener,
                             @NonNull SelectedConversationModel selectedConversationModel,
                             @NonNull ConversationReactionOverlay.OnHideListener onHideListener)
  {
    reactionDelegate.setOnActionSelectedListener(onActionSelectedListener);
    reactionDelegate.setOnHideListener(onHideListener);
    reactionDelegate.show(requireActivity(), recipient.get(), conversationMessage, groupViewModel.isNonAdminInAnnouncementGroup(), selectedConversationModel);
    composeText.clearFocus();
    if (attachmentKeyboardStub.resolved()) {
      attachmentKeyboardStub.get().hide(true);
    }
  }

  @Override
  public void onMessageWithErrorClicked(@NonNull MessageRecord messageRecord) {
    if (messageRecord.isIdentityMismatchFailure()) {
      SafetyNumberBottomSheet
          .forMessageRecord(requireContext(), messageRecord)
          .show(getChildFragmentManager());
    } else if (messageRecord.hasFailedWithNetworkFailures()) {
      new MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.conversation_activity__message_could_not_be_sent)
          .setNegativeButton(android.R.string.cancel, null)
          .setPositiveButton(R.string.conversation_activity__send, (dialog, which) -> {
            SignalExecutors.BOUNDED.execute(() -> {
              MessageSender.resend(requireContext(), messageRecord);
            });
          })
          .show();
    } else {
      MessageDetailsFragment.create(messageRecord, recipient.getId()).show(getChildFragmentManager(), null);
    }
  }

  @Override
  public void onVoiceNotePause(@NonNull Uri uri) {
    voiceNoteMediaController.pausePlayback(uri);
  }

  @Override
  public void onVoiceNotePlay(@NonNull Uri uri, long messageId, double progress) {
    voiceNoteMediaController.startConsecutivePlayback(uri, messageId, progress);
  }

  @Override
  public void onVoiceNoteResume(@NonNull Uri uri, long messageId) {
    voiceNoteMediaController.resumePlayback(uri, messageId);
  }

  @Override
  public void onVoiceNoteSeekTo(@NonNull Uri uri, double progress) {
    voiceNoteMediaController.seekToPosition(uri, progress);
  }

  @Override
  public void onVoiceNotePlaybackSpeedChanged(@NonNull Uri uri, float speed) {
    voiceNoteMediaController.setPlaybackSpeed(uri, speed);
  }

  @Override
  public void onRegisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver) {
    voiceNoteMediaController.getVoiceNotePlaybackState().observe(getViewLifecycleOwner(), onPlaybackStartObserver);
  }

  @Override
  public void onUnregisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver) {
    voiceNoteMediaController.getVoiceNotePlaybackState().removeObserver(onPlaybackStartObserver);
  }

  @Override
  public void onInviteToSignal() {
    handleInviteLink();
  }

  @Override
  public void onCursorChanged() {
    if (!reactionDelegate.isShowing()) {
      return;
    }

    SimpleTask.run(() -> {
          //noinspection CodeBlock2Expr
          return SignalDatabase.mmsSms().checkMessageExists(reactionDelegate.getMessageRecord());
        }, messageExists -> {
          if (!messageExists) {
            reactionDelegate.hide();
          }
        });
  }

  @Override
  public int getSendButtonTint() {
    return getSendButtonColor(sendButton.getSelectedSendType());
  }

  @Override
  public boolean isKeyboardOpen() {
    return container.isKeyboardOpen();
  }

  @Override
  public boolean isAttachmentKeyboardOpen() {
    return attachmentKeyboardStub.resolved() && attachmentKeyboardStub.get().isShowing();
  }

  @Override
  public void openAttachmentKeyboard() {
    attachmentKeyboardStub.get().show(container.getKeyboardHeight(), true);
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
    draftViewModel.setThreadId(threadId);
  }

  @Override
  public void handleReplyMessage(ConversationMessage conversationMessage) {
    if (isSearchRequested) {
      searchViewItem.collapseActionView();
    }

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
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(requireContext(), contact.getAvatarAttachment()));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          body,
                          slideDeck,
                          MessageRecordUtil.getRecordQuoteType(messageRecord));

    } else if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getLinkPreviews().isEmpty()) {
      LinkPreview linkPreview = ((MmsMessageRecord) messageRecord).getLinkPreviews().get(0);
      SlideDeck   slideDeck   = new SlideDeck();

      if (linkPreview.getThumbnail().isPresent()) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(requireContext(), linkPreview.getThumbnail().get()));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          conversationMessage.getDisplayBody(requireContext()),
                          slideDeck,
                          MessageRecordUtil.getRecordQuoteType(messageRecord));
    } else {
      SlideDeck slideDeck = messageRecord.isMms() ? ((MmsMessageRecord) messageRecord).getSlideDeck() : new SlideDeck();

      if (messageRecord.isMms() && ((MmsMessageRecord) messageRecord).isViewOnce()) {
        Attachment attachment = new TombstoneAttachment(MediaUtil.VIEW_ONCE, true);
        slideDeck = new SlideDeck();
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(requireContext(), attachment));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          conversationMessage.getDisplayBody(requireContext()),
                          slideDeck,
                          MessageRecordUtil.getRecordQuoteType(messageRecord));
    }

    inputPanel.clickOnComposeInput();
  }

  @Override
  public void onMessageActionToolbarOpened() {
    searchViewItem.collapseActionView();
    toolbar.setVisibility(View.GONE);
  }

  @Override
  public void onMessageActionToolbarClosed() {
    toolbar.setVisibility(View.VISIBLE);
  }

  @Override
  public void onBottomActionBarVisibilityChanged(int visibility) {
    inputPanel.setHideForSelection(visibility == View.VISIBLE);
  }

  @Override
  public void onForwardClicked()  {
    inputPanel.clearQuote();
  }

  @Override
  public void onAttachmentChanged() {
    handleSecurityChange(viewModel.getConversationStateSnapshot().getSecurityInfo());
    updateToggleButtonState();
    updateLinkPreviewState();
  }

  @Override
  public void onLocationRemoved() {
    draftViewModel.clearLocationDraft();
  }

  private void onMessageRequestDeleteClicked(@NonNull MessageRequestViewModel requestModel) {
    Recipient recipient = requestModel.getRecipient().getValue();
    if (recipient == null) {
      Log.w(TAG, "[onMessageRequestDeleteClicked] No recipient!");
      return;
    }

    AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext())
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

    BlockUnblockDialog.showBlockAndReportSpamFor(requireContext(), getLifecycle(), recipient, requestModel::onBlock, requestModel::onBlockAndReportSpam);
  }

  private void onMessageRequestUnblockClicked(@NonNull MessageRequestViewModel requestModel) {
    Recipient recipient = requestModel.getRecipient().getValue();
    if (recipient == null) {
      Log.w(TAG, "[onMessageRequestUnblockClicked] No recipient!");
      return;
    }

    BlockUnblockDialog.showUnblockFor(requireContext(), getLifecycle(), recipient, requestModel::onUnblock);
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

    long       expiresIn      = TimeUnit.SECONDS.toMillis(recipient.get().getExpiresInSeconds());
    boolean    initiating     = threadId == -1;
    SlideDeck  slideDeck      = new SlideDeck();

    if (MediaUtil.isGif(contentType)) {
      slideDeck.addSlide(new GifSlide(requireContext(), uri, 0, details.width, details.height, details.hasTransparency, null));
    } else if (MediaUtil.isImageType(contentType)) {
      slideDeck.addSlide(new ImageSlide(requireContext(), uri, contentType, 0, details.width, details.height, details.hasTransparency, null, null));
    } else {
      throw new AssertionError("Only images are supported!");
    }

    sendMediaMessage(recipient.getId(),
                     sendButton.getSelectedSendType(),
                     "",
                     slideDeck,
                     null,
                     Collections.emptyList(),
                     Collections.emptyList(),
                     composeText.getMentions(),
                     expiresIn,
                     false,
                     initiating,
                     false,
                     null);
  }

  private class UnverifiedDismissedListener implements UnverifiedBannerView.DismissListener {
    @Override
    public void onDismissed(final List<IdentityRecord> unverifiedIdentities) {
      SimpleTask.run(() -> {
        try (SignalSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
          for (IdentityRecord identityRecord : unverifiedIdentities) {
            ApplicationDependencies.getProtocolStore().aci().identities().setVerified(identityRecord.getRecipientId(),
                                                                                      identityRecord.getIdentityKey(),
                                                                                      VerifiedStatus.DEFAULT);
          }
        }
        return null;
      }, nothing -> initializeIdentityRecords());
    }
  }

  private class UnverifiedClickedListener implements UnverifiedBannerView.ClickListener {
    @Override
    public void onClicked(final List<IdentityRecord> unverifiedIdentities) {
      Log.i(TAG, "onClicked: " + unverifiedIdentities.size());
      if (unverifiedIdentities.size() == 1) {
        startActivity(VerifyIdentityActivity.newIntent(requireContext(), unverifiedIdentities.get(0), false));
      } else {
        String[] unverifiedNames = new String[unverifiedIdentities.size()];

        for (int i=0;i<unverifiedIdentities.size();i++) {
          unverifiedNames[i] = Recipient.resolved(unverifiedIdentities.get(i).getRecipientId()).getDisplayName(requireContext());
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setIcon(R.drawable.ic_warning);
        builder.setTitle("No longer verified");
        builder.setItems(unverifiedNames, (dialog, which) -> {
          startActivity(VerifyIdentityActivity.newIntent(requireContext(), unverifiedIdentities.get(which), false));
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
      QuoteId quoteId = QuoteId.deserialize(ApplicationDependencies.getApplication(), serialized);

      if (quoteId == null) {
        return null;
      }

      Context context = ApplicationDependencies.getApplication();

      MessageRecord messageRecord = SignalDatabase.mmsSms().getMessageFor(quoteId.getId(), quoteId.getAuthor());
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

  private final class VoiceNotePlayerViewListener implements VoiceNotePlayerView.Listener {
    @Override
    public void onCloseRequested(@NonNull Uri uri) {
      voiceNoteMediaController.stopPlaybackAndReset(uri);
    }

    @Override
    public void onSpeedChangeRequested(@NonNull Uri uri, float speed) {
      voiceNoteMediaController.setPlaybackSpeed(uri, speed);
    }

    @Override
    public void onPlay(@NonNull Uri uri, long messageId, double position) {
      voiceNoteMediaController.startSinglePlayback(uri, messageId, position);
    }

    @Override
    public void onPause(@NonNull Uri uri) {
      voiceNoteMediaController.pausePlayback(uri);
    }

    @Override
    public void onNavigateToMessage(long threadId, @NonNull RecipientId threadRecipientId, @NonNull RecipientId senderId, long messageTimestamp, long messagePositionInThread) {
      if (threadId != ConversationParentFragment.this.threadId) {
        startActivity(ConversationIntents.createBuilder(requireActivity(), threadRecipientId, threadId)
                                         .withStartingPosition((int) messagePositionInThread)
                                         .build());
      } else {
        fragment.jumpToMessage(senderId, messageTimestamp, () -> { });
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
      inputPanel.setHideForMessageRequestState(false);
    } else if (isPushGroupV1Conversation() && !isActiveGroup()) {
      Log.d(TAG, "[presentMessageRequestState] Inactive push group V1, so ignoring provided state.");
      messageRequestBottomView.setVisibility(View.GONE);
      inputPanel.setHideForMessageRequestState(false);
    } else if (messageData == null) {
      Log.d(TAG, "[presentMessageRequestState] Null messageData. Ignoring.");
    } else if (messageData.getMessageState() == MessageRequestState.NONE) {
      Log.d(TAG, "[presentMessageRequestState] No message request necessary.");
      messageRequestBottomView.setVisibility(View.GONE);
      inputPanel.setHideForMessageRequestState(false);
    } else {
      Log.d(TAG, "[presentMessageRequestState] " + messageData.getMessageState());
      messageRequestBottomView.setMessageData(messageData);
      messageRequestBottomView.setVisibility(View.VISIBLE);
      noLongerMemberBanner.setVisibility(View.GONE);
      inputPanel.setHideForMessageRequestState(true);
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

  public interface Callback {
    long getShareDataTimestamp();

    void setShareDataTimestamp(long timestamp);

    default void onInitializeToolbar(@NonNull Toolbar toolbar) {
    }

    default void onSendComplete(long threadId) {
    }

    /**
     * @return true to skip built in, otherwise false.
     */
    default boolean onUpdateReminders() {
      return false;
    }

    default boolean isInBubble() {
      return false;
    }
  }
}
