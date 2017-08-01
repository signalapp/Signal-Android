package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.support.v7.app.AlertDialog;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;

import java.util.LinkedList;
import java.util.List;

public class GroupMembersDialog extends AsyncTask<Void, Void, List<Recipient>> {

  private static final String TAG = GroupMembersDialog.class.getSimpleName();

  private final Recipient  recipient;
  private final Context    context;

  public GroupMembersDialog(Context context, Recipient recipient) {
    this.recipient = recipient;
    this.context   = context;
  }

  @Override
  public void onPreExecute() {}

  @Override
  protected List<Recipient> doInBackground(Void... params) {
    return DatabaseFactory.getGroupDatabase(context).getGroupMembers(recipient.getAddress().toGroupString(), true);
  }

  @Override
  public void onPostExecute(List<Recipient> members) {
    GroupMembers groupMembers = new GroupMembers(members);
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(R.string.ConversationActivity_group_members);
    builder.setIconAttribute(R.attr.group_members_dialog_icon);
    builder.setCancelable(true);
    builder.setItems(groupMembers.getRecipientStrings(), new GroupMembersOnClickListener(context, groupMembers));
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  public void display() {
    execute();
  }

  private static class GroupMembersOnClickListener implements DialogInterface.OnClickListener {
    private final GroupMembers groupMembers;
    private final Context      context;

    public GroupMembersOnClickListener(Context context, GroupMembers members) {
      this.context      = context;
      this.groupMembers = members;
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int item) {
      Recipient recipient = groupMembers.get(item);

      if (recipient.getContactUri() != null) {
        ContactsContract.QuickContact.showQuickContact(context, new Rect(0,0,0,0),
                                                       recipient.getContactUri(),
                                                       ContactsContract.QuickContact.MODE_LARGE, null);
      } else {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        if (recipient.getAddress().isEmail()) {
          intent.putExtra(ContactsContract.Intents.Insert.EMAIL, recipient.getAddress().toEmailString());
        } else {
          intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getAddress().toPhoneString());
        }
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        context.startActivity(intent);
      }
    }
  }

  /**
   * Wraps a List of Recipient (just like @class Recipients),
   * but with focus on the order of the Recipients.
   * So that the order of the RecipientStrings[] matches
   * the internal order.
   *
   * @author Christoph Haefner
   */
  private class GroupMembers {
    private final String TAG = GroupMembers.class.getSimpleName();

    private final LinkedList<Recipient> members = new LinkedList<>();

    public GroupMembers(List<Recipient> recipients) {
      for (Recipient recipient : recipients) {
        if (isLocalNumber(recipient)) {
          members.push(recipient);
        } else {
          members.add(recipient);
        }
      }
    }

    public String[] getRecipientStrings() {
      List<String> recipientStrings = new LinkedList<>();

      for (Recipient recipient : members) {
        if (isLocalNumber(recipient)) {
          recipientStrings.add(context.getString(R.string.GroupMembersDialog_me));
        } else {
          recipientStrings.add(recipient.toShortString());
        }
      }

      return recipientStrings.toArray(new String[members.size()]);
    }

    public Recipient get(int index) {
      return members.get(index);
    }

    private boolean isLocalNumber(Recipient recipient) {
      return Util.isOwnNumber(context, recipient.getAddress());
    }
  }
}
