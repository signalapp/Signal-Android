package de.gdata.messaging.selfdestruction;

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

import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelfDestOptions {
    private static final String TAG = SelfDestOptions.class.getSimpleName();

    private final Context context;
    private PopupWindow popupWindow;
    private final List<String> enabledSelfDest = new ArrayList<String>();
    private final Map<String, DestroyOption> selfDestMetaData = new HashMap<String, DestroyOption>();
    private String selectedSelfDestTime;
    private boolean selfDestOverride = false;
    private onDestroyTimeChangedListener listener;

    public SelfDestOptions(Context context) {
        this.context = context;
    }

    private void initializePopup() {
        if (popupWindow == null) {
            final View selectionMenu = LayoutInflater.from(context).inflate(R.layout.transport_selection, null);
            final ListView list = (ListView) selectionMenu.findViewById(R.id.transport_selection_list);

            final SelfDestOptionsAdapter adapter = new SelfDestOptionsAdapter(context, enabledSelfDest, selfDestMetaData);

            list.setAdapter(adapter);
            popupWindow = new PopupWindow(selectionMenu);
            popupWindow.setFocusable(true);
            popupWindow.setBackgroundDrawable(new BitmapDrawable(context.getResources(), ""));
            popupWindow.setOutsideTouchable(true);
            popupWindow.setWindowLayoutMode(0, WindowManager.LayoutParams.WRAP_CONTENT);
            popupWindow.setWidth(context.getResources().getDimensionPixelSize(R.dimen.transport_selection_popup_width));
            list.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    selfDestOverride = true;
                    setSelfDest((DestroyOption) adapter.getItem(position));
                    popupWindow.dismiss();
                }
            });
        } else {
            final ListView list = (ListView) popupWindow.getContentView().findViewById(R.id.transport_selection_list);
            final SelfDestOptionsAdapter adapter = (SelfDestOptionsAdapter) list.getAdapter();
            adapter.setEnabledSelfDest(enabledSelfDest);
            adapter.notifyDataSetInvalidated();
        }
    }

    public void initializeAvailableSelfDests() {

        String[] entryArray = context.getResources().getStringArray(R.array.gdata_selfdestroy_entrys);

        final String[] valuesArray = context.getResources().getStringArray(R.array.gdata_selfdestroy_values);

//        final int[] attrs = new int[] {R.attr.conversation_selfdestroy_icon_indicator};
//        final TypedArray iconArray = context.obtainStyledAttributes(attrs);
//        final int iconArrayResource = iconArray.getResourceId(R.array.gdata_selfdestroy_icons, 0);
//        final TypedArray icons = context.getResources().obtainTypedArray(iconArrayResource);

        final TypedArray iconArray = context.getResources().obtainTypedArray(R.array.gdata_selfdestroy_icons);

        enabledSelfDest.clear();
        for (int i = 0; i < valuesArray.length; i++) {
            String key = valuesArray[i];
            enabledSelfDest.add(key);

            int iconId = iconArray.getResourceId(i, 0);

            selfDestMetaData.put(key, new DestroyOption(key, iconId, entryArray[i]));
        }
        iconArray.recycle();

        updateViews();
    }

    public void setSelfDest(String selfDest) {
        selectedSelfDestTime = selfDest;
        updateViews();
    }

    private void setSelfDest(DestroyOption destroyOption) {
        setSelfDest(destroyOption.key);
    }

    public void showPopup(final View parent) {
        initializePopup();
        final int xoff = context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_xoff);
        final int yoff = context.getResources().getDimensionPixelOffset(R.dimen.transport_selection_popup_yoff);
        popupWindow.showAsDropDown(parent,
                xoff,
                yoff);
        parent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                popupWindow.update(parent, xoff, yoff, -1, -1);
            }
        });
    }

    public void setDefaultSelfDest(String selfDestName) {
        if (!selfDestOverride) {
            setSelfDest(selfDestName);
        }
    }

    public DestroyOption getSelectedSelfDestTime() {
        return selfDestMetaData.get(selectedSelfDestTime);
    }

    public void disableSelfDest(String selfDest) {
        enabledSelfDest.remove(selfDest);
    }

    public List<String> getEnabledSelfDest() {
        return enabledSelfDest;
    }

    private void updateViews() {
        if (selectedSelfDestTime == null) return;

        if (listener != null) {
            listener.onChange(getSelectedSelfDestTime());
        }
    }

    public void setOnSelfDestChangedListener(onDestroyTimeChangedListener listener) {
        this.listener = listener;
    }

    public interface onDestroyTimeChangedListener {
        public void onChange(DestroyOption destroyOption);
    }
}
