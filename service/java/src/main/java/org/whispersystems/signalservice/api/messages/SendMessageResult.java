package org.whispersystems.signalservice.api.messages;


import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.loki.api.SnodeAPI;

public class SendMessageResult {

  private final SignalServiceAddress address;
  private final Success              success;
  private final boolean              networkFailure;
  private final boolean              unregisteredFailure;
  private final IdentityFailure      identityFailure;
  private final SnodeAPI.Error        lokiAPIError;

  public static SendMessageResult success(SignalServiceAddress address, boolean unidentified, boolean needsSync) {
    return new SendMessageResult(address, new Success(unidentified, needsSync), false, false, null, null);
  }

  public static SendMessageResult lokiAPIError(SignalServiceAddress address, SnodeAPI.Error lokiAPIError) {
      return new SendMessageResult(address, null, false, false, null, lokiAPIError);
  }

  public static SendMessageResult networkFailure(SignalServiceAddress address) {
    return new SendMessageResult(address, null, true, false, null, null);
  }

  public static SendMessageResult unregisteredFailure(SignalServiceAddress address) {
    return new SendMessageResult(address, null, false, true, null, null);
  }

  public static SendMessageResult identityFailure(SignalServiceAddress address, IdentityKey identityKey) {
    return new SendMessageResult(address, null, false, false, new IdentityFailure(identityKey), null);
  }

  public SignalServiceAddress getAddress() {
    return address;
  }

  public Success getSuccess() {
    return success;
  }

  public boolean isNetworkFailure() {
    return networkFailure;
  }

  public boolean isUnregisteredFailure() {
    return unregisteredFailure;
  }

  public IdentityFailure getIdentityFailure() {
    return identityFailure;
  }

  public SnodeAPI.Error getLokiAPIError() { return lokiAPIError; }

  private SendMessageResult(SignalServiceAddress address, Success success, boolean networkFailure, boolean unregisteredFailure, IdentityFailure identityFailure, SnodeAPI.Error lokiAPIError) {
    this.address             = address;
    this.success             = success;
    this.networkFailure      = networkFailure;
    this.unregisteredFailure = unregisteredFailure;
    this.identityFailure     = identityFailure;
    this.lokiAPIError        = lokiAPIError;
  }

  public static class Success {
    private final boolean unidentified;
    private final boolean needsSync;

    private Success(boolean unidentified, boolean needsSync) {
      this.unidentified = unidentified;
      this.needsSync    = needsSync;
    }

    public boolean isUnidentified() {
      return unidentified;
    }

    public boolean isNeedsSync() {
      return needsSync;
    }
  }

  public static class IdentityFailure {
    private final IdentityKey identityKey;

    private IdentityFailure(IdentityKey identityKey) {
      this.identityKey = identityKey;
    }

    public IdentityKey getIdentityKey() {
      return identityKey;
    }
  }



}
