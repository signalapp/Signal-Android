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
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.util.BitmapDecodingException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GUtil;
import de.gdata.messaging.util.ProfileAccessor;
import ws.com.google.android.mms.ContentType;

public class AttachmentManager {
  private final static String TAG = AttachmentManager.class.getSimpleName();

  private static Context            context;
  private final View               attachmentView;
  private final ThumbnailView      thumbnail;
  private final ImageButton        removeButton;
  private final SlideDeck          slideDeck;
  private final AttachmentListener attachmentListener;

  private static File captureFile;

  public AttachmentManager(Activity view, AttachmentListener listener) {
    this.attachmentView     = view.findViewById(R.id.attachment_editor);
    this.thumbnail          = (ThumbnailView)view.findViewById(R.id.attachment_thumbnail);
    this.removeButton       = (ImageButton)view.findViewById(R.id.remove_image_button);
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

  public void cleanup() {
    if (captureFile != null) captureFile.delete();
    captureFile = null;
  }
  public void setMedia(final Slide slide) {
    slideDeck.clear();
    slideDeck.addSlide(slide);
    if(slide.hasVideo()) {
      ImageSlide videoThumbnail = copyUriToStorageAndGenerateImageSlide(slide.getUri());
      if(videoThumbnail != null) {
        slideDeck.addSlide(videoThumbnail);
      }
    }
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
    new GDataPreferences(activity).getNextImageIndicator();
    selectMediaType(activity, ContentType.IMAGE_UNSPECIFIED, requestCode);
  }
  public static void takePhoto(Activity activity, int requestCode) {
    new GDataPreferences(activity).getNextImageIndicator();
    File image = getOutputMediaFile(activity);
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
  public static File getOutputMediaFile(Context activity){
    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), "SecureChat");
    if (!mediaStorageDir.exists()){
      if (!mediaStorageDir.mkdirs()){
        Log.d("SecureChat", "failed to create directory");
        return null;
      }
    }
    File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
              "prof_image"+ new GDataPreferences(activity).getLastImageIndicator() +" .jpg");

    return mediaFile;
  }
  public static File getOutputMediaFileWithAddition(Context activity, String addition){

    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), "SecureChat");
    if (!mediaStorageDir.exists()){
      if (!mediaStorageDir.mkdirs()){
        Log.d("SecureChat", "failed to create directory");
        return null;
      }
    }
    File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
            "prof_image"+ new GDataPreferences(activity).getLastImageIndicator() +addition+" .jpg");

    return mediaFile;
  }
  public static File getOutputMediaVideo(Context activity){
    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), "SecureChat");
    if (!mediaStorageDir.exists()){
      if (!mediaStorageDir.mkdirs()){
        Log.d("SecureChat", "failed to create directory");
        return null;
      }
    }
    File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
            "sec_video"+ new GDataPreferences(activity).getLastImageIndicator() +" .mp4");

    return mediaFile;
  }
  public ImageSlide copyUriToStorageAndGenerateImageSlide(Uri uri) {
    ImageSlide chosenImage = null;
    if (uri != null) {
        OutputStream out;
        File f = AttachmentManager.getOutputMediaVideo(context);
        if (f.exists()) {
          f.delete();
        }
      try {
        out = new FileOutputStream(f);
        out.write(GUtil.readBytes(context, uri));
        out.close();

        FileOutputStream outImage = null;
        try {
          File image = AttachmentManager.getOutputMediaFile(context);
          outImage = new FileOutputStream(image);
          Bitmap thumb = ThumbnailUtils.createVideoThumbnail(f.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
          thumb.compress(Bitmap.CompressFormat.PNG, 100, outImage); // bmp is your Bitmap instance
          // PNG is a lossless format, the compression factor (100) is ignored
          chosenImage = new ImageSlide(context, Uri.fromFile(image));
        } catch (Exception e) {
          Log.d("GDATA","Warning " + e.getMessage());
        } finally {
          try {
            if (outImage != null) {
              outImage.close();
            }
          } catch (IOException e) {
            Log.d("GDATA", "Warning " + e.getMessage());
          }
        }
      } catch (FileNotFoundException e) {
        Log.d("GDATA", "Warning " + e.getMessage());
      } catch (IOException e) {
        Log.d("GDATA", "Warning " + e.getMessage());
      } catch (BitmapDecodingException e) {
        Log.d("GDATA", "Warning " + e.getMessage());
      }
    }
    return chosenImage;
  }
  public static void selectContactInfo(Activity activity, int requestCode) {
    Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
    activity.startActivityForResult(intent, requestCode);
  }

  private static void selectMediaType(Activity activity, String type, int requestCode) {
    Intent intent = new Intent();
    intent.setType(type);
    intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      if(type.contains("image")) {
        intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
      } else {
        intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
      }
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
