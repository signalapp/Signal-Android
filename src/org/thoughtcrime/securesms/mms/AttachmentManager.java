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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.io.IOException;

public class AttachmentManager {
  private final static String TAG = AttachmentManager.class.getSimpleName();

  private final Context            context;
  private final View               attachmentView;
  private final ThumbnailView      thumbnail;
  private final AttachmentListener attachmentListener;

  private @NonNull  Optional<Slide> slide = Optional.absent();
  private @Nullable Uri             captureUri;

  public AttachmentManager(Activity view, AttachmentListener listener) {
    this.attachmentView     = ViewUtil.findById(view, R.id.attachment_editor);
    this.thumbnail          = ViewUtil.findById(view, R.id.attachment_thumbnail);
    this.context            = view;
    this.attachmentListener = listener;

    thumbnail.setRemoveClickListener(new RemoveButtonListener());
  }

  public void clear() {
    AlphaAnimation animation = new AlphaAnimation(1.0f, 0.0f);
    animation.setDuration(200);
    animation.setAnimationListener(new Animation.AnimationListener() {
      @Override
      public void onAnimationStart(Animation animation) {}

      @Override
      public void onAnimationRepeat(Animation animation) {}

      @Override
      public void onAnimationEnd(Animation animation) {
        slide = Optional.absent();
        thumbnail.clear();
        attachmentView.setVisibility(View.GONE);
        attachmentListener.onAttachmentChanged();
      }
    });

    attachmentView.startAnimation(animation);
  }

  public void cleanup() {
    if (captureUri != null) {
      PersistentBlobProvider.getInstance(context).delete(captureUri);
      captureUri = null;
      slide      = Optional.absent();
    } else {
      cleanupCurrentSlide();
    }
  }

  private void cleanupCurrentSlide() {
    if (getSlideUri() != null && PersistentBlobProvider.isAuthority(getSlideUri())) {
      PersistentBlobProvider.getInstance(context).delete(getSlideUri());
    }
    slide = Optional.absent();
  }

  public void setMedia(@NonNull final MasterSecret masterSecret,
                       @NonNull final Uri uri,
                       @NonNull final MediaType mediaType,
                       @NonNull final MediaConstraints constraints)
  {
    new AsyncTask<Void, Void, Slide>() {
      @Override
      protected void onPreExecute() {
        if (uri != captureUri) cleanup();
        else                   cleanupCurrentSlide();

        thumbnail.clear();
        thumbnail.showProgressSpinner();
        attachmentView.setVisibility(View.VISIBLE);
      }

      @Override
      protected @Nullable Slide doInBackground(Void... params) {
        long start = System.currentTimeMillis();
        try {
          final long  mediaSize = MediaUtil.getMediaSize(context, masterSecret, uri);
          final Slide slide     = mediaType.createSlide(context, uri, mediaSize);
          Log.w(TAG, "slide with size " + mediaSize + " took " + (System.currentTimeMillis() - start) + "ms");
          return slide;
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
          return null;
        }
      }

      @Override
      protected void onPostExecute(@Nullable final Slide slide) {
        if (slide == null) {
          attachmentView.setVisibility(View.GONE);
          Toast.makeText(context,
                         R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                         Toast.LENGTH_SHORT).show();
        } else if (!areConstraintsSatisfied(context, masterSecret, slide, constraints)) {
          attachmentView.setVisibility(View.GONE);
          Toast.makeText(context,
                         R.string.ConversationActivity_attachment_exceeds_size_limits,
                         Toast.LENGTH_SHORT).show();
        } else {
          AttachmentManager.this.slide = Optional.of(slide);
          attachmentView.setVisibility(View.VISIBLE);
          thumbnail.setImageResource(masterSecret, slide, false, true);
          attachmentListener.onAttachmentChanged();
        }
      }
    }.execute();
  }

  public boolean isAttachmentPresent() {
    return attachmentView.getVisibility() == View.VISIBLE;
  }

  public @NonNull SlideDeck buildSlideDeck() {
    SlideDeck deck = new SlideDeck();
    if (slide.isPresent()) deck.addSlide(slide.get());
    return deck;
  }

  public static void selectVideo(Activity activity, int requestCode) {
    selectMediaType(activity, "video/*", requestCode);
  }

  public static void selectImage(Activity activity, int requestCode) {
    selectMediaType(activity, "image/*", requestCode);
  }

  public static void selectAudio(Activity activity, int requestCode) {
    selectMediaType(activity, "audio/*", requestCode);
  }

  public static void selectContactInfo(Activity activity, int requestCode) {
    Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
    activity.startActivityForResult(intent, requestCode);
  }

  private @Nullable Uri getSlideUri() {
    return slide.isPresent() ? slide.get().getUri() : null;
  }

  public @Nullable Uri getCaptureUri() {
    return captureUri;
  }

  public void capturePhoto(Activity activity, Recipients recipients, int requestCode) {
    try {
      Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      if (captureIntent.resolveActivity(activity.getPackageManager()) != null) {
        captureUri = PersistentBlobProvider.getInstance(context).createForExternal(recipients);
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, captureUri);
        activity.startActivityForResult(captureIntent, requestCode);
      }
    } catch (IOException ioe) {
      Log.w(TAG, ioe);
    }
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

  private boolean areConstraintsSatisfied(final @NonNull  Context context,
                                          final @NonNull  MasterSecret masterSecret,
                                          final @Nullable Slide slide,
                                          final @NonNull  MediaConstraints constraints)
  {
   return slide == null                                                        ||
          constraints.isSatisfied(context, masterSecret, slide.asAttachment()) ||
          constraints.canResize(slide.asAttachment());
  }

  private class RemoveButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      cleanup();
      clear();
    }
  }

  public interface AttachmentListener {
    void onAttachmentChanged();
  }

  public enum MediaType {
    IMAGE, GIF, AUDIO, VIDEO;

    public @NonNull Slide createSlide(@NonNull Context context,
                                      @NonNull Uri     uri,
                                               long    dataSize)
        throws IOException
    {
      switch (this) {
      case IMAGE: return new ImageSlide(context, uri, dataSize);
      case GIF:   return new GifSlide(context, uri, dataSize);
      case AUDIO: return new AudioSlide(context, uri, dataSize);
      case VIDEO: return new VideoSlide(context, uri, dataSize);
      default:    throw  new AssertionError("unrecognized enum");
      }
    }
  }
}
