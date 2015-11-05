package org.thoughtcrime.redphone.audio;

public class NativeAudioException extends Exception {

  public NativeAudioException() {
    super();
  }

  public NativeAudioException(String detailMessage) {
    super(detailMessage);
  }

  public NativeAudioException(String detailMessage, Throwable throwable) {
    super(detailMessage, throwable);
  }

  public NativeAudioException(Throwable throwable) {
    super(throwable);
  }

}
