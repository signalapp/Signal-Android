package org.thoughtcrime.securesms.linkpreview;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.Html;
import android.text.TextUtils;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.FutureTarget;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.UriAttachment;
import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.giph.model.ChunkedImageUrl;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.net.CallRequestController;
import org.thoughtcrime.securesms.net.CompositeRequestController;
import org.thoughtcrime.securesms.net.ContentProxySafetyInterceptor;
import org.thoughtcrime.securesms.net.ContentProxySelector;
import org.thoughtcrime.securesms.net.RequestController;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.stickers.StickerRemoteUri;
import org.thoughtcrime.securesms.stickers.StickerUrl;
import org.thoughtcrime.securesms.util.Hex;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifest;
import org.whispersystems.signalservice.api.messages.SignalServiceStickerManifest.StickerInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  @Inject SignalServiceMessageReceiver messageReceiver;

  public LinkPreviewRepository(@NonNull Context context) {
    this.client = new OkHttpClient.Builder()
                                  .proxySelector(new ContentProxySelector())
                                  .addNetworkInterceptor(new ContentProxySafetyInterceptor())
                                  .cache(null)
                                  .build();

    ApplicationContext.getInstance(context).injectDependencies(this);
  }

  RequestController getLinkPreview(@NonNull Context context, @NonNull String url, @NonNull Callback<Optional<LinkPreview>> callback) {
    CompositeRequestController compositeController = new CompositeRequestController();

    if (!LinkPreviewUtil.isWhitelistedLinkUrl(url)) {
      Log.w(TAG, "Tried to get a link preview for a non-whitelisted domain.");
      callback.onComplete(Optional.absent());
      return compositeController;
    }

    RequestController metadataController;

    if (StickerUrl.isValidShareLink(url)) {
      metadataController = fetchStickerPackLinkPreview(context, url, callback);
    } else {
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
    }

    compositeController.addController(metadataController);
    return compositeController;
  }

  private @NonNull RequestController fetchMetadata(@NonNull String url, Callback<Metadata> callback) {
    Call call = client.newCall(new Request.Builder().url(url).cacheControl(NO_CACHE).build());

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
        Optional<String>  title    = getProperty(body, "title");
        Optional<String>  imageUrl = getProperty(body, "image");

        if (imageUrl.isPresent() && !LinkPreviewUtil.isWhitelistedMediaUrl(imageUrl.get())) {
          Log.i(TAG, "Image URL was invalid or for a non-whitelisted domain. Skipping.");
          imageUrl = Optional.absent();
        }

        callback.onComplete(new Metadata(title, imageUrl));
      }
    });

    return new CallRequestController(call);
  }

  private @NonNull RequestController fetchThumbnail(@NonNull Context context, @NonNull String imageUrl, @NonNull Callback<Optional<Attachment>> callback) {
    FutureTarget<Bitmap> bitmapFuture = GlideApp.with(context).asBitmap()
                                                              .load(new ChunkedImageUrl(imageUrl))
                                                              .skipMemoryCache(true)
                                                              .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                              .centerInside()
                                                              .submit(1024, 1024);

    RequestController controller = () -> bitmapFuture.cancel(false);

    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        Bitmap                bitmap = bitmapFuture.get();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);

        byte[]               bytes     = baos.toByteArray();
        Uri                  uri       = BlobProvider.getInstance().forData(bytes).createForSingleSessionInMemory();
        Optional<Attachment> thumbnail = Optional.of(new UriAttachment(uri,
                                                                       uri,
                                                                       MediaUtil.IMAGE_JPEG,
                                                                       AttachmentDatabase.TRANSFER_PROGRESS_STARTED,
                                                                       bytes.length,
                                                                       bitmap.getWidth(),
                                                                       bitmap.getHeight(),
                                                                       null,
                                                                       null,
                                                                       false,
                                                                       false,
                                                                       null,
                                                                       null));

        callback.onComplete(thumbnail);
      } catch (CancellationException | ExecutionException | InterruptedException e) {
        controller.cancel();
        callback.onComplete(Optional.absent());
      } finally {
        bitmapFuture.cancel(false);
      }
    });

    return () -> bitmapFuture.cancel(true);
  }

  private @NonNull Optional<String> getProperty(@NonNull String searchText, @NonNull String property) {
    Pattern pattern = Pattern.compile("<\\s*meta\\s+property\\s*=\\s*\"\\s*og:" + property + "\\s*\"\\s+[^>]*content\\s*=\\s*\"(.*?)\"[^>]*/?\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    Matcher matcher = pattern.matcher(searchText);

    if (matcher.find()) {
      String text = Html.fromHtml(matcher.group(1)).toString();
      return TextUtils.isEmpty(text) ? Optional.absent() : Optional.of(text);
    }

    return Optional.absent();
  }

  private RequestController fetchStickerPackLinkPreview(@NonNull Context context,
                                                        @NonNull String packUrl,
                                                        @NonNull Callback<Optional<LinkPreview>> callback)
  {
    SignalExecutors.UNBOUNDED.execute(() -> {
      try {
        Pair<String, String> stickerParams = StickerUrl.parseShareLink(packUrl).or(new Pair<>("", ""));
        String               packIdString  = stickerParams.first();
        String               packKeyString = stickerParams.second();
        byte[]               packIdBytes   = Hex.fromStringCondensed(packIdString);
        byte[]               packKeyBytes  = Hex.fromStringCondensed(packKeyString);

        SignalServiceStickerManifest manifest = messageReceiver.retrieveStickerManifest(packIdBytes, packKeyBytes);

        String                title        = manifest.getTitle().or(manifest.getAuthor()).or("");
        Optional<StickerInfo> firstSticker = Optional.fromNullable(manifest.getStickers().size() > 0 ? manifest.getStickers().get(0) : null);
        Optional<StickerInfo> cover        = manifest.getCover().or(firstSticker);

        if (cover.isPresent()) {
          Bitmap bitmap = GlideApp.with(context).asBitmap()
                                                .load(new StickerRemoteUri(packIdString, packKeyString, cover.get().getId()))
                                                .skipMemoryCache(true)
                                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                                .centerInside()
                                                .submit(512, 512)
                                                .get();

          ByteArrayOutputStream baos = new ByteArrayOutputStream();

          bitmap.compress(Bitmap.CompressFormat.WEBP, 80, baos);

          byte[]               bytes     = baos.toByteArray();
          Uri                  uri       = BlobProvider.getInstance().forData(bytes).createForSingleSessionInMemory();
          Optional<Attachment> thumbnail = Optional.of(new UriAttachment(uri,
                                                       uri,
                                                       MediaUtil.IMAGE_WEBP,
                                                       AttachmentDatabase.TRANSFER_PROGRESS_STARTED,
                                                       bytes.length,
                                                       bitmap.getWidth(),
                                                       bitmap.getHeight(),
                                                       null,
                                                       null,
                                                       false,
                                                       false,
                                                       null,
                                                       null));

          callback.onComplete(Optional.of(new LinkPreview(packUrl, title, thumbnail)));
        } else {
          callback.onComplete(Optional.absent());
        }
      } catch (IOException | InvalidMessageException | ExecutionException | InterruptedException e) {
        Log.w(TAG, "Failed to fetch sticker pack link preview.");
        callback.onComplete(Optional.absent());
      }
    });

    return () -> Log.i(TAG, "Cancelled sticker pack link preview fetch -- no effect.");
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
