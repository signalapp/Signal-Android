package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.protobuf.ByteString;

import org.thoughtcrime.securesms.components.PushRecipientsPanel;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.RecipientsEditor;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.RecipientProvider;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.util.ActionBarUtil;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.whispersystems.textsecure.directory.Directory;
import org.whispersystems.textsecure.directory.NotInDirectoryException;
import org.whispersystems.textsecure.util.InvalidNumberException;
import org.whispersystems.textsecure.util.PhoneNumberFormatter;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

  private final static String TAG = GroupCreateActivity.class.getSimpleName();

  public static final String GROUP_RECIPIENT_EXTRA = "group_recipient";
  public static final String GROUP_THREAD_EXTRA = "group_thread";
  public static final String MASTER_SECRET_EXTRA    = "master_secret";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final String TEMP_PHOTO_FILE = "__tmp_group_create_avatar_photo.tmp";

  private static final int PICK_CONTACT = 1;
  private static final int PICK_AVATAR  = 2;
  public static final  int AVATAR_SIZE  = 210;

  private EditText            groupName;
  private ListView            lv;
  private PushRecipientsPanel recipientsPanel;
  private ImageView           avatar;
  private TextView            creatingText;
  private ProgressDialog      pd;

  private Recipients     groupRecipient    = null;
  private long           groupThread       = -1;
  private byte[]         groupId           = null;
  private Set<Recipient> existingContacts  = null;
  private String         existingTitle     = null;
  private Bitmap         existingAvatarBmp = null;

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
    dynamicLanguage.onResume(this);
    if (!TextSecurePreferences.isPushRegistered(this)) {
      disableWhisperGroupUi(R.string.GroupCreateActivity_you_dont_support_push);
    }
  }

  private boolean whisperGroupUiEnabled() {
    return groupName.isEnabled() && avatar.isEnabled();
  }

  private void disableWhisperGroupUi(int reasonResId) {
    View pushDisabled = findViewById(R.id.push_disabled);
    pushDisabled.setVisibility(View.VISIBLE);
    ((TextView) findViewById(R.id.push_disabled_reason)).setText(reasonResId);
    avatar.setEnabled(false);
    groupName.setEnabled(false);
  }

  private void enableWhisperGroupUi() {
    findViewById(R.id.push_disabled).setVisibility(View.GONE);
    avatar.setEnabled(true);
    groupName.setEnabled(true);
    final CharSequence groupNameText = groupName.getText();
    if (groupNameText != null && groupNameText.length() > 0)
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
    if (!selectedContacts.contains(contact) && (existingContacts == null || !existingContacts.contains(contact)))
      selectedContacts.add(contact);
    if (!isActiveInDirectory(this, contact)) {
      disableWhisperGroupUi(R.string.GroupCreateActivity_contacts_dont_support_push);
      getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_mms_title);
    }
  }

  private void addAllSelectedContacts(Collection<Recipient> contacts) {
    for (Recipient contact : contacts) {
      addSelectedContact(contact);
    }
  }

  private void removeSelectedContact(Recipient contact) {
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
    groupRecipient = getIntent().getParcelableExtra(GROUP_RECIPIENT_EXTRA);
    groupThread = getIntent().getLongExtra(GROUP_THREAD_EXTRA, -1);
    if (groupRecipient != null) {
      final String encodedGroupId = groupRecipient.getPrimaryRecipient().getNumber();
      if (encodedGroupId != null) {
        try {
          groupId = GroupUtil.getDecodedId(encodedGroupId);
        } catch (IOException ioe) {
          Log.w(TAG, "Couldn't decode the encoded groupId passed in via intent", ioe);
          groupId = null;
        }
        if (groupId != null) {
          new FillExistingGroupInfoAsyncTask().execute();
        }
      }
    }

    masterSecret = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);

    lv              = (ListView)            findViewById(R.id.selected_contacts_list);
    avatar          = (ImageView)           findViewById(R.id.avatar);
    groupName       = (EditText)            findViewById(R.id.group_name);
    creatingText    = (TextView)            findViewById(R.id.creating_group_text);
    recipientsPanel = (PushRecipientsPanel) findViewById(R.id.recipients);

    groupName.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }
      @Override
      public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) { }
      @Override
      public void afterTextChanged(Editable editable) {
        if (editable.length() > 0) {
          final int prefixResId = (groupId != null)
                                  ? R.string.GroupCreateActivity_actionbar_update_title
                                  : R.string.GroupCreateActivity_actionbar_title;
          getSupportActionBar().setTitle(getString(prefixResId) + ": " + editable.toString());
        } else {
          getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_title);
        }
      }
    });

    SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(this, android.R.id.text1, new ArrayList<SelectedRecipientsAdapter.RecipientWrapper>());
    adapter.setOnRecipientDeletedListener(new SelectedRecipientsAdapter.OnRecipientDeletedListener() {
      @Override
      public void onRecipientDeleted(Recipient recipient) {
        removeSelectedContact(recipient);
      }
    });
    lv.setAdapter(adapter);

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

    avatar.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT, null);
        photoPickerIntent.setType("image/*");
        photoPickerIntent.putExtra("crop", "true");
        photoPickerIntent.putExtra("aspectX", 1);
        photoPickerIntent.putExtra("aspectY", 1);
        photoPickerIntent.putExtra("outputX", AVATAR_SIZE);
        photoPickerIntent.putExtra("outputY", AVATAR_SIZE);
        photoPickerIntent.putExtra(MediaStore.EXTRA_OUTPUT, getTempUri());
        photoPickerIntent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());
        startActivityForResult(photoPickerIntent, PICK_AVATAR);
      }
    });

    ((RecipientsEditor)findViewById(R.id.recipients_text)).setHint(R.string.recipients_panel__add_member);
  }

  private Uri getTempUri() {
    return Uri.fromFile(getTempFile());
  }

  private File getTempFile() {
    if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {

      File f = new File(Environment.getExternalStorageDirectory(), TEMP_PHOTO_FILE);
      try {
        f.createNewFile();
        f.deleteOnExit();
      } catch (IOException e) {
        Log.e(TAG, "Error creating new temp file.", e);
        Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_file_io_exception, Toast.LENGTH_SHORT).show();
      }
      return f;
    } else {
      return null;
    }
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
        if (groupId == null) handleGroupCreate();
        else                 handleGroupUpdate();
        return true;
    }

    return false;
  }

  private void handleGroupCreate() {
    if (selectedContacts.size() < 1) {
      Log.i(TAG, getString(R.string.GroupCreateActivity_contacts_no_members));
      Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_no_members, Toast.LENGTH_SHORT).show();
      return;
    }
    if (whisperGroupUiEnabled()) {
      enableWhisperGroupCreatingUi();
      new CreateWhisperGroupAsyncTask().execute();
    } else {
      new CreateMmsGroupAsyncTask().execute();
    }
  }

  private void handleGroupUpdate() {
    Log.w("GroupCreateActivity", "Creating...");
    new UpdateWhisperGroupAsyncTask().execute();
  }

  private static List<String> recipientsToNormalizedStrings(Collection<Recipient> recipients, String localNumber) {
    final List<String> e164numbers = new ArrayList<String>(recipients.size());
    for (Recipient contact : recipients) {
      try {
        e164numbers.add(PhoneNumberFormatter.formatNumber(contact.getNumber(), localNumber));
      } catch (InvalidNumberException ine) {
        Log.w(TAG, "Failed to format number for added group member.", ine);
      }
    }
    return e164numbers;
  }

  private void enableWhisperGroupCreatingUi() {
    findViewById(R.id.group_details_layout).setVisibility(View.GONE);
    findViewById(R.id.creating_group_layout).setVisibility(View.VISIBLE);
    findViewById(R.id.menu_create_group).setVisibility(View.GONE);
    if (groupName.getText() != null)
      creatingText.setText(getString(R.string.GroupCreateActivity_creating_group, groupName.getText().toString()));
  }

  private void disableWhisperGroupCreatingUi() {
    findViewById(R.id.group_details_layout).setVisibility(View.VISIBLE);
    findViewById(R.id.creating_group_layout).setVisibility(View.GONE);
    findViewById(R.id.menu_create_group).setVisibility(View.VISIBLE);
  }

  private void syncAdapterWithSelectedContacts() {
    SelectedRecipientsAdapter adapter = (SelectedRecipientsAdapter)lv.getAdapter();
    adapter.clear();
    for (Recipient contact : selectedContacts) {
      adapter.add(new SelectedRecipientsAdapter.RecipientWrapper(contact, true));
    }
    if (existingContacts != null) {
      for (Recipient contact : existingContacts) {
        adapter.add(new SelectedRecipientsAdapter.RecipientWrapper(contact, false));
      }
    }
    adapter.notifyDataSetChanged();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
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

              if (!selectedContacts.contains(recipient)
                  && (existingContacts == null || !existingContacts.contains(recipient))) {
                addSelectedContact(recipient);
              }
            } catch (RecipientFormattingException e) {
              Log.w(TAG, e);
            }
          }
        }
        syncAdapterWithSelectedContacts();
        break;
      case PICK_AVATAR:
          new DecodeCropAndSetAsyncTask().execute();
          break;
    }
  }

  private class AddRecipientButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(GroupCreateActivity.this, PushContactSelectionActivity.class);
      startActivityForResult(intent, PICK_CONTACT);
    }
  }

  private Pair<Long, Recipients> handleCreatePushGroup(String groupName, byte[] avatar,
                                                       Set<Recipient> members)
      throws InvalidNumberException, MmsException
  {
    GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(this);
    byte[]        groupId           = groupDatabase.allocateGroupId();
    List<String>  memberE164Numbers = getE164Numbers(members);

    groupDatabase.create(groupId, TextSecurePreferences.getLocalNumber(this), groupName,
                         memberE164Numbers, null, null);
    groupDatabase.updateAvatar(groupId, avatar);

    return handlePushOperation(groupId, groupName, avatar, memberE164Numbers);
  }

  private Pair<Long, Recipients> handleUpdatePushGroup(byte[] groupId, String groupName,
                                                       byte[] avatar, Set<Recipient> members)
      throws InvalidNumberException, MmsException
  {
    GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(this);
    List<String>  memberE164Numbers = getE164Numbers(members);

    GroupDatabase.GroupRecord record     = groupDatabase.getGroup(groupId);
    Set<String>               newMembers = new HashSet<String>(memberE164Numbers);
    newMembers.removeAll(record.getMembers());

    groupDatabase.add(groupId, TextSecurePreferences.getLocalNumber(this),
                      new LinkedList<String>(newMembers));

    groupDatabase.updateTitle(groupId, groupName);
    groupDatabase.updateAvatar(groupId, avatar);


    return handlePushOperation(groupId, groupName, avatar, memberE164Numbers);
  }

  private Pair<Long, Recipients> handlePushOperation(byte[] groupId, String groupName, byte[] avatar,
                                                     List<String> e164numbers)
      throws MmsException, InvalidNumberException
  {

    try {
      String     groupRecipientId = GroupUtil.getEncodedId(groupId);
      Recipients groupRecipient   = RecipientFactory.getRecipientsFromString(this, groupRecipientId, false);

      GroupContext context = GroupContext.newBuilder()
                                         .setId(ByteString.copyFrom(groupId))
                                         .setType(GroupContext.Type.UPDATE)
                                         .setName(groupName)
                                         .addAllMembers(e164numbers)
                                         .build();

      OutgoingGroupMediaMessage outgoingMessage = new OutgoingGroupMediaMessage(this, groupRecipient, context, avatar);
      long                      threadId        = MessageSender.send(this, masterSecret, outgoingMessage, -1);

      return new Pair<Long, Recipients>(threadId, groupRecipient);
    } catch (RecipientFormattingException e) {
      throw new AssertionError(e);
    } catch (MmsException e) {
      Log.w(TAG, e);
      throw new MmsException(e);
    }
  }

  private long handleCreateMmsGroup(Set<Recipient> members) {
    Recipients recipients = new Recipients(new LinkedList<Recipient>(members));
    return DatabaseFactory.getThreadDatabase(this)
                          .getThreadIdFor(recipients,
                                          ThreadDatabase.DistributionTypes.CONVERSATION);
  }

  private static <T> ArrayList<T> setToArrayList(Set<T> set) {
    ArrayList<T> arrayList = new ArrayList<T>(set.size());
    for (T item : set) {
      arrayList.add(item);
    }
    return arrayList;
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

  private class DecodeCropAndSetAsyncTask extends AsyncTask<Void,Void,Bitmap> {

    @Override
    protected Bitmap doInBackground(Void... voids) {
      File tempFile = getTempFile();
      avatarBmp = BitmapUtil.getCircleCroppedBitmap(BitmapFactory.decodeFile(tempFile.getAbsolutePath()));
      tempFile.delete();
      return avatarBmp;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
      if (avatarBmp != null) avatar.setImageBitmap(avatarBmp);
    }
  }

  private class CreateMmsGroupAsyncTask extends AsyncTask<Void,Void,Long> {

    @Override
    protected Long doInBackground(Void... voids) {
      return handleCreateMmsGroup(selectedContacts);
    }

    @Override
    protected void onPostExecute(Long resultThread) {
      if (resultThread > -1) {
        Intent intent = new Intent(GroupCreateActivity.this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, resultThread.longValue());
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

        ArrayList<Recipient> selectedContactsList = setToArrayList(selectedContacts);
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, new Recipients(selectedContactsList));
        startActivity(intent);
        finish();
      } else {
        Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_mms_exception, Toast.LENGTH_LONG).show();
        finish();
      }
    }

    @Override
    protected void onProgressUpdate(Void... values) {
      super.onProgressUpdate(values);
    }
  }

  private class UpdateWhisperGroupAsyncTask extends AsyncTask<Void,Void,Pair<Long,Recipients>> {
    private long RES_BAD_NUMBER = -2;
    private long RES_MMS_EXCEPTION = -3;
    @Override
    protected Pair<Long, Recipients> doInBackground(Void... params) {
      byte[] avatarBytes = null;
      if (avatarBmp != null) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        avatarBmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
        avatarBytes = stream.toByteArray();
      }
      final String name = (groupName.getText() != null) ? groupName.getText().toString() : null;
      try {
        return handleUpdatePushGroup(groupId, name, avatarBytes, selectedContacts);
      } catch (MmsException e) {
        Log.w(TAG, e);
        return new Pair<Long,Recipients>(RES_MMS_EXCEPTION, null);
      } catch (InvalidNumberException e) {
        Log.w(TAG, e);
        return new Pair<Long,Recipients>(RES_BAD_NUMBER, null);
      }
    }
  }

  private class CreateWhisperGroupAsyncTask extends AsyncTask<Void,Void,Pair<Long,Recipients>> {
    private long RES_BAD_NUMBER = -2;
    private long RES_MMS_EXCEPTION = -3;

    @Override
    protected Pair<Long,Recipients> doInBackground(Void... voids) {
      byte[] avatarBytes = null;
      if (avatarBmp != null) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        avatarBmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
        avatarBytes = stream.toByteArray();
      }
      final String name = (groupName.getText() != null) ? groupName.getText().toString() : null;
      try {
        return handleCreatePushGroup(name, avatarBytes, selectedContacts);
      } catch (MmsException e) {
        Log.w(TAG, e);
        return new Pair<Long,Recipients>(RES_MMS_EXCEPTION, null);
      } catch (InvalidNumberException e) {
        Log.w(TAG, e);
        return new Pair<Long,Recipients>(RES_BAD_NUMBER, null);
      }
    }

    @Override
    protected void onPostExecute(Pair<Long,Recipients> groupInfo) {
      super.onPostExecute(groupInfo);
      final long threadId = groupInfo.first;
      final Recipients recipients = groupInfo.second;
      if (threadId > -1) {
        Intent intent = new Intent(GroupCreateActivity.this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.MASTER_SECRET_EXTRA, masterSecret);
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients);
        startActivity(intent);
        finish();
      } else if (threadId == RES_BAD_NUMBER) {
        Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_invalid_number, Toast.LENGTH_LONG).show();
        disableWhisperGroupCreatingUi();
      } else if (threadId == RES_MMS_EXCEPTION) {
        Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_mms_exception, Toast.LENGTH_LONG).show();
        finish();
      }
    }

    @Override
    protected void onProgressUpdate(Void... values) {
      super.onProgressUpdate(values);
    }
  }

  private class FillExistingGroupInfoAsyncTask extends AsyncTask<Void,Void,Boolean> {

    @Override
    protected void onPreExecute() {
      pd = new ProgressDialog(GroupCreateActivity.this);
      pd.setTitle("Loading group details...");
      pd.setMessage("Please wait.");
      pd.setCancelable(false);
      pd.setIndeterminate(true);
      pd.show();
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
      final GroupDatabase db = DatabaseFactory.getGroupDatabase(GroupCreateActivity.this);
      final Recipients recipients = db.getGroupMembers(groupId);
      if (recipients != null) {
        final List<Recipient> recipientList = recipients.getRecipientsList();
        if (recipientList != null) {
          if (existingContacts == null)
            existingContacts = new HashSet<Recipient>(recipientList.size());
          existingContacts.addAll(recipientList);
        }
      }
      GroupDatabase.GroupRecord group = db.getGroup(groupId);
      if (group != null) {
        existingTitle = group.getTitle();
        final byte[] existingAvatar = group.getAvatar();
        if (existingAvatar != null) {
          existingAvatarBmp = BitmapUtil.getCircleCroppedBitmap(
              BitmapFactory.decodeByteArray(existingAvatar, 0, existingAvatar.length));
        }
        return true;
      }
      return null;
    }

    @Override
    protected void onPostExecute(Boolean isOwner) {
      super.onPostExecute(isOwner);

      if (pd != null) pd.dismiss();
      if (existingTitle != null) groupName.setText(existingTitle);
      if (existingAvatarBmp != null) avatar.setImageBitmap(existingAvatarBmp);
      if (existingContacts != null) syncAdapterWithSelectedContacts();
      if (!isOwner) {
        disableWhisperGroupUi(R.string.GroupCreateActivity_you_dont_own_this_group);
        getSupportActionBar().setTitle(getString(R.string.GroupCreateActivity_actionbar_update_title)
                                       + (TextUtils.isEmpty(existingTitle) ? "" : ": " + existingTitle));
      }
    }
  }
}
