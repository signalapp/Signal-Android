/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.internal.push.http.CancelationSignal;
import org.whispersystems.signalservice.internal.push.http.OutputStreamFactory;

import java.io.InputStream;

public class PushAttachmentData {

  private final String              contentType;
  private final InputStream         data;
  private final long                dataSize;
  private final OutputStreamFactory outputStreamFactory;
  private final ProgressListener    listener;
  private final CancelationSignal   cancelationSignal;

  public PushAttachmentData(String contentType, InputStream data, long dataSize,
                            OutputStreamFactory outputStreamFactory, ProgressListener listener,
                            CancelationSignal cancelationSignal)
  {
    this.contentType         = contentType;
    this.data                = data;
    this.dataSize            = dataSize;
    this.outputStreamFactory = outputStreamFactory;
    this.listener            = listener;
    this.cancelationSignal   = cancelationSignal;
  }

  public String getContentType() {
    return contentType;
  }

  public InputStream getData() {
    return data;
  }

  public long getDataSize() {
    return dataSize;
  }

  public OutputStreamFactory getOutputStreamFactory() {
    return outputStreamFactory;
  }

  public ProgressListener getListener() {
    return listener;
  }

  public CancelationSignal getCancelationSignal() {
    return cancelationSignal;
  }
}
