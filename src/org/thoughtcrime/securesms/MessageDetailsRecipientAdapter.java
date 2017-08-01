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

public class MessageDetailsRecipientAdapter extends BaseAdapter implements AbsListView.RecyclerListener {

  private final Context         context;
  private final MasterSecret    masterSecret;
  private final MessageRecord   record;
  private final List<Recipient> recipients;
  private final boolean         isPushGroup;

  public MessageDetailsRecipientAdapter(Context context, MasterSecret masterSecret,
                                        MessageRecord record, List<Recipient> recipients,
                                        boolean isPushGroup)
  {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.record       = record;
    this.recipients   = recipients;
    this.isPushGroup  = isPushGroup;
  }

  @Override
  public int getCount() {
    return recipients.size();
  }

  @Override
  public Object getItem(int position) {
    return recipients.get(position);
  }

  @Override
  public long getItemId(int position) {
    try {
      return Conversions.byteArrayToLong(MessageDigest.getInstance("SHA1").digest(recipients.get(position).getAddress().serialize().getBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(R.layout.message_recipient_list_item, parent, false);
    }

    Recipient recipient = recipients.get(position);
    ((MessageRecipientListItem)convertView).set(masterSecret, record, recipient, isPushGroup);
    return convertView;
  }

  @Override
  public void onMovedToScrapHeap(View view) {
    ((MessageRecipientListItem)view).unbind();
  }

}
