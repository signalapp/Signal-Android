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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.ui.PlacePicker;

import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RemovableMediaView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.components.location.SignalMapView;
import org.thoughtcrime.securesms.components.location.SignalPlace;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.util.BitmapDecodingException;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import de.gdata.messaging.util.GDataPreferences;
import de.gdata.messaging.util.GUtil;
import ws.com.google.android.mms.ContentType;

public class AttachmentManager {
  private final static String TAG = AttachmentManager.class.getSimpleName();

  public static final int MEDIA_TYPE_VIDEO = 23;
  public static final int MEDIA_TYPE_IMAGE = 24;
  private static final String MEDIA_FILE_NAME = "media_file_";
  private static final String HIDDEN_FOLDER = ".SecureChat";

  private static Activity            context;
  private final View               attachmentView;
  private final ThumbnailView      thumbnail;
  private final ImageButton        removeButton;
  private final SlideDeck          slideDeck;
  private final AttachmentListener attachmentListener;
  private final RemovableMediaView removableMediaView;
  private final SignalMapView mapView;

  private @NonNull
  Optional<Slide> slide   = Optional.absent();
  private @Nullable Uri             captureUri;

  private int widthBefore = 0;

  private static File captureFile;

  public AttachmentManager(Activity view, AttachmentListener listener) {
    this.attachmentView     = view.findViewById(R.id.attachment_editor);
    this.thumbnail          = (ThumbnailView)view.findViewById(R.id.attachment_thumbnail);
    this.removeButton       = (ImageButton)view.findViewById(R.id.cancel_image_button);
    this.slideDeck          = new SlideDeck();
    this.context            = view;
    this.attachmentListener = listener;

    this.removeButton.setOnClickListener(new RemoveButtonListener());

    this.mapView = (SignalMapView) view.findViewById(R.id.attachment_location);
    this.removableMediaView = (RemovableMediaView) view.findViewById(R.id.removable_media_view);
  }

  public void clear() {
    slideDeck.clear();
    thumbnail.setVisibility(View.GONE);
    attachmentView.setVisibility(View.GONE);
    removableMediaView.setVisibility(View.GONE);
    attachmentListener.onAttachmentChanged();
  }

  public void cleanup() {
    if (captureFile != null) captureFile.delete();
    captureFile = null;
  }

  private void cleanup(final @Nullable Uri uri) {
    if (uri != null && PersistentBlobProvider.isAuthority(context, uri)) {
      Log.w(TAG, "cleaning up " + uri);
      PersistentBlobProvider.getInstance(context).delete(uri);
    }
  }

  private @Nullable
  Uri getSlideUri() {
    return slide.isPresent() ? slide.get().getUri() : null;
  }

  private void setSlide(@NonNull Slide slide) {
    if (getSlideUri() != null)                                    cleanup(getSlideUri());
    if (captureUri != null && !captureUri.equals(slide.getUri())) cleanup(captureUri);

    this.captureUri = null;
    this.slide      = Optional.of(slide);
  }

  public Uri getImageUri(Context inContext, Bitmap inImage) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
    String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
    return Uri.parse(path);
  }
  public void setLocation(@NonNull final MasterSecret masterSecret, @NonNull final SignalPlace place)
  {
    clear();
    attachmentView.findViewById(R.id.triangle_tick).setVisibility(View.GONE);

    ListenableFuture<Bitmap> future = mapView.display(place);

    attachmentView.setVisibility(View.VISIBLE);
    removableMediaView.setVisibility(View.VISIBLE);
    removableMediaView.display(mapView);


    future.addListener(new AssertedSuccessListener<Bitmap>() {
      @Override
      public void onSuccess(@NonNull Bitmap result) {
        byte[]        blob          = BitmapUtil.toByteArray(result);
        Uri           uri           = PersistentBlobProvider.getInstance(context)
                .create(masterSecret, blob, ContentType.IMAGE_PNG);
        LocationSlide locationSlide = null;
        try {
          locationSlide = new LocationSlide(context, uri, blob.length, place);





          //clear();
          slideDeck.clear();
          slideDeck.addSlide(new ImageSlide(context, getImageUri(context,result)));

          //setImage(getImageUri(context,result));

          /*
          ImageSlide mapThumbnail = copyUriToStorageAndGenerateImageSlide();
          if(mapThumbnail != null) {
            slideDeck.addSlide(mapThumbnail);
          }
          */
          //slideDeck.clear();
          //slideDeck.addSlide(locationSlide);




        } catch (IOException e) {
          e.printStackTrace();
        } catch (BitmapDecodingException e) {
          e.printStackTrace();
        }

        setSlide(locationSlide);
        attachmentListener.onAttachmentChanged();
      }
    });
  }
  public void setMedia(final Slide slide) {
    clear();
    slideDeck.clear();
    slideDeck.addSlide(slide);
    if(slide.hasVideo()) {
      ImageSlide videoThumbnail = copyUriToStorageAndGenerateImageSlide(slide.getUri());
      if(videoThumbnail != null) {
        slideDeck.addSlide(videoThumbnail);
      }
    }
    attachmentView.setVisibility(View.VISIBLE);
    thumbnail.setVisibility(View.VISIBLE);
    thumbnail.setImageResource(slide);
    if(slide instanceof AudioSlide) {
      widthBefore =  widthBefore < thumbnail.getLayoutParams().height ? thumbnail.getLayoutParams().height : widthBefore;
      attachmentView.getLayoutParams().height = 100;
      thumbnail.getLayoutParams().height = 150;
      thumbnail.getLayoutParams().width = 150;
      thumbnail.setBackgroundResource(R.drawable.ic_settings_voice_black);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        thumbnail.setImageAlpha(0);
        attachmentView.findViewById(R.id.triangle_tick).setVisibility(View.INVISIBLE);
      }
    } else {
      if(widthBefore != 0) {
        attachmentView.getLayoutParams().height = 2;
        thumbnail.getLayoutParams().height = widthBefore;
        thumbnail.getLayoutParams().width = widthBefore;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
          thumbnail.setImageAlpha(255);
        }
        thumbnail.setBackgroundResource(R.drawable.gdata_conversation_item_sent_shape);
        attachmentView.findViewById(R.id.triangle_tick).setVisibility(View.VISIBLE);
      }
    }
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
    new GDataPreferences(activity).getNextImageIndicator();
    selectMediaType(activity, ContentType.VIDEO_UNSPECIFIED, requestCode);
  }

  public static void selectImage(Activity activity, int requestCode) {
    new GDataPreferences(activity).getNextImageIndicator();
    selectMediaType(activity, ContentType.IMAGE_UNSPECIFIED, requestCode);
  }
  public static void takePhoto(Activity activity, int requestCode) {
    new GDataPreferences(activity).getNextImageIndicator();
    File image = getOutputMediaFile(activity, MEDIA_TYPE_IMAGE);
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
  public static File getOutputMediaFile(Context activity, int type){
    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), HIDDEN_FOLDER);
    if (!mediaStorageDir.exists()){
      if (!mediaStorageDir.mkdirs()){
        Log.d("SecureChat", "failed to create directory");
        return null;
      }
    }
    String end = ".jpg";
    if(type == MEDIA_TYPE_VIDEO) {
      end = ".mp4";
    } else if(type == MEDIA_TYPE_IMAGE) {
      end = ".jpg";
    }
    File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
              MEDIA_FILE_NAME + new GDataPreferences(activity).getLastImageIndicator() + end);

    return mediaFile;
  }
  public static File getOutputMediaFileWithAddition(Context activity, String addition){

    File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES), HIDDEN_FOLDER);
    if (!mediaStorageDir.exists()){
      if (!mediaStorageDir.mkdirs()){
        Log.d("SecureChat", "failed to create directory");
        return null;
      }
    }
    File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
            MEDIA_FILE_NAME + new GDataPreferences(activity).getLastImageIndicator() +addition+" .jpg");

    return mediaFile;
  }

  public static void selectLocation(Activity activity, int requestCode) {
    try {
      activity.startActivityForResult(new PlacePicker.IntentBuilder().build(activity), requestCode);
    } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
      Log.w(TAG, e);
    }
  }


  public ImageSlide copyUriToStorageAndGenerateImageSlide(Uri uri) {
    ImageSlide chosenImage = null;
    if (uri != null) {
        OutputStream out;
        File f = AttachmentManager.getOutputMediaFile(context, MEDIA_TYPE_VIDEO);
        if (!f.exists()) {
          try {
            out = new FileOutputStream(f);
            out.write(GUtil.readBytes(context, uri));
            out.close();
          } catch (IOException e) {
            Log.d("GDATA", "Warning " + e.getMessage());
          }
        }
        FileOutputStream outImage = null;
        try {
          File image = AttachmentManager.getOutputMediaFile(context, MEDIA_TYPE_IMAGE);
          outImage = new FileOutputStream(image);
          Bitmap thumb = ThumbnailUtils.createVideoThumbnail(f.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
          thumb.compress(Bitmap.CompressFormat.PNG, 100, outImage); // bmp is your Bitmap instance
          // PNG is a lossless format, the compression factor (100) is ignored
          try {
            chosenImage = new ImageSlide(context, Uri.fromFile(image));
          } catch (BitmapDecodingException e) {
            Log.d("GDATA", "Warning " + e.getMessage());
          }
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
      Intent intent = new Intent(ConversationActivity.CLEAR_COMPOSE_ACTION);
      context.sendBroadcast(intent);
    }
  }

  public interface AttachmentListener {
    public void onAttachmentChanged();
  }
}
