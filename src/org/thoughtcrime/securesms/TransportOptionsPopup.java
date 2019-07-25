package org.thoughtcrime.securesms;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.ListPopupWindow;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.LinkedList;
import java.util.List;

public class TransportOptionsPopup extends ListPopupWindow implements ListView.OnItemClickListener {

  private final TransportOptionsAdapter adapter;
  private final SelectedListener        listener;

  public TransportOptionsPopup(@NonNull Context context, @NonNull View anchor, @NonNull SelectedListener listener) {
    super(context);
    this.listener = listener;
    this.adapter  = new TransportOptionsAdapter(context, new LinkedList<TransportOption>());

    setVerticalOffset(context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_yoff));
    setHorizontalOffset(context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_xoff));
    setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
    setModal(true);
    setAnchorView(anchor);
    setAdapter(adapter);
    setContentWidth(context.getResources().getDimensionPixelSize(R.dimen.transport_selection_popup_width));

    setOnItemClickListener(this);
  }

  public void display(List<TransportOption> enabledTransports) {
    adapter.setEnabledTransports(enabledTransports);
    adapter.notifyDataSetChanged();
    show();
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    listener.onSelected((TransportOption)adapter.getItem(position));
  }

  public interface SelectedListener {
    void onSelected(TransportOption option);
  }

}
