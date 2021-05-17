package org.thoughtcrime.securesms.linkpreview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.util.IOUtils;

import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress;
import org.session.libsession.utilities.MediaTypes;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.session.libsignal.utilities.Log;
import org.thoughtcrime.securesms.net.CallRequestController;
import org.thoughtcrime.securesms.net.CompositeRequestController;
import org.thoughtcrime.securesms.net.ContentProxySafetyInterceptor;
import org.thoughtcrime.securesms.net.RequestController;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.session.libsignal.utilities.guava.Optional;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewUtil.OpenGraph;

import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.attachments.UriAttachment;
import org.session.libsession.messaging.sending_receiving.link_preview.LinkPreview;
import org.session.libsession.utilities.concurrent.SignalExecutors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LinkPreviewRepository implements InjectableType {

  private static final String TAG = LinkPreviewRepository.class.getSimpleName();

  private static final CacheControl NO_CACHE = new CacheControl.Builder().noCache().build();

  private final OkHttpClient client;

  public LinkPreviewRepository(@NonNull Context context) {
    this.client = new OkHttpClient.Builder()
                                  .addNetworkInterceptor(new ContentProxySafetyInterceptor())
                                  .cache(null)
                                  .build();

    ApplicationContext.getInstance(context).injectDependencies(this);
  }

  RequestController getLinkPreview(@NonNull Context context, @NonNull String url, @NonNull Callback<Optional<LinkPreview>> callback) {
    CompositeRequestController compositeController = new CompositeRequestController();

    if (!LinkPreviewUtil.isValidLinkUrl(url)) {
      Log.w(TAG, "Tried to get a link preview for a non-whitelisted domain.");
      callback.onComplete(Optional.absent());
      return compositeController;
    }

    RequestController metadataController;

    metadataController = fetchMetadata(url, metadata -> {
      if (metadata.isEmpty()) {
        callback.onComplete(Optional.absent());
        return;
      }

      if (!metadata.getImageUrl().isPresent()) {
        callback.onComplete(Optional.of(new LinkPreview(url, metadata.getTitle().get(), Optional.absent())));
        return;
      }

      RequestController imageController = fetchThumbnail(context, metadata.getImageUrl().get(), attachment -> {
        if (!metadata.getTitle().isPresent() && !attachment.isPresent()) {
          callback.onComplete(Optional.absent());
        } else {
          callback.onComplete(Optional.of(new LinkPreview(url, metadata.getTitle().or(""), attachment)));
        }
      });

      compositeController.addController(imageController);
    });

    compositeController.addController(metadataController);
    return compositeController;
  }

  private @NonNull RequestController fetchMetadata(@NonNull String url, Callback<Metadata> callback) {
    Call call = client.newCall(new Request.Builder().url(url).removeHeader("User-Agent").addHeader("User-Agent",
            "WhatsApp").cacheControl(NO_CACHE).build());

    call.enqueue(new okhttp3.Callback() {
      @Override
      public void onFailure(@NonNull Call call, @NonNull IOException e) {
        Log.w(TAG, "Request failed.", e);
        callback.onComplete(Metadata.empty());
      }

      @Override
      public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
        if (!response.isSuccessful()) {
          Log.w(TAG, "Non-successful response. Code: " + response.code());
          callback.onComplete(Metadata.empty());
          return;
        } else if (response.body() == null) {
          Log.w(TAG, "No response body.");
          callback.onComplete(Metadata.empty());
          return;
        }

        String            body     = response.body().string();
        OpenGraph        openGraph   = LinkPreviewUtil.parseOpenGraphFields(body);
        Optional<String> title       = openGraph.getTitle();
        Optional<String> imageUrl    = openGraph.getImageUrl();

        if (imageUrl.isPresent() && !LinkPreviewUtil.isValidMediaUrl(imageUrl.get())) {
          Log.i(TAG, "Image URL was invalid or for a non-whitelisted domain. Skipping.");
          imageUrl = Optional.absent();
        }

        if (imageUrl.isPresent() && !LinkPreviewUtil.isValidMimeType(imageUrl.get())) {
          Log.i(TAG, "Image URL was invalid mime type. Skipping.");
          imageUrl = Optional.absent();
        }

        callback.onComplete(new Metadata(title, imageUrl));
      }
    });

    return new CallRequestController(call);
  }

  private @NonNull RequestController fetchThumbnail(@NonNull Context context, @NonNull String imageUrl, @NonNull Callback<Optional<Attachment>> callback) {
    Call                  call       = client.newCall(new Request.Builder().url(imageUrl).build());
    CallRequestController controller = new CallRequestController(call);

    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        Response response = call.execute();
        if (!response.isSuccessful() || response.body() == null) {
          controller.cancel();
          callback.onComplete(Optional.absent());
          return;
        }

        InputStream bodyStream = response.body().byteStream();
        controller.setStream(bodyStream);

        byte[]               data      = IOUtils.readInputStreamFully(bodyStream);
        Bitmap               bitmap    = BitmapFactory.decodeByteArray(data, 0, data.length);
        Optional<Attachment> thumbnail = bitmapToAttachment(bitmap, Bitmap.CompressFormat.JPEG, MediaTypes.IMAGE_JPEG);

        if (bitmap != null) bitmap.recycle();

        callback.onComplete(thumbnail);
      } catch (IOException e) {
        Log.w(TAG, "Exception during link preview image retrieval.", e);
        controller.cancel();
        callback.onComplete(Optional.absent());
      }
    });

    return controller;
  }

  private static Optional<Attachment> bitmapToAttachment(@Nullable Bitmap bitmap,
                                                         @NonNull Bitmap.CompressFormat format,
                                                         @NonNull String contentType)
  {
    if (bitmap == null) {
      return Optional.absent();
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    bitmap.compress(format, 80, baos);

    byte[] bytes = baos.toByteArray();
    Uri    uri   = BlobProvider.getInstance().forData(bytes).createForSingleSessionInMemory();

    return  Optional.of(new UriAttachment(uri,
            uri,
            contentType,
            AttachmentTransferProgress.TRANSFER_PROGRESS_STARTED,
            bytes.length,
            bitmap.getWidth(),
            bitmap.getHeight(),
            null,
            null,
            false,
            false,
            null));

  }


  private static class Metadata {
    private final Optional<String> title;
    private final Optional<String> imageUrl;

    Metadata(Optional<String> title, Optional<String> imageUrl) {
      this.title    = title;
      this.imageUrl = imageUrl;
    }

    static Metadata empty() {
      return new Metadata(Optional.absent(), Optional.absent());
    }

    Optional<String> getTitle() {
      return title;
    }

    Optional<String> getImageUrl() {
      return imageUrl;
    }

    boolean isEmpty() {
      return !title.isPresent() && !imageUrl.isPresent();
    }
  }

  interface Callback<T> {
    void onComplete(@NonNull T result);
  }
}
