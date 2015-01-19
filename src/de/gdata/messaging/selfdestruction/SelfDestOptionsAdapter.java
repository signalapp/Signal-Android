package de.gdata.messaging.selfdestruction;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

import java.util.List;
import java.util.Map;

public class SelfDestOptionsAdapter extends BaseAdapter {
  private final Context context;
  private final LayoutInflater inflater;
  private List<String> enabledTransports;
  private final Map<String, DestroyOption> transportMetadata;

  public SelfDestOptionsAdapter(final Context context,
                                final List<String> enabledTransports,
                                final Map<String, DestroyOption> transportMetadata) {
    super();
    this.context = context;
    this.inflater = LayoutInflater.from(context);
    this.enabledTransports = enabledTransports;
    this.transportMetadata = transportMetadata;
  }

  public SelfDestOptionsAdapter(final Context context,
                                final Map<String, DestroyOption> transportMetadata) {
    this(context, null, transportMetadata);
  }

  public void setEnabledSelfDest(final List<String> enabledTransports) {
    this.enabledTransports = enabledTransports;
  }

  @Override
  public int getCount() {
    return enabledTransports == null ? 0 : enabledTransports.size();
  }

  @Override
  public Object getItem(int position) {
    return transportMetadata.get(enabledTransports.get(position));
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    final View view;
    if (convertView == null) {
      view = inflater.inflate(R.layout.transport_selection_list_item, parent, false);
    } else {
      view = convertView;
    }

    DestroyOption transport = (DestroyOption) getItem(position);
    final ImageView imageView = (ImageView) view.findViewById(R.id.icon);
    final TextView textView = (TextView) view.findViewById(R.id.text);

    imageView.setImageResource(transport.drawable);
    textView.setText(transport.text);
    return view;
  }
}
