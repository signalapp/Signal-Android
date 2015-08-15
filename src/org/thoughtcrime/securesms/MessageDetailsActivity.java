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

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.loaders.MessageDetailsLoader;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;

/**
 * @author Jake McGinty
 */
public class MessageDetailsActivity extends PassphraseRequiredActionBarActivity implements LoaderCallbacks<Cursor>, Recipients.RecipientsModifiedListener {
  private final static String TAG = MessageDetailsActivity.class.getSimpleName();

  public final static String MASTER_SECRET_EXTRA  = "master_secret";
  public final static String MESSAGE_ID_EXTRA     = "message_id";
  public final static String IS_PUSH_GROUP_EXTRA  = "is_push_group";
  public final static String TYPE_EXTRA           = "type";
  public final static String RECIPIENTS_IDS_EXTRA = "recipients_ids";

  private MasterSecret     masterSecret;
  private long             messageId;
  private boolean          isPushGroup;
  private ConversationItem conversationItem;
  private ViewGroup        itemParent;
  private View             metadataContainer;
  private TextView         errorText;
  private TextView         sentDate;
  private TextView         receivedDate;
  private View             receivedContainer;
  private TextView         transport;
  private TextView         toFrom;
  private ListView         recipientsList;
  private LayoutInflater   inflater;

  private DynamicTheme     dynamicTheme    = new DynamicTheme();
  private DynamicLanguage  dynamicLanguage = new DynamicLanguage();

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle, @NonNull MasterSecret masterSecret) {
    setContentView(R.layout.message_details_activity);

    initializeResources();
    initializeActionBar();
    getSupportLoaderManager().initLoader(0, null, this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);
    getSupportActionBar().setTitle(R.string.AndroidManifest__message_details);

    MessageNotifier.setVisibleMessage(messageId);
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleMessage(-1L);
  }

  private void initializeActionBar() {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    Recipients recipients = RecipientFactory.getRecipientsForIds(this, getIntent().getLongArrayExtra(RECIPIENTS_IDS_EXTRA), true);
    recipients.addListener(this);

    setActionBarColor(recipients.getColor());
  }

  private void setActionBarColor(MaterialColor color) {
    getSupportActionBar().setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getWindow().setStatusBarColor(color.toStatusBarColor(this));
    }
  }

  @Override
  public void onModified(final Recipients recipients) {
    Util.runOnMain(new Runnable() {
      @Override
      public void run() {
        setActionBarColor(recipients.getColor());
      }
    });
  }

  private void initializeResources() {
    inflater       = LayoutInflater.from(this);
    View header = inflater.inflate(R.layout.message_details_header, recipientsList, false);

    masterSecret      = getIntent().getParcelableExtra(MASTER_SECRET_EXTRA);
    messageId         = getIntent().getLongExtra(MESSAGE_ID_EXTRA, -1);
    isPushGroup       = getIntent().getBooleanExtra(IS_PUSH_GROUP_EXTRA, false);
    itemParent        = (ViewGroup) header.findViewById(R.id.item_container);
    recipientsList    = (ListView ) findViewById(R.id.recipients_list);
    metadataContainer =             header.findViewById(R.id.metadata_container);
    errorText         = (TextView ) header.findViewById(R.id.error_text);
    sentDate          = (TextView ) header.findViewById(R.id.sent_time);
    receivedContainer =             header.findViewById(R.id.received_container);
    receivedDate      = (TextView ) header.findViewById(R.id.received_time);
    transport         = (TextView ) header.findViewById(R.id.transport);
    toFrom            = (TextView ) header.findViewById(R.id.tofrom);
    recipientsList.setHeaderDividersEnabled(false);
    recipientsList.addHeaderView(header, null, false);
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
      Locale           dateLocale    = dynamicLanguage.getCurrentLocale();
      SimpleDateFormat dateFormatter = DateUtils.getDetailedDateFormatter(this, dateLocale);
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
    conversationItem.bind(masterSecret, messageRecord, dynamicLanguage.getCurrentLocale(),
                         new HashSet<MessageRecord>(), recipients);
    recipientsList.setAdapter(new MessageDetailsRecipientAdapter(this, masterSecret, messageRecord,
                                                                 recipients, isPushGroup));
  }

  private void inflateMessageViewIfAbsent(MessageRecord messageRecord) {
    if (conversationItem == null) {
      if (messageRecord.isGroupAction()) {
        conversationItem = (ConversationItem) inflater.inflate(R.layout.conversation_item_update, itemParent, false);
      } else if (messageRecord.isOutgoing()) {
        conversationItem = (ConversationItem) inflater.inflate(R.layout.conversation_item_sent, itemParent, false);
      } else {
        conversationItem = (ConversationItem) inflater.inflate(R.layout.conversation_item_received, itemParent, false);
      }
      itemParent.addView(conversationItem);
    }
  }

  private MessageRecord getMessageRecord(Context context, Cursor cursor, String type) {
    switch (type) {
      case MmsSmsDatabase.SMS_TRANSPORT:
        EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
        SmsDatabase.Reader    reader      = smsDatabase.readerFor(masterSecret, cursor);
        return reader.getNext();
      case MmsSmsDatabase.MMS_TRANSPORT:
        MmsDatabase        mmsDatabase = DatabaseFactory.getMmsDatabase(context);
        MmsDatabase.Reader mmsReader   = mmsDatabase.readerFor(masterSecret, cursor);
        return mmsReader.getNext();
      default:
        throw new AssertionError("no valid message type specified");
    }
  }


  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new MessageDetailsLoader(this, getIntent().getStringExtra(TYPE_EXTRA),
                                    getIntent().getLongExtra(MESSAGE_ID_EXTRA, -1));
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    final MessageRecord messageRecord = getMessageRecord(this, cursor, getIntent().getStringExtra(TYPE_EXTRA));
    new MessageRecipientAsyncTask(this, messageRecord).execute();
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    recipientsList.setAdapter(null);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case android.R.id.home: finish(); return true;
    }

    return false;
  }

  private class MessageRecipientAsyncTask extends AsyncTask<Void,Void,Recipients> {
    private WeakReference<Context> weakContext;
    private MessageRecord          messageRecord;

    public MessageRecipientAsyncTask(Context context, MessageRecord messageRecord) {
      this.weakContext   = new WeakReference<>(context);
      this.messageRecord = messageRecord;
    }

    protected Context getContext() {
      return weakContext.get();
    }

    @Override
    public Recipients doInBackground(Void... voids) {
      Context context = getContext();
      if (context == null) {
        Log.w(TAG, "associated context is destroyed, finishing early");
        return null;
      }

      Recipients recipients;

      final Recipients intermediaryRecipients;
      if (messageRecord.isMms()) {
        intermediaryRecipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageRecord.getId());
      } else {
        intermediaryRecipients = messageRecord.getRecipients();
      }

      if (!intermediaryRecipients.isGroupRecipient()) {
        Log.w(TAG, "Recipient is not a group, resolving members immediately.");
        recipients = intermediaryRecipients;
      } else {
        try {
          String groupId = intermediaryRecipients.getPrimaryRecipient().getNumber();
          recipients = DatabaseFactory.getGroupDatabase(context)
                                      .getGroupMembers(GroupUtil.getDecodedId(groupId), false);
        } catch (IOException e) {
          Log.w(TAG, e);
          recipients = RecipientFactory.getRecipientsFor(MessageDetailsActivity.this, new LinkedList<Recipient>(), false);
        }
      }

      return recipients;
    }

    @Override
    public void onPostExecute(Recipients recipients) {
      if (getContext() == null) {
        Log.w(TAG, "AsyncTask finished with a destroyed context, leaving early.");
        return;
      }

      inflateMessageViewIfAbsent(messageRecord);

      updateRecipients(messageRecord, recipients);
      if (messageRecord.isFailed()) {
        errorText.setVisibility(View.VISIBLE);
        metadataContainer.setVisibility(View.GONE);
      } else {
        updateTransport(messageRecord);
        updateTime(messageRecord);
        errorText.setVisibility(View.GONE);
        metadataContainer.setVisibility(View.VISIBLE);
      }
    }
  }
}
