package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.BitmapDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupWindow;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TransportOptions {
  private static final String TAG = TransportOptions.class.getSimpleName();

  private final Context                      context;
  private       PopupWindow                  transportPopup;
  private final List<String>                 enabledTransports = new ArrayList<String>();
  private final Map<String, TransportOption> transportMetadata = new HashMap<String, TransportOption>();
  private       String                       selectedTransport;
  private       boolean                      transportOverride = false;
  private       OnTransportChangedListener   listener;

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
          setTransport((TransportOption) adapter.getItem(position));
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

  public void initializeAvailableTransports(boolean isMediaMessage, boolean isSingleEncryptedConversation) {
    String[] entryArray = (isMediaMessage)
                          ? context.getResources().getStringArray(R.array.transport_selection_entries_media)
                          : context.getResources().getStringArray(R.array.transport_selection_entries_text);

    String[] composeHintArray = (isMediaMessage)
                                ? context.getResources().getStringArray(R.array.transport_selection_entries_compose_media)
                                : context.getResources().getStringArray(R.array.transport_selection_entries_compose_text);

    composeHintArray = isSingleEncryptedConversation
            ? composeHintArray
            : context.getResources().getStringArray(R.array.transport_selection_entries_compose_text_invite);

    entryArray = (isSingleEncryptedConversation)
            ? entryArray
            : context.getResources().getStringArray(R.array.transport_selection_entries_text_invite);

    final String[] valuesArray = isSingleEncryptedConversation ?
             context.getResources().getStringArray(R.array.transport_selection_values)
            : context.getResources().getStringArray(R.array.transport_selection_values_invite);


    final int[]        attrs             = isSingleEncryptedConversation
            ? new int[]{R.attr.conversation_transport_indicators}
            : new int[]{R.attr.conversation_transport_indicators_invite};

    final TypedArray   iconArray         = context.obtainStyledAttributes(attrs);
    final int          iconArrayResource = iconArray.getResourceId(0, -1);
    final TypedArray   icons             = context.getResources().obtainTypedArray(iconArrayResource);


    enabledTransports.clear();
    for (int i=0; i<valuesArray.length; i++) {
      String key = valuesArray[i];
      enabledTransports.add(key);
      transportMetadata.put(key, new TransportOption(key, icons.getResourceId(i, -1), entryArray[i], composeHintArray[i]));
    }
    iconArray.recycle();
    icons.recycle();
    updateViews();
  }

  public void setTransport(String transport) {
    selectedTransport = transport;
    updateViews();
  }

  private void setTransport(TransportOption transport) {
    setTransport(transport.key);
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

  public void setDefaultTransport(String transportName) {
    if (!transportOverride) {
      setTransport(transportName);
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

  private void updateViews() {
    if (selectedTransport == null) return;

    if (listener != null) {
      listener.onChange(getSelectedTransport());
    }
  }

  public void setOnTransportChangedListener(OnTransportChangedListener listener) {
    this.listener = listener;
  }

  public interface OnTransportChangedListener {
    public void onChange(TransportOption newTransport);
  }
}
