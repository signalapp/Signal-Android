package org.thoughtcrime.securesms.media;

import android.content.Context;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public abstract class MediaInput implements Closeable {

  @NonNull
  public abstract MediaExtractor createExtractor() throws IOException;

  public static class FileMediaInput extends MediaInput {

    private final File file;

    public FileMediaInput(@NonNull File file) {
      this.file = file;
    }

    @Override
    public @NonNull MediaExtractor createExtractor() throws IOException {
      final MediaExtractor extractor = new MediaExtractor();
      extractor.setDataSource(file.getAbsolutePath());
      return extractor;
    }

    @Override
    public void close() {
    }
  }

  public static class UriMediaInput extends MediaInput {

    private final Uri     uri;
    private final Context context;

    public UriMediaInput(@NonNull Context context, @NonNull Uri uri) {
      this.uri     = uri;
      this.context = context;
    }

    @Override
    public @NonNull MediaExtractor createExtractor() throws IOException {
      final MediaExtractor extractor = new MediaExtractor();
      extractor.setDataSource(context, uri, null);
      return extractor;
    }

    @Override
    public void close() {
    }
  }

  @RequiresApi(23)
  public static class MediaDataSourceMediaInput extends MediaInput {

    private final MediaDataSource mediaDataSource;

    public MediaDataSourceMediaInput(@NonNull MediaDataSource mediaDataSource) {
      this.mediaDataSource = mediaDataSource;
    }

    @Override
    public @NonNull MediaExtractor createExtractor() throws IOException {
      final MediaExtractor extractor = new MediaExtractor();
      extractor.setDataSource(mediaDataSource);
      return extractor;
    }

    @Override
    public void close() throws IOException {
      mediaDataSource.close();
    }
  }
}
