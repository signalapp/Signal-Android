/**
 * Copyright (C) 2015 Open Whisper Systems
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
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ItemAnimator.ItemAnimatorFinishedListener;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import org.thoughtcrime.securesms.ConversationAdapter.ItemClickListener;
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
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConversationFragment extends Fragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = ConversationFragment.class.getSimpleName();

  private static final long   PARTIAL_CONVERSATION_LIMIT = 500L;

  private final ActionModeCallback actionModeCallback     = new ActionModeCallback();
  private final ItemClickListener  selectionClickListener = new ConversationFragmentItemClickListener();

  private ConversationFragmentListener listener;

  private MasterSecret masterSecret;
  private Recipients   recipients;
  private long         threadId;
  private ActionMode   actionMode;
  private Locale       locale;
  private RecyclerView list;
  private View         loadMoreView;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.masterSecret = getArguments().getParcelable("master_secret");
    this.locale       = (Locale) getArguments().getSerializable(PassphraseRequiredActionBarActivity.LOCALE_EXTRA);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_fragment, container, false);
    list = ViewUtil.findById(view, android.R.id.list);
    final LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, true);
    list.setHasFixedSize(false);
    list.setLayoutManager(layoutManager);

    loadMoreView = inflater.inflate(R.layout.load_more_header, container, false);
    loadMoreView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        Bundle args = new Bundle();
        args.putLong("limit", 0);
        getLoaderManager().restartLoader(0, args, ConversationFragment.this);
      }
    });
    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    initializeResources();
    initializeListAdapter();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.listener = (ConversationFragmentListener)activity;
  }

  @Override
  public void onResume() {
    super.onResume();

    if (list.getAdapter() != null) {
      list.getAdapter().notifyDataSetChanged();
    }
  }

  public void onNewIntent() {
    if (actionMode != null) {
      actionMode.finish();
    }

    initializeResources();
    initializeListAdapter();

    if (threadId == -1) {
      getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
    }
  }

  public void reloadList() {
    getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
  }

  private void initializeResources() {
    this.recipients = RecipientFactory.getRecipientsForIds(getActivity(), getActivity().getIntent().getLongArrayExtra("recipients"), true);
    this.threadId   = this.getActivity().getIntent().getLongExtra("thread_id", -1);
  }

  private void initializeListAdapter() {
    if (this.recipients != null && this.threadId != -1) {
      list.setAdapter(new ConversationAdapter(getActivity(), masterSecret, locale, selectionClickListener, null, this.recipients));
      getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
      list.getItemAnimator().setMoveDuration(120);
    }
  }

  private void setCorrectMenuVisibility(Menu menu) {
    Set<MessageRecord> messageRecords = getListAdapter().getSelectedItems();
    boolean            actionMessage  = false;

    if (actionMode != null && messageRecords.size() == 0) {
      actionMode.finish();
      return;
    }

    for (MessageRecord messageRecord : messageRecords) {
      if (messageRecord.isGroupAction() || messageRecord.isCallLog() ||
          messageRecord.isJoined() || messageRecord.isExpirationTimerUpdate() ||
          messageRecord.isEndSession())
      {
        actionMessage = true;
        break;
      }
    }

    if (messageRecords.size() > 1) {
      menu.findItem(R.id.menu_context_forward).setVisible(false);
      menu.findItem(R.id.menu_context_details).setVisible(false);
      menu.findItem(R.id.menu_context_save_attachment).setVisible(false);
      menu.findItem(R.id.menu_context_resend).setVisible(false);
      menu.findItem(R.id.menu_context_copy).setVisible(!actionMessage);
    } else {
      MessageRecord messageRecord = messageRecords.iterator().next();

      menu.findItem(R.id.menu_context_resend).setVisible(messageRecord.isFailed());
      menu.findItem(R.id.menu_context_save_attachment).setVisible(!actionMessage                     &&
                                                                  messageRecord.isMms()              &&
                                                                  !messageRecord.isMmsNotification() &&
                                                                  ((MediaMmsMessageRecord)messageRecord).containsMediaSlide());

      menu.findItem(R.id.menu_context_forward).setVisible(!actionMessage);
      menu.findItem(R.id.menu_context_details).setVisible(!actionMessage);
      menu.findItem(R.id.menu_context_copy).setVisible(!actionMessage);
    }
  }

  private ConversationAdapter getListAdapter() {
    return (ConversationAdapter) list.getAdapter();
  }

  private MessageRecord getSelectedMessageRecord() {
    Set<MessageRecord> messageRecords = getListAdapter().getSelectedItems();

    if (messageRecords.size() == 1) return messageRecords.iterator().next();
    else                            throw new AssertionError();
  }

  public void reload(Recipients recipients, long threadId) {
    this.recipients = recipients;

    if (this.threadId != threadId) {
      this.threadId = threadId;
      initializeListAdapter();
    }
  }

  public void scrollToBottom() {
    list.getItemAnimator().isRunning(new ItemAnimatorFinishedListener() {
      @Override
      public void onAnimationsFinished() {
        list.stopScroll();
        list.smoothScrollToPosition(0);
      }
    });
  }

  private void handleCopyMessage(final Set<MessageRecord> messageRecords) {
    List<MessageRecord> messageList = new LinkedList<>(messageRecords);
    Collections.sort(messageList, new Comparator<MessageRecord>() {
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

    for (MessageRecord messageRecord : messageList) {
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

  private void handleDeleteMessages(final Set<MessageRecord> messageRecords) {
    int                 messagesCount = messageRecords.size();
    AlertDialog.Builder builder       = new AlertDialog.Builder(getActivity());

    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messagesCount, messagesCount));
    builder.setMessage(getActivity().getResources().getQuantityString(R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages, messagesCount, messagesCount));
    builder.setCancelable(true);

    builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
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

    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleDisplayDetails(MessageRecord message) {
    Intent intent = new Intent(getActivity(), MessageDetailsActivity.class);
    intent.putExtra(MessageDetailsActivity.MASTER_SECRET_EXTRA, masterSecret);
    intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, message.getId());
    intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
    intent.putExtra(MessageDetailsActivity.RECIPIENTS_IDS_EXTRA, recipients.getIds());
    startActivity(intent);
  }

  private void handleForwardMessage(MessageRecord message) {
    Intent composeIntent = new Intent(getActivity(), ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_TEXT, message.getDisplayBody().toString());
    if (message.isMms()) {
      MediaMmsMessageRecord mediaMessage = (MediaMmsMessageRecord) message;
      if (mediaMessage.containsMediaSlide()) {
        Slide slide = mediaMessage.getSlideDeck().getSlides().get(0);
        composeIntent.putExtra(Intent.EXTRA_STREAM, slide.getUri());
        composeIntent.setType(slide.getContentType());
      }
    }
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
        for (Slide slide : message.getSlideDeck().getSlides()) {
          if (slide.hasImage() || slide.hasVideo() || slide.hasAudio()) {
            SaveAttachmentTask saveTask = new SaveAttachmentTask(getActivity(), masterSecret);
            saveTask.execute(new Attachment(slide.getUri(), slide.getContentType(), message.getDateReceived()));
            return;
          }
        }

        Log.w(TAG, "No slide with attachable media found, failing nicely.");
        Toast.makeText(getActivity(),
                       getResources().getQuantityString(R.plurals.ConversationFragment_error_while_saving_attachments_to_sd_card, 1),
                       Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new ConversationLoader(getActivity(), threadId, args.getLong("limit", PARTIAL_CONVERSATION_LIMIT));
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    if (list.getAdapter() != null) {
      if (cursor.getCount() >= PARTIAL_CONVERSATION_LIMIT && ((ConversationLoader)loader).hasLimit()) {
        getListAdapter().setFooterView(loadMoreView);
      } else {
        getListAdapter().setFooterView(null);
      }
      getListAdapter().changeCursor(cursor);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    if (list.getAdapter() != null) {
      getListAdapter().changeCursor(null);
    }
  }

  public interface ConversationFragmentListener {
    void setThreadId(long threadId);
  }

  private class ConversationFragmentItemClickListener implements ItemClickListener {

    @Override
    public void onItemClick(MessageRecord messageRecord) {
      if (actionMode != null) {
        ((ConversationAdapter) list.getAdapter()).toggleSelection(messageRecord);
        list.getAdapter().notifyDataSetChanged();

        setCorrectMenuVisibility(actionMode.getMenu());
      }
    }

    @Override
    public void onItemLongClick(MessageRecord messageRecord) {
      if (actionMode == null) {
        ((ConversationAdapter) list.getAdapter()).toggleSelection(messageRecord);
        list.getAdapter().notifyDataSetChanged();

        actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(actionModeCallback);
      }
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
      ((ConversationAdapter)list.getAdapter()).clearSelection();
      list.getAdapter().notifyDataSetChanged();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        getActivity().getWindow().setStatusBarColor(statusBarColor);
      }

      actionMode = null;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      switch(item.getItemId()) {
        case R.id.menu_context_copy:
          handleCopyMessage(getListAdapter().getSelectedItems());
          actionMode.finish();
          return true;
        case R.id.menu_context_delete_message:
          handleDeleteMessages(getListAdapter().getSelectedItems());
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
  }
}
