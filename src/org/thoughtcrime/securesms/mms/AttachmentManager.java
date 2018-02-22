/*
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;

import org.thoughtcrime.securesms.MediaPreviewActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AudioView;
import org.thoughtcrime.securesms.components.DocumentView;
import org.thoughtcrime.securesms.components.RemovableEditableMediaView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.components.location.SignalMapView;
import org.thoughtcrime.securesms.components.location.SignalPlace;
import org.thoughtcrime.securesms.giph.ui.GiphyActivity;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.PersistentBlobProvider;
import org.thoughtcrime.securesms.scribbles.ScribbleActivity;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.views.Stub;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class AttachmentManager {

  private final static String TAG = AttachmentManager.class.getSimpleName();

  private final @NonNull Context                    context;
  private final @NonNull Stub<View>                 attachmentViewStub;
  private final @NonNull AttachmentListener         attachmentListener;
  private                RemovableEditableMediaView attachmentMapRemovableMediaView;
  private                SignalMapView              attachmentMapView;
  private                RecyclerView               attachmentRecyclerView;

  private @NonNull  List<Uri>   garbage = new LinkedList<>();
  private @Nullable List<Slide> slides;
  private @Nullable Uri         captureUri;

  public AttachmentManager(@NonNull Activity activity, @NonNull AttachmentListener listener) {
    this.context            = activity;
    this.attachmentListener = listener;
    this.attachmentViewStub = ViewUtil.findStubById(activity, R.id.attachment_editor_stub);
  }

  private void inflateStub() {
    if (!attachmentViewStub.resolved()) {
      View root = attachmentViewStub.get();

      attachmentMapRemovableMediaView = ViewUtil.findById(root, R.id.attachment_map_removable_media_view);
      attachmentMapView               = ViewUtil.findById(root, R.id.attachment_map);
      attachmentRecyclerView          = ViewUtil.findById(root, R.id.attachment_recycler_view);

      attachmentMapRemovableMediaView.setRemoveClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            clear(GlideApp.with(context.getApplicationContext()), true);
            cleanup();
          }
      });

      attachmentRecyclerView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
      attachmentRecyclerView.setItemAnimator(new DefaultItemAnimator());
    }
  }

  public void clear(@NonNull GlideRequests glideRequests, boolean clearAll) {
    if (!clearAll && slides != null && !slides.isEmpty()) {
      markGarbage(slides.get(0).getUri());
      slides.remove(0);
      if (attachmentViewStub.resolved() && attachmentRecyclerView.getAdapter() != null) {
        attachmentRecyclerView.getAdapter().notifyItemRemoved(0);
        attachmentRecyclerView.getAdapter().notifyItemRangeChanged(0, slides.size());
      }
    }

    if (clearAll || slides == null || slides.isEmpty()) {
      if (attachmentViewStub.resolved()) {
        if (attachmentRecyclerView.getAdapter() != null) {
          if (clearAll) {
            for (int i = 0; i < attachmentRecyclerView.getChildCount(); i++) {
              AttachmentAdapter.ViewHolder holder = (AttachmentAdapter.ViewHolder) attachmentRecyclerView
                      .getChildViewHolder(attachmentRecyclerView.getChildAt(i));
              holder.thumbnail.clear(glideRequests);
              holder.audioView.cleanup();
            }

            attachmentRecyclerView.setAdapter(null);
            attachmentRecyclerView.removeAllViewsInLayout();
          }
        }

        attachmentRecyclerView.setVisibility(View.GONE);
        attachmentMapRemovableMediaView.setVisibility(View.GONE);
        attachmentViewStub.get().setVisibility(View.GONE);

        attachmentListener.onAttachmentChanged();
      }

      markGarbage(getSlideUris());
      slides = null;
    }
  }

  private void cleanup(final @Nullable Uri uri) {
    if (uri != null && PersistentBlobProvider.isAuthority(context, uri)) {
      Log.w(TAG, "cleaning up " + uri);
      PersistentBlobProvider.getInstance(context).delete(context, uri);
    }
  }

  private void cleanup(final @Nullable Uri[] uris) {
    if (uris != null) {
      for (Uri uri : uris) {
        cleanup(uri);
      }
    }
  }

  public void cleanup() {
    if (slides == null || slides.isEmpty()) {
      cleanup(captureUri);
      slides = null;
      captureUri = null;
    }

    Iterator<Uri> iterator = garbage.listIterator();
    while (iterator.hasNext()) {
      cleanup(iterator.next());
      iterator.remove();
    }
  }

  private void markGarbage(@Nullable Uri uri) {
    if (uri != null && PersistentBlobProvider.isAuthority(context, uri)) {
      Log.w(TAG, "Marking garbage that needs cleaning: " + uri);
      garbage.add(uri);
    }
  }

  private void markGarbage(@Nullable Uri[] uris) {
    if (uris != null) {
      for (Uri uri : uris) {
        markGarbage(uri);
      }
    }
  }

  private void setSlides(@NonNull Slide[] slides) {
    if (getSlideUris() != null) cleanup(getSlideUris());
    if (captureUri != null) {
      boolean captureUriInSlides = false;
      for (Slide slide : slides) {
        if (captureUri.equals(slide.getUri())) {
          captureUriInSlides = true;
        }
      }

      if (!captureUriInSlides) {
        cleanup(captureUri);
      }
    }

    this.captureUri = null;
    this.slides = new LinkedList<>(Arrays.asList(slides));
  }

  public void setLocation(@NonNull final SignalPlace place,
                          @NonNull final MediaConstraints constraints)
  {
    inflateStub();

    ListenableFuture<Bitmap> future = attachmentMapView.display(place);

    attachmentViewStub.get().setVisibility(View.VISIBLE);
    attachmentRecyclerView.setVisibility(View.GONE);
    attachmentMapRemovableMediaView.setVisibility(View.VISIBLE);
    attachmentMapRemovableMediaView.display(attachmentMapView, false);

    future.addListener(new AssertedSuccessListener<Bitmap>() {
      @Override
      public void onSuccess(@NonNull Bitmap result) {
        byte[]        blob          = BitmapUtil.toByteArray(result);
        Uri           uri           = PersistentBlobProvider.getInstance(context)
                                                            .create(context, blob, MediaUtil.IMAGE_PNG, null);
        LocationSlide locationSlide = new LocationSlide(context, uri, blob.length, place);

        setSlides(new Slide[]{locationSlide});
        attachmentListener.onAttachmentChanged();
      }
    });
  }

  @SuppressLint("StaticFieldLeak")
  public void setMedia(@NonNull final GlideRequests glideRequests,
                       @NonNull final Uri[] uris,
                       @NonNull final MediaType[] mediaTypes,
                       @NonNull final MediaConstraints constraints)
  {
    inflateStub();

    new AsyncTask<Void, Void, Slide[]>() {
      @Override
      protected void onPreExecute() {
          attachmentViewStub.get().setVisibility(View.VISIBLE);
          attachmentMapView.setVisibility(View.GONE);
          attachmentRecyclerView.setVisibility(View.VISIBLE);
      }

      @Override
      protected @Nullable Slide[] doInBackground(Void... params) {
        Slide[] result = new Slide[uris.length];
        for (int i = 0; i < uris.length; i++) {
          try {
            if (PartAuthority.isLocalUri(uris[i])) {
              result[i] = getManuallyCalculatedSlideInfo(uris[i], mediaTypes[i]);
            } else {
              result[i] = getContentResolverSlideInfo(uris[i], mediaTypes[i]);

              if (result[i] == null) {
                result[i] = getManuallyCalculatedSlideInfo(uris[i], mediaTypes[i]);
              }
            }
          } catch (IOException e) {
            Log.w(TAG, e);
            return null;
          }
        }

        return result;
      }

      @Override
      protected void onPostExecute(@Nullable final Slide[] result) {
        if (result == null) {
          attachmentViewStub.get().setVisibility(View.GONE);
          Toast.makeText(context,
                         R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                         Toast.LENGTH_SHORT).show();
        } else if (!areConstraintsSatisfied(context, result, constraints)) {
          attachmentViewStub.get().setVisibility(View.GONE);
          Toast.makeText(context,
                         R.string.ConversationActivity_attachment_exceeds_size_limits,
                         Toast.LENGTH_SHORT).show();
        } else {
          setSlides(result);

          attachmentRecyclerView.setAdapter(new AttachmentAdapter(slides, glideRequests));
          attachmentListener.onAttachmentChanged();
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @SuppressLint("StaticFieldLeak")
  public void updateMedia(@NonNull final GlideRequests glideRequests,
                          @NonNull final Uri uri,
                          @NonNull final MediaType mediaType,
                          @NonNull final MediaConstraints constraints,
                          final int datasetPosition) {
    if (!attachmentViewStub.resolved() ||
        slides == null ||
        attachmentRecyclerView.getAdapter() == null) {
      setMedia(glideRequests, new Uri[]{uri}, new MediaType[]{mediaType}, constraints);
    } else {
      new AsyncTask<Void, Void, Slide>() {
        @Override
        protected void onPreExecute() {
          attachmentViewStub.get().setVisibility(View.VISIBLE);
          attachmentMapView.setVisibility(View.GONE);
          attachmentRecyclerView.setVisibility(View.VISIBLE);
        }

        @Override
        protected @Nullable Slide doInBackground(Void... params) {
          Slide result;
          try {
            if (PartAuthority.isLocalUri(uri)) {
              result = getManuallyCalculatedSlideInfo(uri, mediaType);
            } else {
              result = getContentResolverSlideInfo(uri, mediaType);

              if (result == null) {
                result = getManuallyCalculatedSlideInfo(uri, mediaType);
              }
            }
          } catch (IOException e) {
            Log.w(TAG, e);
            return null;
          }

          return result;
        }

        @Override
        protected void onPostExecute(@Nullable final Slide result) {
          if (result == null) {
            attachmentViewStub.get().setVisibility(View.GONE);
            Toast.makeText(context,
                    R.string.ConversationActivity_sorry_there_was_an_error_setting_your_attachment,
                    Toast.LENGTH_SHORT).show();
          } else if (!areConstraintsSatisfied(context, new Slide[]{result}, constraints)) {
            attachmentViewStub.get().setVisibility(View.GONE);
            Toast.makeText(context,
                    R.string.ConversationActivity_attachment_exceeds_size_limits,
                    Toast.LENGTH_SHORT).show();
          } else {
            AttachmentAdapter.ViewHolder holder =
                    (AttachmentAdapter.ViewHolder) attachmentRecyclerView.findViewHolderForAdapterPosition(datasetPosition);

            holder.audioView.cleanup();
            holder.thumbnail.clear(glideRequests);
            cleanup(slides.get(datasetPosition).getUri());
            slides.set(datasetPosition, result);

            attachmentRecyclerView.getAdapter().notifyItemChanged(datasetPosition);
          }
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private @Nullable Slide getContentResolverSlideInfo(Uri uri, MediaType mediaType) {
    Cursor cursor = null;
    long   start  = System.currentTimeMillis();

    try {
      cursor = context.getContentResolver().query(uri, null, null, null, null);

      if (cursor != null && cursor.moveToFirst()) {
        String fileName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        long   fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
        String mimeType = context.getContentResolver().getType(uri);

        Log.w(TAG, "remote slide with size " + fileSize + " took " + (System.currentTimeMillis() - start) + "ms");
        return mediaType.createSlide(context, uri, fileName, mimeType, fileSize);
      }
    } finally {
      if (cursor != null) cursor.close();
    }

    return null;
  }

  private @NonNull Slide getManuallyCalculatedSlideInfo(Uri uri, MediaType mediaType) throws IOException {
    long start      = System.currentTimeMillis();
    Long mediaSize  = null;
    String fileName = null;
    String mimeType = null;

    if (PartAuthority.isLocalUri(uri)) {
      mediaSize = PartAuthority.getAttachmentSize(context, uri);
      fileName  = PartAuthority.getAttachmentFileName(context, uri);
      mimeType  = PartAuthority.getAttachmentContentType(context, uri);
    }

    if (mediaSize == null) {
      mediaSize = MediaUtil.getMediaSize(context, uri);
    }

    Log.w(TAG, "local slide with size " + mediaSize + " took " + (System.currentTimeMillis() - start) + "ms");
    return mediaType.createSlide(context, uri, fileName, mimeType, mediaSize);
  }

  public boolean isAttachmentPresent() {
    return attachmentViewStub.resolved() && attachmentViewStub.get().getVisibility() == View.VISIBLE;
  }

  public @NonNull SlideDeck buildSlideDeck() {
    SlideDeck deck = new SlideDeck();
    if (slides != null) {
      for (Slide slide : slides) {
        deck.addSlide(slide);
      }
    }
    return deck;
  }

  public static void selectDocument(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
               .onAllGranted(() -> selectMediaType(activity, "*/*", null, requestCode))
               .execute();
  }

  public static void selectGallery(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
               .onAllGranted(() -> selectMediaType(activity, "image/*", new String[] {"image/*", "video/*"}, requestCode))
               .execute();
  }

  public static void selectAudio(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
               .onAllGranted(() -> selectMediaType(activity, "audio/*", null, requestCode))
               .execute();
  }

  public static void selectContactInfo(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.WRITE_CONTACTS)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_signal_requires_contacts_permission_in_order_to_attach_contact_information))
               .onAllGranted(() -> {
                 Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                 activity.startActivityForResult(intent, requestCode);
               })
               .execute();
  }

  public static void selectLocation(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_signal_requires_location_information_in_order_to_attach_a_location))
               .onAllGranted(() -> {
                 try {
                   activity.startActivityForResult(new PlacePicker.IntentBuilder().build(activity), requestCode);
                 } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
                   Log.w(TAG, e);
                 }
               })
               .execute();
  }

  public static void selectGif(Activity activity, int requestCode, boolean isForMms) {
    Intent intent = new Intent(activity, GiphyActivity.class);
    intent.putExtra(GiphyActivity.EXTRA_IS_MMS, isForMms);
    activity.startActivityForResult(intent, requestCode);
  }

  private @Nullable Uri[] getSlideUris() {
    if (slides == null || slides.isEmpty()) return null;

    Uri[] result = new Uri[slides.size()];
    int i = 0;
    for (Slide slide : slides) {
      result[i++] = slide.getUri();
    }

    return result;
  }

  public @Nullable Uri getCaptureUri() {
    return captureUri;
  }

  public void capturePhoto(Activity activity, int requestCode) {
    Permissions.with(activity)
               .request(Manifest.permission.CAMERA)
               .ifNecessary()
               .withPermanentDenialDialog(activity.getString(R.string.AttachmentManager_signal_requires_the_camera_permission_in_order_to_take_photos_but_it_has_been_permanently_denied))
               .onAllGranted(() -> {
                 try {
                   Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                   if (captureIntent.resolveActivity(activity.getPackageManager()) != null) {
                     if (captureUri == null) {
                       captureUri = PersistentBlobProvider.getInstance(context).createForExternal(context, MediaUtil.IMAGE_JPEG);
                     }
                     Log.w(TAG, "captureUri path is " + captureUri.getPath());
                     captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, captureUri);
                     activity.startActivityForResult(captureIntent, requestCode);
                   }
                 } catch (IOException ioe) {
                   Log.w(TAG, ioe);
                 }
               })
               .execute();
  }

  private static void selectMediaType(Activity activity, @NonNull String type, @Nullable String[] extraMimeType, int requestCode) {
    final Intent intent = new Intent();
    intent.setType(type);

    if (extraMimeType != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeType);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    }

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
                                          final @Nullable Slide[] slides,
                                          final @NonNull  MediaConstraints constraints)
  {
    if (slides == null) return true;

    for (Slide slide : slides) {
      if (slide != null                                               &&
              !constraints.isSatisfied(context, slide.asAttachment()) &&
              !constraints.canResize(slide.asAttachment())) {
        return false;
      }
    }

    return true;
  }

  private void previewImageDraft(final @NonNull Slide slide) {
    if (MediaPreviewActivity.isContentTypeSupported(slide.getContentType()) && slide.getUri() != null) {
      Intent intent = new Intent(context, MediaPreviewActivity.class);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.putExtra(MediaPreviewActivity.SIZE_EXTRA, slide.asAttachment().getSize());
      intent.putExtra(MediaPreviewActivity.OUTGOING_EXTRA, true);
      intent.setDataAndType(slide.getUri(), slide.getContentType());

      context.startActivity(intent);
    }
  }

  public interface AttachmentListener {
    void onAttachmentChanged();
  }

  public enum MediaType {
    IMAGE, GIF, AUDIO, VIDEO, DOCUMENT;

    public @NonNull
    Slide createSlide(@NonNull Context context,
                      @NonNull Uri uri,
                      @Nullable String fileName,
                      @Nullable String mimeType,
                      long dataSize) {
      if (mimeType == null) {
        mimeType = "application/octet-stream";
      }

      switch (this) {
        case IMAGE:
          return new ImageSlide(context, uri, dataSize);
        case GIF:
          return new GifSlide(context, uri, dataSize);
        case AUDIO:
          return new AudioSlide(context, uri, dataSize, false);
        case VIDEO:
          return new VideoSlide(context, uri, dataSize);
        case DOCUMENT:
          return new DocumentSlide(context, uri, mimeType, dataSize, fileName);
        default:
          throw new AssertionError("unrecognized enum");
      }
    }

    public static @Nullable
    MediaType from(final @Nullable String mimeType) {
      if (TextUtils.isEmpty(mimeType)) return null;
      if (MediaUtil.isGif(mimeType)) return GIF;
      if (MediaUtil.isImageType(mimeType)) return IMAGE;
      if (MediaUtil.isAudioType(mimeType)) return AUDIO;
      if (MediaUtil.isVideoType(mimeType)) return VIDEO;

      return DOCUMENT;
    }
  }

  public class AttachmentAdapter extends RecyclerView.Adapter<AttachmentAdapter.ViewHolder> {
    private @NonNull final List<Slide> slides;
    private @NonNull final GlideRequests glideRequests;

    public AttachmentAdapter(@NonNull List<Slide> slides,
                             @NonNull GlideRequests glideRequests) {
      super();
      this.slides = slides;
      this.glideRequests = glideRequests;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(parent.getContext())
              .inflate(R.layout.removable_editable_media_view, parent, false);
      return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
      // TODO: check if this is necessary
      holder.thumbnail.clear(glideRequests);
      holder.thumbnail.showProgressSpinner();

      holder.thumbnail.setOnClickListener(new ThumbnailClickListener(position));
      holder.removableMediaView.setRemoveClickListener(new RemoveButtonListener(position));
      holder.removableMediaView.setEditClickListener(new EditButtonListener(position));

      if (slides.get(position).hasAudio()) {
        holder.audioView.setAudio((AudioSlide) slides.get(position), false);
        holder.removableMediaView.display(holder.audioView, false);
      } else if (slides.get(position).hasDocument()) {
        holder.documentView.setDocument((DocumentSlide) slides.get(position), false);
        holder.removableMediaView.display(holder.documentView, false);
      } else {
        holder.thumbnail.setImageResource(glideRequests, slides.get(position), false, true);
        holder.removableMediaView.display(holder.thumbnail, slides.get(position).hasImage());
      }
    }

    @Override
    public int getItemCount() {
      return slides.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
      private RemovableEditableMediaView removableMediaView;
      private ThumbnailView              thumbnail;
      private AudioView                  audioView;
      private DocumentView               documentView;

      public ViewHolder(View view) {
        super(view);
        this.removableMediaView = (RemovableEditableMediaView) view;
        this.thumbnail          = ViewUtil.findById(view, R.id.attachment_thumbnail);
        this.audioView          = ViewUtil.findById(view, R.id.attachment_audio);
        this.documentView       = ViewUtil.findById(view, R.id.attachment_document);
      }
    }

    private class ThumbnailClickListener implements View.OnClickListener {
      private int position;

      public ThumbnailClickListener(int position) {
        this.position = position;
      }

      @Override
      public void onClick(View v) {
        previewImageDraft(slides.get(position));
      }
    }

    private class RemoveButtonListener implements View.OnClickListener {
      private int position;

      public RemoveButtonListener(int position) {
          this.position = position;
      }

      @Override
      public void onClick(View v) {
        ViewHolder holder =
                (ViewHolder) attachmentRecyclerView.findViewHolderForAdapterPosition(position);

        holder.audioView.cleanup();
        holder.thumbnail.clear(glideRequests);
        cleanup(slides.get(position).getUri());

        slides.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, slides.size());

        if (slides.isEmpty()) {
          clear(glideRequests, true);
          cleanup();
        }
      }
    }

    private class EditButtonListener implements View.OnClickListener {
      private int position;

      public EditButtonListener(int position) {
          this.position = position;
      }

      @Override
      public void onClick(View v) {
        Intent intent = new Intent(context, ScribbleActivity.class);
        intent.setData(slides.get(position).getUri());
        intent.putExtra(ScribbleActivity.EXTRA_DATASET_POSITION, position);
        ((Activity)context).startActivityForResult(intent, ScribbleActivity.SCRIBBLE_REQUEST_CODE);
      }
    }
  }
}
