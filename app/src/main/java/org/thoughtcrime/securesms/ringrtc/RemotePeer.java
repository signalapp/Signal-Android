package org.thoughtcrime.securesms.ringrtc;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.signal.ringrtc.CallId;
import org.signal.ringrtc.Remote;
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

  public static final CallId NO_CALL_ID    = new CallId(-1L);
  public static final CallId GROUP_CALL_ID = new CallId(-2L);

  @NonNull private final RecipientId recipientId;
  @NonNull private       CallState   callState;
  @NonNull private       CallId      callId;
           private       long        callStartTimestamp;

  public RemotePeer(@NonNull RecipientId recipientId) {
    this(recipientId, new CallId(-1L));
  }

  public RemotePeer(@NonNull RecipientId recipientId, @NonNull CallId callId) {
    this.recipientId        = recipientId;
    this.callState          = CallState.IDLE;
    this.callId             = callId;
    this.callStartTimestamp = 0;
  }

  private RemotePeer(@NonNull Parcel in) {
    this.recipientId        = RecipientId.CREATOR.createFromParcel(in);
    this.callState          = CallState.values()[in.readInt()];
    this.callId             = new CallId(in.readLong());
    this.callStartTimestamp = in.readLong();
  }

  public @NonNull CallId getCallId() {
    return callId;
  }

  public void setCallId(@NonNull CallId callId) {
    this.callId = callId;
  }

  public void setCallStartTimestamp(long callStartTimestamp) {
    this.callStartTimestamp = callStartTimestamp;
  }

  public long getCallStartTimestamp() {
    return callStartTimestamp;
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

  public boolean callIdEquals(@Nullable RemotePeer remotePeer) {
    return remotePeer != null && this.callId.equals(remotePeer.callId);
  }

  public void dialing() {
    if (callState != CallState.IDLE) {
      throw new IllegalStateException("Cannot transition to DIALING from state: " + callState);
    }

    this.callState = CallState.DIALING;
  }

  public void answering() {
    if (callState != CallState.IDLE) {
      throw new IllegalStateException("Cannot transition to ANSWERING from state: " + callState);
    }

    this.callState = CallState.ANSWERING;
  }

  public void remoteRinging() {
    if (callState != CallState.DIALING) {
      throw new IllegalStateException("Cannot transition to REMOTE_RINGING from state: " + callState);
    }

    this.callState = CallState.REMOTE_RINGING;
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

  public void receivedBusy() {
    if (callState != CallState.DIALING) {
      Log.w(TAG, "RECEIVED_BUSY from unexpected state: " + callState);
    }

    this.callState = CallState.RECEIVED_BUSY;
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
    dest.writeLong(callStartTimestamp);
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
