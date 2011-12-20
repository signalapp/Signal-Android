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

import java.util.ArrayList;
import java.util.List;

import org.thoughtcrime.securesms.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AttachmentTypeSelectorAdapter extends ArrayAdapter<AttachmentTypeSelectorAdapter.IconListItem> {

	public static final int ADD_IMAGE     = 1;
//	public static final int TAKE_PICTURE  = 2;
	public static final int ADD_VIDEO     = 3;
//	public static final int	RECORD_VIDEO  = 4;
	public static final int	ADD_SOUND	  = 5;
//	public static final int	RECORD_SOUND  = 6;
	
	private final Context context;
	
	public AttachmentTypeSelectorAdapter(Context context) {
		super(context, R.layout.icon_list_item, getItemList());
		this.context = context;
	}
	
	public int buttonToCommand(int position) {
		return getItem(position).getCommand();
	}
	
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView text;
        ImageView image;

        View view;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.icon_list_item, parent, false);
        } else {
            view = convertView;
        }

        text = (TextView) view.findViewById(R.id.text1);
        text.setText(getItem(position).getTitle());

        image = (ImageView) view.findViewById(R.id.icon);
        image.setImageResource(getItem(position).getResource());

        return view;
    }
	
	private static List<IconListItem> getItemList() {
        List<IconListItem> data = new ArrayList<IconListItem>(7);
        addItem(data, "Pictures", R.drawable.ic_launcher_gallery, ADD_IMAGE);
//        addItem(data, "Capture picture", R.drawable.ic_launcher_camera, TAKE_PICTURE);
        addItem(data, "Videos", R.drawable.ic_launcher_video_player, ADD_VIDEO);
//        addItem(data, "Capture video", R.drawable.ic_launcher_camera_record, RECORD_VIDEO);
        addItem(data, "Audio", R.drawable.ic_launcher_musicplayer_2, ADD_SOUND);
//        addItem(data, "Record audio", R.drawable.ic_launcher_record_audio, RECORD_SOUND);

        return data;
	}
	
	private static void addItem(List<IconListItem> list, String text, int resource, int id) {
		list.add(new IconListItem(text, resource, id));
	}
	
    public static class IconListItem {
        private final String mTitle;
        private final int mResource;
        private final int id;

        public IconListItem(String title, int resource, int id) {
            mResource = resource;
            mTitle = title;
            this.id = id;
        }

        public int getCommand() {
        	return id;
        }
        
        public String getTitle() {
            return mTitle;
        }

        public int getResource() {
            return mResource;
        }
    }

}
