package org.whispersystems.signalservice.internal.push.http;


import org.whispersystems.libsignal.util.guava.Preconditions;
import org.whispersystems.signalservice.api.crypto.DigestingOutputStream;
import org.whispersystems.signalservice.api.crypto.SkippingOutputStream;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;

public class DigestingRequestBody extends RequestBody {

  private final InputStream         inputStream;
  private final OutputStreamFactory outputStreamFactory;
  private final String              contentType;
  private final long                contentLength;
  private final ProgressListener    progressListener;
  private final CancelationSignal   cancelationSignal;
  private final long                contentStart;

  private byte[] digest;

  public DigestingRequestBody(InputStream inputStream,
                              OutputStreamFactory outputStreamFactory,
                              String contentType, long contentLength,
                              ProgressListener progressListener,
                              CancelationSignal cancelationSignal,
                              long contentStart)
  {
    Preconditions.checkArgument(contentLength >= contentStart);
    Preconditions.checkArgument(contentStart >= 0);

    this.inputStream         = inputStream;
    this.outputStreamFactory = outputStreamFactory;
    this.contentType         = contentType;
    this.contentLength       = contentLength;
    this.progressListener    = progressListener;
    this.cancelationSignal   = cancelationSignal;
    this.contentStart        = contentStart;
  }

  @Override
  public MediaType contentType() {
    return MediaType.parse(contentType);
  }

  @Override
  public void writeTo(BufferedSink sink) throws IOException {
    DigestingOutputStream outputStream = outputStreamFactory.createFor(new SkippingOutputStream(contentStart, sink.outputStream()));
    byte[]                buffer       = new byte[8192];

    int read;
    long total = 0;

    while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
      if (cancelationSignal != null && cancelationSignal.isCanceled()) {
        throw new IOException("Canceled!");
      }

      outputStream.write(buffer, 0, read);
      total += read;

      if (progressListener != null) {
        progressListener.onAttachmentProgress(contentLength, total);
      }
    }

    outputStream.flush();
    digest = outputStream.getTransmittedDigest();
  }

  @Override
  public long contentLength() {
    if (contentLength > 0) return contentLength - contentStart;
    else                   return -1;
  }

  public byte[] getTransmittedDigest() {
    return digest;
  }
}
