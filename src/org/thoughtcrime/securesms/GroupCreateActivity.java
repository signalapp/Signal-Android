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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
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
import com.soundcloud.android.crop.Crop;

import org.thoughtcrime.securesms.components.PushRecipientsPanel;
import org.thoughtcrime.securesms.components.PushRecipientsPanel.RecipientsPanelChangedListener;
import org.thoughtcrime.securesms.contacts.RecipientsEditor;
import org.thoughtcrime.securesms.contacts.avatars.ContactColors;
import org.thoughtcrime.securesms.contacts.avatars.ContactPhotoFactory;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.GroupManager;
import org.thoughtcrime.securesms.groups.GroupManager.GroupActionResult;
import org.thoughtcrime.securesms.mms.RoundedCorners;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter;
import org.thoughtcrime.securesms.util.SelectedRecipientsAdapter.OnRecipientDeletedListener;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.util.InvalidNumberException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Activity to create and update groups
 *
 * @author Jake McGinty
 */
public class GroupCreateActivity extends PassphraseRequiredActionBarActivity
                                 implements OnRecipientDeletedListener,
                                            RecipientsPanelChangedListener
{

  private final static String TAG = GroupCreateActivity.class.getSimpleName();

  public static final String GROUP_RECIPIENT_EXTRA = "group_recipient";
  public static final String GROUP_THREAD_EXTRA    = "group_thread";

  private final DynamicTheme    dynamicTheme    = new DynamicTheme();
  private final DynamicLanguage dynamicLanguage = new DynamicLanguage();

  private static final int PICK_CONTACT = 1;
  public static final  int AVATAR_SIZE  = 210;

  private EditText     groupName;
  private ListView     lv;
  private ImageView    avatar;
  private TextView     creatingText;
  private MasterSecret masterSecret;
  private Bitmap       avatarBmp;

  @NonNull private Optional<GroupData> groupToUpdate = Optional.absent();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, @NonNull MasterSecret masterSecret) {
    this.masterSecret = masterSecret;

    setContentView(R.layout.group_create_activity);
    //noinspection ConstantConditions
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    initializeResources();
    initializeExistingGroup();
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    updateViewState();
  }

  private boolean isSignalGroup() {
    return TextSecurePreferences.isPushRegistered(this) && !getAdapter().hasNonPushMembers();
  }

  private void disableSignalGroupViews(int reasonResId) {
    View pushDisabled = findViewById(R.id.push_disabled);
    pushDisabled.setVisibility(View.VISIBLE);
    ((TextView) findViewById(R.id.push_disabled_reason)).setText(reasonResId);
    avatar.setEnabled(false);
    groupName.setEnabled(false);
  }

  private void enableSignalGroupViews() {
    findViewById(R.id.push_disabled).setVisibility(View.GONE);
    avatar.setEnabled(true);
    groupName.setEnabled(true);
  }

  @SuppressWarnings("ConstantConditions")
  private void updateViewState() {
    if (!TextSecurePreferences.isPushRegistered(this)) {
      disableSignalGroupViews(R.string.GroupCreateActivity_youre_not_registered_for_signal);
      getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_mms_title);
    } else if (getAdapter().hasNonPushMembers()) {
      disableSignalGroupViews(R.string.GroupCreateActivity_contacts_dont_support_push);
      getSupportActionBar().setTitle(R.string.GroupCreateActivity_actionbar_mms_title);
    } else {
      enableSignalGroupViews();
      getSupportActionBar().setTitle(groupToUpdate.isPresent()
                                     ? R.string.GroupCreateActivity_actionbar_update_title
                                     : R.string.GroupCreateActivity_actionbar_title);
    }
  }

  private static boolean isActiveInDirectory(Context context, Recipient recipient) {
    try {
      return TextSecureDirectory.getInstance(context)
                                .isSecureTextSupported(Util.canonicalizeNumber(context, recipient.getNumber()));
    } catch (NotInDirectoryException | InvalidNumberException e) {
      return false;
    }
  }

  private void addSelectedContacts(@NonNull Recipient... recipients) {
    new AddMembersTask(this).execute(recipients);
  }

  private void addSelectedContacts(@NonNull Collection<Recipient> recipients) {
    addSelectedContacts(recipients.toArray(new Recipient[recipients.size()]));
  }

  private void initializeResources() {
    RecipientsEditor    recipientsEditor = ViewUtil.findById(this, R.id.recipients_text);
    PushRecipientsPanel recipientsPanel  = ViewUtil.findById(this, R.id.recipients);
    lv           = ViewUtil.findById(this, R.id.selected_contacts_list);
    avatar       = ViewUtil.findById(this, R.id.avatar);
    groupName    = ViewUtil.findById(this, R.id.group_name);
    creatingText = ViewUtil.findById(this, R.id.creating_group_text);
    SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(this);
    adapter.setOnRecipientDeletedListener(this);
    lv.setAdapter(adapter);
    recipientsEditor.setHint(R.string.recipients_panel__add_members);
    recipientsPanel.setPanelChangeListener(this);
    findViewById(R.id.contacts_button).setOnClickListener(new AddRecipientButtonListener());
    avatar.setImageDrawable(ContactPhotoFactory.getDefaultGroupPhoto()
                                               .asDrawable(this, ContactColors.UNKNOWN_COLOR.toConversationColor(this)));
    avatar.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Crop.pickImage(GroupCreateActivity.this);
      }
    });
  }

  private void initializeExistingGroup() {
    final String encodedGroupId = RecipientFactory.getRecipientForId(this, getIntent().getLongExtra(GROUP_RECIPIENT_EXTRA, -1), true)
                                                  .getNumber();
    byte[] groupId;
    try {
      groupId = GroupUtil.getDecodedId(encodedGroupId);
    } catch (IOException ioe) {
      Log.w(TAG, "Couldn't decode the encoded groupId passed in via intent", ioe);
      groupId = null;
    }
    if (groupId != null) {
      new FillExistingGroupInfoAsyncTask(this).execute(groupId);
    }
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
        if (groupToUpdate.isPresent()) handleGroupUpdate();
        else                           handleGroupCreate();
        return true;
    }

    return false;
  }

  @Override
  public void onRecipientDeleted(Recipient recipient) {
    getAdapter().remove(recipient);
    updateViewState();
  }

  @Override
  public void onRecipientsPanelUpdate(Recipients recipients) {
    if (recipients != null) addSelectedContacts(recipients.getRecipientsList());
  }

  private void handleGroupCreate() {
    if (getAdapter().getCount() < 1) {
      Log.i(TAG, getString(R.string.GroupCreateActivity_contacts_no_members));
      Toast.makeText(getApplicationContext(), R.string.GroupCreateActivity_contacts_no_members, Toast.LENGTH_SHORT).show();
      return;
    }
    if (isSignalGroup()) {
      new CreateSignalGroupTask(this, masterSecret, avatarBmp, getGroupName(), getAdapter().getRecipients()).execute();
    } else {
      new CreateMmsGroupTask(this, getAdapter().getRecipients()).execute();
    }
  }

  private void handleGroupUpdate() {
    new UpdateSignalGroupTask(this, masterSecret, groupToUpdate.get().id, avatarBmp,
                              getGroupName(), getAdapter().getRecipients()).execute();
  }

  private void handleOpenConversation(long threadId, Recipients recipients) {
    Intent intent = new Intent(this, ConversationActivity.class);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
    startActivity(intent);
    finish();
  }

  private SelectedRecipientsAdapter getAdapter() {
    return (SelectedRecipientsAdapter)lv.getAdapter();
  }

  private @Nullable String getGroupName() {
    return groupName.getText() != null ? groupName.getText().toString() : null;
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
          final Recipient recipient = RecipientFactory.getRecipientsFromString(this, contact, false).getPrimaryRecipient();
          if (recipient != null) addSelectedContacts(recipient);
        }
        break;

      case Crop.REQUEST_PICK:
        new Crop(data.getData()).output(outputFile).asSquare().start(this);
        break;
      case Crop.REQUEST_CROP:
        Glide.with(this).load(Crop.getOutput(data)).asBitmap().skipMemoryCache(true)
             .centerCrop().override(AVATAR_SIZE, AVATAR_SIZE)
             .into(new SimpleTarget<Bitmap>() {
               @Override
               public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                 setAvatar(Crop.getOutput(data), resource);
               }
             });
    }
  }

  private class AddRecipientButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      Intent intent = new Intent(GroupCreateActivity.this, PushContactSelectionActivity.class);
      if (groupToUpdate.isPresent()) intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE,
                                                     ContactSelectionListFragment.DISPLAY_MODE_PUSH_ONLY);
      startActivityForResult(intent, PICK_CONTACT);
    }
  }

  private static class CreateMmsGroupTask extends AsyncTask<Void,Void,Long> {
    private GroupCreateActivity activity;
    private Set<Recipient>      members;

    public CreateMmsGroupTask(GroupCreateActivity activity, Set<Recipient> members) {
      this.activity = activity;
      this.members  = members;
    }

    @Override
    protected Long doInBackground(Void... avoid) {
      Recipients recipients = RecipientFactory.getRecipientsFor(activity, members, false);
      return DatabaseFactory.getThreadDatabase(activity)
                            .getThreadIdFor(recipients, ThreadDatabase.DistributionTypes.CONVERSATION);
    }

    @Override
    protected void onPostExecute(Long resultThread) {
      if (resultThread > -1) {
        activity.handleOpenConversation(resultThread,
                                        RecipientFactory.getRecipientsFor(activity, members, true));
      } else {
        Toast.makeText(activity, R.string.GroupCreateActivity_contacts_mms_exception, Toast.LENGTH_LONG).show();
        activity.finish();
      }
    }

    @Override
    protected void onProgressUpdate(Void... values) {
      super.onProgressUpdate(values);
    }
  }

  private abstract static class SignalGroupTask extends AsyncTask<Void,Void,Optional<GroupActionResult>> {
    protected GroupCreateActivity activity;
    protected MasterSecret        masterSecret;
    protected Bitmap              avatar;
    protected Set<Recipient>      members;
    protected String              name;

    public SignalGroupTask(GroupCreateActivity activity,
                           MasterSecret        masterSecret,
                           Bitmap              avatar,
                           String              name,
                           Set<Recipient>      members)
    {
      this.activity     = activity;
      this.masterSecret = masterSecret;
      this.avatar       = avatar;
      this.name         = name;
      this.members      = members;
    }

    @Override
    protected void onPreExecute() {
      activity.findViewById(R.id.group_details_layout).setVisibility(View.GONE);
      activity.findViewById(R.id.creating_group_layout).setVisibility(View.VISIBLE);
      activity.findViewById(R.id.menu_create_group).setVisibility(View.GONE);
        final int titleResId = activity.groupToUpdate.isPresent()
                             ? R.string.GroupCreateActivity_updating_group
                             : R.string.GroupCreateActivity_creating_group;
        activity.creatingText.setText(activity.getString(titleResId, activity.getGroupName()));
      }

    @Override
    protected void onPostExecute(Optional<GroupActionResult> groupActionResultOptional) {
      if (activity.isFinishing()) return;
      activity.findViewById(R.id.group_details_layout).setVisibility(View.VISIBLE);
      activity.findViewById(R.id.creating_group_layout).setVisibility(View.GONE);
      activity.findViewById(R.id.menu_create_group).setVisibility(View.VISIBLE);
    }
  }

  private static class CreateSignalGroupTask extends SignalGroupTask {
    public CreateSignalGroupTask(GroupCreateActivity activity, MasterSecret masterSecret, Bitmap avatar, String name, Set<Recipient> members) {
      super(activity, masterSecret, avatar, name, members);
    }

    @Override
    protected Optional<GroupActionResult> doInBackground(Void... aVoid) {
      try {
        return Optional.of(GroupManager.createGroup(activity, masterSecret, members, avatar, name));
      } catch (InvalidNumberException e) {
        return Optional.absent();
      }
    }

    @Override
    protected void onPostExecute(Optional<GroupActionResult> result) {
      if (result.isPresent() && result.get().getThreadId() > -1) {
        if (!activity.isFinishing()) {
          activity.handleOpenConversation(result.get().getThreadId(), result.get().getGroupRecipient());
        }
      } else {
        super.onPostExecute(result);
        Toast.makeText(activity.getApplicationContext(),
                       R.string.GroupCreateActivity_contacts_invalid_number, Toast.LENGTH_LONG).show();
      }
    }
  }

  private static class UpdateSignalGroupTask extends SignalGroupTask {
    private byte[] groupId;

    public UpdateSignalGroupTask(GroupCreateActivity activity,
                                 MasterSecret masterSecret, byte[] groupId, Bitmap avatar, String name,
                                 Set<Recipient> members)
    {
      super(activity, masterSecret, avatar, name, members);
      this.groupId = groupId;
    }

    @Override
    protected Optional<GroupActionResult> doInBackground(Void... aVoid) {
      try {
        return Optional.of(GroupManager.updateGroup(activity, masterSecret, groupId, members, avatar, name));
      } catch (InvalidNumberException e) {
        return Optional.absent();
      }
    }

    @Override
    protected void onPostExecute(Optional<GroupActionResult> result) {
      if (result.isPresent() && result.get().getThreadId() > -1) {
        if (!activity.isFinishing()) {
          Intent intent = activity.getIntent();
          intent.putExtra(GROUP_THREAD_EXTRA, result.get().getThreadId());
          intent.putExtra(GROUP_RECIPIENT_EXTRA, result.get().getGroupRecipient().getIds());
          activity.setResult(RESULT_OK, intent);
          activity.finish();
        }
      } else {
        super.onPostExecute(result);
        Toast.makeText(activity.getApplicationContext(),
                       R.string.GroupCreateActivity_contacts_invalid_number, Toast.LENGTH_LONG).show();
      }
    }
  }

  private static class AddMembersTask extends AsyncTask<Recipient,Void,List<AddMembersTask.Result>> {
    static class Result {
      Optional<Recipient> recipient;
      boolean             isPush;
      String              reason;

      public Result(@Nullable Recipient recipient, boolean isPush, @Nullable String reason) {
        this.recipient = Optional.fromNullable(recipient);
        this.isPush    = isPush;
        this.reason    = reason;
      }
    }

    private GroupCreateActivity activity;
    private boolean             failIfNotPush;

    public AddMembersTask(@NonNull GroupCreateActivity activity) {
      this.activity      = activity;
      this.failIfNotPush = activity.groupToUpdate.isPresent();
    }

    @Override
    protected List<Result> doInBackground(Recipient... recipients) {
      final List<Result> results = new LinkedList<>();

      for (Recipient recipient : recipients) {
        boolean isPush        = isActiveInDirectory(activity, recipient);
        String  recipientE164 = null;
        try {
          recipientE164 = Util.canonicalizeNumber(activity, recipient.getNumber());
        } catch (InvalidNumberException ine) { /* do nothing */ }

        if (failIfNotPush && !isPush) {
          results.add(new Result(null, false, activity.getString(R.string.GroupCreateActivity_cannot_add_non_push_to_existing_group,
                                                                 recipient.getNumber())));
        } else if (TextUtils.equals(TextSecurePreferences.getLocalNumber(activity), recipientE164)) {
          results.add(new Result(null, false, activity.getString(R.string.GroupCreateActivity_youre_already_in_the_group)));
        } else {
          results.add(new Result(recipient, isPush, null));
        }
      }
      return results;
    }

    @Override
    protected void onPostExecute(List<Result> results) {
      if (activity.isFinishing()) return;

      for (Result result : results) {
        if (result.recipient.isPresent()) {
          activity.getAdapter().add(result.recipient.get(), result.isPush);
        } else {
          Toast.makeText(activity, result.reason, Toast.LENGTH_SHORT).show();
        }
      }
      activity.updateViewState();
    }
  }

  private static class FillExistingGroupInfoAsyncTask extends ProgressDialogAsyncTask<byte[],Void,Optional<GroupData>> {
    private GroupCreateActivity activity;

    public FillExistingGroupInfoAsyncTask(GroupCreateActivity activity) {
      super(activity,
            R.string.GroupCreateActivity_loading_group_details,
            R.string.please_wait);
      this.activity = activity;
    }

    @Override
    protected Optional<GroupData> doInBackground(byte[]... groupIds) {
      final GroupDatabase   db               = DatabaseFactory.getGroupDatabase(activity);
      final Recipients      recipients       = db.getGroupMembers(groupIds[0], false);
      final GroupRecord     group            = db.getGroup(groupIds[0]);
      final Set<Recipient>  existingContacts = new HashSet<>(recipients.getRecipientsList().size());
      existingContacts.addAll(recipients.getRecipientsList());

      if (group != null) {
        return Optional.of(new GroupData(groupIds[0],
                                         existingContacts,
                                         BitmapUtil.fromByteArray(group.getAvatar()),
                                         group.getAvatar(),
                                         group.getTitle()));
      } else {
        return Optional.absent();
      }
    }

    @Override
    protected void onPostExecute(Optional<GroupData> group) {
      super.onPostExecute(group);

      if (group.isPresent() && !activity.isFinishing()) {
        activity.groupToUpdate = group;

        activity.groupName.setText(group.get().name);
        if (group.get().avatarBmp != null) {
          activity.setAvatar(group.get().avatarBytes, group.get().avatarBmp);
        }
        SelectedRecipientsAdapter adapter = new SelectedRecipientsAdapter(activity, group.get().recipients);
        adapter.setOnRecipientDeletedListener(activity);
        activity.lv.setAdapter(adapter);
        activity.updateViewState();
      }
    }
  }

  private <T> void setAvatar(T model, Bitmap bitmap) {
    avatarBmp = bitmap;
    Glide.with(this)
         .load(model)
         .skipMemoryCache(true)
         .transform(new RoundedCorners(this, avatar.getWidth() / 2))
         .into(avatar);
  }

  private static class GroupData {
    byte[]         id;
    Set<Recipient> recipients;
    Bitmap         avatarBmp;
    byte[]         avatarBytes;
    String         name;

    public GroupData(byte[] id, Set<Recipient> recipients, Bitmap avatarBmp, byte[] avatarBytes, String name) {
      this.id          = id;
      this.recipients  = recipients;
      this.avatarBmp   = avatarBmp;
      this.avatarBytes = avatarBytes;
      this.name        = name;
    }
  }
}
