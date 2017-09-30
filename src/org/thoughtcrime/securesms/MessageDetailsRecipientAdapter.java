package org.thoughtcrime.securesms;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Conversions;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

class MessageDetailsRecipientAdapter extends BaseAdapter implements AbsListView.RecyclerListener {

  private final Context                       context;
  private final MasterSecret                  masterSecret;
  private final MessageRecord                 record;
  private final List<RecipientDeliveryStatus> members;
  private final boolean                       isPushGroup;

  MessageDetailsRecipientAdapter(Context context, MasterSecret masterSecret, MessageRecord record,
                                 List<RecipientDeliveryStatus> members, boolean isPushGroup)
  {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.record       = record;
    this.isPushGroup  = isPushGroup;
    this.members      = members;
  }

  @Override
  public int getCount() {
    return members.size();
  }

  @Override
  public Object getItem(int position) {
    return members.get(position);
  }

  @Override
  public long getItemId(int position) {
    try {
      return Conversions.byteArrayToLong(MessageDigest.getInstance("SHA1").digest(members.get(position).recipient.getAddress().serialize().getBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(R.layout.message_recipient_list_item, parent, false);
    }

    RecipientDeliveryStatus member = members.get(position);

    ((MessageRecipientListItem)convertView).set(masterSecret, record, member, isPushGroup);
    return convertView;
  }

  @Override
  public void onMovedToScrapHeap(View view) {
    ((MessageRecipientListItem)view).unbind();
  }


  static class RecipientDeliveryStatus {

    enum Status {
      UNKNOWN, PENDING, SENT, DELIVERED, READ
    }

    private final Recipient recipient;
    private final Status    deliveryStatus;
    private final long      timestamp;

    RecipientDeliveryStatus(Recipient recipient, Status deliveryStatus, long timestamp) {
      this.recipient      = recipient;
      this.deliveryStatus = deliveryStatus;
      this.timestamp      = timestamp;
    }

    Status getDeliveryStatus() {
      return deliveryStatus;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public Recipient getRecipient() {
      return recipient;
    }

  }

}
