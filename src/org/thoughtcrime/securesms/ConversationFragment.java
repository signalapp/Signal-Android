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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.CursorAdapter;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.loaders.ConversationLoader;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;
import org.whispersystems.textsecure.crypto.MasterSecret;

import java.sql.Date;
import java.text.SimpleDateFormat;

public class ConversationFragment extends SherlockListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = ConversationFragment.class.getSimpleName();

  private ConversationFragmentListener listener;

  private MasterSecret masterSecret;
  private Recipients   recipients;
  private long         threadId;
  private ActionMode   actionMode;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    initializeResources();
    initializeListAdapter();
    initializeContextualActionBar();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.listener = (ConversationFragmentListener)activity;
  }

  private void initializeResources() {
    String recipientIds = this.getActivity().getIntent().getStringExtra("recipients");

    this.masterSecret = this.getActivity().getIntent().getParcelableExtra("master_secret");
    this.recipients   = RecipientFactory.getRecipientsForIds(getActivity(), recipientIds, true);
    this.threadId     = this.getActivity().getIntent().getLongExtra("thread_id", -1);
  }

  private void initializeListAdapter() {
    if (this.recipients != null && this.threadId != -1) {
      this.setListAdapter(new ConversationAdapter(getActivity(), masterSecret,
                                                  new FailedIconClickHandler(),
                                                  (!this.recipients.isSingleRecipient()) || this.recipients.isGroupRecipient(),
                                                  DirectoryHelper.isPushDestination(getActivity(), this.recipients)));
      getListView().setRecyclerListener((ConversationAdapter)getListAdapter());
      getLoaderManager().initLoader(0, null, this);
    }
  }

  private void initializeContextualActionBar() {
    getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (actionMode != null) {
          view.setSelected(true);
          return false;
        }

        actionMode = getSherlockActivity().startActionMode(actionModeCallback);
        view.setSelected(true);
        return true;
      }
    });

    getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (actionMode != null) {
          view.setSelected(true);
          setCorrectMenuVisibility(getMessageRecord(), actionMode.getMenu());
        }
      }
    });
  }

  private void setCorrectMenuVisibility(MessageRecord messageRecord, Menu menu) {
    MenuItem resend         = menu.findItem(R.id.menu_context_resend);
    MenuItem saveAttachment = menu.findItem(R.id.menu_context_save_attachment);

    if (messageRecord.isFailed()) resend.setVisible(true);
    else                          resend.setVisible(false);

    if (messageRecord.isMms() && !messageRecord.isMmsNotification()) {
      saveAttachment.setVisible(((MediaMmsMessageRecord)messageRecord).containsMediaSlide());
    } else {
      saveAttachment.setVisible(false);
    }
  }

  private MessageRecord getMessageRecord() {
    Cursor cursor                     = ((CursorAdapter)getListAdapter()).getCursor();
    ConversationItem conversationItem = (ConversationItem)(((ConversationAdapter)getListAdapter()).newView(getActivity(), cursor, null));
    return conversationItem.getMessageRecord();
  }

  public void reload(Recipients recipients, long threadId) {
    this.recipients = recipients;
    this.threadId   = threadId;

    initializeListAdapter();
  }

  public void scrollToBottom() {
    final ListView list = getListView();
    list.post(new Runnable() {
      @Override
      public void run() {
        list.setSelection(getListAdapter().getCount() - 1);
      }
    });
  }

  private void handleCopyMessage(MessageRecord message) {
    String body = message.getDisplayBody().toString();
    if (body == null) return;

    ClipboardManager clipboard = (ClipboardManager)getActivity()
        .getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setText(body);
  }

  private void handleDeleteMessage(final MessageRecord message) {
    final long messageId   = message.getId();

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_confirm_message_delete);
    builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_alert_icon));
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationFragment_are_you_sure_you_want_to_permanently_delete_this_message);

    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (message.isMms()) {
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
    long dateReceived = message.getDateReceived();
    long dateSent     = message.getDateSent();

    String transport;

    if      (message.isPending()) transport = "pending";
    else if (message.isPush())    transport = "push";
    else if (message.isMms())     transport = "mms";
    else                          transport = "sms";

    SimpleDateFormat dateFormatter = new SimpleDateFormat("EEE MMM d, yyyy 'at' hh:mm:ss a zzz");
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_message_details);
    builder.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_info_icon));
    builder.setCancelable(true);

    if (dateReceived == dateSent || message.isOutgoing()) {
      builder.setMessage(String.format(getSherlockActivity()
                                       .getString(R.string.ConversationFragment_transport_s_sent_received_s),
                                       transport.toUpperCase(),
                                       dateFormatter.format(new Date(dateSent))));
    } else {
      builder.setMessage(String.format(getSherlockActivity()
                                       .getString(R.string.ConversationFragment_sender_s_transport_s_sent_s_received_s),
                                       message.getIndividualRecipient().getNumber(),
                                       transport.toUpperCase(),
                                       dateFormatter.format(new Date(dateSent)),
                                       dateFormatter.format(new Date(dateReceived))));
    }

    builder.setPositiveButton(android.R.string.ok, null);
    builder.show();
  }

  private void handleForwardMessage(MessageRecord message) {
    Intent composeIntent = new Intent(getActivity(), ShareActivity.class);
    composeIntent.putExtra(ConversationActivity.DRAFT_TEXT_EXTRA, message.getDisplayBody().toString());
    composeIntent.putExtra(ShareActivity.MASTER_SECRET_EXTRA, masterSecret);
    startActivity(composeIntent);
  }

  private void handleResendMessage(MessageRecord message) {
    long messageId = message.getId();
    final Activity activity = getActivity();
    MessageSender.resend(activity, messageId, message.isMms());
  }

  private void handleSaveAttachment(final MediaMmsMessageRecord message) {
    SaveAttachmentTask.showWarningDialog(getActivity(), new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {

        final Slide slide = message.getMediaSlideSync();
        if (slide == null) {
          Log.w(TAG, "No slide with attachable media found, failing nicely.");
          Toast.makeText(getActivity(), R.string.ConversationFragment_error_while_saving_attachment_to_sd_card, Toast.LENGTH_LONG).show();
          return;
        }
        SaveAttachmentTask saveTask = new SaveAttachmentTask(getActivity(), masterSecret);
        saveTask.execute(new Attachment(slide.getUri(), slide.getContentType(), message.getDateReceived()));
      }
    });
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

  private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.conversation_context, menu);

      MessageRecord messageRecord = getMessageRecord();
      setCorrectMenuVisibility(messageRecord, menu);

      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      if (getListView() != null && getListView().getChildCount() > 0) {
        for (int i = 0; i < getListView().getChildCount(); i++){
          getListView().getChildAt(i).setSelected(false);
        }
      }
      actionMode = null;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      MessageRecord messageRecord = getMessageRecord();

      switch(item.getItemId()) {
        case R.id.menu_context_copy:
          handleCopyMessage(messageRecord);
          actionMode.finish();
          return true;
        case R.id.menu_context_delete_message:
          handleDeleteMessage(messageRecord);
          actionMode.finish();
          return true;
        case R.id.menu_context_details:
          handleDisplayDetails(messageRecord);
          actionMode.finish();
          return true;
        case R.id.menu_context_forward:
          handleForwardMessage(messageRecord);
          actionMode.finish();
          return true;
        case R.id.menu_context_resend:
          handleResendMessage(messageRecord);
          actionMode.finish();
          return true;
        case R.id.menu_context_save_attachment:
          handleSaveAttachment((MediaMmsMessageRecord)messageRecord);
          actionMode.finish();
          return true;
      }

      return false;
    }
  };
}
