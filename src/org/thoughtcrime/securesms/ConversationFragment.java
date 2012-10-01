package org.thoughtcrime.securesms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.ClipboardManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import com.actionbarsherlock.app.SherlockListFragment;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageRecord;
import org.thoughtcrime.securesms.database.loaders.ConversationLoader;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.sql.Date;
import java.text.SimpleDateFormat;

public class ConversationFragment extends SherlockListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{

  private ConversationFragmentListener listener;

  private MasterSecret masterSecret;
  private Recipients   recipients;
  private long         threadId;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    initializeResources();
    initializeListAdapter();
    registerForContextMenu(getListView());
  }

  @Override
  public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    android.view.MenuInflater inflater = this.getSherlockActivity().getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.conversation_context, menu);
  }

  @Override
  public boolean onContextItemSelected(android.view.MenuItem item) {
    Cursor cursor                     = ((CursorAdapter)getListAdapter()).getCursor();
    ConversationItem conversationItem = (ConversationItem)(((ConversationAdapter)getListAdapter()).newView(getActivity(), cursor, null));
    MessageRecord messageRecord       = conversationItem.getMessageRecord();

    switch(item.getItemId()) {
    case R.id.menu_context_copy:           handleCopyMessage(messageRecord);     return true;
    case R.id.menu_context_delete_message: handleDeleteMessage(messageRecord);   return true;
    case R.id.menu_context_details:        handleDisplayDetails(messageRecord);  return true;
    case R.id.menu_context_forward:        handleForwardMessage(messageRecord);  return true;
    case R.id.menu_context_resend:         handleResendMessage(messageRecord);  return true;
    }

    return false;
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.listener = (ConversationFragmentListener)activity;
  }

  public void reload(Recipients recipients, long threadId) {
    this.recipients = recipients;
    this.threadId   = threadId;

    initializeListAdapter();
  }

  private void handleCopyMessage(MessageRecord message) {
    String body = message.getBody();
    if (body == null) return;

    ClipboardManager clipboard = (ClipboardManager)getActivity()
        .getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setText(body);
  }

  private void handleDeleteMessage(MessageRecord message) {
    final long messageId   = message.getId();
    final String transport = message.isMms() ? "mms" : "sms";

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_confirm_message_delete);
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationFragment_are_you_sure_you_want_to_permanently_delete_this_message);

    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (transport.equals("mms")) {
          DatabaseFactory.getMmsDatabase(getActivity()).delete(messageId);
        } else {
          DatabaseFactory.getSmsDatabase(getActivity()).deleteMessage(messageId);
        }
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleDisplayDetails(MessageRecord message) {
    String sender    = message.getRecipients().getPrimaryRecipient().getNumber();
    String transport = message.isMms() ? "mms" : "sms";
    long date        = message.getDate();

    SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE MMM d, yyyy 'at' hh:mm:ss a zzz");
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_message_details);
    builder.setIcon(android.R.drawable.ic_dialog_info);
    builder.setCancelable(false);
    builder.setMessage(String.format(getSherlockActivity()
                                     .getString(R.string.ConversationFragment_sender_s_transport_s_sent_received_s),
                                     sender, transport.toUpperCase(),
                                     dateFormatter.format(new Date(date))));
    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  private void handleForwardMessage(MessageRecord message) {
    Intent composeIntent = new Intent(getActivity(), ConversationActivity.class);
    composeIntent.putExtra("forwarded_message", message.getBody());
    composeIntent.putExtra("master_secret", masterSecret);
    startActivity(composeIntent);
  }

  private void handleResendMessage(MessageRecord message) {
	    Intent resendIntent = new Intent(getActivity(), ConversationActivity.class);
	    resendIntent.putExtra("recipients", message.getRecipients());
	    resendIntent.putExtra("resent_message", message.getBody());
	    resendIntent.putExtra("master_secret", masterSecret);
	    startActivity(resendIntent);
	  }  
  
  private void initializeResources() {
    this.masterSecret = (MasterSecret)this.getActivity().getIntent()
                          .getParcelableExtra("master_secret");
    this.recipients   = this.getActivity().getIntent().getParcelableExtra("recipients");
    this.threadId     = this.getActivity().getIntent().getLongExtra("thread_id", -1);
  }

  private void initializeListAdapter() {
    if (this.recipients != null && this.threadId != -1) {
      this.setListAdapter(new ConversationAdapter(recipients, threadId, getActivity(),
                                                  masterSecret, new FailedIconClickHandler()));
      getLoaderManager().initLoader(0, null, this);
    }
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationLoader(getActivity(), threadId);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    ((CursorAdapter)getListAdapter()).changeCursor(null);
  }

  private class FailedIconClickHandler extends Handler {
    @Override
    public void handleMessage(android.os.Message message) {
      if (listener != null) {
        listener.setComposeText((String)message.obj);
      }
    }
  }

  public interface ConversationFragmentListener {
    public void setComposeText(String text);
  }

}