package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;


public class GroupCreateActivity extends PassphraseRequiredSherlockFragmentActivity {

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final int PICK_CONTACT = 1;
  private static final int SELECT_PHOTO = 100;
  private ListView lv;

  private Set<Recipient> selectedContacts;

  @Override
  public void onCreate(Bundle state) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(state);

    setContentView(R.layout.group_create_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    ActionBarUtil.initializeDefaultActionBar(this, getSupportActionBar(), "New Group");

    selectedContacts = new HashSet<Recipient>();
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private void initializeResources() {
    lv = (ListView) findViewById(R.id.selected_contacts_list);
    lv.setAdapter(new SelectedRecipientsAdapter(this, android.R.id.text1, new ArrayList<Recipient>()));
    (findViewById(R.id.add_people_button)).setOnClickListener(new AddRecipientButtonListener());
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

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    Log.w("ComposeMessageActivity", "onActivityResult called: " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if (data == null || resultCode != Activity.RESULT_OK)
      return;

    switch (reqCode) {
      case PICK_CONTACT:
        List<ContactData> selected = data.getParcelableArrayListExtra("contacts");
        for (ContactData contact : selected) {
          for (ContactAccessor.NumberData numberData : contact.numbers) {
            try {
              Recipient recipient = RecipientFactory.getRecipientsFromString(this, numberData.number, true)
                                                    .getPrimaryRecipient();

              if (!selectedContacts.contains(recipient)) {
                selectedContacts.add(recipient);
              }
            } catch (RecipientFormattingException e) {
              Log.w("GroupCreateActivity", e);
            }
          }
        }

        SelectedRecipientsAdapter adapter = (SelectedRecipientsAdapter)lv.getAdapter();
        adapter.clear();
        Iterator<Recipient> selectedContactsIter = selectedContacts.iterator();
        while (selectedContactsIter.hasNext()) {
          adapter.add(selectedContactsIter.next());
        }
        break;
      case SELECT_PHOTO:
        if(resultCode == RESULT_OK){
          Uri selectedImage = data.getData();
          String[] filePathColumn = {MediaStore.Images.Media.DATA};

          Cursor cursor = getContentResolver().query(
              selectedImage, filePathColumn, null, null, null);
          cursor.moveToFirst();

          int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
          String filePath = cursor.getString(columnIndex);
          cursor.close();

          Bitmap selectedBitmap = BitmapFactory.decodeFile(filePath);
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
