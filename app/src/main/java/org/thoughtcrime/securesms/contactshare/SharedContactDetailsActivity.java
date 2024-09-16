package org.thoughtcrime.securesms.contactshare;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.WindowUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;

public class SharedContactDetailsActivity extends PassphraseRequiredActivity {

  private static final int    CODE_ADD_EDIT_CONTACT = 2323;
  private static final String KEY_CONTACT           = "contact";

  private ContactFieldAdapter contactFieldAdapter;
  private TextView            nameView;
  private TextView            numberView;
  private ImageView           avatarView;
  private View                addButtonView;
  private View                inviteButtonView;
  private ViewGroup           engageContainerView;
  private View                messageButtonView;
  private View                callButtonView;

  private RequestManager      requestManager;
  private Contact             contact;

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private final Map<RecipientId, LiveRecipient> activeRecipients = new HashMap<>();

  public static Intent getIntent(@NonNull Context context, @NonNull Contact contact) {
    Intent intent = new Intent(context, SharedContactDetailsActivity.class);
    intent.putExtra(KEY_CONTACT, contact);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.activity_shared_contact_details);

    if (getIntent() == null) {
      throw new IllegalStateException("You must supply arguments to this activity. Please use the #getIntent() method.");
    }

    contact = getIntent().getParcelableExtra(KEY_CONTACT);
    if (contact == null) {
      throw new IllegalStateException("You must supply a contact to this activity. Please use the #getIntent() method.");
    }

    initToolbar();
    initViews();

    presentContact(contact);
    presentActionButtons(ContactUtil.getRecipients(this, contact));
    presentAvatar(contact.getAvatarAttachment() != null ? contact.getAvatarAttachment().getUri() : null);

    for (LiveRecipient recipient : activeRecipients.values()) {
      recipient.observe(this, r -> presentActionButtons(Collections.singletonList(r.getId())));
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onCreate(this);
    dynamicTheme.onResume(this);
  }

  private void initToolbar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setLogo(null);
    getSupportActionBar().setTitle("");
    toolbar.setNavigationOnClickListener(v -> onBackPressed());

    WindowUtil.setStatusBarColor(getWindow(), ContextCompat.getColor(this, R.color.shared_contact_details_titlebar));
  }

  private void initViews() {
    nameView            = findViewById(R.id.contact_details_name);
    numberView          = findViewById(R.id.contact_details_number);
    avatarView          = findViewById(R.id.contact_details_avatar);
    addButtonView       = findViewById(R.id.contact_details_add_button);
    inviteButtonView    = findViewById(R.id.contact_details_invite_button);
    engageContainerView = findViewById(R.id.contact_details_engage_container);
    messageButtonView   = findViewById(R.id.contact_details_message_button);
    callButtonView      = findViewById(R.id.contact_details_call_button);

    contactFieldAdapter = new ContactFieldAdapter(dynamicLanguage.getCurrentLocale(), requestManager, false);

    RecyclerView list = findViewById(R.id.contact_details_fields);
    list.setLayoutManager(new LinearLayoutManager(this));
    list.setAdapter(contactFieldAdapter);

    requestManager = Glide.with(this);
  }

  @SuppressLint("StaticFieldLeak")
  private void presentContact(@Nullable Contact contact) {
    this.contact = contact;

    if (contact != null) {
      nameView.setText(ContactUtil.getDisplayName(contact));
      numberView.setText(ContactUtil.getDisplayNumber(contact, dynamicLanguage.getCurrentLocale()));

      addButtonView.setOnClickListener(v -> {
        new AsyncTask<Void, Void, Intent>() {
          @Override
          protected Intent doInBackground(Void... voids) {
            return ContactUtil.buildAddToContactsIntent(SharedContactDetailsActivity.this, contact);
          }

          @Override
          protected void onPostExecute(Intent intent) {
            startActivityForResult(intent, CODE_ADD_EDIT_CONTACT);
          }
        }.execute();
      });

      contactFieldAdapter.setFields(this, null, contact.getPhoneNumbers(), contact.getEmails(), contact.getPostalAddresses());
    } else {
      nameView.setText("");
      numberView.setText("");
    }
  }

  public void presentAvatar(@Nullable Uri uri) {
    if (uri != null) {
      requestManager
          .load(new DecryptableUri(uri))
          .fallback(R.drawable.symbol_person_display_40)
          .circleCrop()
          .diskCacheStrategy(DiskCacheStrategy.ALL)
          .into(avatarView);
    } else {
      requestManager
          .load(R.drawable.symbol_person_display_40)
          .circleCrop()
          .diskCacheStrategy(DiskCacheStrategy.ALL)
          .into(avatarView);
    }
  }

  private void presentActionButtons(@NonNull List<RecipientId> recipients) {
    for (RecipientId recipientId : recipients) {
      activeRecipients.put(recipientId, Recipient.live(recipientId));
    }

    List<Recipient> pushUsers   = new ArrayList<>(recipients.size());
    List<Recipient> systemUsers = new ArrayList<>(recipients.size());

    for (LiveRecipient recipient : activeRecipients.values()) {
      if (recipient.get().getRegistered() == RecipientTable.RegisteredState.REGISTERED) {
        pushUsers.add(recipient.get());
      } else if (recipient.get().isSystemContact()) {
        systemUsers.add(recipient.get());
      }
    }

    if (!pushUsers.isEmpty()) {
      engageContainerView.setVisibility(View.VISIBLE);
      inviteButtonView.setVisibility(View.GONE);

      messageButtonView.setOnClickListener(v -> {
        ContactUtil.selectRecipientThroughDialog(this, pushUsers, dynamicLanguage.getCurrentLocale(), recipient -> {
          CommunicationActions.startConversation(this, recipient, null);
        });
      });

      callButtonView.setOnClickListener(v -> {
        ContactUtil.selectRecipientThroughDialog(this, pushUsers, dynamicLanguage.getCurrentLocale(), recipient -> CommunicationActions.startVoiceCall(this, recipient, () -> {
          YouAreAlreadyInACallSnackbar.show(callButtonView);
        }));
      });
    } else if (!systemUsers.isEmpty()) {
      inviteButtonView.setVisibility(View.VISIBLE);
      engageContainerView.setVisibility(View.GONE);

      inviteButtonView.setOnClickListener(v -> {
        ContactUtil.selectRecipientThroughDialog(this, systemUsers, dynamicLanguage.getCurrentLocale(), recipient -> {
          CommunicationActions.composeSmsThroughDefaultApp(this, recipient, getString(R.string.InviteActivity_lets_switch_to_signal, getString(R.string.install_url)));
        });
      });
    } else {
      inviteButtonView.setVisibility(View.GONE);
      engageContainerView.setVisibility(View.GONE);
    }
  }

  private void clearView() {
    nameView.setText("");
    numberView.setText("");
    inviteButtonView.setVisibility(View.GONE);
    engageContainerView.setVisibility(View.GONE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CODE_ADD_EDIT_CONTACT && contact != null) {
      AppDependencies.getJobManager().add(new DirectoryRefreshJob(false));
    }
  }
}
