package org.thoughtcrime.securesms;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipients;

public class MessageDetailsRecipientAdapter extends BaseAdapter implements AbsListView.RecyclerListener {

  private Context       context;
  private MasterSecret  masterSecret;
  private MessageRecord record;
  private Recipients    recipients;

  public MessageDetailsRecipientAdapter(Context context, MasterSecret masterSecret, MessageRecord record, Recipients recipients) {
    super();
    this.context      = context;
    this.masterSecret = masterSecret;
    this.record       = record;
    this.recipients   = recipients;
  }

  @Override
  public int getCount() {
    return recipients.getRecipientsList().size();
  }

  @Override
  public Object getItem(int position) {
    return recipients.getRecipientsList().get(position);
  }

  @Override
  public long getItemId(int position) {
    return recipients.getRecipientsList().get(position).getRecipientId();
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(R.layout.message_details_recipient, parent, false);
    }

    ((MessageDetailsRecipient)convertView).set(masterSecret, record, recipients, position);
    return convertView;
  }

  @Override
  public void onMovedToScrapHeap(View view) {
    ((MessageDetailsRecipient)view).unbind();
  }

}
