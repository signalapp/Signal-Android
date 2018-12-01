/*
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.text.ClipboardManager;
import android.text.TextUtils;

import org.thoughtcrime.securesms.components.ConversationTypingView;
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager;
import org.thoughtcrime.securesms.logging.Log;

import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import org.thoughtcrime.securesms.ConversationAdapter.HeaderViewHolder;
import org.thoughtcrime.securesms.ConversationAdapter.ItemClickListener;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.loaders.ConversationLoader;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.profiles.UnknownSenderView;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressLint("StaticFieldLeak")
public class ConversationFragment extends Fragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG       = ConversationFragment.class.getSimpleName();
  private static final String KEY_LIMIT = "limit";

  private static final int PARTIAL_CONVERSATION_LIMIT = 500;
  private static final int SCROLL_ANIMATION_THRESHOLD = 50;
  private static final int CODE_ADD_EDIT_CONTACT      = 77;

  private final ActionModeCallback actionModeCallback     = new ActionModeCallback();
  private final ItemClickListener  selectionClickListener = new ConversationFragmentItemClickListener();

  private ConversationFragmentListener listener;

  private Recipient                   recipient;
  private long                        threadId;
  private long                        lastSeen;
  private int                         startingPosition;
  private int                         previousOffset;
  private boolean                     firstLoad;
  private long                        loaderStartTime;
  private ActionMode                  actionMode;
  private Locale                      locale;
  private RecyclerView                list;
  private RecyclerView.ItemDecoration lastSeenDecoration;
  private ViewSwitcher                topLoadMoreView;
  private ViewSwitcher                bottomLoadMoreView;
  private ConversationTypingView      typingView;
  private UnknownSenderView           unknownSenderView;
  private View                        composeDivider;
  private View                        scrollToBottomButton;
  private TextView                    scrollDateHeader;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.locale = (Locale) getArguments().getSerializable(PassphraseRequiredActionBarActivity.LOCALE_EXTRA);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_fragment, container, false);
    list                 = ViewUtil.findById(view, android.R.id.list);
    composeDivider       = ViewUtil.findById(view, R.id.compose_divider);
    scrollToBottomButton = ViewUtil.findById(view, R.id.scroll_to_bottom_button);
    scrollDateHeader     = ViewUtil.findById(view, R.id.scroll_date_header);

    scrollToBottomButton.setOnClickListener(v -> scrollToBottom());

    final LinearLayoutManager layoutManager = new SmoothScrollingLinearLayoutManager(getActivity(), true);
    list.setHasFixedSize(false);
    list.setLayoutManager(layoutManager);
    list.setItemAnimator(null);

    topLoadMoreView    = (ViewSwitcher) inflater.inflate(R.layout.load_more_header, container, false);
    bottomLoadMoreView = (ViewSwitcher) inflater.inflate(R.layout.load_more_header, container, false);
    initializeLoadMoreView(topLoadMoreView);
    initializeLoadMoreView(bottomLoadMoreView);

    typingView = (ConversationTypingView) inflater.inflate(R.layout.conversation_typing_view, container, false);

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
  public void onStart() {
    super.onStart();
    initializeTypingObserver();
  }

  @Override
  public void onResume() {
    super.onResume();

    if (list.getAdapter() != null) {
      list.getAdapter().notifyDataSetChanged();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypists(threadId).removeObservers(this);
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

  public void moveToLastSeen() {
    if (lastSeen <= 0) {
      Log.i(TAG, "No need to move to last seen.");
      return;
    }

    if (list == null || getListAdapter() == null) {
      Log.w(TAG, "Tried to move to last seen position, but we hadn't initialized the view yet.");
      return;
    }

    int position = getListAdapter().findLastSeenPosition(lastSeen);
    scrollToLastSeenPosition(position);
  }

  private void initializeResources() {
    this.recipient         = Recipient.from(getActivity(), getActivity().getIntent().getParcelableExtra(ConversationActivity.ADDRESS_EXTRA), true);
    this.threadId          = this.getActivity().getIntent().getLongExtra(ConversationActivity.THREAD_ID_EXTRA, -1);
    this.lastSeen          = this.getActivity().getIntent().getLongExtra(ConversationActivity.LAST_SEEN_EXTRA, -1);
    this.startingPosition  = this.getActivity().getIntent().getIntExtra(ConversationActivity.STARTING_POSITION_EXTRA, -1);
    this.firstLoad         = true;
    this.unknownSenderView = new UnknownSenderView(getActivity(), recipient, threadId);

    OnScrollListener scrollListener = new ConversationScrollListener(getActivity());
    list.addOnScrollListener(scrollListener);
  }

  private void initializeListAdapter() {
    if (this.recipient != null && this.threadId != -1) {
      ConversationAdapter adapter = new ConversationAdapter(getActivity(), GlideApp.with(this), locale, selectionClickListener, null, this.recipient);
      list.setAdapter(adapter);
      list.addItemDecoration(new StickyHeaderDecoration(adapter, false, false));

      setLastSeen(lastSeen);
      getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
    }
  }

  private void initializeLoadMoreView(ViewSwitcher loadMoreView) {
    loadMoreView.setOnClickListener(v -> {
      Bundle args = new Bundle();
      args.putInt(KEY_LIMIT, 0);
      getLoaderManager().restartLoader(0, args, ConversationFragment.this);
      loadMoreView.showNext();
      loadMoreView.setOnClickListener(null);
    });
  }

  private void initializeTypingObserver() {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(requireContext())) {
      return;
    }

    ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypists(threadId).observe(this, typingState ->  {
      List<Recipient> recipients;
      boolean         replacedByIncomingMessage;

      if (typingState != null) {
        recipients                = typingState.getTypists();
        replacedByIncomingMessage = typingState.isReplacedByIncomingMessage();
      } else {
        recipients                = Collections.emptyList();
        replacedByIncomingMessage = false;
      }

      typingView.setTypists(GlideApp.with(ConversationFragment.this), recipients, recipient.isGroupRecipient());

      ConversationAdapter adapter = getListAdapter();

      if (adapter.getHeaderView() != null && adapter.getHeaderView() != typingView) {
        Log.i(TAG, "Skipping typing indicator -- the header slot is occupied.");
        return;
      }

      if (recipients.size() > 0) {
        if (adapter.getHeaderView() == null && getListLayoutManager().findFirstCompletelyVisibleItemPosition() == 0) {
          list.setVerticalScrollBarEnabled(false);
          list.post(() -> getListLayoutManager().smoothScrollToPosition(requireContext(), 0, 250));
          list.postDelayed(() -> list.setVerticalScrollBarEnabled(true), 300);
          adapter.setHeaderView(typingView);
          adapter.notifyItemInserted(0);
        } else {
          if (adapter.getHeaderView() == null) {
            adapter.setHeaderView(typingView);
            adapter.notifyItemInserted(0);
          } else  {
            adapter.setHeaderView(typingView);
            adapter.notifyItemChanged(0);
          }
        }
      } else {
        if (getListLayoutManager().findFirstCompletelyVisibleItemPosition() == 0 && getListLayoutManager().getItemCount() > 1 && !replacedByIncomingMessage) {
          getListLayoutManager().smoothScrollToPosition(requireContext(), 1, 250);
          list.setVerticalScrollBarEnabled(false);
          list.postDelayed(() -> {
            adapter.setHeaderView(null);
            adapter.notifyItemRemoved(0);
            list.post(() -> list.setVerticalScrollBarEnabled(true));
          }, 200);
        } else if (!replacedByIncomingMessage) {
          adapter.setHeaderView(null);
          adapter.notifyItemRemoved(0);
        } else {
          adapter.setHeaderView(null);
        }
      }
    });
  }

  private void setCorrectMenuVisibility(Menu menu) {
    Set<MessageRecord> messageRecords = getListAdapter().getSelectedItems();
    boolean            actionMessage  = false;
    boolean            hasText        = false;
    boolean            sharedContact  = false;

    if (actionMode != null && messageRecords.size() == 0) {
      actionMode.finish();
      return;
    }

    for (MessageRecord messageRecord : messageRecords) {
      if (messageRecord.isGroupAction() || messageRecord.isCallLog() ||
          messageRecord.isJoined() || messageRecord.isExpirationTimerUpdate() ||
          messageRecord.isEndSession() || messageRecord.isIdentityUpdate() ||
          messageRecord.isIdentityVerified() || messageRecord.isIdentityDefault())
      {
        actionMessage = true;
      }

      if (messageRecord.getBody().length() > 0) {
        hasText = true;
      }

      if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getSharedContacts().isEmpty()) {
        sharedContact = true;
      }
    }

    if (messageRecords.size() > 1) {
      menu.findItem(R.id.menu_context_forward).setVisible(false);
      menu.findItem(R.id.menu_context_reply).setVisible(false);
      menu.findItem(R.id.menu_context_details).setVisible(false);
      menu.findItem(R.id.menu_context_save_attachment).setVisible(false);
      menu.findItem(R.id.menu_context_resend).setVisible(false);
    } else {
      MessageRecord messageRecord = messageRecords.iterator().next();

      menu.findItem(R.id.menu_context_resend).setVisible(messageRecord.isFailed());
      menu.findItem(R.id.menu_context_save_attachment).setVisible(!actionMessage                     &&
                                                                  messageRecord.isMms()              &&
                                                                  !messageRecord.isMmsNotification() &&
                                                                  ((MediaMmsMessageRecord)messageRecord).containsMediaSlide());

      menu.findItem(R.id.menu_context_forward).setVisible(!actionMessage && !sharedContact);
      menu.findItem(R.id.menu_context_details).setVisible(!actionMessage);
      menu.findItem(R.id.menu_context_reply).setVisible(!actionMessage             &&
                                                        !messageRecord.isPending() &&
                                                        !messageRecord.isFailed()  &&
                                                        messageRecord.isSecure());
    }
    menu.findItem(R.id.menu_context_copy).setVisible(!actionMessage && hasText);
  }

  private ConversationAdapter getListAdapter() {
    return (ConversationAdapter) list.getAdapter();
  }

  private SmoothScrollingLinearLayoutManager getListLayoutManager() {
    return (SmoothScrollingLinearLayoutManager) list.getLayoutManager();
  }

  private MessageRecord getSelectedMessageRecord() {
    Set<MessageRecord> messageRecords = getListAdapter().getSelectedItems();

    if (messageRecords.size() == 1) return messageRecords.iterator().next();
    else                            throw new AssertionError();
  }

  public void reload(Recipient recipient, long threadId) {
    this.recipient = recipient;

    if (this.threadId != threadId) {
      this.threadId = threadId;
      initializeListAdapter();
    }
  }

  public void scrollToBottom() {
    if (((LinearLayoutManager) list.getLayoutManager()).findFirstVisibleItemPosition() < SCROLL_ANIMATION_THRESHOLD) {
      list.smoothScrollToPosition(0);
    } else {
      list.scrollToPosition(0);
    }
  }

  public void setLastSeen(long lastSeen) {
    this.lastSeen = lastSeen;
    if (lastSeenDecoration != null) {
      list.removeItemDecoration(lastSeenDecoration);
    }

    lastSeenDecoration = new ConversationAdapter.LastSeenHeader(getListAdapter(), lastSeen);
    list.addItemDecoration(lastSeenDecoration);
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

    for (MessageRecord messageRecord : messageList) {
      String body = messageRecord.getDisplayBody().toString();
      if (!TextUtils.isEmpty(body)) {
        bodyBuilder.append(body).append('\n');
      }
    }
    if (bodyBuilder.length() > 0 && bodyBuilder.charAt(bodyBuilder.length() - 1) == '\n') {
      bodyBuilder.deleteCharAt(bodyBuilder.length() - 1);
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
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, messageRecords.toArray(new MessageRecord[messageRecords.size()]));
      }
    });

    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleDisplayDetails(MessageRecord message) {
    Intent intent = new Intent(getActivity(), MessageDetailsActivity.class);
    intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, message.getId());
    intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
    intent.putExtra(MessageDetailsActivity.ADDRESS_EXTRA, recipient.getAddress());
    intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, recipient.isGroupRecipient() && message.isPush());
    startActivity(intent);
  }

  private void handleForwardMessage(MessageRecord message) {
    Intent composeIntent = new Intent(getActivity(), ShareActivity.class);
    composeIntent.putExtra(Intent.EXTRA_TEXT, message.getDisplayBody().toString());
    if (message.isMms()) {
      MmsMessageRecord mediaMessage = (MmsMessageRecord) message;
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
        MessageSender.resend(context, messageRecords[0]);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
  }

  private void handleReplyMessage(final MessageRecord message) {
    listener.handleReplyMessage(message);
  }

  private void handleSaveAttachment(final MediaMmsMessageRecord message) {
    SaveAttachmentTask.showWarningDialog(getActivity(), new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        for (Slide slide : message.getSlideDeck().getSlides()) {
          if ((slide.hasImage() || slide.hasVideo() || slide.hasAudio() || slide.hasDocument()) && slide.getUri() != null) {
            SaveAttachmentTask saveTask = new SaveAttachmentTask(getActivity());
            saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new Attachment(slide.getUri(), slide.getContentType(), message.getDateReceived(), slide.getFileName().orNull()));
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
    Log.i(TAG, "onCreateLoader");
    loaderStartTime = System.currentTimeMillis();

    int limit  = args.getInt(KEY_LIMIT, PARTIAL_CONVERSATION_LIMIT);
    int offset = 0;
    if (limit != 0 && startingPosition >= limit) {
      offset = Math.max(startingPosition - (limit / 2) + 1, 0);
      startingPosition -= offset - 1;
    }

    return new ConversationLoader(getActivity(), threadId, offset, limit, lastSeen);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    long loadTime = System.currentTimeMillis() - loaderStartTime;
    int  count    = cursor.getCount();
    Log.i(TAG, "onLoadFinished - took " + loadTime + " ms to load a cursor of size " + count);
    ConversationLoader loader = (ConversationLoader)cursorLoader;

    ConversationAdapter adapter = getListAdapter();
    if (adapter == null) {
      return;
    }

    if (cursor.getCount() >= PARTIAL_CONVERSATION_LIMIT && loader.hasLimit()) {
      adapter.setFooterView(topLoadMoreView);
    } else {
      adapter.setFooterView(null);
    }

    if (lastSeen == -1) {
      setLastSeen(loader.getLastSeen());
    }

    if (!loader.hasSent() && !recipient.isSystemContact() && !recipient.isGroupRecipient() && recipient.getRegistered() == RecipientDatabase.RegisteredState.REGISTERED) {
      adapter.setHeaderView(unknownSenderView);
    } else {
      clearHeaderIfNotTyping(adapter);
    }

    if (loader.hasOffset()) {
      adapter.setHeaderView(bottomLoadMoreView);
      previousOffset = loader.getOffset();
    }

    adapter.changeCursor(cursor);

    int lastSeenPosition = adapter.findLastSeenPosition(lastSeen);

    if (adapter.getHeaderView() == typingView) {
      lastSeenPosition = Math.max(lastSeenPosition - 1, 0);
    }

    if (firstLoad) {
      if (startingPosition >= 0) {
        scrollToStartingPosition(startingPosition);
      } else {
        scrollToLastSeenPosition(lastSeenPosition);
      }
      firstLoad = false;
    } else if (previousOffset > 0) {
      int scrollPosition = previousOffset + ((LinearLayoutManager) list.getLayoutManager()).findFirstVisibleItemPosition();
      scrollPosition = Math.min(scrollPosition, count - 1);

      View firstView = list.getLayoutManager().getChildAt(scrollPosition);
      int pixelOffset = (firstView == null) ? 0 : (firstView.getBottom() - list.getPaddingBottom());

      ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(scrollPosition, pixelOffset);
      previousOffset = 0;
    }

    if (lastSeenPosition <= 0) {
      setLastSeen(0);
    }
  }

  private void clearHeaderIfNotTyping(ConversationAdapter adapter) {
    if (adapter.getHeaderView() != typingView) {
      adapter.setHeaderView(null);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    if (list.getAdapter() != null) {
      getListAdapter().changeCursor(null);
    }
  }

  public long stageOutgoingMessage(OutgoingMediaMessage message) {
    MessageRecord messageRecord = DatabaseFactory.getMmsDatabase(getContext()).readerFor(message, threadId).getCurrent();

    if (getListAdapter() != null) {
      clearHeaderIfNotTyping(getListAdapter());
      setLastSeen(0);
      getListAdapter().addFastRecord(messageRecord);
    }

    return messageRecord.getId();
  }

  public long stageOutgoingMessage(OutgoingTextMessage message) {
    MessageRecord messageRecord = DatabaseFactory.getSmsDatabase(getContext()).readerFor(message, threadId).getCurrent();

    if (getListAdapter() != null) {
      clearHeaderIfNotTyping(getListAdapter());
      setLastSeen(0);
      getListAdapter().addFastRecord(messageRecord);
    }

    return messageRecord.getId();
  }

  public void releaseOutgoingMessage(long id) {
    if (getListAdapter() != null) {
      getListAdapter().releaseFastRecord(id);
    }
  }

  private void scrollToStartingPosition(final int startingPosition) {
    list.post(() -> {
      list.getLayoutManager().scrollToPosition(startingPosition);
      getListAdapter().pulseHighlightItem(startingPosition);
    });
  }

  private void scrollToLastSeenPosition(final int lastSeenPosition) {
    if (lastSeenPosition > 0) {
      list.post(() -> ((LinearLayoutManager)list.getLayoutManager()).scrollToPositionWithOffset(lastSeenPosition, list.getHeight()));
    }
  }

  public interface ConversationFragmentListener {
    void setThreadId(long threadId);
    void handleReplyMessage(MessageRecord messageRecord);
  }

  private class ConversationScrollListener extends OnScrollListener {

    private final Animation              scrollButtonInAnimation;
    private final Animation              scrollButtonOutAnimation;
    private final ConversationDateHeader conversationDateHeader;

    private boolean wasAtBottom           = true;
    private boolean wasAtZoomScrollHeight = false;
    private long    lastPositionId        = -1;

    ConversationScrollListener(@NonNull Context context) {
      this.scrollButtonInAnimation  = AnimationUtils.loadAnimation(context, R.anim.fade_scale_in);
      this.scrollButtonOutAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_scale_out);
      this.conversationDateHeader   = new ConversationDateHeader(context, scrollDateHeader);

      this.scrollButtonInAnimation.setDuration(100);
      this.scrollButtonOutAnimation.setDuration(50);
    }

    @Override
    public void onScrolled(final RecyclerView rv, final int dx, final int dy) {
      boolean currentlyAtBottom           = isAtBottom();
      boolean currentlyAtZoomScrollHeight = isAtZoomScrollHeight();
      int     positionId                  = getHeaderPositionId();

      if (currentlyAtBottom && !wasAtBottom) {
        ViewUtil.fadeOut(composeDivider, 50, View.INVISIBLE);
        ViewUtil.animateOut(scrollToBottomButton, scrollButtonOutAnimation, View.INVISIBLE);
      } else if (!currentlyAtBottom && wasAtBottom) {
        ViewUtil.fadeIn(composeDivider, 500);
      }

      if (currentlyAtZoomScrollHeight && !wasAtZoomScrollHeight) {
        ViewUtil.animateIn(scrollToBottomButton, scrollButtonInAnimation);
      }

      if (positionId != lastPositionId) {
        bindScrollHeader(conversationDateHeader, positionId);
      }

      wasAtBottom           = currentlyAtBottom;
      wasAtZoomScrollHeight = currentlyAtZoomScrollHeight;
      lastPositionId        = positionId;
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
      if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
        conversationDateHeader.show();
      } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        conversationDateHeader.hide();
      }
    }

    private boolean isAtBottom() {
      if (list.getChildCount() == 0) return true;

      int firstCompletelyVisiblePosition = ((LinearLayoutManager) list.getLayoutManager()).findFirstCompletelyVisibleItemPosition();

      if (getListAdapter().getHeaderView() == typingView) {
        return firstCompletelyVisiblePosition <= 1;
      }
      return firstCompletelyVisiblePosition == 0;
    }

    private boolean isAtZoomScrollHeight() {
      return ((LinearLayoutManager) list.getLayoutManager()).findFirstCompletelyVisibleItemPosition() > 4;
    }

    private int getHeaderPositionId() {
      return ((LinearLayoutManager)list.getLayoutManager()).findLastVisibleItemPosition();
    }

    private void bindScrollHeader(HeaderViewHolder headerViewHolder, int positionId) {
      if (((ConversationAdapter)list.getAdapter()).getHeaderId(positionId) != -1) {
        ((ConversationAdapter) list.getAdapter()).onBindHeaderViewHolder(headerViewHolder, positionId);
      }
    }
  }

  private class ConversationFragmentItemClickListener implements ItemClickListener {

    @Override
    public void onItemClick(MessageRecord messageRecord) {
      if (actionMode != null) {
        ((ConversationAdapter) list.getAdapter()).toggleSelection(messageRecord);
        list.getAdapter().notifyDataSetChanged();

        if (getListAdapter().getSelectedItems().size() == 0) {
          actionMode.finish();
        } else {
          setCorrectMenuVisibility(actionMode.getMenu());
          actionMode.setTitle(String.valueOf(getListAdapter().getSelectedItems().size()));
        }
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

    @Override
    public void onQuoteClicked(MmsMessageRecord messageRecord) {
      if (messageRecord.getQuote() == null) {
        Log.w(TAG, "Received a 'quote clicked' event, but there's no quote...");
        return;
      }

      if (messageRecord.getQuote().isOriginalMissing()) {
        Log.i(TAG, "Clicked on a quote whose original message we never had.");
        Toast.makeText(getContext(), R.string.ConversationFragment_quoted_message_not_found, Toast.LENGTH_SHORT).show();
        return;
      }

      new AsyncTask<Void, Void, Integer>() {
        @Override
        protected Integer doInBackground(Void... voids) {
          if (getActivity() == null || getActivity().isFinishing()) {
            Log.w(TAG, "Task to retrieve quote position started after the fragment was detached.");
            return 0;
          }
          return DatabaseFactory.getMmsSmsDatabase(getContext())
                                .getQuotedMessagePosition(threadId,
                                                          messageRecord.getQuote().getId(),
                                                          messageRecord.getQuote().getAuthor());
        }

        @Override
        protected void onPostExecute(Integer position) {
          if (getActivity() == null || getActivity().isFinishing()) {
            Log.w(TAG, "Task to retrieve quote position finished after the fragment was detached.");
            return;
          }

          if (position >= 0 && position < getListAdapter().getItemCount()) {
            list.scrollToPosition(position);
            getListAdapter().pulseHighlightItem(position);
          } else if (position < 0) {
            Log.w(TAG, "Tried to navigate to quoted message, but it was deleted.");
            Toast.makeText(getContext(), R.string.ConversationFragment_quoted_message_no_longer_available, Toast.LENGTH_SHORT).show();
          } else {
            Log.i(TAG, "Quoted message was outside of the loaded range. Need to restart the loader.");

            firstLoad        = true;
            startingPosition = position;
            getLoaderManager().restartLoader(0, Bundle.EMPTY, ConversationFragment.this);
          }
        }
      }.execute();
    }

    @Override
    public void onSharedContactDetailsClicked(@NonNull Contact contact, @NonNull View avatarTransitionView) {
      if (getContext() != null && getActivity() != null) {
        Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(), avatarTransitionView, "avatar").toBundle();
        ActivityCompat.startActivity(getActivity(), SharedContactDetailsActivity.getIntent(getContext(), contact), bundle);
      }
    }

    @Override
    public void onAddToContactsClicked(@NonNull Contact contactWithAvatar) {
      if (getContext() != null) {
        new AsyncTask<Void, Void, Intent>() {
          @Override
          protected Intent doInBackground(Void... voids) {
            return ContactUtil.buildAddToContactsIntent(getContext(), contactWithAvatar);
          }

          @Override
          protected void onPostExecute(Intent intent) {
            startActivityForResult(intent, CODE_ADD_EDIT_CONTACT);
          }
        }.execute();
      }
    }

    @Override
    public void onMessageSharedContactClicked(@NonNull List<Recipient> choices) {
      if (getContext() == null) return;

      ContactUtil.selectRecipientThroughDialog(getContext(), choices, locale, recipient -> {
        CommunicationActions.startConversation(getContext(), recipient, null);
      });
    }

    @Override
    public void onInviteSharedContactClicked(@NonNull List<Recipient> choices) {
      if (getContext() == null) return;

      ContactUtil.selectRecipientThroughDialog(getContext(), choices, locale, recipient -> {
        CommunicationActions.composeSmsThroughDefaultApp(getContext(), recipient.getAddress(), getString(R.string.InviteActivity_lets_switch_to_signal, "https://sgnl.link/1KpeYmF"));
      });
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CODE_ADD_EDIT_CONTACT && getContext() != null) {
      ApplicationContext.getInstance(getContext().getApplicationContext())
                        .getJobManager()
                        .add(new DirectoryRefreshJob(getContext().getApplicationContext(), false));
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    private int statusBarColor;

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.conversation_context, menu);

      mode.setTitle("1");

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
        case R.id.menu_context_reply:
          handleReplyMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
      }

      return false;
    }
  }

  private static class ConversationDateHeader extends HeaderViewHolder {

    private final Animation animateIn;
    private final Animation animateOut;

    private boolean pendingHide = false;

    private ConversationDateHeader(Context context, TextView textView) {
      super(textView);
      this.animateIn  = AnimationUtils.loadAnimation(context, R.anim.slide_from_top);
      this.animateOut = AnimationUtils.loadAnimation(context, R.anim.slide_to_top);

      this.animateIn.setDuration(100);
      this.animateOut.setDuration(100);
    }

    public void show() {
      if (pendingHide) {
        pendingHide = false;
      } else {
        ViewUtil.animateIn(textView, animateIn);
      }
    }

    public void hide() {
      pendingHide = true;

      textView.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (pendingHide) {
            pendingHide = false;
            ViewUtil.animateOut(textView, animateOut, View.GONE);
          }
        }
      }, 400);
    }
  }
}
