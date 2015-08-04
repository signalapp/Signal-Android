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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.util.BitmapDecodingException;

import java.io.File;
import java.io.IOException;

import ws.com.google.android.mms.ContentType;

public class AttachmentManager {
  private final static String TAG = AttachmentManager.class.getSimpleName();

  private final Context            context;
  private final View               attachmentView;
  private final ThumbnailView      thumbnail;
  private final Button             removeButton;
  private final SlideDeck          slideDeck;
  private final AttachmentListener attachmentListener;

  public static String random  = "0";

  private static File captureFile;

  public AttachmentManager(Activity view, AttachmentListener listener) {
    this.attachmentView     = view.findViewById(R.id.attachment_editor);
    this.thumbnail          = (ThumbnailView)view.findViewById(R.id.attachment_thumbnail);
    this.removeButton       = (Button)view.findViewById(R.id.remove_image_button);
    this.slideDeck          = new SlideDeck();
    this.context            = view;
    this.attachmentListener = listener;

    this.removeButton.setOnClickListener(new RemoveButtonListener());
  }

  public void clear() {
    slideDeck.clear();
    attachmentView.setVisibility(View.GONE);
    attachmentListener.onAttachmentChanged();
  }

public static void generateNewRandomOutputName() {
  random = ((int)(Math.random() * 30.0)) + "";
}
  public void cleanup() {
    if (captureFile != null) captureFile.delete();
    captureFile = null;
  }
  public void setMedia(final Slide slide) {
    slideDeck.clear();
    slideDeck.addSlide(slide);
    attachmentView.setVisibility(View.VISIBLE);
    thumbnail.setImageResource(slide);
    attachmentListener.onAttachmentChanged();
  }

  public boolean isAttachmentPresent() {
    return attachmentView.getVisibility() == View.VISIBLE;
  }

  public SlideDeck getSlideDeck() {
    return slideDeck;
  }

  public static File getCaptureFile() {
    return captureFile;
  }
  public static void selectVideo(Activity activity, int requestCode) {
    selectMediaType(activity, ContentType.VIDEO_UNSPECIFIED, requestCode);
  }

  public static void selectImage(Activity activity, int requestCode) {
    generateNewRandomOutputName();
    selectMediaType(activity, ContentType.IMAGE_UNSPECIFIED, requestCode);
  }
  public static void takePhoto(Activity activity, int requestCode) {
    generateNewRandomOutputName();
    File image = getOutputMediaFile();
    if(image != null) {
      Uri fileUri = Uri.fromFile(image);
      Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
      cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
      activity.startActivityForResult(cameraIntent, requestCode);
    }
  }
  public static void selectAudio(Activity activity, int requestCode) {
    selectMediaType(activity, ContentType.AUDIO_UNSPECIFIED, requestCode);
  }
  public static File getOutputMediaFile(){
    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), "SecureChat");
    if (!mediaStorageDir.exists()){
      if (!mediaStorageDir.mkdirs()){
        Log.d("SecureChat", "failed to create directory");
        return null;
      }
    }
    File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
              "prof_image"+ random +" .jpg");

    return mediaFile;
  }
  public static void selectContactInfo(Activity activity, int requestCode) {
    Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
    activity.startActivityForResult(intent, requestCode);
  }

  private static void selectMediaType(Activity activity, String type, int requestCode) {
    final Intent intent = new Intent();
    intent.setType(type);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
      try {
        activity.startActivityForResult(intent, requestCode);
        return;
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, "couldn't complete ACTION_OPEN_DOCUMENT, no activity found. falling back.");
      }
    }

    intent.setAction(Intent.ACTION_GET_CONTENT);
    try {
      activity.startActivityForResult(intent, requestCode);
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, "couldn't complete ACTION_GET_CONTENT intent, no activity found. falling back.");
      Toast.makeText(activity, R.string.AttachmentManager_cant_open_media_selection, Toast.LENGTH_LONG).show();
    }
  }
  public void setImage(Uri image) throws IOException, BitmapDecodingException {
    setMedia(new ImageSlide(context, image));
  }

  public void setVideo(Uri video, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    setMedia(new VideoSlide(context, video, sendOrReceive));
  }
  public void setVideo(Uri video, String contentType, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    setMedia(new VideoSlide(context, video, contentType, sendOrReceive));
  }
  public void setAudio(Uri audio, String contentType, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    setMedia(new AudioSlide(context, audio, contentType, sendOrReceive));
  }
  public void setAudio(Uri audio, boolean sendOrReceive) throws IOException, MediaTooLargeException {
    setMedia(new AudioSlide(context, audio, sendOrReceive));
  }
  private class RemoveButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      clear();
      cleanup();
    }
  }

  public interface AttachmentListener {
    public void onAttachmentChanged();
  }
}
