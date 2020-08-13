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
package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.TooltipCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.components.RatingManager;
import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.lock.RegistrationLockDialog;
import org.thoughtcrime.securesms.loki.utilities.ProfilePictureModifiedEvent;
import org.thoughtcrime.securesms.loki.activities.JoinPublicChatActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.search.SearchFragment;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;

import java.util.List;

import network.loki.messenger.R;

public class ConversationListActivity extends PassphraseRequiredActionBarActivity
    implements ConversationListFragment.ConversationSelectedListener
{
  @SuppressWarnings("unused")
  private static final String TAG = ConversationListActivity.class.getSimpleName();

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ConversationListFragment conversationListFragment;
  private SearchFragment           searchFragment;
  private SearchToolbar            searchToolbar;
  private ImageView                searchAction;
  private ViewGroup                fragmentContainer;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle icicle, boolean ready) {
    setContentView(R.layout.conversation_list_activity);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    searchToolbar            = findViewById(R.id.search_toolbar);
    searchAction             = findViewById(R.id.search_action);
    fragmentContainer        = findViewById(R.id.fragment_container);
    conversationListFragment = initFragment(R.id.fragment_container, new ConversationListFragment(), dynamicLanguage.getCurrentLocale());

    initializeSearchListener();

    RatingManager.showRatingDialogIfNecessary(this);
    RegistrationLockDialog.showReminderIfNecessary(this);

    TooltipCompat.setTooltipText(searchAction, getText(R.string.SearchToolbar_search_for_conversations_contacts_and_messages));
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    SimpleTask.run(getLifecycle(), () -> {
      return Recipient.from(this, Address.fromSerialized(TextSecurePreferences.getLocalNumber(this)), false);
    }, this::initializeProfileIcon);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  public void onStop() {
    EventBus.getDefault().unregister(this);
    super.onStop();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

//    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(this));

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private void initializeSearchListener() {
    searchAction.setOnClickListener(v -> {
      /* Loki - We don't need contact permissions
      Permissions.with(this)
                 .request(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                 .ifNecessary()
                 .onAllGranted(() -> searchToolbar.display(searchAction.getX() + (searchAction.getWidth() / 2),
                                                           searchAction.getY() + (searchAction.getHeight() / 2)))
                 .withPermanentDenialDialog(getString(R.string.ConversationListActivity_signal_needs_contacts_permission_in_order_to_search_your_contacts_but_it_has_been_permanently_denied))
                 .execute();
       */
    });

    searchToolbar.setListener(new SearchToolbar.SearchListener() {
      @Override
      public void onSearchTextChange(String text) {
        String trimmed = text.trim();

        if (trimmed.length() > 0) {
          if (searchFragment == null) {
            searchFragment = SearchFragment.newInstance(dynamicLanguage.getCurrentLocale());
            getSupportFragmentManager().beginTransaction()
                                       .add(R.id.fragment_container, searchFragment, null)
                                       .commit();
          }
          searchFragment.updateSearchQuery(trimmed);
        } else if (searchFragment != null) {
          getSupportFragmentManager().beginTransaction()
                                     .remove(searchFragment)
                                     .commit();
          searchFragment = null;
        }
      }

      @Override
      public void onSearchClosed() {
        if (searchFragment != null) {
          getSupportFragmentManager().beginTransaction()
                                     .remove(searchFragment)
                                     .commit();
          searchFragment = null;
        }
      }
    });
  }

  private void initializeProfileIcon(@NonNull Recipient recipient) {
    ImageView profilePictureImageView = findViewById(R.id.toolbar_icon);
    profilePictureImageView.setOutlineProvider(new ViewOutlineProvider() {

      @Override
      public void getOutline(View view, Outline outline) {
        outline.setOval(0, 0, view.getWidth(), view.getHeight());
      }
    });
    profilePictureImageView.setClipToOutline(true);

    // Display the correct identicon if we're a secondary device
    String primaryAddress = TextSecurePreferences.getMasterHexEncodedPublicKey(this);
    String profileAddress = (recipient.isLocalNumber() && primaryAddress != null) ? primaryAddress : recipient.getAddress().serialize();
    Recipient primaryRecipient = Recipient.from(this, Address.fromSerialized(profileAddress), false);

    Drawable fallback = primaryRecipient.getFallbackContactPhotoDrawable(this, false);

    GlideApp.with(this)
            .load(primaryRecipient.getContactPhoto())
            .fallback(fallback)
            .error(fallback)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .into(profilePictureImageView);


    profilePictureImageView.setOnClickListener(v -> handleDisplaySettings());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
//    case R.id.menu_new_group:                                createGroup();           return true;
//    case R.id.menu_settings:                                 handleDisplaySettings(); return true;
//    case R.id.menu_clear_passphrase:                         handleClearPassphrase(); return true;
//    case R.id.menu_mark_all_read:                            handleMarkAllRead();     return true;
//    case R.id.menu_invite:                                   handleInvite();          return true;
//    case R.id.menu_help:                                     handleHelp();            return true;
    case R.id.menu_conversation_list_add_public_chat_option: addNewPublicChat(); return true;
    }

    return false;
  }

  @Override
  public void onCreateConversation(long threadId, Recipient recipient, int distributionType, long lastSeen) {
    openConversation(threadId, recipient, distributionType, lastSeen, -1);
  }

  public void openConversation(long threadId, Recipient recipient, int distributionType, long lastSeen, int startingPosition) {
    searchToolbar.clearFocus();

    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.ADDRESS_EXTRA, recipient.getAddress());
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);
    intent.putExtra(ConversationActivity.TIMING_EXTRA, System.currentTimeMillis());
    intent.putExtra(ConversationActivity.LAST_SEEN_EXTRA, lastSeen);
    intent.putExtra(ConversationActivity.STARTING_POSITION_EXTRA, startingPosition);

    startActivity(intent);
    overridePendingTransition(R.anim.slide_from_right, R.anim.fade_scale_out);
  }

  @Override
  public void onSwitchToArchive() {
    Intent intent = new Intent(this, ConversationListArchiveActivity.class);
    startActivity(intent);
  }

  @Override
  public void onBackPressed() {
    if (searchToolbar.isVisible()) searchToolbar.collapse();
    else                           super.onBackPressed();
  }

  private void createGroup() {
    Intent intent = new Intent(this, GroupCreateActivity.class);
    startActivity(intent);
  }

  private void handleDisplaySettings() {
    Intent preferencesIntent = new Intent(this, ApplicationPreferencesActivity.class);
    startActivity(preferencesIntent);
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(this, KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    startService(intent);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleMarkAllRead() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        Context                 context    = ConversationListActivity.this;
        List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setAllThreadsRead();

        ApplicationContext.getInstance(context).messageNotifier.updateNotification(context);
        MarkReadReceiver.process(context, messageIds);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleInvite() {
    startActivity(new Intent(this, InviteActivity.class));
  }

  private void handleHelp() {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://support.signal.org")));
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this, R.string.ConversationListActivity_there_is_no_browser_installed_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  private void addNewPublicChat() {
    startActivity(new Intent(this, JoinPublicChatActivity.class));
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onAvatarModified(ProfilePictureModifiedEvent event) {
    Recipient recipient = event.getRecipient();
    if (recipient.isLocalNumber() || recipient.isUserMasterDevice()) {
      initializeProfileIcon(recipient);
    }
  }
}
