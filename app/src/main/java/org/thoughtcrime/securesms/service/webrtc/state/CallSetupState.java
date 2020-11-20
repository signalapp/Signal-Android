package org.thoughtcrime.securesms.service.webrtc.state;

import androidx.annotation.NonNull;

/**
 * Information specific to setting up a call.
 */
public final class CallSetupState {
  boolean enableVideoOnCreate;
  boolean isRemoteVideoOffer;
  boolean acceptWithVideo;
  boolean sentJoinedMessage;

  public CallSetupState() {
    this(false, false, false, false);
  }

  public CallSetupState(@NonNull CallSetupState toCopy) {
    this(toCopy.enableVideoOnCreate, toCopy.isRemoteVideoOffer, toCopy.acceptWithVideo, toCopy.sentJoinedMessage);
  }

  public CallSetupState(boolean enableVideoOnCreate, boolean isRemoteVideoOffer, boolean acceptWithVideo, boolean sentJoinedMessage) {
    this.enableVideoOnCreate = enableVideoOnCreate;
    this.isRemoteVideoOffer  = isRemoteVideoOffer;
    this.acceptWithVideo     = acceptWithVideo;
    this.sentJoinedMessage   = sentJoinedMessage;
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

  public boolean hasSentJoinedMessage() {
    return sentJoinedMessage;
  }
}
