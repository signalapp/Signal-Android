package org.thoughtcrime.securesms.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;

import java.util.ArrayList;

public class SelectedRecipientsAdapter extends ArrayAdapter<SelectedRecipientsAdapter.RecipientWrapper> {

  private ArrayList<RecipientWrapper> recipients;
  private OnRecipientDeletedListener onRecipientDeletedListener;

  public SelectedRecipientsAdapter(Context context, int textViewResourceId) {
    super(context, textViewResourceId);
  }

  public SelectedRecipientsAdapter(Context context, int resource, ArrayList<RecipientWrapper> recipients) {
    super(context, resource, recipients);
    this.recipients = recipients;
  }

  @Override
  public View getView(final int position, final View convertView, final ViewGroup parent) {

    View v = convertView;

    if (v == null) {

      LayoutInflater vi;
      vi = LayoutInflater.from(getContext());
      v = vi.inflate(R.layout.selected_recipient_list_item, null);

    }

    final RecipientWrapper rw = getItem(position);
    final Recipient p = rw.getRecipient();
    final boolean modifiable = rw.isModifiable();

    if (p != null) {

      TextView name = (TextView) v.findViewById(R.id.name);
      TextView phone = (TextView) v.findViewById(R.id.phone);
      ImageButton delete = (ImageButton) v.findViewById(R.id.delete);

      if (name != null) {
        name.setText(p.getName());
      }
      if (phone != null) {
        phone.setText(p.getNumber());
      }
      if (delete != null) {
        if (modifiable) {
          delete.setVisibility(View.VISIBLE);
          delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
              if (onRecipientDeletedListener != null) {
                onRecipientDeletedListener.onRecipientDeleted(recipients.get(position).getRecipient());
              }
              recipients.remove(position);
              SelectedRecipientsAdapter.this.notifyDataSetChanged();
            }
          });
        } else {
          delete.setVisibility(View.INVISIBLE);
          delete.setOnClickListener(null);
        }
      }
    }

    return v;
  }

  public void setOnRecipientDeletedListener(OnRecipientDeletedListener listener) {
    onRecipientDeletedListener = listener;
  }

  public interface OnRecipientDeletedListener {
    public void onRecipientDeleted(Recipient recipient);
  }

  public static class RecipientWrapper {
    private final Recipient recipient;
    private final boolean modifiable;

    public RecipientWrapper(final Recipient recipient, final boolean modifiable) {
      this.recipient = recipient;
      this.modifiable = modifiable;
    }

    public Recipient getRecipient() {
      return recipient;
    }

    public boolean isModifiable() {
      return modifiable;
    }
  }
}