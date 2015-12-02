package org.thoughtcrime.securesms.mms;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.GenericLoaderFactory;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.stream.StreamModelLoader;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.mms.AttachmentStreamUriLoader.AttachmentModel;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader.DecryptableUri;
import org.thoughtcrime.securesms.util.SaveAttachmentTask.Attachment;

import java.io.File;
import java.io.InputStream;

/**
 * A {@link ModelLoader} for translating uri models into {@link InputStream} data. Capable of handling 'http',
 * 'https', 'android.resource', 'content', and 'file' schemes. Unsupported schemes will throw an exception in
 * {@link #getResourceFetcher(Uri, int, int)}.
 */
public class AttachmentStreamUriLoader implements StreamModelLoader<AttachmentModel> {
  private final Context context;

  /**
   * THe default factory for {@link com.bumptech.glide.load.model.stream.StreamUriLoader}s.
   */
  public static class Factory implements ModelLoaderFactory<AttachmentModel, InputStream> {

    @Override
    public StreamModelLoader<AttachmentModel> build(Context context, GenericLoaderFactory factories) {
      return new AttachmentStreamUriLoader(context);
    }

    @Override
    public void teardown() {
      // Do nothing.
    }
  }

  public AttachmentStreamUriLoader(Context context) {
    this.context = context;
  }

  @Override
  public DataFetcher<InputStream> getResourceFetcher(AttachmentModel model, int width, int height) {
    return new AttachmentStreamLocalUriFetcher(model.attachment, model.key);
  }

  public static class AttachmentModel {
    public @NonNull File   attachment;
    public @NonNull byte[] key;

    public AttachmentModel(@NonNull File attachment, @NonNull byte[] key) {
      this.attachment = attachment;
      this.key        = key;
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

