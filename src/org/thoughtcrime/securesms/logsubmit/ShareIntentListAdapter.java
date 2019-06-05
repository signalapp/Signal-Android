/*
 * *
 *  Copyright (C) 2014 Open Whisper Systems
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 * /
 */

package org.thoughtcrime.securesms.logsubmit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

import java.util.List;

/**
 * rhodey
 */
public class ShareIntentListAdapter extends ArrayAdapter<ResolveInfo> {

  public static ShareIntentListAdapter getAdapterForIntent(Context context, Intent shareIntent) {
    List<ResolveInfo> activities = context.getPackageManager().queryIntentActivities(shareIntent, 0);
    return new ShareIntentListAdapter(context, activities.toArray(new ResolveInfo[activities.size()]));
  }

  public ShareIntentListAdapter(Context context, ResolveInfo[] items) {
    super(context, R.layout.share_intent_list, items);
  }

  @Override
  public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
    LayoutInflater  inflater    = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View            rowView     = inflater.inflate(R.layout.share_intent_row, parent, false);
    ImageView       intentImage = (ImageView) rowView.findViewById(R.id.share_intent_image);
    TextView        intentLabel = (TextView)  rowView.findViewById(R.id.share_intent_label);

    ApplicationInfo intentInfo = getItem(position).activityInfo.applicationInfo;

    intentImage.setImageDrawable(intentInfo.loadIcon(getContext().getPackageManager()));
    intentLabel.setText(intentInfo.loadLabel(getContext().getPackageManager()));

    return rowView;
  }

}