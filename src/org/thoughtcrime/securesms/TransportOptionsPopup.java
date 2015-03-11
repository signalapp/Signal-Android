package org.thoughtcrime.securesms;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.PopupWindow;

import java.util.LinkedList;
import java.util.List;

public class TransportOptionsPopup implements ListView.OnItemClickListener {

  private final TransportOptionsAdapter adapter;
  private final PopupWindow             popupWindow;
  private final SelectedListener        listener;

  public TransportOptionsPopup(@NonNull Context context, @NonNull SelectedListener listener) {
    this.listener = listener;
    this.adapter  = new TransportOptionsAdapter(context, new LinkedList<TransportOption>());

    View     selectionMenu = LayoutInflater.from(context).inflate(R.layout.transport_selection, null);
    ListView listView      = (ListView) selectionMenu.findViewById(R.id.transport_selection_list);


    listView.setAdapter(adapter);

    this.popupWindow = new PopupWindow(selectionMenu);
    this.popupWindow.setFocusable(true);
    this.popupWindow.setBackgroundDrawable(new BitmapDrawable(context.getResources(), ""));
    this.popupWindow.setOutsideTouchable(true);
    this.popupWindow.setWindowLayoutMode(0, WindowManager.LayoutParams.WRAP_CONTENT);
    this.popupWindow.setWidth(context.getResources().getDimensionPixelSize(R.dimen.transport_selection_popup_width));

    listView.setOnItemClickListener(this);
  }

  public void display(Context context, final View parent, List<TransportOption> enabledTransports) {
    this.adapter.setEnabledTransports(enabledTransports);
    this.adapter.notifyDataSetChanged();

    final int xoff = context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_xoff);
    final int yoff = context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_yoff);

    popupWindow.showAsDropDown(parent, xoff, yoff);

    parent.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        popupWindow.update(parent, xoff, yoff, -1, -1);
      }
    });
  }

  public void dismiss() {
    this.popupWindow.dismiss();
  }

  @Override
  public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
    listener.onSelected((TransportOption)adapter.getItem(position));
  }

  public interface SelectedListener {
    public void onSelected(TransportOption option);
  }

}
