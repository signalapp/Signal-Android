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
import java.util.List;

public class SelectedRecipientsAdapter extends ArrayAdapter<Recipient> {

  private ArrayList<Recipient> recipients;

  public SelectedRecipientsAdapter(Context context, int textViewResourceId) {
    super(context, textViewResourceId);
  }

  public SelectedRecipientsAdapter(Context context, int resource, ArrayList<Recipient> recipients) {
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

    Recipient p = getItem(position);

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
        delete.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            recipients.remove(position);
            SelectedRecipientsAdapter.this.notifyDataSetChanged();
          }
        });
      }
    }

    return v;

  }
}