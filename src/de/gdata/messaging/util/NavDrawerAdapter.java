package de.gdata.messaging.util;

/**
 * Created by jan on 24.04.15.
 */

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class NavDrawerAdapter extends BaseAdapter {

  public static final int menu_new_message = -1;
  public static final int menu_new_group = 0;
  public static final int menu_clear_passphrase = 8;
  public static final int menu_mark_all_read = 1;
  public static final int menu_import_export = 2;
  public static final int menu_my_identity = 3;
  public static final int menu_privacy = 5;
  public static final int menu_privacy_hide = 6;
  public static final int menu_filter = 4;
  public static final int menu_settings = 7;

  private Activity activity;
  private String[] labels;
  private TypedArray icons;
  private static LayoutInflater inflater = null;

  public NavDrawerAdapter(Activity a, String[] labelsArray, TypedArray iconArray) {

    activity = a;
    labels = labelsArray;
    icons = iconArray;

    inflater = (LayoutInflater) activity.
        getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  public int getCount() {

    if (labels.length <= 0)
      return 1;
    return labels.length;
  }

  public Object getItem(int position) {
    return position;
  }

  public long getItemId(int position) {
    return position;
  }

  public static class ViewHolder {

    public TextView text;
    public ImageView image;
    private LinearLayout rootLayout;

  }

  @Override
  public boolean areAllItemsEnabled() {
    return false;
  }

  @Override
  public boolean isEnabled(int position) {
    if (position == menu_clear_passphrase) {
      if (!TextSecurePreferences.isPasswordDisabled(activity)) {
        return true;
      } else {
        return false;
      }
    }
    return super.isEnabled(position);
  }

  public View getView(int position, View convertView, ViewGroup parent) {

    View vi = convertView;
    ViewHolder holder;

    if (convertView == null) {
      vi = inflater.inflate(R.layout.drawer_list_item, null);
      holder = new ViewHolder();
      holder.text = (TextView) vi.findViewById(R.id.navDrawerLabel);
      holder.image = (ImageView) vi.findViewById(R.id.navDrawerIcon);
      holder.rootLayout = (LinearLayout) vi.findViewById(R.id.rootLayout);
      vi.setTag(holder);
    }
    holder = (ViewHolder) vi.getTag();
    if (labels.length <= 0) {
      holder.text.setText("No Data");
    } else {
      if (holder.text != null) {
        holder.rootLayout.setVisibility(View.VISIBLE);
        holder.text.setText(labels[position]);
        holder.text.setTextColor(Color.BLACK);
        holder.image.setImageResource(icons.getResourceId(position, -1));
        if (position == menu_privacy_hide) {
          if (new GDataPreferences(activity).isPrivacyActivated()) {
            holder.image.setImageResource(R.drawable.btn_check_on_disabled_holo_light);
          } else {
            holder.image.setImageResource(R.drawable.btn_check_off_disabled_holo_light);
          }
        }
        if (position == menu_clear_passphrase) {
          if (TextSecurePreferences.isPasswordDisabled(activity)) {
            holder.rootLayout.setVisibility(View.GONE);
          } else {
            holder.rootLayout.setVisibility(View.VISIBLE);
          }
        }
      }
    }
    return vi;
  }
}