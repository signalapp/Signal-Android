package org.thoughtcrime.securesms.contactshare;

import android.app.Activity;
import androidx.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.Toast;

import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;

import java.util.ArrayList;
import java.util.List;

import static org.thoughtcrime.securesms.contactshare.Contact.*;
import static org.thoughtcrime.securesms.contactshare.ContactShareEditViewModel.*;

public class ContactShareEditActivity extends PassphraseRequiredActionBarActivity implements ContactShareEditAdapter.EventListener {

  public  static final String KEY_CONTACTS     = "contacts";
  private static final String KEY_CONTACT_URIS = "contact_uris";
  private static final int    CODE_NAME_EDIT   = 55;

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ContactShareEditViewModel viewModel;

  public static Intent getIntent(@NonNull Context context, @NonNull List<Uri> contactUris) {
    ArrayList<Uri> contactUriList = new ArrayList<>(contactUris);

    Intent intent = new Intent(context, ContactShareEditActivity.class);
    intent.putParcelableArrayListExtra(KEY_CONTACT_URIS, contactUriList);
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState, boolean ready) {
    setContentView(R.layout.activity_contact_share_edit);

    if (getIntent() == null) {
      throw new IllegalStateException("You must supply extras to this activity. Please use the #getIntent() method.");
    }

    List<Uri> contactUris = getIntent().getParcelableArrayListExtra(KEY_CONTACT_URIS);
    if (contactUris == null) {
      throw new IllegalStateException("You must supply contact Uri's to this activity. Please use the #getIntent() method.");
    }

    View sendButton = findViewById(R.id.contact_share_edit_send);
    sendButton.setOnClickListener(v -> onSendClicked(viewModel.getFinalizedContacts()));

    RecyclerView contactList = findViewById(R.id.contact_share_edit_list);
    contactList.setLayoutManager(new LinearLayoutManager(this));
    contactList.getLayoutManager().setAutoMeasureEnabled(true);

    ContactShareEditAdapter contactAdapter = new ContactShareEditAdapter(GlideApp.with(this), dynamicLanguage.getCurrentLocale(), this);
    contactList.setAdapter(contactAdapter);

    ContactRepository contactRepository = new ContactRepository(this,
                                                                AsyncTask.THREAD_POOL_EXECUTOR,
                                                                DatabaseFactory.getContactsDatabase(this));

    viewModel = ViewModelProviders.of(this, new Factory(contactUris, contactRepository)).get(ContactShareEditViewModel.class);
    viewModel.getContacts().observe(this, contacts -> {
      contactAdapter.setContacts(contacts);
      contactList.post(() -> contactList.scrollToPosition(0));
    });
    viewModel.getEvents().observe(this, this::presentEvent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicTheme.onResume(this);
  }

  private void presentEvent(@Nullable Event event) {
    if (event == null) {
      return;
    }

    if (event == Event.BAD_CONTACT) {
      Toast.makeText(this, R.string.ContactShareEditActivity_invalid_contact, Toast.LENGTH_SHORT).show();
      finish();
    }
  }

  private void onSendClicked(List<Contact> contacts) {
    Intent intent = new Intent();

    ArrayList<Contact> contactArrayList = new ArrayList<>(contacts.size());
    contactArrayList.addAll(contacts);
    intent.putExtra(KEY_CONTACTS, contactArrayList);

    setResult(Activity.RESULT_OK, intent);

    finish();
  }

  @Override
  public void onNameEditClicked(int position, @NonNull Name name) {
    startActivityForResult(ContactNameEditActivity.getIntent(this, name, position), CODE_NAME_EDIT);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode != CODE_NAME_EDIT || resultCode != RESULT_OK || data == null) {
      return;
    }

    int  position = data.getIntExtra(ContactNameEditActivity.KEY_CONTACT_INDEX, -1);
    Name name     = data.getParcelableExtra(ContactNameEditActivity.KEY_NAME);

    if (name != null) {
      viewModel.updateContactName(position, name);
    }
  }
}
