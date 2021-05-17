package org.session.libsession.messaging.messages.signal;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

import org.session.libsession.messaging.messages.visible.OpenGroupInvitation;
import org.session.libsession.messaging.messages.visible.VisibleMessage;
import org.session.libsession.messaging.threads.Address;
import org.session.libsession.messaging.utilities.UpdateMessageData;
import org.session.libsession.utilities.GroupUtil;
import org.session.libsignal.utilities.guava.Optional;
import org.session.libsignal.messages.SignalServiceGroup;

public class IncomingTextMessage implements Parcelable {

  public static final Parcelable.Creator<IncomingTextMessage> CREATOR = new Parcelable.Creator<IncomingTextMessage>() {
    @Override
    public IncomingTextMessage createFromParcel(Parcel in) {
      return new IncomingTextMessage(in);
    }

    @Override
    public IncomingTextMessage[] newArray(int size) {
      return new IncomingTextMessage[size];
    }
  };
  private static final String TAG = IncomingTextMessage.class.getSimpleName();

  private final String  message;
  private       Address sender;
  private final int     senderDeviceId;
  private final int     protocol;
  private final String  serviceCenterAddress;
  private final boolean replyPathPresent;
  private final String  pseudoSubject;
  private final long    sentTimestampMillis;
  private final Address groupId;
  private final boolean push;
  private final int     subscriptionId;
  private final long    expiresInMillis;
  private final boolean unidentified;

  private boolean isOpenGroupInvitation = false;

  public IncomingTextMessage(Address sender, int senderDeviceId, long sentTimestampMillis,
                             String encodedBody, Optional<SignalServiceGroup> group,
                             long expiresInMillis, boolean unidentified)
  {
    this.message              = encodedBody;
    this.sender               = sender;
    this.senderDeviceId       = senderDeviceId;
    this.protocol             = 31337;
    this.serviceCenterAddress = "GCM";
    this.replyPathPresent     = true;
    this.pseudoSubject        = "";
    this.sentTimestampMillis  = sentTimestampMillis;
    this.push                 = true;
    this.subscriptionId       = -1;
    this.expiresInMillis      = expiresInMillis;
    this.unidentified         = unidentified;

    if (group.isPresent()) {
      this.groupId = Address.fromSerialized(GroupUtil.getEncodedId(group.get()));
    } else {
      this.groupId = null;
    }
  }

  public IncomingTextMessage(Parcel in) {
    this.message              = in.readString();
    this.sender               = in.readParcelable(IncomingTextMessage.class.getClassLoader());
    this.senderDeviceId       = in.readInt();
    this.protocol             = in.readInt();
    this.serviceCenterAddress = in.readString();
    this.replyPathPresent     = (in.readInt() == 1);
    this.pseudoSubject        = in.readString();
    this.sentTimestampMillis  = in.readLong();
    this.groupId              = in.readParcelable(IncomingTextMessage.class.getClassLoader());
    this.push                 = (in.readInt() == 1);
    this.subscriptionId       = in.readInt();
    this.expiresInMillis      = in.readLong();
    this.unidentified         = in.readInt() == 1;
  }

  public IncomingTextMessage(IncomingTextMessage base, String newBody) {
    this.message              = newBody;
    this.sender               = base.getSender();
    this.senderDeviceId       = base.getSenderDeviceId();
    this.protocol             = base.getProtocol();
    this.serviceCenterAddress = base.getServiceCenterAddress();
    this.replyPathPresent     = base.isReplyPathPresent();
    this.pseudoSubject        = base.getPseudoSubject();
    this.sentTimestampMillis  = base.getSentTimestampMillis();
    this.groupId              = base.getGroupId();
    this.push                 = base.isPush();
    this.subscriptionId       = base.getSubscriptionId();
    this.expiresInMillis      = base.getExpiresIn();
    this.unidentified         = base.isUnidentified();
    this.isOpenGroupInvitation= base.isOpenGroupInvitation();
  }

  public static IncomingTextMessage from(VisibleMessage message,
                                         Address sender,
                                         Optional<SignalServiceGroup> group,
                                         long expiresInMillis)
  {
    return new IncomingTextMessage(sender, 1, message.getSentTimestamp(), message.getText(), group, expiresInMillis, false);
  }

  public static IncomingTextMessage fromOpenGroupInvitation(OpenGroupInvitation openGroupInvitation, Address sender, Long sentTimestamp)
  {
    String url = openGroupInvitation.getUrl();
    String name = openGroupInvitation.getName();
    if (url == null || name == null) { return null; }
    // FIXME: Doing toJSON() to get the body here is weird
    String body = UpdateMessageData.Companion.buildOpenGroupInvitation(url, name).toJSON();
    IncomingTextMessage incomingTextMessage = new IncomingTextMessage(sender, 1, sentTimestamp, body, Optional.absent(), 0, false);
    incomingTextMessage.isOpenGroupInvitation = true;
    return incomingTextMessage;
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresInMillis;
  }

  public long getSentTimestampMillis() {
    return sentTimestampMillis;
  }

  public String getPseudoSubject() {
    return pseudoSubject;
  }

  public String getMessageBody() {
    return message;
  }

  public Address getSender() {
    return sender;
  }

  public int getSenderDeviceId() {
    return senderDeviceId;
  }

  public int getProtocol() {
    return protocol;
  }

  public String getServiceCenterAddress() {
    return serviceCenterAddress;
  }

  public boolean isReplyPathPresent() {
    return replyPathPresent;
  }

  public boolean isSecureMessage() {
    return false;
  }

  public boolean isPush() {
    return push;
  }

  public @Nullable Address getGroupId() {
    return groupId;
  }

  public boolean isGroup() {
    return false;
  }

  public boolean isUnidentified() {
    return unidentified;
  }

  public boolean isOpenGroupInvitation() { return isOpenGroupInvitation; }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(message);
    out.writeParcelable(sender, flags);
    out.writeInt(senderDeviceId);
    out.writeInt(protocol);
    out.writeString(serviceCenterAddress);
    out.writeInt(replyPathPresent ? 1 : 0);
    out.writeString(pseudoSubject);
    out.writeLong(sentTimestampMillis);
    out.writeParcelable(groupId, flags);
    out.writeInt(push ? 1 : 0);
    out.writeInt(subscriptionId);
    out.writeInt(unidentified ? 1 : 0);
  }
}
