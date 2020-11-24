package org.thoughtcrime.securesms.stickers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;

import java.io.InputStream;

import javax.inject.Inject;

/**
 * Glide loader to fetch a sticker remotely.
 */
public final class StickerRemoteUriLoader implements ModelLoader<StickerRemoteUri, InputStream> {

  private final SignalServiceMessageReceiver receiver;

  public StickerRemoteUriLoader(@NonNull SignalServiceMessageReceiver receiver) {
    this.receiver = receiver;
  }


  @Override
  public @Nullable LoadData<InputStream> buildLoadData(@NonNull StickerRemoteUri sticker, int width, int height, @NonNull Options options) {
    return new LoadData<>(sticker, new StickerRemoteUriFetcher(receiver, sticker));
  }

  @Override
  public boolean handles(@NonNull StickerRemoteUri sticker) {
    return true;
  }

  public static class Factory implements ModelLoaderFactory<StickerRemoteUri, InputStream>, InjectableType {

    @Inject SignalServiceMessageReceiver receiver;

    public Factory(@NonNull Context context) {
      ApplicationContext.getInstance(context).injectDependencies(this);
    }

    @Override
    public @NonNull ModelLoader<StickerRemoteUri, InputStream> build(@NonNull MultiModelLoaderFactory multiFactory) {
      return new StickerRemoteUriLoader(receiver);
    }

    @Override
    public void teardown() {
    }
  }
}
