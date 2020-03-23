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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.contacts.SelectedContact;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
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

  private ShareViewModel viewModel;

  private Toolbar toolbar;

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

  private void restartActivity(Intent intent){
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    finish();
    overridePendingTransition(0, 0);

    startActivity(intent);
    overridePendingTransition(0, 0);
  }

  private Intent getIntentForMultiSelect(Optional<RecipientId> recipientId, String number){
    Intent intent = getIntent();
    intent.putExtra(ContactSelectionListFragment.MULTI_SELECT, true);

    RecipientId resolvedRecipientId = recipientId.or(Recipient.external(this, number).getId());
    intent.putExtra(ContactSelectionListFragment.EXTRA_PRESELECTED_RECIPIENT_ID, resolvedRecipientId);

    intent.putExtra(ContactSelectionListFragment.EXTRA_PRESELECTED_NUMBER, number);

    return intent;
  }

  private Intent getIntentForSingleSelect(){
    Intent intent = getIntent();
    intent.putExtra(ContactSelectionListFragment.MULTI_SELECT, false);

    intent.removeExtra(ContactSelectionListFragment.EXTRA_PRESELECTED_RECIPIENT_ID);
    intent.removeExtra(ContactSelectionListFragment.EXTRA_PRESELECTED_NUMBER);

    return intent;
  }

  @Override
  public void onContactSelected(Optional<RecipientId> recipientId, String number, boolean isLongClick) {
    if (isMulti()){
      return;
    }

    if(isLongClick){
      Log.w(TAG, "[onContactSelected] Switching to multiple share.");
      restartActivity(getIntentForMultiSelect(recipientId, number));
    } else {
      openConversationWithRecipient(recipientId, number);
    }
  }

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, String number, boolean isLongClick) {
    if (isMulti() && contactsFragment.getSelectedContacts().isEmpty()){
      Log.w(TAG, "[onContactDeselected] Switching from multiple share to single share.");
      restartActivity(getIntentForSingleSelect());
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
  }

  private void initializeResources() {
    searchToolbar    = findViewById(R.id.search_toolbar);
    searchAction     = findViewById(R.id.search_action);
    contactsFragment = (ContactSelectionListFragment) getSupportFragmentManager().findFragmentById(R.id.contact_selection_list_fragment);

    if (contactsFragment == null) {
      throw new IllegalStateException("Could not find contacts fragment!");
    }

    contactsFragment.setOnContactSelectedListener(this);
    contactsFragment.setOnRefreshListener(this);

    if(isMulti()) {
      getToolbar().setNavigationIcon(R.drawable.ic_check_24);
      getToolbar().setNavigationOnClickListener(v -> {
        Log.w(TAG, "[OnClickListener] Multi share sendMessageToContact.");
        for (SelectedContact contact : contactsFragment.getSelectedContacts()) {
          sendMessageToContact(contact);
        }

        startActivity(new Intent(this, MainActivity.class));
      });
    }

  }

  private void sendMessageToContact(SelectedContact contact) {
    SimpleTask.run(this.getLifecycle(), () -> {
      RecipientId recipientId = contact.getOrCreateRecipientId(this.getBaseContext());
      Recipient recipient = Recipient.resolved(recipientId);
      long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipient);
      return new Pair<>(existingThread, recipient);
    }, result -> onDestinationChosen(result.first(), result.second().getId(), true));
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
    }, result -> onDestinationChosen(result.first(), result.second().getId(), false));
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
      onDestinationChosen(threadId, recipientId, false);
    }
  }

  private void onDestinationChosen(long threadId, @NonNull RecipientId recipientId, boolean sendMessage) {
    if (!viewModel.isExternalShare()) {
      openConversationOrSendMessage(threadId, recipientId, null, sendMessage);
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

      openConversationOrSendMessage(threadId, recipientId, data.get(), sendMessage);
    });
  }

  private void openConversationOrSendMessage(long threadId, @NonNull RecipientId recipientId, @Nullable ShareData shareData, boolean sendMessage) {
    Intent           intent          = new Intent(this, ConversationActivity.class);
    String           textExtra       = getIntent().getStringExtra(Intent.EXTRA_TEXT);
    ArrayList<Media> mediaExtra      = getIntent().getParcelableArrayListExtra(ConversationActivity.MEDIA_EXTRA);
    StickerLocator   stickerExtra    = getIntent().getParcelableExtra(ConversationActivity.STICKER_EXTRA);
    boolean          borderlessExtra = getIntent().getBooleanExtra(ConversationActivity.BORDERLESS_EXTRA, false);

    if(sendMessage) {
      viewModel.onSuccessulShare();
      sendMessage(threadId, recipientId, textExtra, mediaExtra, stickerExtra, shareData);
      return;
    }

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

  @SuppressLint("StaticFieldLeak")
  private void sendMessage(long threadId, @NonNull RecipientId recipientId, String text,
                           @Nullable ArrayList<Media> mediaExtra,
                           @Nullable StickerLocator stickerLocator,
                           @Nullable ShareData shareData){

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        Log.w(TAG, "[sendMessage] Sharing message in single conversation or group.");
        Recipient recipient = Recipient.resolved(recipientId);

        long replyThreadId;

        int  subscriptionId = recipient.getDefaultSubscriptionId().or(-1);
        long expiresIn      = recipient.getExpireMessages() * 1000L;

        if(shareData == null && mediaExtra == null && stickerLocator == null){
          replyThreadId = sendTextMessage(recipient, text, expiresIn, subscriptionId, threadId);
        } else {
          SlideDeck slideDeck = getSlideDeckForMediaMessageAsync(shareData, mediaExtra, stickerLocator);
          if (slideDeck == null){
            return null;
          } else {
            replyThreadId = sendMediaMessage(recipient, slideDeck, text, subscriptionId, expiresIn, threadId);
          }
        }

        List<MessagingDatabase.MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(ShareActivity.this).setRead(replyThreadId, true);

        //MessageNotifier.updateNotification(getApplicationContext(), threadId);
        MarkReadReceiver.process(ShareActivity.this, messageIds);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private long sendTextMessage(Recipient recipient, String text, long expiresIn, int subscriptionId, long threadId) {
    long replyThreadId;
    if (recipient.resolve().isGroup()){
      Log.w(TAG, "[sendTextMessage] Sharing text message in group.");

      OutgoingMediaMessage message = new OutgoingMediaMessage(recipient, text, new LinkedList<>(),
              System.currentTimeMillis(), subscriptionId, expiresIn,false, 0,
              null, Collections.emptyList(), Collections.emptyList(),
              Collections.emptyList(), Collections.emptyList());

      replyThreadId = MessageSender.send(ShareActivity.this, message, threadId, false, null);
    } else {
      Log.w(TAG, "[sendTextMessage] Sharing regular text message.");
      OutgoingTextMessage message = new OutgoingTextMessage(recipient, text, expiresIn, subscriptionId);

      replyThreadId = MessageSender.send(ShareActivity.this, message, threadId, false, null);
    }
    return replyThreadId;
  }

  private long sendMediaMessage(Recipient recipient, SlideDeck slideDeck, String text, int subscriptionId, long expiresIn, long threadId) {
    Log.w(TAG, "[sendMediaMessage] Sharing text message in group.");
    int distributionType = ThreadDatabase.DistributionTypes.DEFAULT;
    OutgoingMediaMessage message = new OutgoingMediaMessage(recipient, slideDeck, text,
            System.currentTimeMillis(), subscriptionId, expiresIn,false, distributionType,
            null, Collections.emptyList(), Collections.emptyList());

    return MessageSender.send(ShareActivity.this, message, threadId, false, null);
  }

  @SuppressLint("StaticFieldLeak")
  private SlideDeck getSlideDeckForMediaMessageAsync(ShareData shareData, ArrayList<Media> mediaExtra, StickerLocator stickerLocator) {
    final SettableFuture<SlideDeck> slideDeckFuture = new SettableFuture<>();

    new AsyncTask<Void, Void, SlideDeck>() {
      @Override protected SlideDeck doInBackground(Void... params) {
        Log.w(TAG, "[getSlideDeckForMediaMessageAsync] Retrieving media data to share.");
        return getSlideDeckForMediaMessage(shareData, mediaExtra, stickerLocator);
      }
        protected void onPostExecute(SlideDeck result) {
          slideDeckFuture.set(result);
        }

      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    try{
        return slideDeckFuture.get();
    } catch (ExecutionException | InterruptedException e){
      return null;
    }
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

}