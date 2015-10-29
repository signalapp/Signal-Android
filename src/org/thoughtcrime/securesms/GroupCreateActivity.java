/**
 * Copyright (C) 2014 Open Whisper Systems
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.google.protobuf.ByteString;
import com.soundcloud.android.crop.Crop;

import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.components.PushRecipientsPanel;
import org.thoughtcrime.securesms.contacts.RecipientsEditor;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.mms.RoundedCorners;
import org.thoughtcrime.securesms.providers.SingleUseBlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.textsecure.api.util.InvalidNumberException;
import org.whispersystems.textsecure.internal.push.TextSecureProtos.GroupContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ws.com.google.android.mms.ContentType;
import ws.com.google.android.mms.MmsException;


/**
 * Activity to create and update groups
 *
 * @author Jake McGinty
 */
public class GroupCreateActivity extends PassphraseRequiredActionBarActivity {

  private final static String TAG = GroupCreateActivity.class.getSimpleName();

  public static final String GROUP_RECIPIENT_EXTRA = "group_recipient";
  public static final String GROUP_THREAD_EXTRA    = "group_thread";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final int PICK_CONTACT = 1;
  private static final int PICK_AVATAR  = 2;
  public static final  int AVATAR_SIZE  = 210;

  private EditText            groupName;
  private ListView            lv;
  private PushRecipientsPanel recipientsPanel;
  private ImageView           avatar;
  private TextView            creatingText;

  private Recipient      groupRecipient    = null;
  private long           groupThread       = -1;
  private byte[]         groupId           = null;
  private Set<Recipient> existingContacts  = null;
  private String         existingTitle     = null;
  private Bitmap         existingAvatarBmp = null;

  private MasterSecret   masterSecret;
  private Bitmap         avatarBmp;
  private Set<Recipient> selectedContacts;

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;

    setContentView(R.layout.group_create_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    selectedContacts = new HashSet<>();
    initializeResources();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_title);
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
    if (groupNameText != null && groupNameText.length() > 0) {
      getSupportActionBar().setTitle(groupNameText);
    } else {
      getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_title);
    }
  }

  private static boolean isActiveInDirectory(Context context, Recipient recipient) {
    try {
      if (!TextSecureDirectory.getInstance(context).isSecureTextSupported(Util.canonicalizeNumber(context, recipient.getNumber()))) {
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
    final boolean isPushUser = isActiveInDirectory(this, contact);
    if (existingContacts != null && !isPushUser) {
      Toast.makeText(getApplicationContext(),
                     R.string.GroupCreateActivity_cannot_add_non_push_to_existing_group,
                     Toast.LENGTH_LONG).show();
      return;
    }

    if (!selectedContacts.contains(contact) && (existingContacts == null || !existingContacts.contains(contact)))
      selectedContacts.add(contact);
    if (!isPushUser) {
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
    groupRecipient = RecipientFactory.getRecipientForId(this, getIntent().getLongExtra(GROUP_RECIPIENT_EXTRA, -1), true);
    groupThread = getIntent().getLongExtra(GROUP_THREAD_EXTRA, -1);
    if (groupRecipient != null) {
      final String encodedGroupId = groupRecipient.getNumber();
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
        final int prefixResId = (groupId != null)
                                ? R.string.GroupCreateActivity_actionbar_update_title
                                : R.string.GroupCreateActivity_actionbar_title;
        if (editable.length() > 0) {
          getSupportActionBar().setTitle(getString(prefixResId) + ": " + editable.toString());
        } else {
          getSupportActionBar().setTitle(prefixResId);
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
        Crop.pickImage(GroupCreateActivity.this);
      }
    });

    ((RecipientsEditor)findViewById(R.id.recipients_text)).setHint(R.string.recipients_panel__add_member);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
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
      enableWhisperGroupProgressUi(false);
      new CreateWhisperGroupAsyncTask().execute();
    } else {
      new CreateMmsGroupAsyncTask().execute();
    }
  }

  private void handleGroupUpdate() {
    if (whisperGroupUiEnabled()) {
      enableWhisperGroupProgressUi(true);
    }
    new UpdateWhisperGroupAsyncTask().execute();
  }

  private void enableWhisperGroupProgressUi(boolean isGroupUpdate) {
    findViewById(R.id.group_details_layout).setVisibility(View.GONE);
    findViewById(R.id.creating_group_layout).setVisibility(View.VISIBLE);
    findViewById(R.id.menu_create_group).setVisibility(View.GONE);
    if (groupName.getText() != null) {
      final int titleResId = isGroupUpdate
                             ? R.string.GroupCreateActivity_updating_group
                             : R.string.GroupCreateActivity_creating_group;
      creatingText.setText(getString(titleResId, groupName.getText().toString()));
    }
  }

  private void disableWhisperGroupProgressUi() {
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
  public void onActivityResult(int reqCode, int resultCode, final Intent data) {
    super.onActivityResult(reqCode, resultCode, data);
    Uri outputFile = Uri.fromFile(new File(getCacheDir(), "cropped"));

    if (data == null || resultCode != Activity.RESULT_OK)
      return;

    switch (reqCode) {
      case PICK_CONTACT:
        List<String> selected = data.getStringArrayListExtra("contacts");
        for (String contact : selected) {
          Recipient recipient = RecipientFactory.getRecipientsFromString(this, contact, false).getPrimaryRecipient();

          if (!selectedContacts.contains(recipient)                               &&
              (existingContacts == null || !existingContacts.contains(recipient)) &&
              recipient != null) {
            addSelectedContact(recipient);
          }
        }
        syncAdapterWithSelectedContacts();
        break;

      case Crop.REQUEST_PICK:
        new Crop(data.getData()).output(outputFile).asSquare().start(this);
        break;
      case Crop.REQUEST_CROP:
        Glide.with(this).load(Crop.getOutput(data)).asBitmap().skipMemoryCache(true)
             .centerCrop().override(AVATAR_SIZE, AVATAR_SIZE)
             .into(new SimpleTarget<Bitmap>() {
               @Override public void onResourceReady(Bitmap resource,
                                                     GlideAnimation<? super Bitmap> glideAnimation)
               {
                 avatarBmp = resource;
                 Glide.with(GroupCreateActivity.this).load(Crop.getOutput(data)).skipMemoryCache(true)
                      .transform(new RoundedCorners(GroupCreateActivity.this, avatar.getWidth() / 2))
                      .into(avatar);
               }
             });
    }
  }

  private class AddRecipientButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(GroupCreateActivity.this, PushContactSelectionActivity.class);
      if (existingContacts != null) intent.putExtra(PushContactSelectionActivity.PUSH_ONLY_EXTRA, true);
      startActivityForResult(intent, PICK_CONTACT);
    }
  }

  private Pair<Long, Recipients> handleCreatePushGroup(String groupName, byte[] avatar,
                                                       Set<Recipient> members)
      throws InvalidNumberException, MmsException
  {
    GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(this);
    byte[]        groupId           = groupDatabase.allocateGroupId();
    Set<String>   memberE164Numbers = getE164Numbers(members);

    memberE164Numbers.add(TextSecurePreferences.getLocalNumber(this));

    groupDatabase.create(groupId, groupName, new LinkedList<String>(memberE164Numbers), null, null);
    groupDatabase.updateAvatar(groupId, avatar);

    return handlePushOperation(groupId, groupName, avatar, memberE164Numbers);
  }

  private Pair<Long, Recipients> handleUpdatePushGroup(byte[] groupId, String groupName,
                                                       byte[] avatar, Set<Recipient> members)
      throws InvalidNumberException, MmsException
  {
    GroupDatabase groupDatabase     = DatabaseFactory.getGroupDatabase(this);
    Set<String>  memberE164Numbers = getE164Numbers(members);
    memberE164Numbers.add(TextSecurePreferences.getLocalNumber(this));

    for (String number : memberE164Numbers)
      Log.w(TAG, "Updating: " + number);

    groupDatabase.updateMembers(groupId, new LinkedList<String>(memberE164Numbers));
    groupDatabase.updateTitle(groupId, groupName);
    groupDatabase.updateAvatar(groupId, avatar);

    return handlePushOperation(groupId, groupName, avatar, memberE164Numbers);
  }

  private Pair<Long, Recipients> handlePushOperation(byte[] groupId, String groupName,
                                                     @Nullable byte[] avatar,
                                                     Set<String> e164numbers)
      throws InvalidNumberException
  {
    Attachment avatarAttachment = null;
    String     groupRecipientId = GroupUtil.getEncodedId(groupId);
    Recipients groupRecipient   = RecipientFactory.getRecipientsFromString(this, groupRecipientId, false);

    GroupContext context = GroupContext.newBuilder()
                                       .setId(ByteString.copyFrom(groupId))
                                       .setType(GroupContext.Type.UPDATE)
                                       .setName(groupName)
                                       .addAllMembers(e164numbers)
                                       .build();

    if (avatar != null) {
      Uri avatarUri = SingleUseBlobProvider.getInstance().createUri(avatar);
      avatarAttachment = new UriAttachment(avatarUri, ContentType.IMAGE_JPEG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length);
    }

    OutgoingGroupMediaMessage outgoingMessage  = new OutgoingGroupMediaMessage(groupRecipient, context, avatarAttachment, System.currentTimeMillis());
    long                      threadId         = MessageSender.send(this, masterSecret, outgoingMessage, -1, false);

    return new Pair<>(threadId, groupRecipient);
  }

  private long handleCreateMmsGroup(Set<Recipient> members) {
    Recipients recipients = RecipientFactory.getRecipientsFor(this, new LinkedList<>(members), false);
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

  private Set<String> getE164Numbers(Set<Recipient> recipients)
      throws InvalidNumberException
  {
    Set<String> results = new HashSet<String>();

    for (Recipient recipient : recipients) {
      results.add(Util.canonicalizeNumber(this, recipient.getNumber()));
    }

    return results;
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
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, resultThread.longValue());
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);

        ArrayList<Recipient> selectedContactsList = setToArrayList(selectedContacts);
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, RecipientFactory.getRecipientsFor(GroupCreateActivity.this, selectedContactsList, true).getIds());
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
      final Bitmap bitmap;
      if (avatarBmp == null) bitmap = existingAvatarBmp;
      else                   bitmap = avatarBmp;

      if (bitmap != null) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        avatarBytes = stream.toByteArray();
      }
      final String name = (groupName.getText() != null) ? groupName.getText().toString() : null;
      try {
        Set<Recipient> unionContacts = new HashSet<Recipient>(selectedContacts);
        unionContacts.addAll(existingContacts);
        return handleUpdatePushGroup(groupId, name, avatarBytes, unionContacts);
      } catch (MmsException e) {
        Log.w(TAG, e);
        return new Pair<Long,Recipients>(RES_MMS_EXCEPTION, null);
      } catch (InvalidNumberException e) {
        Log.w(TAG, e);
        return new Pair<Long,Recipients>(RES_BAD_NUMBER, null);
      }
    }

    @Override
    protected void onPostExecute(Pair<Long, Recipients> groupInfo) {
      final long threadId = groupInfo.first;
      final Recipients recipients = groupInfo.second;
      if (threadId > -1) {
        Intent intent = getIntent();
        intent.putExtra(GROUP_THREAD_EXTRA, threadId);
        intent.putExtra(GROUP_RECIPIENT_EXTRA, recipients.getIds());
        setResult(RESULT_OK, intent);
        finish();
      } else if (threadId == RES_BAD_NUMBER) {
        Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_invalid_number, Toast.LENGTH_LONG).show();
        disableWhisperGroupProgressUi();
      } else if (threadId == RES_MMS_EXCEPTION) {
        Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_mms_exception, Toast.LENGTH_LONG).show();
        setResult(RESULT_CANCELED);
        finish();
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
        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
        startActivity(intent);
        finish();
      } else if (threadId == RES_BAD_NUMBER) {
        Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_invalid_number, Toast.LENGTH_LONG).show();
        disableWhisperGroupProgressUi();
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

  private class FillExistingGroupInfoAsyncTask extends ProgressDialogAsyncTask<Void,Void,Void> {

    public FillExistingGroupInfoAsyncTask() {
      super(GroupCreateActivity.this,
            R.string.GroupCreateActivity_loading_group_details,
            R.string.please_wait);
    }

    @Override
    protected Void doInBackground(Void... voids) {
      final GroupDatabase db = DatabaseFactory.getGroupDatabase(GroupCreateActivity.this);
      final Recipients recipients = db.getGroupMembers(groupId, false);
      if (recipients != null) {
        final List<Recipient> recipientList = recipients.getRecipientsList();
        if (recipientList != null) {
          if (existingContacts == null)
            existingContacts = new HashSet<>(recipientList.size());
          existingContacts.addAll(recipientList);
        }
      }
      GroupDatabase.GroupRecord group = db.getGroup(groupId);
      if (group != null) {
        existingTitle = group.getTitle();
        final byte[] existingAvatar = group.getAvatar();
        if (existingAvatar != null) {
          existingAvatarBmp = BitmapFactory.decodeByteArray(existingAvatar, 0, existingAvatar.length);
        }
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      super.onPostExecute(aVoid);

      if (existingTitle != null) groupName.setText(existingTitle);
      if (existingAvatarBmp != null) avatar.setImageBitmap(existingAvatarBmp);
      if (existingContacts != null) syncAdapterWithSelectedContacts();
    }
  }
}
