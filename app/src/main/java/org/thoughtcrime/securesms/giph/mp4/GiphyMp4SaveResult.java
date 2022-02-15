package org.thoughtcrime.securesms.giph.mp4;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.giph.model.GiphyImage;

/**
 * Encapsulates the result of downloading a Giphy MP4 or GIF for
 * sending to a user.
 */
public abstract class GiphyMp4SaveResult {
  private GiphyMp4SaveResult() {}

  public final static class Success extends GiphyMp4SaveResult {
    private final Uri     blobUri;
    private final int     width;
    private final int     height;
    private final boolean isBorderless;

    Success(@NonNull Uri blobUri, @NonNull GiphyImage giphyImage) {
      this.blobUri      = blobUri;
      this.width        = giphyImage.getGifWidth();
      this.height       = giphyImage.getGifHeight();
      this.isBorderless = giphyImage.isSticker();
    }

    public int getHeight() {
      return height;
    }

    public int getWidth() {
      return width;
    }

    public @NonNull Uri getBlobUri() {
      return blobUri;
    }

    public boolean isBorderless() {
      return isBorderless;
    }
  }

  public final static class InProgress extends GiphyMp4SaveResult {
  }

  public final static class Error extends GiphyMp4SaveResult {
    private final Exception exception;

    Error(@NonNull Exception exception) {
      this.exception = exception;
    }
  }
}
