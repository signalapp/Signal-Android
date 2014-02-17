package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.components.PushRecipientsPanel;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.ActionBarUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.util.Base64;
import org.whispersystems.textsecure.util.InvalidNumberException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ws.com.google.android.mms.MmsException;

import static org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import static org.whispersystems.textsecure.push.PushMessageProtos.PushMessageContent.GroupContext;


public class GroupCreateActivity extends PassphraseRequiredSherlockFragmentActivity {

  private final static String TAG = "GroupCreateActivity";

  public static final String MASTER_SECRET_EXTRA = "master_secret";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final int PICK_CONTACT = 1;
  private static final int PICK_AVATAR  = 2;

  private EditText            groupName;
  private ListView            lv;
  private PushRecipientsPanel recipientsPanel;
  private ImageView           avatar;

  private MasterSecret masterSecret;
  private Bitmap       avatarBmp;

  private Set<Recipient> selectedContacts;

  @Override
  public void onCreate(Bundle state) {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
    super.onCreate(state);

    setContentView(R.layout.group_create_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    ActionBarUtil.initializeDefaultActionBar(this, getSupportActionBar(), R.string.GroupCreateActivity_actionbar_title);

    selectedContacts = new HashSet<Recipient>();
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  private boolean whisperGroupUiEnabled() {
    return groupName.isEnabled() && avatar.isEnabled();
  }

  private void disableWhisperGroupUi() {
    View pushDisabled = findViewById(R.id.push_disabled);
    pushDisabled.setVisibility(View.VISIBLE);
    avatar.setEnabled(false);
    groupName.setEnabled(false);
    getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_mms_title);
  }

  private void enableWhisperGroupUi() {
    findViewById(R.id.push_disabled).setVisibility(View.GONE);
    avatar.setEnabled(true);
    groupName.setEnabled(true);
    final CharSequence groupNameText = groupName.getText();
    if (groupNameText.length() > 0)
      getSupportActionBar().setTitle(groupNameText);
    else
      getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_title);
  }

  private static boolean isActiveInDirectory(Context context, Recipient recipient) {
    try {
      if (!Directory.getInstance(context).isActiveNumber(Util.canonicalizeNumber(context, recipient.getNumber()))) {
        return false;
      }
    } catch (NotInDirectoryException e) {
      return false;
    } catch (InvalidNumberException e) {
      return false;
    }
    return true;
  }

  private void addSelectedContact(Recipient contact) {
    selectedContacts.add(contact);
    if (!isActiveInDirectory(this, contact)) disableWhisperGroupUi();
  }

  private void addAllSelectedContacts(Collection<Recipient> contacts) {
    for (Recipient contact : contacts) {
      addSelectedContact(contact);
    }
  }

  private void removeSelectedContact(Recipient contact) {
    Log.i(TAG, "remoevSelectedContact: " + contact.getName() + "/" + contact.getNumber());
    selectedContacts.remove(contact);
    if (!isActiveInDirectory(this, contact)) {
      for (Recipient recipient : selectedContacts) {
        if (!isActiveInDirectory(this, recipient))
          return;
      }
      enableWhisperGroupUi();
    }
  }

  private void initializeResources() {
    masterSecret = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
    groupName = (EditText) findViewById(R.id.group_name);
    groupName.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }
      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) { }
      @Override
      public void afterTextChanged(Editable editable) {
        if (editable.length() > 0)
          getSupportActionBar().setTitle(getString(R.string.GroupCreateActivity_actionbar_title) + ": " + editable.toString());
        else
          getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_title);
      }
    });

    lv = (ListView) findViewById(R.id.selected_contacts_list);
    SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(this, android.R.id.text1, new ArrayList<Recipient>());
    adapter.setOnRecipientDeletedListener(new SelectedRecipientsAdapter.OnRecipientDeletedListener() {
      @Override
      public void onRecipientDeleted(Recipient recipient) {
        removeSelectedContact(recipient);
      }
    });
    lv.setAdapter(adapter);

    recipientsPanel = (PushRecipientsPanel) findViewById(R.id.recipients);
    recipientsPanel.setPanelChangeListener(new PushRecipientsPanel.RecipientsPanelChangedListener() {
      @Override
      public void onRecipientsPanelUpdate(Recipients recipients) {
        Log.i(TAG, "onRecipientsPanelUpdate received.");
        if (recipients != null) {
          addAllSelectedContacts(recipients.getRecipientsList());
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
        photoPickerIntent.putExtra("outputX", 210);
        photoPickerIntent.putExtra("outputY", 210);
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

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
      case android.R.id.home:
        finish();
        return true;
      case R.id.menu_create_group:
        if (whisperGroupUiEnabled()) {
          findViewById(R.id.group_details_layout).setVisibility(View.GONE);
          findViewById(R.id.creating_group_layout).setVisibility(View.VISIBLE);
          findViewById(R.id.menu_create_group).setVisibility(View.GONE);
          ((TextView)findViewById(R.id.creating_group_text)).setText("Creating " + groupName.getText().toString() + "...");
          new AsyncTask<Void,Void,Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
              byte[] byteArray = null;
              if (avatarBmp != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                avatarBmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
                byteArray = stream.toByteArray();
              }
              try {
                handleCreatePushGroup(groupName.getText().toString(), byteArray, selectedContacts);
              } catch (IOException e) {
                // TODO Jake's gonna fill this in.
                Log.w("GroupCreateActivity", e);
              } catch (InvalidNumberException e) {
                // TODO jake's gonna fill this in.
                Log.w("GroupCreateActivity", e);
              }
              return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
              super.onPostExecute(aVoid);
              finish();
            }

            @Override
            protected void onProgressUpdate(Void... values) {
              super.onProgressUpdate(values);
            }
          }.execute();
        } else {
          new AsyncTask<Void,Void,Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
              handleCreateMmsGroup(selectedContacts);
              return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
              super.onPostExecute(aVoid);
              finish();
            }

            @Override
            protected void onProgressUpdate(Void... values) {
              super.onProgressUpdate(values);
            }
          }.execute();
        }
        return true;
    }

    return false;
  }

  private void syncAdapterWithSelectedContacts() {
    SelectedRecipientsAdapter adapter = (SelectedRecipientsAdapter)lv.getAdapter();
    adapter.clear();
    for (Recipient contact : selectedContacts) {
      adapter.add(contact);
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
        for (ContactData contact : selected) {
          for (ContactAccessor.NumberData numberData : contact.numbers) {
            try {
              Recipient recipient = RecipientFactory.getRecipientsFromString(this, numberData.number, false)
                                                    .getPrimaryRecipient();

              if (!selectedContacts.contains(recipient)) {
                addSelectedContact(recipient);
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
          avatarBmp = data.getParcelableExtra("data");
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

  private long handleCreatePushGroup(String groupName,
                                     byte[] avatar,
                                     Set<Recipient> members)
      throws InvalidNumberException, MmsException
  {
    GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(this);
    byte[]        groupId       = groupDatabase.allocateGroupId();

    try {
      List<String> memberE164Numbers = getE164Numbers(members);
      String       groupRecipientId  = GroupUtil.getEncodedId(groupId);

      String groupActionArguments = GroupUtil.serializeArguments(groupId, groupName, memberE164Numbers);

      groupDatabase.create(groupId, TextSecurePreferences.getLocalNumber(this), groupName,
                           memberE164Numbers, null, null);
      groupDatabase.updateAvatar(groupId, avatar);

      Recipients groupRecipient = RecipientFactory.getRecipientsFromString(this, groupRecipientId, false);

      return MessageSender.sendGroupAction(this, masterSecret, groupRecipient, -1,
                                           GroupContext.Type.CREATE_VALUE,
                                           groupActionArguments, avatar);
    } catch (RecipientFormattingException e) {
      throw new AssertionError(e);
    } catch (MmsException e) {
      Log.w("GroupCreateActivity", e);
      groupDatabase.remove(groupId, TextSecurePreferences.getLocalNumber(this));
      throw new MmsException(e);
    }
  }

  private long handleCreateMmsGroup(Set<Recipient> members) {
    Recipients recipients = new Recipients(new LinkedList<Recipient>(members));
    return DatabaseFactory.getThreadDatabase(this)
                          .getThreadIdFor(recipients,
                                          ThreadDatabase.DistributionTypes.CONVERSATION);
  }

  private List<String> getE164Numbers(Set<Recipient> recipients)
      throws InvalidNumberException
  {
    List<String> results = new LinkedList<String>();

    for (Recipient recipient : recipients) {
      results.add(Util.canonicalizeNumber(this, recipient.getNumber()));
    }

    return results;
  }
}
