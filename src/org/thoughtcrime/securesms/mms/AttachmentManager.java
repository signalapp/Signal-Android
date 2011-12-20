/** 
 * Copyright (C) 2011 Whisper Systems
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.mms;

import java.io.IOException;

import org.thoughtcrime.securesms.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

public class AttachmentManager {

  private final Context context;
  private final View attachmentView;
  private final ImageView thumbnail;
  //	private final Button viewButton;
  //	private final Button editButton;
  private final Button removeButton;
  private final SlideDeck slideDeck;
	
  public AttachmentManager(Activity view) {
    this.attachmentView = (View)view.findViewById(R.id.attachment_editor);
    this.thumbnail      = (ImageView)view.findViewById(R.id.attachment_thumbnail);
    //		this.viewButton     = (Button)view.findViewById(R.id.view_image_button);
    //		this.editButton     = (Button)view.findViewById(R.id.replace_image_button);
    this.removeButton   = (Button)view.findViewById(R.id.remove_image_button);
    this.slideDeck      = new SlideDeck();
    this.context        = view;		
		
    this.removeButton.setOnClickListener(new RemoveButtonListener());
  }
	
  public void clear() {
    slideDeck.clear();
    attachmentView.setVisibility(View.GONE);
  }
	
  public void setImage(Uri image) throws IOException {
    Log.w("AttachmentManager" , "Setting image: "  + image);
    ImageSlide slide = new ImageSlide(context, image);
    slideDeck.addSlide(slide);
    thumbnail.setImageBitmap(slide.getThumbnail());
    attachmentView.setVisibility(View.VISIBLE);
  }
	
  public void setVideo(Uri video) throws IOException, MediaTooLargeException {
    VideoSlide slide = new VideoSlide(context, video);
    slideDeck.addSlide(slide);
    thumbnail.setImageBitmap(slide.getThumbnail());
    attachmentView.setVisibility(View.VISIBLE);
  }
	
  public void setAudio(Uri audio)throws IOException, MediaTooLargeException {
    AudioSlide slide = new AudioSlide(context, audio);
    slideDeck.addSlide(slide);
    thumbnail.setImageBitmap(slide.getThumbnail());
    attachmentView.setVisibility(View.VISIBLE);
  }
	

  //	public Bitmap constructThumbnailFromVideo(Uri uri) {
  //		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
  //	    try {
  //	    	retriever.setMode(MediaMetadataRetriever.MODE_CAPTURE_FRAME_ONLY);
  //	    	retriever.setDataSource(context, uri);
  //	    	return retriever.captureFrame();
  //	    } catch (RuntimeException re) {
  //	    	Log.w("AttachmentManager", re);
  //	    } finally {
  //	    	try {
  //	    		retriever.release();
  //	        } catch (RuntimeException ex) {
  //	        }
  //	    }
  //	    return null;
  //	}
	
  //	private void displayMessageSizeException() {
  //        AlertDialog.Builder builder = new AlertDialog.Builder(context);
  //        builder.setIcon(R.drawable.ic_sms_mms_not_delivered);
  //        builder.setTitle("Message size limit reached.");
  //        builder.setMessage("Sorry, this attachment is too large for your message.");
  //        builder.setPositiveButton("Ok", null);
  //        builder.show();
  //	}

  public boolean isAttachmentPresent() {
    return attachmentView.getVisibility() == View.VISIBLE;
  }

  public SlideDeck getSlideDeck() {
    return slideDeck;
  }
	
  //	public static void selectAudio(Activity activity, int requestCode) {
  //        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
  //        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
  //        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
  //        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM, false);
  //        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select audio");
  //        activity.startActivityForResult(intent, requestCode);
  //	}
	
  public static void selectVideo(Activity activity, int requestCode) {
    selectMediaType(activity, "video/*", requestCode);
  }
	
  public static void selectImage(Activity activity, int requestCode) {
    selectMediaType(activity, "image/*", requestCode);
  }
	
  public static void selectAudio(Activity activity, int requestCode) {
    selectMediaType(activity, "audio/*", requestCode);
  }
	
  private static void selectMediaType(Activity activity, String type, int requestCode) {
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    intent.setType(type);
    activity.startActivityForResult(intent, requestCode);
  }
	
  private class RemoveButtonListener implements View.OnClickListener {
    public void onClick(View v) {
      clear();
    }
  }

}
