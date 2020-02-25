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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entry point for sharing content into the app.
 *
 * Handles contact selection when necessary, but also serves as an entry point for when the contact
 * is known (such as choosing someone in a direct share).
 */
public class ShareActivity extends PassphraseRequiredActionBarActivity
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

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    if (!getIntent().hasExtra(ContactSelectionListFragment.DISPLAY_MODE)) {
      int mode = DisplayMode.FLAG_PUSH | DisplayMode.FLAG_ACTIVE_GROUPS;

      if (TextSecurePreferences.isSmsEnabled(this))  {
        mode |= DisplayMode.FLAG_SMS;

      }
      getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE, mode);
    }

    getIntent().putExtra(ContactSelectionListFragment.REFRESHABLE, false);
    getIntent().putExtra(ContactSelectionListFragment.RECENTS, true);

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

  @Override
  public void onContactSelected(Optional<RecipientId> recipientId, String number) {
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

  @Override
  public void onContactDeselected(@NonNull Optional<RecipientId> recipientId, String number) {
  }

  @Override
  public void onRefresh() {
  }

  private void initializeToolbar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
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
    Intent           intent       = new Intent(this, ConversationActivity.class);
    String           textExtra    = getIntent().getStringExtra(Intent.EXTRA_TEXT);
    ArrayList<Media> mediaExtra   = getIntent().getParcelableArrayListExtra(ConversationActivity.MEDIA_EXTRA);
    StickerLocator   stickerExtra = getIntent().getParcelableExtra(ConversationActivity.STICKER_EXTRA);

    intent.putExtra(ConversationActivity.TEXT_EXTRA, textExtra);
    intent.putExtra(ConversationActivity.MEDIA_EXTRA, mediaExtra);
    intent.putExtra(ConversationActivity.STICKER_EXTRA, stickerExtra);

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
}