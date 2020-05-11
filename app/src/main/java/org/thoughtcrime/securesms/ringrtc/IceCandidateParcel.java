package org.thoughtcrime.securesms.ringrtc;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

import org.signal.ringrtc.CallId;

import org.webrtc.IceCandidate;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;

/**
 * Utility class for passing ICE candidate objects via Intents.
 *
 * Also provides utility methods for converting to/from Signal ICE
 * candidate messages.
 */
public class IceCandidateParcel implements Parcelable {

  @NonNull private final IceCandidate iceCandidate;

  public IceCandidateParcel(@NonNull IceCandidate iceCandidate) {
    this.iceCandidate = iceCandidate;
  }

  public IceCandidateParcel(@NonNull IceUpdateMessage iceUpdateMessage) {
    this.iceCandidate = new IceCandidate(iceUpdateMessage.getSdpMid(),
                                         iceUpdateMessage.getSdpMLineIndex(),
                                         iceUpdateMessage.getSdp());
  }

  private IceCandidateParcel(@NonNull Parcel in) {
    this.iceCandidate = new IceCandidate(in.readString(),
                                         in.readInt(),
                                         in.readString());
  }

  public @NonNull IceCandidate getIceCandidate() {
    return iceCandidate;
  }

  public @NonNull IceUpdateMessage getIceUpdateMessage(@NonNull CallId callId) {
    return new IceUpdateMessage(callId.longValue(),
                                iceCandidate.sdpMid,
                                iceCandidate.sdpMLineIndex,
                                iceCandidate.sdp);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeString(iceCandidate.sdpMid);
    dest.writeInt(iceCandidate.sdpMLineIndex);
    dest.writeString(iceCandidate.sdp);
  }

  public static final Creator<IceCandidateParcel> CREATOR = new Creator<IceCandidateParcel>() {
      @Override
      public IceCandidateParcel createFromParcel(@NonNull Parcel in) {
        return new IceCandidateParcel(in);
      }

      @Override
      public IceCandidateParcel[] newArray(int size) {
        return new IceCandidateParcel[size];
      }
    };
}
