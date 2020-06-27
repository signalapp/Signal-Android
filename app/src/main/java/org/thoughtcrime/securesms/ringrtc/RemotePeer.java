package org.thoughtcrime.securesms.ringrtc;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

import org.signal.ringrtc.CallId;
import org.signal.ringrtc.Remote;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;

/**
 * Container class that represents the remote peer and current state
 * of a video/voice call.
 *
 * The class is also Parcelable for passing around via an Intent.
 */
public final class RemotePeer implements Remote, Parcelable
{
  private static final String TAG = Log.tag(RemotePeer.class);

  @NonNull private final RecipientId recipientId;
  @NonNull private       CallState   callState;
  @NonNull private       CallId      callId;

  public RemotePeer(@NonNull RecipientId recipientId) {
    this.recipientId = recipientId;
    this.callState   = CallState.IDLE;
    this.callId      = new CallId(-1L);
  }

  private RemotePeer(@NonNull Parcel in) {
    this.recipientId = RecipientId.CREATOR.createFromParcel(in);
    this.callState   = CallState.values()[in.readInt()];
    this.callId      = new CallId(in.readLong());
  }

  public @NonNull CallId getCallId() {
    return callId;
  }

  public @NonNull CallState getState() {
    return callState;
  }

  public @NonNull RecipientId getId() {
    return recipientId;
  }

  public @NonNull Recipient getRecipient() {
    return Recipient.resolved(recipientId);
  }

  @Override
  public String toString() {
    return "recipientId: " + this.recipientId +
           ", callId: "    + this.callId      +
           ", state: "     + this.callState;
  }

  @Override
  public boolean recipientEquals(Remote obj) {
    if (obj != null && this.getClass() == obj.getClass()) {
      RemotePeer that = (RemotePeer)obj;
      return this.recipientId.equals(that.recipientId);
    }

    return false;
  }

  public boolean callIdEquals(RemotePeer remotePeer) {
    return remotePeer != null && this.callId.equals(remotePeer.callId);
  }

  public void dialing(@NonNull CallId callId) {
    if (callState != CallState.IDLE) {
      throw new IllegalStateException("Cannot transition to DIALING from state: " + callState);
    }

    this.callId = callId;
    this.callState = CallState.DIALING;
  }

  public void answering(@NonNull CallId callId) {
    if (callState != CallState.IDLE) {
      throw new IllegalStateException("Cannot transition to ANSWERING from state: " + callState);
    }

    this.callId = callId;
    this.callState = CallState.ANSWERING;
  }

  public void remoteRinging() {
    if (callState != CallState.DIALING) {
      throw new IllegalStateException("Cannot transition to REMOTE_RINGING from state: " + callState);
    }

    this.callState = CallState.REMOTE_RINGING;
  }

  public void receivedBusy() {
    if (callState != CallState.DIALING) {
      Log.w(TAG, "RECEIVED_BUSY from unexpected state: " + callState);
    }

    this.callState = CallState.RECEIVED_BUSY;
  }

  public void localRinging() {
    if (callState != CallState.ANSWERING) {
      throw new IllegalStateException("Cannot transition to LOCAL_RINGING from state: " + callState);
    }

    this.callState = CallState.LOCAL_RINGING;
  }

  public void connected() {
    if (callState != CallState.REMOTE_RINGING && callState != CallState.LOCAL_RINGING) {
        throw new IllegalStateException("Cannot transition outgoing call to CONNECTED from state: " + callState);
    }

    this.callState = CallState.CONNECTED;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    recipientId.writeToParcel(dest, flags);
    dest.writeInt(callState.ordinal());
    dest.writeLong(callId.longValue());
  }

  public static final Creator<RemotePeer> CREATOR = new Creator<RemotePeer>() {
      @Override
      public RemotePeer createFromParcel(@NonNull Parcel in) {
        return new RemotePeer(in);
      }

      @Override
      public RemotePeer[] newArray(int size) {
        return new RemotePeer[size];
      }
    };
}
