package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.loaders.ConversationLoader;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ConversationFragment extends ListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = ConversationFragment.class.getSimpleName();

  private final ActionModeCallback     actionModeCallback     = new ActionModeCallback();
  private final SelectionClickListener selectionClickListener = new ConversationFragmentSelectionClickListener();

  private ConversationFragmentListener listener;

  private MasterSecret masterSecret;
  private Recipients   recipients;
  private long         threadId;
  private ActionMode   actionMode;
  private Locale       locale;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.masterSecret = getArguments().getParcelable("master_secret");
    this.locale       = (Locale) getArguments().getSerializable(PassphraseRequiredActionBarActivity.LOCALE_EXTRA);
  }

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

  @Override
  public void onResume() {
    super.onResume();

    if (getListAdapter() != null) {
      ((ConversationAdapter) getListAdapter()).notifyDataSetChanged();
    }
  }

  public void onNewIntent() {
    if (actionMode != null) {
      actionMode.finish();
    }

    initializeResources();
    initializeListAdapter();

    if (threadId == -1) {
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  private void initializeResources() {
    this.recipients   = RecipientFactory.getRecipientsForIds(getActivity(), getActivity().getIntent().getLongArrayExtra("recipients"), true);
    this.threadId     = this.getActivity().getIntent().getLongExtra("thread_id", -1);
  }

  private void initializeListAdapter() {
    if (this.recipients != null && this.threadId != -1) {
      this.setListAdapter(new ConversationAdapter(getActivity(), masterSecret, locale, selectionClickListener,
                                                  (!this.recipients.isSingleRecipient()) || this.recipients.isGroupRecipient(),
                                                  DirectoryHelper.isPushDestination(getActivity(), this.recipients)));
      getListView().setRecyclerListener((ConversationAdapter)getListAdapter());
      getLoaderManager().restartLoader(0, null, this);
    }
  }

  private void initializeContextualActionBar() {
    getListView().setOnItemClickListener(selectionClickListener);
    getListView().setOnItemLongClickListener(selectionClickListener);
  }

  private void setCorrectMenuVisibility(Menu menu) {
    List<MessageRecord> messageRecords = getSelectedMessageRecords();

    if (actionMode != null && messageRecords.size() == 0) {
      actionMode.finish();
      return;
    }

    if (messageRecords.size() > 1) {
      menu.findItem(R.id.menu_context_forward).setVisible(false);
      menu.findItem(R.id.menu_context_details).setVisible(false);
      menu.findItem(R.id.menu_context_save_attachment).setVisible(false);
      menu.findItem(R.id.menu_context_resend).setVisible(false);
    } else {
      MessageRecord messageRecord = messageRecords.get(0);

      menu.findItem(R.id.menu_context_resend).setVisible(messageRecord.isFailed());
      menu.findItem(R.id.menu_context_save_attachment).setVisible(messageRecord.isMms()              &&
                                                                  !messageRecord.isMmsNotification() &&
                                                                  ((MediaMmsMessageRecord)messageRecord).containsMediaSlide());

      menu.findItem(R.id.menu_context_forward).setVisible(true);
      menu.findItem(R.id.menu_context_details).setVisible(true);
      menu.findItem(R.id.menu_context_copy).setVisible(true);
    }
  }

  private MessageRecord getSelectedMessageRecord() {
    List<MessageRecord> messageRecords = getSelectedMessageRecords();

    if (messageRecords.size() == 1) return messageRecords.get(0);
    else                            throw new AssertionError();
  }

  private List<MessageRecord> getSelectedMessageRecords() {
    return new LinkedList<>(((ConversationAdapter)getListAdapter()).getBatchSelected());
  }

  public void reload(Recipients recipients, long threadId) {
    this.recipients = recipients;

    if (this.threadId != threadId) {
      this.threadId = threadId;
      initializeListAdapter();
    }
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

  private void handleCopyMessage(final List<MessageRecord> messageRecords) {
    Collections.sort(messageRecords, new Comparator<MessageRecord>() {
      @Override
      public int compare(MessageRecord lhs, MessageRecord rhs) {
        if      (lhs.getDateReceived() < rhs.getDateReceived())  return -1;
        else if (lhs.getDateReceived() == rhs.getDateReceived()) return 0;
        else                                                     return 1;
      }
    });

    StringBuilder    bodyBuilder = new StringBuilder();
    ClipboardManager clipboard   = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
    boolean          first       = true;

    for (MessageRecord messageRecord : messageRecords) {
      String body = messageRecord.getDisplayBody().toString();

      if (body != null) {
        if (!first) bodyBuilder.append('\n');
        bodyBuilder.append(body);
        first = false;
      }
    }

    String result = bodyBuilder.toString();

    if (!TextUtils.isEmpty(result))
        clipboard.setText(result);
  }

  private void handleDeleteMessages(final List<MessageRecord> messageRecords) {
    AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
    builder.setTitle(R.string.ConversationFragment_confirm_message_delete);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationFragment_are_you_sure_you_want_to_permanently_delete_all_selected_messages);
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        new ProgressDialogAsyncTask<MessageRecord, Void, Void>(getActivity(),
                                                               R.string.ConversationFragment_deleting,
                                                               R.string.ConversationFragment_deleting_messages)
        {
          @Override
          protected Void doInBackground(MessageRecord... messageRecords) {
            for (MessageRecord messageRecord : messageRecords) {
              boolean threadDeleted;

              if (messageRecord.isMms()) {
                threadDeleted = DatabaseFactory.getMmsDatabase(getActivity()).delete(messageRecord.getId());
              } else {
                threadDeleted = DatabaseFactory.getSmsDatabase(getActivity()).deleteMessage(messageRecord.getId());
              }

              if (threadDeleted) {
                threadId = -1;
                listener.setThreadId(threadId);
              }
            }

            return null;
          }
        }.execute(messageRecords.toArray(new MessageRecord[messageRecords.size()]));
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleDisplayDetails(MessageRecord message) {
    Intent intent = new Intent(getActivity(), MessageDetailsActivity.class);
    intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
    intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, message.getId());
    intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
    startActivity(intent);
  }

  private void handleForwardMessage(MessageRecord message) {
    Intent composeIntent = new Intent(getActivity(), ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_TEXT, message.getDisplayBody().toString());
    startActivity(composeIntent);
  }

  private void handleResendMessage(final MessageRecord message) {
    final Context context = getActivity().getApplicationContext();
    new AsyncTask<MessageRecord, Void, Void>() {
      @Override
      protected Void doInBackground(MessageRecord... messageRecords) {
        MessageSender.resend(context, masterSecret, messageRecords[0]);
        return null;
      }
    }.execute(message);
  }

  private void handleSaveAttachment(final MediaMmsMessageRecord message) {
    SaveAttachmentTask.showWarningDialog(getActivity(), new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {

        message.fetchMediaSlide(new FutureTaskListener<Slide>() {
          @Override
          public void onSuccess(Slide slide) {
            SaveAttachmentTask saveTask = new SaveAttachmentTask(getActivity(), masterSecret);
            saveTask.execute(new Attachment(slide.getUri(), slide.getContentType(), message.getDateReceived()));
          }

          @Override
          public void onFailure(Throwable error) {
            Log.w(TAG, "No slide with attachable media found, failing nicely.");
            Log.w(TAG, error);
            Toast.makeText(getActivity(), R.string.ConversationFragment_error_while_saving_attachment_to_sd_card, Toast.LENGTH_LONG).show();
          }
        });
      }
    });
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationLoader(getActivity(), threadId);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    if (getListAdapter() != null) {
      ((CursorAdapter) getListAdapter()).changeCursor(cursor);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    if (getListAdapter() != null) {
      ((CursorAdapter) getListAdapter()).changeCursor(null);
    }
  }

  public interface ConversationFragmentListener {
    public void setComposeText(String text);

    public void setThreadId(long threadId);
  }

  public interface SelectionClickListener extends
      AdapterView.OnItemLongClickListener, AdapterView.OnItemClickListener {}

  private class ConversationFragmentSelectionClickListener
      implements SelectionClickListener
  {
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      if (actionMode != null && view instanceof ConversationItem) {
        MessageRecord messageRecord = ((ConversationItem)view).getMessageRecord();
        ((ConversationAdapter) getListAdapter()).toggleBatchSelected(messageRecord);
        ((ConversationAdapter) getListAdapter()).notifyDataSetChanged();

        setCorrectMenuVisibility(actionMode.getMenu());
      }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
      if (actionMode == null && view instanceof ConversationItem) {
        MessageRecord messageRecord = ((ConversationItem)view).getMessageRecord();
        ((ConversationAdapter) getListAdapter()).toggleBatchSelected(messageRecord);
        ((ConversationAdapter) getListAdapter()).notifyDataSetChanged();

        actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(actionModeCallback);
        return true;
      }

      return false;
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    private int statusBarColor;

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.conversation_context, menu);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Window window = getActivity().getWindow();
        statusBarColor = window.getStatusBarColor();
        window.setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
      }

      setCorrectMenuVisibility(menu);
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      ((ConversationAdapter)getListAdapter()).getBatchSelected().clear();
      ((ConversationAdapter)getListAdapter()).notifyDataSetChanged();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        getActivity().getWindow().setStatusBarColor(statusBarColor);
      }

      actionMode = null;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      switch(item.getItemId()) {
        case R.id.menu_context_copy:
          handleCopyMessage(getSelectedMessageRecords());
          actionMode.finish();
          return true;
        case R.id.menu_context_delete_message:
          handleDeleteMessages(getSelectedMessageRecords());
          actionMode.finish();
          return true;
        case R.id.menu_context_details:
          handleDisplayDetails(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_forward:
          handleForwardMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_resend:
          handleResendMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_save_attachment:
          handleSaveAttachment((MediaMmsMessageRecord)getSelectedMessageRecord());
          actionMode.finish();
          return true;
      }

      return false;
    }
  };
}
