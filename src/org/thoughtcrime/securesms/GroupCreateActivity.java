package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.components.PushRecipientsPanel;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.ActionBarUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;


public class GroupCreateActivity extends PassphraseRequiredSherlockFragmentActivity {

  private final static String TAG = "GroupCreateActivity";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private String defaultTitle;

  private static final int PICK_CONTACT = 1;
  private static final int PICK_AVATAR  = 2;

  private EditText            groupName;
  private ListView            lv;
  private PushRecipientsPanel recipientsPanel;
  private ImageView           avatar;

  private Set<Recipient> selectedContacts;

  @Override
  public void onCreate(Bundle state) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    defaultTitle = getString(R.string.GroupCreateActivity_actionbar_title);
    super.onCreate(state);

    setContentView(R.layout.group_create_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    ActionBarUtil.initializeDefaultActionBar(this, getSupportActionBar(), defaultTitle);

    selectedContacts = new HashSet<Recipient>();
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private void initializeResources() {
    groupName = (EditText) findViewById(R.id.group_name);
    groupName.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }
      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) { }
      @Override
      public void afterTextChanged(Editable editable) {
        if (editable.length() > 0)
          getSupportActionBar().setTitle(editable);
        else
          getSupportActionBar().setTitle(defaultTitle);
      }
    });

    lv = (ListView) findViewById(R.id.selected_contacts_list);
    lv.setAdapter(new SelectedRecipientsAdapter(this, android.R.id.text1, new ArrayList<Recipient>()));

    recipientsPanel = (PushRecipientsPanel) findViewById(R.id.recipients);
    recipientsPanel.setPanelChangeListener(new PushRecipientsPanel.RecipientsPanelChangedListener() {
      @Override
      public void onRecipientsPanelUpdate(Recipients recipients) {
        Log.i(TAG, "onRecipientsPanelUpdate received.");
        if (recipients != null) {
          selectedContacts.addAll(recipients.getRecipientsList());
          syncAdapterWithSelectedContacts();
        }
      }
    });
    (findViewById(R.id.contacts_button)).setOnClickListener(new AddRecipientButtonListener());

    avatar = (ImageView) findViewById(R.id.avatar);
    avatar.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT, null);
        photoPickerIntent.setType("image/*");
        photoPickerIntent.putExtra("crop", "true");
        photoPickerIntent.putExtra("aspectX", 1);
        photoPickerIntent.putExtra("aspectY", 1);
        photoPickerIntent.putExtra("outputX", 256);
        photoPickerIntent.putExtra("outputY", 256);
        photoPickerIntent.putExtra("return-data", "true");
        startActivityForResult(photoPickerIntent, PICK_AVATAR);
      }
    });
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSupportMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.group_create, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  private List<String> selectedContactsAsIdArray() {
    final List<String> ids = new ArrayList<String>();
    for (Recipient recipient : selectedContacts) {
      ids.add(String.valueOf(recipient.getRecipientId()));
    }
    return ids;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
      case R.id.menu_create_group:
        finish(); // TODO not this
        return true;
    }

    return false;
  }

  private void syncAdapterWithSelectedContacts() {
    SelectedRecipientsAdapter adapter = (SelectedRecipientsAdapter)lv.getAdapter();
    adapter.clear();
    for (Recipient contact : selectedContacts) {
      adapter.add(contact);
      Log.i("GroupCreateActivity", "Adding " + contact.getName() + "/" + contact.getNumber());
    }
    adapter.notifyDataSetChanged();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    Log.w("ComposeMessageActivity", "onActivityResult called: " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if (data == null || resultCode != Activity.RESULT_OK)
      return;

    switch (reqCode) {
      case PICK_CONTACT:
        List<ContactData> selected = data.getParcelableArrayListExtra("contacts");
        for (ContactData cdata : selected) {
          Log.i("PushContactSelect", "selected report: " + cdata.name);
        }
        for (ContactData contact : selected) {
          for (ContactAccessor.NumberData numberData : contact.numbers) {
            try {
              Recipient recipient = RecipientFactory.getRecipientsFromString(this, numberData.number, false)
                                                    .getPrimaryRecipient();

              if (!selectedContacts.contains(recipient)) {
                selectedContacts.add(recipient);
              }
            } catch (RecipientFormattingException e) {
              Log.w("GroupCreateActivity", e);
            }
          }
        }
        syncAdapterWithSelectedContacts();
        break;
      case PICK_AVATAR:
        if(resultCode == RESULT_OK) {
          Bitmap avatarBmp = data.getParcelableExtra("data");
          avatar.setImageBitmap(avatarBmp);
          //Uri selectedImage = data.getData();
          //avatar.setImageURI(selectedImage);
          break;
        }
    }
  }

  private class AddRecipientButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(GroupCreateActivity.this, PushContactSelectionActivity.class);
      startActivityForResult(intent, PICK_CONTACT);
    }
  }
}
