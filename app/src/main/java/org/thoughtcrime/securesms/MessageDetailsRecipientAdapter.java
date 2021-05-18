package org.thoughtcrime.securesms;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;


import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.loki.views.UserView;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.session.libsession.utilities.recipients.Recipient;
import org.session.libsession.utilities.Conversions;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

class MessageDetailsRecipientAdapter extends BaseAdapter implements AbsListView.RecyclerListener {

  private final Context                       context;
  private final GlideRequests glideRequests;
  private final MessageRecord                 record;
  private final List<RecipientDeliveryStatus> members;
  private final boolean                       isPushGroup;

  MessageDetailsRecipientAdapter(@NonNull Context context, @NonNull GlideRequests glideRequests,
                                 @NonNull MessageRecord record, @NonNull List<RecipientDeliveryStatus> members,
                                 boolean isPushGroup)
  {
    this.context       = context;
    this.glideRequests = glideRequests;
    this.record        = record;
    this.isPushGroup   = isPushGroup;
    this.members       = members;
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
    UserView result = new UserView(context);
    Recipient recipient = members.get(position).getRecipient();
    result.setOpenGroupThreadID(record.getThreadId());
    result.bind(recipient, glideRequests, UserView.ActionIndicator.None, false);
    return result;
  }

  @Override
  public void onMovedToScrapHeap(View view) {
    ((UserView)view).unbind();
  }


  static class RecipientDeliveryStatus {

    enum Status {
      UNKNOWN, PENDING, SENT, DELIVERED, READ
    }

    private final Recipient recipient;
    private final Status    deliveryStatus;
    private final boolean   isUnidentified;
    private final long      timestamp;

    RecipientDeliveryStatus(Recipient recipient, Status deliveryStatus, boolean isUnidentified, long timestamp) {
      this.recipient      = recipient;
      this.deliveryStatus = deliveryStatus;
      this.isUnidentified = isUnidentified;
      this.timestamp      = timestamp;
    }

    Status getDeliveryStatus() {
      return deliveryStatus;
    }

    boolean isUnidentified() {
      return isUnidentified;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public Recipient getRecipient() {
      return recipient;
    }

  }

}
