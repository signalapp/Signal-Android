package org.thoughtcrime.securesms.mms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.thoughtcrime.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;

public class AttachmentStreamUriLoader implements ModelLoader<AttachmentModel, InputStream> {

  @Nullable
  @Override
  public LoadData<InputStream> buildLoadData(AttachmentModel attachmentModel, int width, int height, Options options) {
    return new LoadData<>(attachmentModel, new AttachmentStreamLocalUriFetcher(attachmentModel.attachment, attachmentModel.plaintextLength, attachmentModel.key, attachmentModel.digest));
  }

  @Override
  public boolean handles(AttachmentModel attachmentModel) {
    return true;
  }

  static class Factory implements ModelLoaderFactory<AttachmentModel, InputStream> {

    @Override
    public ModelLoader<AttachmentModel, InputStream> build(MultiModelLoaderFactory multiFactory) {
      return new AttachmentStreamUriLoader();
    }

    @Override
    public void teardown() {
      // Do nothing.
    }
  }

  public static class AttachmentModel implements Key {
    public @NonNull File             attachment;
    public @NonNull byte[]           key;
    public @NonNull Optional<byte[]> digest;
    public          long             plaintextLength;

    public AttachmentModel(@NonNull File attachment, @NonNull byte[] key,
                           long plaintextLength, @NonNull Optional<byte[]> digest)
    {
      this.attachment      = attachment;
      this.key             = key;
      this.digest          = digest;
      this.plaintextLength = plaintextLength;
    }

    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
      messageDigest.update(attachment.toString().getBytes());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AttachmentModel that = (AttachmentModel)o;

      return attachment.equals(that.attachment);

    }

    @Override
    public int hashCode() {
      return attachment.hashCode();
    }
  }
}

