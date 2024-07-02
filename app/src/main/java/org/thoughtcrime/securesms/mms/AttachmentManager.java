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
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.ThreadUtil;
import org.signal.core.util.concurrent.ListenableFuture;
import org.signal.core.util.concurrent.ListenableFuture.Listener;
import org.signal.core.util.concurrent.SettableFuture;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AudioView;
import org.thoughtcrime.securesms.components.DocumentView;
import org.thoughtcrime.securesms.components.RemovableEditableMediaView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.components.location.SignalMapView;
import org.thoughtcrime.securesms.components.location.SignalPlace;
import org.thoughtcrime.securesms.conversation.MessageSendType;
import org.thoughtcrime.securesms.database.MediaTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.giph.ui.GiphyActivity;
import org.thoughtcrime.securesms.maps.PlacePickerActivity;
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory;
import org.thoughtcrime.securesms.mediapreview.MediaPreviewCache;
import org.thoughtcrime.securesms.mediapreview.MediaPreviewV2Fragment;
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity;
import org.thoughtcrime.securesms.payments.CanNotSendPaymentDialog;
import org.thoughtcrime.securesms.payments.PaymentsAddressException;
import org.thoughtcrime.securesms.payments.create.CreatePaymentFragmentArgs;
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity;
import org.thoughtcrime.securesms.payments.preferences.RecipientHasNotEnabledPaymentsDialog;
import org.thoughtcrime.securesms.payments.preferences.model.PayeeParcelable;
import org.thoughtcrime.securesms.permissions.PermissionCompat;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.providers.DeprecatedPersistentBlobProvider;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ProfileUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.signalservice.api.util.ExpiringProfileCredentialUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;


public class AttachmentManager {

  private final static String TAG = Log.tag(AttachmentManager.class);

  private final @NonNull Context                    context;
  private final @NonNull Stub<View>                 attachmentViewStub;
  private final @NonNull AttachmentListener         attachmentListener;

  private RemovableEditableMediaView removableMediaView;
  private ThumbnailView              thumbnail;
  private AudioView                  audioView;
  private DocumentView               documentView;
  private SignalMapView              mapView;

  private @NonNull  List<Uri>       garbage = new LinkedList<>();
  private @NonNull  Optional<Slide> slide   = Optional.empty();
  private @Nullable Uri             captureUri;

  public AttachmentManager(@NonNull Context context, @NonNull View rootView, @NonNull AttachmentListener listener) {
    this.context            = context;
    this.attachmentListener = listener;
    this.attachmentViewStub = ViewUtil.findStubById(rootView, R.id.attachment_editor_stub);
  }

  private void inflateStub() {
    if (!attachmentViewStub.resolved()) {
      View root = attachmentViewStub.get();

      this.thumbnail          = root.findViewById(R.id.attachment_thumbnail);
      this.audioView          = root.findViewById(R.id.attachment_audio);
      this.documentView       = root.findViewById(R.id.attachment_document);
      this.mapView            = root.findViewById(R.id.attachment_location);
      this.removableMediaView = root.findViewById(R.id.removable_media_view);

      removableMediaView.setRemoveClickListener(new RemoveButtonListener());
      thumbnail.setOnClickListener(new ThumbnailClickListener());
      documentView.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.signal_background_secondary), PorterDuff.Mode.MULTIPLY);
    }

  }

  public void clear(@NonNull RequestManager requestManager, boolean animate) {
    if (attachmentViewStub.resolved()) {

      if (animate) {
        ViewUtil.fadeOut(attachmentViewStub.get(), 200).addListener(new Listener<Boolean>() {
          @Override
          public void onSuccess(Boolean result) {
            thumbnail.clear(requestManager);
            attachmentViewStub.get().setVisibility(View.GONE);
            attachmentListener.onAttachmentChanged();
          }

          @Override
          public void onFailure(ExecutionException e) {
          }
        });
      } else {
        thumbnail.clear(requestManager);
        attachmentViewStub.get().setVisibility(View.GONE);
        attachmentListener.onAttachmentChanged();
      }

      markGarbage(getSlideUri());
      slide = Optional.empty();
    }
  }

  public void cleanup() {
    cleanup(captureUri);
    cleanup(getSlideUri());

    captureUri = null;
    slide      = Optional.empty();

    Iterator<Uri> iterator = garbage.listIterator();

    while (iterator.hasNext()) {
      cleanup(iterator.next());
      iterator.remove();
    }
  }

  private void cleanup(final @Nullable Uri uri) {
    if (uri != null && DeprecatedPersistentBlobProvider.isAuthority(context, uri)) {
      Log.d(TAG, "cleaning up " + uri);
      DeprecatedPersistentBlobProvider.getInstance(context).delete(context, uri);
    } else if (uri != null && BlobProvider.isAuthority(uri)) {
      BlobProvider.getInstance().delete(context, uri);
    }
  }

  private void markGarbage(@Nullable Uri uri) {
    if (uri != null && (DeprecatedPersistentBlobProvider.isAuthority(context, uri) || BlobProvider.isAuthority(uri))) {
      Log.d(TAG, "Marking garbage that needs cleaning: " + uri);
      garbage.add(uri);
    }
  }

  private void setSlide(@NonNull Slide slide) {
    if (getSlideUri() != null) {
      cleanup(getSlideUri());
    }

    if (captureUri != null && !captureUri.equals(slide.getUri())) {
      cleanup(captureUri);
      captureUri = null;
    }

    this.slide = Optional.of(slide);
  }

  public ListenableFuture<Boolean> setLocation(@NonNull final SignalPlace place,
                                               @NonNull final MediaConstraints constraints)
  {
    inflateStub();

    SettableFuture<Boolean>  returnResult = new SettableFuture<>();
    ListenableFuture<Bitmap> future       = mapView.display(place);

    attachmentViewStub.get().setVisibility(View.VISIBLE);
    removableMediaView.display(mapView, false);

    future.addListener(new AssertedSuccessListener<Bitmap>() {
      @Override
      public void onSuccess(@NonNull Bitmap result) {
        byte[]        blob          = BitmapUtil.toByteArray(result);
        Uri           uri           = BlobProvider.getInstance()
                                                  .forData(blob)
                                                  .withMimeType(MediaUtil.IMAGE_JPEG)
                                                  .createForSingleSessionInMemory();
        LocationSlide locationSlide = new LocationSlide(context, uri, blob.length, place);

        ThreadUtil.runOnMain(() -> {
          setSlide(locationSlide);
          attachmentListener.onAttachmentChanged();
          returnResult.set(true);
        });
      }
    });

    return returnResult;
  }

  public void setLocation(@NonNull final SignalPlace place,
                          @NonNull final Uri thumbnailUri)
  {
    inflateStub();

    mapView.display(place);

    attachmentViewStub.get().setVisibility(View.VISIBLE);
    removableMediaView.display(mapView, false);
    LocationSlide locationSlide = new LocationSlide(context, thumbnailUri, BlobProvider.getFileSize(thumbnailUri), place);
    setSlide(locationSlide);
    attachmentListener.onAttachmentChanged();
  }

  public boolean isAttachmentPresent() {
    return attachmentViewStub.resolved() && attachmentViewStub.get().getVisibility() == View.VISIBLE;
  }

  public @NonNull SlideDeck buildSlideDeck() {
    SlideDeck deck = new SlideDeck();
    if (slide.isPresent()) deck.addSlide(slide.get());
    return deck;
  }

  public static void selectDocument(Fragment fragment, int requestCode) {
    selectMediaType(fragment, "*/*", null, requestCode);
  }

  public static void selectGallery(Fragment fragment, int requestCode, @NonNull Recipient recipient, @NonNull CharSequence body, @NonNull MessageSendType messageSendType, boolean hasQuote) {
    Permissions.with(fragment)
               .request(PermissionCompat.forImagesAndVideos())
               .ifNecessary()
               .withPermanentDenialDialog(fragment.getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio))
               .onAllGranted(() -> fragment.startActivityForResult(MediaSelectionActivity.gallery(fragment.requireContext(), messageSendType, Collections.emptyList(), recipient.getId(), body, hasQuote), requestCode))
               .execute();
  }

  public static void selectContactInfo(Fragment fragment, int requestCode) {
    Permissions.with(fragment)
               .request(Manifest.permission.READ_CONTACTS)
               .ifNecessary()
               .withPermanentDenialDialog(fragment.getString(R.string.AttachmentManager_signal_requires_contacts_permission_in_order_to_attach_contact_information))
               .onAllGranted(() -> {
                 Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                 fragment.startActivityForResult(intent, requestCode);
               })
               .execute();
  }

  public static void selectLocation(Fragment fragment, int requestCode, @ColorInt int chatColor) {
    if (Permissions.hasAny(fragment.requireContext(), Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {
      PlacePickerActivity.startActivityForResultAtCurrentLocation(fragment, requestCode, chatColor);
    } else {
      Permissions.with(fragment)
                 .request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                 .ifNecessary()
                 .withPermanentDenialDialog(fragment.getString(R.string.AttachmentManager_signal_requires_location_information_in_order_to_attach_a_location))
                 .onSomeGranted((permissions) -> PlacePickerActivity.startActivityForResultAtCurrentLocation(fragment, requestCode, chatColor))
                 .execute();
    }
  }

  public static void selectGif(Fragment fragment, int requestCode, RecipientId id, MessageSendType sendType, boolean isForMms, CharSequence textTrimmed) {
    Intent intent = new Intent(fragment.requireContext(), GiphyActivity.class);
    intent.putExtra(GiphyActivity.EXTRA_IS_MMS, isForMms);
    intent.putExtra(GiphyActivity.EXTRA_RECIPIENT_ID, id);
    intent.putExtra(GiphyActivity.EXTRA_TRANSPORT, sendType);
    intent.putExtra(GiphyActivity.EXTRA_TEXT, textTrimmed);
    fragment.startActivityForResult(intent, requestCode);
  }

  public static void selectPayment(@NonNull Fragment fragment, @NonNull Recipient recipient) {
    if (!ExpiringProfileCredentialUtil.isValid(recipient.getExpiringProfileKeyCredential())) {
      CanNotSendPaymentDialog.show(fragment.requireContext());
      return;
    }

    SimpleTask.run(fragment.getViewLifecycleOwner().getLifecycle(),
                   () -> {
                     try {
                       return ProfileUtil.getAddressForRecipient(recipient);
                     } catch (IOException | PaymentsAddressException e) {
                       Log.w(TAG, "Could not get address for recipient: ", e);
                       return null;
                     }
                   },
                   (address) -> {
                     if (address != null) {
                       Intent intent = new Intent(fragment.requireContext(), PaymentsActivity.class);
                       intent.putExtra(PaymentsActivity.EXTRA_PAYMENTS_STARTING_ACTION, R.id.action_directly_to_createPayment);
                       intent.putExtra(PaymentsActivity.EXTRA_STARTING_ARGUMENTS, new CreatePaymentFragmentArgs.Builder(new PayeeParcelable(recipient.getId())).setFinishOnConfirm(true).build().toBundle());
                       fragment.startActivity(intent);
                     } else if (RemoteConfig.paymentsRequestActivateFlow()) {
                       showRequestToActivatePayments(fragment.requireContext(), recipient);
                     } else {
                       RecipientHasNotEnabledPaymentsDialog.show(fragment.requireContext());
                     }
                   });
  }

  public static void showRequestToActivatePayments(@NonNull Context context, @NonNull Recipient recipient) {
    new MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.AttachmentManager__not_activated_payments, recipient.getShortDisplayName(context)))
        .setMessage(context.getString(R.string.AttachmentManager__request_to_activate_payments))
        .setPositiveButton(context.getString(R.string.AttachmentManager__send_request), (dialog, which) -> {
          OutgoingMessage outgoingMessage = OutgoingMessage.requestToActivatePaymentsMessage(recipient, System.currentTimeMillis(), 0);
          MessageSender.send(context, outgoingMessage, SignalDatabase.threads().getOrCreateThreadIdFor(recipient), MessageSender.SendType.SIGNAL, null, null);
        })
        .setNegativeButton(context.getString(R.string.AttachmentManager__cancel), null)
        .show();
  }

  private @Nullable Uri getSlideUri() {
    return slide.isPresent() ? slide.get().getUri() : null;
  }

  public @Nullable Uri getCaptureUri() {
    return captureUri;
  }

  private static void selectMediaType(Fragment fragment, @NonNull String type, @Nullable String[] extraMimeType, int requestCode) {
    final Intent intent = new Intent();
    intent.setType(type);

    if (extraMimeType != null) {
      intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeType);
    }

    intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
    try {
      fragment.startActivityForResult(intent, requestCode);
      return;
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, "couldn't complete ACTION_OPEN_DOCUMENT, no activity found. falling back.");
    }

    intent.setAction(Intent.ACTION_GET_CONTENT);

    try {
      fragment.startActivityForResult(intent, requestCode);
    } catch (ActivityNotFoundException anfe) {
      Log.w(TAG, "couldn't complete ACTION_GET_CONTENT intent, no activity found. falling back.");
      Toast.makeText(fragment.requireContext(), R.string.AttachmentManager_cant_open_media_selection, Toast.LENGTH_LONG).show();
    }
  }

  private void previewImageDraft(final @NonNull Slide slide) {
    if (MediaPreviewV2Fragment.isContentTypeSupported(slide.getContentType()) && slide.getUri() != null) {
      MediaIntentFactory.MediaPreviewArgs args = new MediaIntentFactory.MediaPreviewArgs(
          MediaIntentFactory.NOT_IN_A_THREAD,
          MediaIntentFactory.UNKNOWN_TIMESTAMP,
          slide.getUri(),
          slide.getContentType(),
          slide.asAttachment().size,
          slide.getCaption().orElse(null),
          false,
          false,
          false,
          false,
          MediaTable.Sorting.Newest,
          slide.isVideoGif(),
          new MediaIntentFactory.SharedElementArgs(),
          false);
      context.startActivity(MediaIntentFactory.create(context, args));
    }
  }

  private class ThumbnailClickListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      if (slide.isPresent()) {
        MediaPreviewCache.INSTANCE.setDrawable(((ThumbnailView) v).getImageDrawable());
        previewImageDraft(slide.get());
      }
    }
  }

  private class RemoveButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      slide.ifPresent(oldSlide -> {
        if (oldSlide instanceof LocationSlide) {
          attachmentListener.onLocationRemoved();
        }
      });

      cleanup();
      clear(Glide.with(context.getApplicationContext()), true);
    }
  }

  public interface AttachmentListener {
    void onAttachmentChanged();
    void onLocationRemoved();
  }

}
