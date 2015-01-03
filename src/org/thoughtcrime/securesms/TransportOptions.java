package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.PopupWindow;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransportOptions {
  private static final String TAG = TransportOptions.class.getSimpleName();

  private final Context                          context;
  private       PopupWindow                      transportPopup;
  private final List<String>                     enabledTransports = new ArrayList<String>();
  private final Map<String, TransportOption>     transportMetadata = new HashMap<String, TransportOption>();
  private       String                           selectedTransport;
  private       boolean                          transportOverride = false;
  private       List<OnTransportChangedListener> listeners = new ArrayList<>();

  public TransportOptions(Context context) {
    this.context = context;
  }

  private void initializeTransportPopup() {
    if (transportPopup == null) {
      final View selectionMenu = LayoutInflater.from(context).inflate(R.layout.transport_selection, null);
      final ListView list      = (ListView) selectionMenu.findViewById(R.id.transport_selection_list);

      final TransportOptionsAdapter adapter = new TransportOptionsAdapter(context, enabledTransports, transportMetadata);

      list.setAdapter(adapter);
      transportPopup = new PopupWindow(selectionMenu);
      transportPopup.setFocusable(true);
      transportPopup.setBackgroundDrawable(new BitmapDrawable(context.getResources(), ""));
      transportPopup.setOutsideTouchable(true);
      transportPopup.setWindowLayoutMode(0, WindowManager.LayoutParams.WRAP_CONTENT);
      transportPopup.setWidth(context.getResources().getDimensionPixelSize(R.dimen.transport_selection_popup_width));
      list.setOnItemClickListener(new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
          transportOverride = true;
          setTransport((TransportOption) adapter.getItem(position), true);
          transportPopup.dismiss();
        }
      });
    } else {
      final ListView                list    = (ListView) transportPopup.getContentView().findViewById(R.id.transport_selection_list);
      final TransportOptionsAdapter adapter = (TransportOptionsAdapter) list.getAdapter();
      adapter.setEnabledTransports(enabledTransports);
      adapter.notifyDataSetInvalidated();
    }
  }

  public void initializeAvailableTransports(boolean isMediaMessage) {
    String[] entryArray = (isMediaMessage)
                          ? context.getResources().getStringArray(R.array.transport_selection_entries_media)
                          : context.getResources().getStringArray(R.array.transport_selection_entries_text);

    String[] composeHintArray = (isMediaMessage)
                                ? context.getResources().getStringArray(R.array.transport_selection_entries_compose_media)
                                : context.getResources().getStringArray(R.array.transport_selection_entries_compose_text);

    final String[] valuesArray = context.getResources().getStringArray(R.array.transport_selection_values);

    // Normal Button Icons (Transport Button)
    final int[]        buttonAttrs             = new int[]{R.attr.conversation_transport_button_icons};
    final TypedArray   buttonIconArray         = context.obtainStyledAttributes(buttonAttrs);
    final TypedArray   buttonIcons             = context.getResources().obtainTypedArray(buttonIconArray.getResourceId(0, -1));

    // Send Button Icons
    final int[]        sendButtonAttrs       = new int[]{R.attr.conversation_transport_send_button_icons};
    final TypedArray   sendButtonIconArray   = context.obtainStyledAttributes(sendButtonAttrs);
    final TypedArray   sendButtonIcons       = context.getResources().obtainTypedArray(sendButtonIconArray.getResourceId(0, -1));

    enabledTransports.clear();
    for (int i=0; i<valuesArray.length; i++) {
      String key = valuesArray[i];
      enabledTransports.add(key);
      transportMetadata.put(key, new TransportOption(
          key,
          buttonIcons.getResourceId(i, -1),
          sendButtonIcons.getResourceId(i, -1),
          entryArray[i],
          composeHintArray[i]
          ));
    }
    buttonIconArray.recycle();
    buttonIcons.recycle();
    sendButtonIconArray.recycle();
    sendButtonIcons.recycle();
    updateViews(false);
  }

  public void setTransport(String transport, boolean userChange) {
    selectedTransport = transport;
    updateViews(userChange);
  }

  private void setTransport(TransportOption transport, boolean userChange) {
    setTransport(transport.key, userChange);
  }

  public void showPopup(final View parent) {
    initializeTransportPopup();
    final int xoff = context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_xoff);
    final int yoff = context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_yoff);
    transportPopup.showAsDropDown(parent,
                                  xoff,
                                  yoff);
    parent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        transportPopup.update(parent, xoff, yoff, -1, -1);
      }
    });
  }

  public void setDefaultTransport(String transportName, boolean overrideIfDefaultSet) {
    System.out.println("SET DEFAULT " + transportName);
    if (transportName != null && !transportOverride && (overrideIfDefaultSet || selectedTransport == null)) {
      setTransport(transportName, false);
    }
  }

  public TransportOption getSelectedTransport() {
    return transportMetadata.get(selectedTransport);
  }

  public void disableTransport(String transportName) {
    enabledTransports.remove(transportName);
  }

  public List<String> getEnabledTransports() {
    return enabledTransports;
  }

  private void updateViews(boolean userChange) {
    if (selectedTransport == null) return;

    for (OnTransportChangedListener listener : listeners) {
      listener.onChange(getSelectedTransport(), userChange);
    }
  }

  public void addOnTransportChangedListener(OnTransportChangedListener listener) {
    listeners.add(listener);
  }

  public interface OnTransportChangedListener {
    public void onChange(TransportOption newTransport, boolean userChange);
  }
}
