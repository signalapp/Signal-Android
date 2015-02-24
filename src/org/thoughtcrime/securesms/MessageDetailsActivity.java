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

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.loaders.MessageDetailsLoader;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DirectoryHelper;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;

public class MessageDetailsActivity extends PassphraseRequiredActionBarActivity implements LoaderCallbacks<Cursor> {
  private final static String TAG = MessageDetailsActivity.class.getSimpleName();

  public final static String MASTER_SECRET_EXTRA = "master_secret";
  public final static String MESSAGE_ID_EXTRA    = "message_id";
  public final static String TYPE_EXTRA          = "type";
  public final static String PUSH_EXTRA          = "push";

  private MasterSecret     masterSecret;
  private ConversationItem conversationItem;
  private ViewGroup        itemParent;
  private ViewGroup        header;
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
    masterSecret   = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
  }

  private void updateTransport(MessageRecord messageRecord) {
    final String transportText;
    if (messageRecord.isOutgoing() && messageRecord.isFailed()) {
      transportText = "-";
    } else if (messageRecord.isPending()) {
      transportText = getString(R.string.ConversationFragment_pending);
    } else if (messageRecord.isPush()) {
      transportText = getString(R.string.ConversationFragment_push);
    } else if (messageRecord.isMms()) {
      transportText = getString(R.string.ConversationFragment_mms);
    } else {
      transportText = getString(R.string.ConversationFragment_sms);
    }

    transport.setText(transportText);
  }

  private void updateTime(MessageRecord messageRecord) {
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

  private void updateRecipients(MessageRecord messageRecord, Recipients recipients) {
    final int toFromRes;
    if (messageRecord.isMms() && !messageRecord.isPush() && !messageRecord.isOutgoing()) {
      toFromRes = R.string.message_details_header__with;
    } else if (messageRecord.isOutgoing()) {
      toFromRes = R.string.message_details_header__to;
    } else {
      toFromRes = R.string.message_details_header__from;
    }
    toFrom.setText(toFromRes);
    conversationItem.set(masterSecret, messageRecord, new HashSet<MessageRecord>(), null,
                         recipients != messageRecord.getRecipients(),
                         DirectoryHelper.isPushDestination(this, recipients));
    recipientsList.setAdapter(new MessageDetailsRecipientAdapter(this, masterSecret, messageRecord, recipients));
  }

  private void inflateMessageViewIfAbsent(MessageRecord messageRecord) {
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
  }

  private void inflateHeaderIfAbsent() {
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
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new MessageDetailsLoader(this, getIntent().getStringExtra(TYPE_EXTRA),
                                    getIntent().getLongExtra(MESSAGE_ID_EXTRA, -1));
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    new MessageRecipientAsyncTask(this, masterSecret, cursor, getIntent().getStringExtra(TYPE_EXTRA)) {
      @Override
      public void onPostExecute(Result result) {
        if (getContext() == null) {
          Log.w(TAG, "AsyncTask finished with a destroyed context, leaving early.");
          return;
        }

        inflateMessageViewIfAbsent(result.messageRecord);
        inflateHeaderIfAbsent();

        updateRecipients(result.messageRecord, result.recipients);
        updateTransport(result.messageRecord);
        updateTime(result.messageRecord);
      }
    }.execute();
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    recipientsList.setAdapter(null);
  }
}
