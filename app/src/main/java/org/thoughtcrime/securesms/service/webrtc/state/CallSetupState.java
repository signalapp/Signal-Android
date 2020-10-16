package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;

/**
 * Information specific to setting up a call.
 */
public final class CallSetupState {
  boolean enableVideoOnCreate;
  boolean isRemoteVideoOffer;
  boolean acceptWithVideo;

  public CallSetupState() {
    this(false, false, false);
  }

  public CallSetupState(@NonNull CallSetupState toCopy) {
    this(toCopy.enableVideoOnCreate, toCopy.isRemoteVideoOffer, toCopy.acceptWithVideo);
  }

  public CallSetupState(boolean enableVideoOnCreate, boolean isRemoteVideoOffer, boolean acceptWithVideo) {
    this.enableVideoOnCreate = enableVideoOnCreate;
    this.isRemoteVideoOffer  = isRemoteVideoOffer;
    this.acceptWithVideo     = acceptWithVideo;
  }

  public boolean isEnableVideoOnCreate() {
    return enableVideoOnCreate;
  }

  public boolean isRemoteVideoOffer() {
    return isRemoteVideoOffer;
  }

  public boolean isAcceptWithVideo() {
    return acceptWithVideo;
  }
}
