/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;

import java.util.ArrayList;
import java.util.List;

public class ProfileImageTypeSelectorAdapter extends ArrayAdapter<ProfileImageTypeSelectorAdapter.IconListItem> {

  public static final int ADD_IMAGE         = 1;
  public static final int TAKE_PHOTO        = 6;

  private final Context context;

  public ProfileImageTypeSelectorAdapter(Context context) {
    super(context, R.layout.icon_list_item, getItemList(context));
    this.context = context;
  }

  public int buttonToCommand(int position) {
    return getItem(position).getCommand();
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    View view;

    if (convertView == null) {
      LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      view = inflater.inflate(R.layout.icon_list_item, parent, false);
    } else {
      view = convertView;
    }

    TextView  text  = (TextView)  view.findViewById(R.id.text1);
    ImageView image = (ImageView) view.findViewById(R.id.icon);

    text.setText(getItem(position).getTitle());
    image.setImageResource(getItem(position).getResource());

    return view;
  }

  private static List<IconListItem> getItemList(Context context) {
    List<IconListItem> data = new ArrayList<>(2);
    addItem(data, context.getString(R.string.AttachmentTypeSelectorAdapter_picture), R.drawable.ic_attach_picture_holo_light, ADD_IMAGE);
    addItem(data, context.getString(R.string.take_picture), R.drawable.ic_attach_picture_holo_light, TAKE_PHOTO);

    return data;
  }

  private static void addItem(List<IconListItem> list, String text, int resource, int id) {
    list.add(new IconListItem(text, resource, id));
  }

  public static class IconListItem {
    private final String title;
    private final int    resource;
    private final int    id;

    public IconListItem(String title, int resource, int id) {
      this.resource = resource;
      this.title    = title;
      this.id       = id;
    }

    public int getCommand() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    public int getResource() {
      return resource;
    }
  }
}
