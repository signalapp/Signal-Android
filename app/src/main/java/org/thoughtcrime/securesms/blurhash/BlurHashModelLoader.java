package org.thoughtcrime.securesms.blurhash;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.signature.ObjectKey;

public final class BlurHashModelLoader implements ModelLoader<BlurHash, BlurHash> {

  private BlurHashModelLoader() {}

  @Override
  public LoadData<BlurHash> buildLoadData(@NonNull BlurHash blurHash,
                                          int width,
                                          int height,
                                          @NonNull Options options)
  {
    return new LoadData<>(new ObjectKey(blurHash.getHash()), new BlurDataFetcher(blurHash));
  }

  @Override
  public boolean handles(@NonNull BlurHash blurHash) {
    return true;
  }

  private final class BlurDataFetcher implements DataFetcher<BlurHash> {

    private final BlurHash blurHash;

    private BlurDataFetcher(@NonNull BlurHash blurHash) {
      this.blurHash = blurHash;
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super BlurHash> callback) {
      callback.onDataReady(blurHash);
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void cancel() {
    }

    @Override
    public @NonNull Class<BlurHash> getDataClass() {
      return BlurHash.class;
    }

    @Override
    public @NonNull DataSource getDataSource() {
      return DataSource.LOCAL;
    }
  }

  public static class Factory implements ModelLoaderFactory<BlurHash, BlurHash> {
    @Override
    public @NonNull ModelLoader<BlurHash, BlurHash> build(@NonNull MultiModelLoaderFactory multiFactory) {
      return new BlurHashModelLoader();
    }

    @Override
    public void teardown() {
    }
  }
}
