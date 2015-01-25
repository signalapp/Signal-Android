package org.thoughtcrime.securesms;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.format.DateFormat;
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

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;

public class MessageDetailsActivity extends PassphraseRequiredActionBarActivity implements LoaderCallbacks<Pair<MessageRecord,Recipients>> {
  private final static String TAG = MessageDetailsActivity.class.getSimpleName();

  public final static String MASTER_SECRET_EXTRA = "master_secret";
  public final static String MESSAGE_ID_EXTRA    = "message_id";
  public final static String TYPE_EXTRA          = "type";
  public final static String PUSH_EXTRA          = "push";

  private MasterSecret     masterSecret;
  private MessageRecord    messageRecord;
  private ConversationItem item;
  private boolean          pushDestination;
  private Recipients       recipients;
  private TextView         sentDate;
  private TextView         receivedDate;
  private TextView         transport;
  private ListView         recipientsList;

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.conversation_item_details);

    initializeResources();

    getSupportLoaderManager().initLoader(0, null, this);
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  private void initializeResources() {
    recipientsList = (ListView) findViewById(R.id.recipients_list);
    sentDate       = (TextView) findViewById(R.id.sent_time      );
    receivedDate   = (TextView) findViewById(R.id.received_time  );
    transport      = (TextView) findViewById(R.id.transport      );

    masterSecret    = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
    pushDestination = getIntent().getBooleanExtra(PUSH_EXTRA, false);
  }

  private void initializeDates() {
    final String transportText;
    if      (messageRecord.isPending()) transportText = getString(R.string.ConversationFragment_pending);
    else if (messageRecord.isPush())    transportText = getString(R.string.ConversationFragment_push);
    else if (messageRecord.isMms())     transportText = getString(R.string.ConversationFragment_mms);
    else                                transportText = getString(R.string.ConversationFragment_sms);

    transport.setText(transportText);

    String dateFormatPattern;

    if (DateFormat.is24HourFormat(getApplicationContext())) {
      dateFormatPattern = "MMM d, yyyy HH:mm:ss zzz";
    } else {
      dateFormatPattern = "MMM d, yyyy hh:mm:ssa zzz";
    }

    SimpleDateFormat dateFormatter = new SimpleDateFormat(dateFormatPattern, Locale.getDefault());

    sentDate.setText(dateFormatter.format(new Date(messageRecord.getDateSent())));

    if (messageRecord.getDateReceived() != messageRecord.getDateSent() && !messageRecord.isOutgoing()) {
      receivedDate.setText(dateFormatter.format(new Date(messageRecord.getDateReceived())));
      findViewById(R.id.received_container).setVisibility(View.VISIBLE);
    } else {
      findViewById(R.id.received_container).setVisibility(View.GONE);
    }
  }

  private void initializeRecipients() {
    item.set(masterSecret, messageRecord, new HashSet<MessageRecord>(), null, recipients != messageRecord.getRecipients(), pushDestination);
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

    if (item == null) {
      ViewGroup parent = (ViewGroup) findViewById(R.id.item_container);
      if (messageRecord.isGroupAction()) {
        item = (ConversationItem) LayoutInflater.from(this).inflate(R.layout.conversation_item_activity, parent, false);
      } else if (messageRecord.isOutgoing()) {
        item = (ConversationItem) LayoutInflater.from(this).inflate(R.layout.conversation_item_sent, parent, false);
      } else {
        item = (ConversationItem) LayoutInflater.from(this).inflate(R.layout.conversation_item_received, parent, false);
      }
      parent.addView(item);
    }

    initializeRecipients();
    initializeDates();
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
