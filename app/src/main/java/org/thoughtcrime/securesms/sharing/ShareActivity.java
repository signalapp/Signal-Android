/*
 * Copyright (C) 2014-2017 Open Whisper Systems
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

package org.thoughtcrime.securesms.sharing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import android.provider.Telephony;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.PromptMmsActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.components.identity.UntrustedSendDialog;
import org.thoughtcrime.securesms.components.identity.UnverifiedSendDialog;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point for sharing content into the app.
 *
 * Handles contact selection when necessary, but also serves as an entry point for when the contact
 * is known (such as choosing someone in a direct share).
 */
public class ShareActivity extends PassphraseRequiredActivity
    implements ContactSelectionListFragment.OnContactSelectedListener, SwipeRefreshLayout.OnRefreshListener
{
  private static final String TAG = ShareActivity.class.getSimpleName();

  public static final String EXTRA_THREAD_ID          = "thread_id";
  public static final String EXTRA_RECIPIENT_ID       = "recipient_id";
  public static final String EXTRA_DISTRIBUTION_TYPE  = "distribution_type";

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ContactSelectionListFragment contactsFragment;
  private SearchToolbar                searchToolbar;
  private ImageView                    searchAction;
  private ProgressBar                  progressBar;

  private ShareViewModel viewModel;

  private Toolbar toolbar;
  private Drawable backIconToolbar;

  private IdentityRecordList identityRecords = new IdentityRecordList(Collections.emptyList());

  private String textExtra;
  private boolean isMedia;
  private SettableFuture<SlideDeck> slideDeckFuture;
  private List<Long> threadIdList;
  private List<Recipient> recipientList;
  private boolean isDefaultSms;
  private boolean isMmsEnabled;
  private List<Boolean> isSecureTextList;
  private Context context;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    if (!getIntent().hasExtra(ContactSelectionListFragment.DISPLAY_MODE)) {
      int mode = DisplayMode.FLAG_PUSH | DisplayMode.FLAG_ACTIVE_GROUPS | DisplayMode.FLAG_SELF;

      if (TextSecurePreferences.isSmsEnabled(this))  {
        mode |= DisplayMode.FLAG_SMS;
      }

      getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE, mode);
    }

    getIntent().putExtra(ContactSelectionListFragment.REFRESHABLE, false);
    getIntent().putExtra(ContactSelectionListFragment.RECENTS, true);
    getIntent().putExtra(ContactSelectionListFragment.MULTI_SELECT, isMulti());

    setContentView(R.layout.share_activity);

    initializeToolbar();
    initializeResources();
    initializeSearch();
    initializeViewModel();
    initializeMedia();

    handleDestination();
  }

  @Override
  public void onResume() {
    Log.i(TAG, "onResume()");
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
  }

  @Override
  public void onStop() {
    super.onStop();

    if (!isFinishing()) {
      finish();
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onBackPressed() {
    if (searchToolbar.isVisible()) searchToolbar.collapse();
    else                           super.onBackPressed();
  }

  private boolean isMulti() {
    return getIntent().getBooleanExtra(ContactSelectionListFragment.MULTI_SELECT, false);
  }

  @Override
  public void onContactSelected(Optional<RecipientId> recipientId, String number, boolean isLongClick) {
    if (isMulti()){
      return;
    }

    if(isLongClick){
      Log.w(TAG, "[onContactSelected] Switching to multiple share.");
      getIntent().putExtra(ContactSelectionListFragment.MULTI_SELECT, true);
      contactsFragment.onMultiSelectChanged();
      getToolbar().setNavigationIcon(R.drawable.ic_check_24);
    } else {
      openConversationWithRecipient(recipientId, number);
    }
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, String number, boolean isLongClick) {
    if (isMulti() && contactsFragment.getSelectedContacts().isEmpty()){
      Log.w(TAG, "[onContactDeselected] Switching from multiple share to single share.");
      getIntent().putExtra(ContactSelectionListFragment.MULTI_SELECT, false);
      contactsFragment.onMultiSelectChanged();
      getToolbar().setNavigationIcon(backIconToolbar);
    }
  }

  @Override
  public void onRefresh() {
  }

  private Toolbar getToolbar() {
    return toolbar;
  }

  private void initializeToolbar() {
    toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    ActionBar actionBar = getSupportActionBar();

    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    backIconToolbar = toolbar.getNavigationIcon();
    if(isMulti()) {
      toolbar.setNavigationIcon(R.drawable.ic_check_24);
    }
  }

  private void initializeResources() {
    searchToolbar    = findViewById(R.id.search_toolbar);
    searchAction     = findViewById(R.id.search_action);
    progressBar      = findViewById(R.id.progress_bar);
    contactsFragment = (ContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);

    if (contactsFragment == null) {
      throw new IllegalStateException("Could not find contacts fragment!");
    }

    contactsFragment.setOnContactSelectedListener(this);
    contactsFragment.setOnRefreshListener(this);

    getToolbar().setNavigationOnClickListener(v -> {
      if(isMulti()) {
        Log.w(TAG, "[OnClickListener] Multi share sendMessageToContact.");
        handleMultipleSelectedContacts();
      } else {
        finish();
      }
    });
  }

  private void openConversationWithRecipient(Optional<RecipientId> recipientId, String number){
    SimpleTask.run(this.getLifecycle(), () -> {
      Recipient recipient;
      if (recipientId.isPresent()) {
        recipient = Recipient.resolved(recipientId.get());
      } else {
        Log.i(TAG, "[onContactSelected] Maybe creating a new recipient.");
        recipient = Recipient.external(this, number);
      }

      long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);
      return new Pair<>(existingThread, recipient);
    }, result -> onDestinationChosen(result.first(), result.second().getId()));
  }

  private void initializeViewModel() {
    this.viewModel = ViewModelProviders.of(this, new ShareViewModel.Factory()).get(ShareViewModel.class);
  }

  private void initializeSearch() {
    //noinspection IntegerDivisionInFloatingPointContext
    searchAction.setOnClickListener(v -> searchToolbar.display(searchAction.getX() + (searchAction.getWidth() / 2),
                                                               searchAction.getY() + (searchAction.getHeight() / 2)));

    searchToolbar.setListener(new SearchToolbar.SearchListener() {
      @Override
      public void onSearchTextChange(String text) {
        if (contactsFragment != null) {
          contactsFragment.setQueryFilter(text);
        }
      }

      @Override
      public void onSearchClosed() {
        if (contactsFragment != null) {
          contactsFragment.resetQueryFilter();
        }
      }
    });
  }

  private void initializeMedia() {
    if (Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction())) {
      Log.i(TAG, "Multiple media share.");
      List<Uri> uris = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);

      viewModel.onMultipleMediaShared(uris);
    } else if (Intent.ACTION_SEND.equals(getIntent().getAction()) || getIntent().hasExtra(Intent.EXTRA_STREAM)) {
      Log.i(TAG, "Single media share.");
      Uri    uri  = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
      String type = getIntent().getType();

      viewModel.onSingleMediaShared(uri, type);
    } else {
      Log.i(TAG, "Internal media share.");
      viewModel.onNonExternalShare();
    }
  }

  private void handleDestination() {
    Intent      intent           = getIntent();
    long        threadId         = intent.getLongExtra(EXTRA_THREAD_ID, -1);
    int         distributionType = intent.getIntExtra(EXTRA_DISTRIBUTION_TYPE, -1);
    RecipientId recipientId      = null;

    if (intent.hasExtra(EXTRA_RECIPIENT_ID)) {
      recipientId = RecipientId.from(intent.getStringExtra(EXTRA_RECIPIENT_ID));
    }

    boolean hasPreexistingDestination = threadId != -1 && recipientId != null && distributionType != -1;

    if (hasPreexistingDestination) {
      if (contactsFragment.getView() != null) {
        contactsFragment.getView().setVisibility(View.GONE);
      }
      onDestinationChosen(threadId, recipientId);
    }
  }

  private void onDestinationChosen(long threadId, @NonNull RecipientId recipientId) {
    if (!viewModel.isExternalShare()) {
      openConversation(threadId, recipientId, null);
      return;
    }

    AtomicReference<AlertDialog> progressWheel = new AtomicReference<>();

    if (viewModel.getShareData().getValue() == null) {
      progressWheel.set(SimpleProgressDialog.show(this));
    }

    viewModel.getShareData().observe(this, (data) -> {
      if (data == null) return;

      if (progressWheel.get() != null) {
        progressWheel.get().dismiss();
        progressWheel.set(null);
      }

      if (!data.isPresent()) {
        Log.w(TAG, "No data to share!");
        Toast.makeText(this, R.string.ShareActivity_multiple_attachments_are_only_supported, Toast.LENGTH_LONG).show();
        finish();
        return;
      }

      openConversation(threadId, recipientId, data.get());
    });
  }


  private void openConversation(long threadId, @NonNull RecipientId recipientId, @Nullable ShareData shareData) {
    Intent           intent          = new Intent(this, ConversationActivity.class);
    String           textExtra       = getIntent().getStringExtra(Intent.EXTRA_TEXT);
    ArrayList<Media> mediaExtra      = getIntent().getParcelableArrayListExtra(ConversationActivity.MEDIA_EXTRA);
    StickerLocator   stickerExtra    = getIntent().getParcelableExtra(ConversationActivity.STICKER_EXTRA);
    boolean          borderlessExtra = getIntent().getBooleanExtra(ConversationActivity.BORDERLESS_EXTRA, false);

    intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);
    intent.putExtra(ConversationActivity.MEDIA_EXTRA, mediaExtra);
    intent.putExtra(ConversationActivity.STICKER_EXTRA, stickerExtra);
    intent.putExtra(ConversationActivity.BORDERLESS_EXTRA, borderlessExtra);

    if (shareData != null && shareData.isForIntent()) {
      Log.i(TAG, "Shared data is a single file.");
      intent.setDataAndType(shareData.getUri(), shareData.getMimeType());
    } else if (shareData != null && shareData.isForMedia()) {
      Log.i(TAG, "Shared data is set of media.");
      intent.putExtra(ConversationActivity.MEDIA_EXTRA, shareData.getMedia());
    } else if (shareData != null && shareData.isForPrimitive()) {
      Log.i(TAG, "Shared data is a primitive type.");
    } else {
      Log.i(TAG, "Shared data was not external.");
    }

    intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipientId);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

    viewModel.onSuccessulShare();

    startActivity(intent);
  }

  private void handleMultipleSelectedContacts(){
    ArrayList<Media> mediaExtra   = getIntent().getParcelableArrayListExtra(ConversationActivity.MEDIA_EXTRA);
    StickerLocator   stickerExtra = getIntent().getParcelableExtra(ConversationActivity.STICKER_EXTRA);
    slideDeckFuture = new SettableFuture<>(null);

    if (!viewModel.isExternalShare()) {
      isMedia = (mediaExtra != null || stickerExtra != null);
      if(isMedia) {
        setSlideDeckForMediaMessageAsync(null, mediaExtra, stickerExtra);
      }
      checkPermissionsAndSendMessage();
      return;
    }

    AtomicReference<AlertDialog> progressWheel = new AtomicReference<>();

    if (viewModel.getShareData().getValue() == null) {
      progressWheel.set(SimpleProgressDialog.show(this));
    }

    viewModel.getShareData().observe(this, (data) -> {
      if (data == null) return;

      if (progressWheel.get() != null) {
        progressWheel.get().dismiss();
        progressWheel.set(null);
      }

      if (!data.isPresent()) {
        Log.w(TAG, "No data to share!");
        Toast.makeText(this, R.string.ShareActivity_multiple_attachments_are_only_supported, Toast.LENGTH_LONG).show();
        finish();
        return;
      }

      ShareData shareData = data.get();

      isMedia = (shareData != null || mediaExtra != null || stickerExtra != null);
      if(isMedia) {
        setSlideDeckForMediaMessageAsync(shareData, mediaExtra, stickerExtra);
      }
      checkPermissionsAndSendMessage();
    });

  }

  private void checkPermissionsAndSendMessage() {
    SimpleTask.run(this.getLifecycle(), () -> {
      initializeMetaDataForSend();
      initializeIdentityRecords();
      return null;
    }, result -> {
      if (!isDefaultSms && isSecureTextList.contains(false)) {
        showDefaultSmsPrompt();
        return;
      }

      for (Recipient recipient : recipientList) {
        if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !isMmsEnabled) {
          handleManualMmsRequired();
          return;
        }
      }

      if (identityRecords.isUnverified()) {
        handleUnverifiedRecipients();
      } else if (identityRecords.isUntrusted()) {
        handleUntrustedRecipients();
      } else {
        sendMessageToSelectedContacts();
      }
    });
  }

  private void sendMessageToSelectedContacts(){
    String[] permissionsNeeded = new String[]{Manifest.permission.SEND_SMS};
    if(isMedia) {
      permissionsNeeded = new String[]{permissionsNeeded[0], Manifest.permission.READ_SMS};
    }

    Permissions.with(this)
      .request(permissionsNeeded)
      .ifNecessary(isSecureTextList.contains(false))
      .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
      .onAllGranted(() -> {
        initializeProgressBar();
        SimpleTask.run(this.getLifecycle(), () -> {
          for (int i = 0; i < recipientList.size(); ++i) {
            sendMessage(threadIdList.get(i), recipientList.get(i), isSecureTextList.get(i));
            progressBar.incrementProgressBy(1);
          }
          return null;
        }, result -> sendComplete());
      })
      .execute();
  }

  private void initializeProgressBar(){
    progressBar.setMax(recipientList.size());
    progressBar.setProgress(0);
    if(recipientList.size() > 1) {
      progressBar.setVisibility(View.VISIBLE);
    }
  }

  private void initializeMetaDataForSend(){
    textExtra = getIntent().getStringExtra(Intent.EXTRA_TEXT);

    Pair<List<Long>, List<Recipient>> threadIdAndRecipient = getThreadIdAndRecipient(contactsFragment.getSelectedContacts());
    threadIdList = threadIdAndRecipient.first();
    recipientList = threadIdAndRecipient.second();

    isDefaultSms = Util.isDefaultSmsProvider(this);

    isMmsEnabled = Util.isMmsCapable(this);

    isSecureTextList = getSecurity(recipientList);

    context = getApplicationContext();
  }

  private @NonNull Pair<List<Long>, List<Recipient>> getThreadIdAndRecipient(@NonNull List<SelectedContact> contactList) {
    List<Long> threadIdList = new ArrayList<>();
    List<Recipient> recipientList = new ArrayList<>();
    for (SelectedContact contact : contactList) {
      RecipientId recipientId = contact.getOrCreateRecipientId(this.getBaseContext());
      Recipient recipient = Recipient.resolved(recipientId);
      Long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);
      threadIdList.add(existingThread);
      recipientList.add(recipient);
    }
    return new Pair<>(threadIdList, recipientList);
  }

  private @NonNull List<Boolean> getSecurity(@NonNull List<Recipient> recipientList)
  {
    List<Boolean> isSecureTextList = new ArrayList<>();
    for (Recipient recipient : recipientList) {
      Context context = ShareActivity.this;
      Log.i(TAG, "Resolving registered state...");
      RecipientDatabase.RegisteredState registeredState;

      if (recipient.isPushGroup()) {
        Log.i(TAG, "Push group recipient...");
        registeredState = RecipientDatabase.RegisteredState.REGISTERED;
      } else {
        Log.i(TAG, "Checking through resolved recipient");
        registeredState = recipient.resolve().getRegistered();
      }

      Log.i(TAG, "Resolved registered state: " + registeredState);
      boolean signalEnabled = TextSecurePreferences.isPushRegistered(context);

      if (registeredState == RecipientDatabase.RegisteredState.UNKNOWN) {
        try {
          Log.i(TAG, "Refreshing directory for user: " + recipient.getId().serialize());
          registeredState = DirectoryHelper.refreshDirectoryFor(context, recipient, false);
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      Log.i(TAG, "Returning registered state...");
      isSecureTextList.add(registeredState == RecipientDatabase.RegisteredState.REGISTERED && signalEnabled);
    }
    return isSecureTextList;
  }

  private void sendMessage(long threadId, @NonNull Recipient recipient, boolean isSecureText){

      Log.w(TAG, "[sendMessage] Sharing message in single conversation or group.");

      int  subscriptionId = recipient.getDefaultSubscriptionId().or(-1);
      long expiresIn      = recipient.getExpireMessages() * 1000L;

      if(!isMedia){
        sendTextMessage(recipient, expiresIn, subscriptionId, threadId, isSecureText);
      } else {
        SlideDeck slideDeck;
        try {
          slideDeck = slideDeckFuture.get();
        } catch (InterruptedException | ExecutionException e) {
          e.printStackTrace();
          return;
        }
        if (slideDeck == null){
          Log.w(TAG, "[sendMessage] No data to share!");
        } else {
          sendMediaMessage(recipient, slideDeck, subscriptionId, expiresIn, threadId,
            isSecureText);
        }
      }
    }

  private void sendTextMessage(Recipient recipient, long expiresIn, int subscriptionId,
                               long threadId, boolean isSecureText) {
    if (recipient.resolve().isGroup()){
      Log.w(TAG, "[sendTextMessage] Sharing text message in group.");

      OutgoingMediaMessage message = new OutgoingMediaMessage(recipient, textExtra, new LinkedList<>(),
              System.currentTimeMillis(), subscriptionId, expiresIn,false, 0,
              null, Collections.emptyList(), Collections.emptyList(),
              Collections.emptyList(), Collections.emptyList());

      if(isSecureText){
        message = new OutgoingSecureMediaMessage(message);
      }

      MessageSender.send(ShareActivity.this, message, threadId, false, null);
    } else {
      Log.w(TAG, "[sendTextMessage] Sharing regular text message.");
      OutgoingTextMessage message;

      if (isSecureText) {
        message = new OutgoingEncryptedMessage(recipient, textExtra, expiresIn);
      } else {
        message = new OutgoingTextMessage(recipient, textExtra, expiresIn, subscriptionId);
      }

      MessageSender.send(ShareActivity.this, message, threadId, false, null);
    }
  }

  private void sendMediaMessage(Recipient recipient, SlideDeck slideDeck, int subscriptionId, long expiresIn, long threadId,
                                boolean isSecureText) {
    Log.w(TAG, "[sendMediaMessage] Sharing text message in group.");
    int distributionType = ThreadDatabase.DistributionTypes.DEFAULT;
    OutgoingMediaMessage message = new OutgoingMediaMessage(recipient, slideDeck, textExtra,
            System.currentTimeMillis(), subscriptionId, expiresIn,false, distributionType,
            null, Collections.emptyList(), Collections.emptyList());

    if(isSecureText){
      message = new OutgoingSecureMediaMessage(message);
    }

    MessageSender.send(ShareActivity.this, message, threadId, false, null);
  }

  @SuppressLint("StaticFieldLeak")
  private void setSlideDeckForMediaMessageAsync(ShareData shareData, ArrayList<Media> mediaExtra, StickerLocator stickerLocator) {
    slideDeckFuture = new SettableFuture<>();

    new AsyncTask<Void, Void, SlideDeck>() {
      @Override protected SlideDeck doInBackground(Void... params) {
        Log.w(TAG, "[getSlideDeckForMediaMessageAsync] Retrieving media data to share.");
        return getSlideDeckForMediaMessage(shareData, mediaExtra, stickerLocator);
      }
        protected void onPostExecute(SlideDeck result) {
          slideDeckFuture.set(result);
        }

      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private SlideDeck getSlideDeckForMediaMessage(ShareData shareData, ArrayList<Media> mediaExtra, StickerLocator stickerLocator) {
    SlideDeck slideDeck = null;
    if(shareData != null){
      if (shareData.isForIntent()){
        if (stickerLocator != null) {
          slideDeck = getSlideDeckFromStickerLocator(shareData, stickerLocator);
        } else {
          slideDeck = getSlideDeckFromMediaArray(convertShareDataToMediaArray(shareData));
        }
      } else if (shareData.isForMedia()) {
        slideDeck = getSlideDeckFromMediaArray(shareData.getMedia());
      }
    } else {
      if(mediaExtra != null){
        slideDeck = getSlideDeckFromMediaArray(mediaExtra);
      }
    }
    return slideDeck;
  }

  private SlideDeck getSlideDeckFromStickerLocator(ShareData shareData, StickerLocator stickerLocator) {
    Slide slide;
    SlideDeck slideDeck;
    slide = new StickerSlide(ShareActivity.this, shareData.getUri(), 0, stickerLocator);
    slideDeck  = new SlideDeck();
    slideDeck.addSlide(slide);
    return slideDeck;
  }

  private ArrayList<Media> convertShareDataToMediaArray(ShareData shareData) {
    Media mediaItem = new Media(shareData.getUri(), shareData.getMimeType(), 0, 0, 0, 0, 0,
            false, null, null, null);
    ArrayList<Media> mediaArray  = new ArrayList<>();
    mediaArray.add(mediaItem);
    return mediaArray;
  }

  private @Nullable SlideDeck getSlideDeckFromMediaArray(ArrayList<Media> mediaArray) {
    SlideDeck slideDeck  = new SlideDeck();
    Slide slide;
    try {
      for (Media mediaItem : mediaArray) {
        AttachmentManager.MediaType mediaType = AttachmentManager.MediaType.from(mediaItem.getMimeType());
        slide = AttachmentManager.getSlideFromUri(ShareActivity.this, mediaType, mediaItem.getUri(), 0, 0);
        slideDeck.addSlide(slide);
      }
    } catch (IOException e) {
      Log.w(TAG, e);
      return null;
    }
    return slideDeck;
  }

  private void showDefaultSmsPrompt() {
    new AlertDialog.Builder(this)
      .setMessage(R.string.ConversationActivity_signal_cannot_sent_sms_mms_messages_because_it_is_not_your_default_sms_app)
      .setNegativeButton(R.string.ConversationActivity_no, (dialog, which) -> dialog.dismiss())
      .setPositiveButton(R.string.ConversationActivity_yes, (dialog, which) -> handleMakeDefaultSms())
      .show();
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private void handleMakeDefaultSms() {
    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
    startActivity(intent);
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings,
      Toast.LENGTH_LONG).show();

    Bundle extras = getIntent().getExtras();
    Intent intent = new Intent(this, PromptMmsActivity.class);
    if (extras != null) intent.putExtras(extras);
    startActivity(intent);
  }

  private void handleUnverifiedRecipients() {
    List<Recipient>      unverifiedRecipients = identityRecords.getUnverifiedRecipients();
    List<IdentityDatabase.IdentityRecord> unverifiedRecords    = identityRecords.getUnverifiedRecords();
    String               message              = IdentityUtil.getUnverifiedSendDialogDescription(this, unverifiedRecipients);

    if (message == null) return;

    //noinspection CodeBlock2Expr
    new UnverifiedSendDialog(this, message, unverifiedRecords,
      this::sendMessageToSelectedContacts).show();

  }

  private void handleUntrustedRecipients() {
    List<Recipient>      untrustedRecipients = identityRecords.getUntrustedRecipients();
    List<IdentityDatabase.IdentityRecord> untrustedRecords    = identityRecords.getUntrustedRecords();
    String               untrustedMessage    = IdentityUtil.getUntrustedSendDialogDescription(this, untrustedRecipients);

    if (untrustedMessage == null) return;

    //noinspection CodeBlock2Expr
    new UntrustedSendDialog(this, untrustedMessage, untrustedRecords,
      this::sendMessageToSelectedContacts).show();
  }


  private void initializeIdentityRecords() {
    IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(ShareActivity.this);
    List<Recipient> recipients = new LinkedList<>();

    for(Recipient recipientToCheck : recipientList) {
      if (recipientToCheck.isGroup()) {
        recipients.addAll(DatabaseFactory.getGroupDatabase(ShareActivity.this)
          .getGroupMembers(recipientToCheck.requireGroupId(), GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF));
      } else {
        recipients.add(recipientToCheck);
      }
    }

    identityRecords = identityDatabase.getIdentities(recipients);
  }

  private void sendComplete(){
    finish();
    startActivity(new Intent(this, MainActivity.class));
  }

}