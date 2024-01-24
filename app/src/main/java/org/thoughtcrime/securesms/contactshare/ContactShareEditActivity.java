package org.thoughtcrime.securesms.contactshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.Material3OnScrollHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.thoughtcrime.securesms.contactshare.Contact.Name;
import static org.thoughtcrime.securesms.contactshare.ContactShareEditViewModel.Event;
import static org.thoughtcrime.securesms.contactshare.ContactShareEditViewModel.Factory;

public class ContactShareEditActivity extends PassphraseRequiredActivity implements ContactShareEditAdapter.EventListener {

  public  static final String KEY_CONTACTS          = "contacts";
  private static final String KEY_CONTACT_URIS      = "contact_uris";
  private static final String KEY_SEND_BUTTON_COLOR = "send_button_color";
  private static final int    CODE_NAME_EDIT   = 55;

  private final DynamicTheme    dynamicTheme    = new DynamicNoActionBarTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private ContactShareEditViewModel viewModel;

  public static Intent getIntent(@NonNull Context context, @NonNull List<Uri> contactUris, @ColorInt int sendButtonColor) {
    ArrayList<Uri> contactUriList = new ArrayList<>(contactUris);

    Intent intent = new Intent(context, ContactShareEditActivity.class);
    intent.putParcelableArrayListExtra(KEY_CONTACT_URIS, contactUriList);
    intent.putExtra(KEY_SEND_BUTTON_COLOR, sendButtonColor);
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
    ViewCompat.setBackgroundTintList(sendButton, ColorStateList.valueOf(getIntent().getIntExtra(KEY_SEND_BUTTON_COLOR, Color.RED)));
    sendButton.setOnClickListener(v -> onSendClicked(viewModel.getFinalizedContacts()));

    RecyclerView contactList = findViewById(R.id.contact_share_edit_list);
    contactList.setLayoutManager(new LinearLayoutManager(this));
    contactList.getLayoutManager().setAutoMeasureEnabled(true);

    Toolbar toolbar = findViewById(R.id.toolbar);
    toolbar.setNavigationOnClickListener(unused -> onBackPressed());
    Material3OnScrollHelper onScrollHelper = new Material3OnScrollHelper(this, Collections.singletonList(toolbar), Collections.emptyList(), this);
    onScrollHelper.attach(contactList);

    ContactShareEditAdapter contactAdapter = new ContactShareEditAdapter(Glide.with(this), dynamicLanguage.getCurrentLocale(), this);
    contactList.setAdapter(contactAdapter);

    SharedContactRepository contactRepository = new SharedContactRepository(this, AsyncTask.THREAD_POOL_EXECUTOR);

    viewModel = new ViewModelProvider(this, new Factory(contactUris, contactRepository)).get(ContactShareEditViewModel.class);
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
