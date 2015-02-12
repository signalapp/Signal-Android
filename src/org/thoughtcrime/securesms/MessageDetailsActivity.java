package org.thoughtcrime.securesms;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.Database;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DateUtils;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;

public class MessageDetailsActivity extends PassphraseRequiredActionBarActivity implements LoaderCallbacks<Pair<MessageRecord,Recipients>> {
  private final static String TAG = MessageDetailsActivity.class.getSimpleName();

  public final static String MASTER_SECRET_EXTRA = "master_secret";
  public final static String MESSAGE_ID_EXTRA    = "message_id";
  public final static String TYPE_EXTRA          = "type";
  public final static String PUSH_EXTRA          = "push";

  private MasterSecret     masterSecret;
  private MessageRecord    messageRecord;
  private ConversationItem conversationItem;
  private ViewGroup        itemParent;
  private ViewGroup        header;
  private boolean          pushDestination;
  private Recipients       recipients;
  private TextView         sentDate;
  private TextView         receivedDate;
  private View             receivedContainer;
  private TextView         transport;
  private TextView         toFrom;
  private ListView         recipientsList;
  private LayoutInflater   inflater;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.message_details_activity);

    initializeResources();

    getSupportLoaderManager().initLoader(0, null, this);
  }

  private void initializeResources() {
    inflater       = LayoutInflater.from(this);
    itemParent     = (ViewGroup) findViewById(R.id.item_container );
    recipientsList = (ListView ) findViewById(R.id.recipients_list);

    masterSecret    = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
    pushDestination = getIntent().getBooleanExtra(PUSH_EXTRA, false);
  }

  private void initializeTimeAndTransport() {
    if (messageRecord.isOutgoing() && messageRecord.isFailed()) {
      transport.setText("-");
      sentDate.setText("-");
      receivedContainer.setVisibility(View.GONE);
      return;
    }

    final String transportText;
    if      (messageRecord.isPending()) transportText = getString(R.string.ConversationFragment_pending);
    else if (messageRecord.isPush())    transportText = getString(R.string.ConversationFragment_push);
    else if (messageRecord.isMms())     transportText = getString(R.string.ConversationFragment_mms);
    else                                transportText = getString(R.string.ConversationFragment_sms);

    transport.setText(transportText);

    if (messageRecord.isPending() || messageRecord.isFailed()) {
      sentDate.setText("-");
      receivedContainer.setVisibility(View.GONE);
    } else {
      SimpleDateFormat dateFormatter = DateUtils.getDetailedDateFormatter(this);
      sentDate.setText(dateFormatter.format(new Date(messageRecord.getDateSent())));

      if (messageRecord.getDateReceived() != messageRecord.getDateSent() && !messageRecord.isOutgoing()) {
        receivedDate.setText(dateFormatter.format(new Date(messageRecord.getDateReceived())));
        receivedContainer.setVisibility(View.VISIBLE);
      } else {
        receivedContainer.setVisibility(View.GONE);
      }
    }
  }

  private void initializeRecipients() {
    final int toFromRes;
    if (messageRecord.isMms() && !messageRecord.isPush() && !messageRecord.isOutgoing()) {
      toFromRes = R.string.message_details_header__with;
    } else if (messageRecord.isOutgoing()) {
      toFromRes = R.string.message_details_header__to;
    } else {
      toFromRes = R.string.message_details_header__from;
    }
    toFrom.setText(toFromRes);
    conversationItem.set(masterSecret, messageRecord, new HashSet<MessageRecord>(), null, recipients != messageRecord.getRecipients(), pushDestination);
    recipientsList.setAdapter(new MessageDetailsRecipientAdapter(this, masterSecret, messageRecord, recipients));
    getContentResolver().registerContentObserver(Uri.parse(Database.CONVERSATION_URI + messageRecord.getThreadId()),
                                                 false,
                                                 new MessageContentObserver(new Handler()));
  }

  @Override
  public Loader<Pair<MessageRecord,Recipients>> onCreateLoader(int id, Bundle args) {
    Log.w(TAG, "onCreateLoader()");
    return new MessageDetailsRecipientLoader(this,
                                             masterSecret,
                                             getIntent().getStringExtra(TYPE_EXTRA),
                                             getIntent().getLongExtra(MESSAGE_ID_EXTRA, -1));
  }

  @Override
  public void onLoadFinished(Loader<Pair<MessageRecord,Recipients>> loader, Pair<MessageRecord,Recipients> data) {
    messageRecord = data.first;
    recipients    = data.second;

    if (conversationItem == null) {
      if (messageRecord.isGroupAction()) {
        conversationItem = (ConversationItem) inflater.inflate(R.layout.conversation_item_activity, itemParent, false);
      } else if (messageRecord.isOutgoing()) {
        conversationItem = (ConversationItem) inflater.inflate(R.layout.conversation_item_sent, itemParent, false);
      } else {
        conversationItem = (ConversationItem) inflater.inflate(R.layout.conversation_item_received, itemParent, false);
      }
      itemParent.addView(conversationItem);
    }

    if (header == null) {
      header            = (ViewGroup) inflater.inflate(R.layout.message_details_header, recipientsList, false);
      sentDate          = (TextView ) header.findViewById(R.id.sent_time);
      receivedContainer =             header.findViewById(R.id.received_container);
      receivedDate      = (TextView ) header.findViewById(R.id.received_time     );
      transport         = (TextView ) header.findViewById(R.id.transport         );
      toFrom            = (TextView ) header.findViewById(R.id.tofrom            );
      recipientsList.setHeaderDividersEnabled(false);
      recipientsList.addHeaderView(header, null, false);
    }

    initializeRecipients();
    initializeTimeAndTransport();
  }

  @Override
  public void onLoaderReset(Loader<Pair<MessageRecord,Recipients>> loader) {
  }

  private class MessageContentObserver extends ContentObserver {
    private MessageContentObserver(Handler handler) {
      super(handler);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
      super.onChange(selfChange, uri);
      getSupportLoaderManager().restartLoader(0, null, MessageDetailsActivity.this);
    }
  }
}
