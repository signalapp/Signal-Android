package org.thoughtcrime.securesms.sms;

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SmsMessage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.util.List;

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
  private static final String TAG = Log.tag(IncomingTextMessage.class);

            private final String      message;
            private final RecipientId sender;
            private final int         senderDeviceId;
            private final int         protocol;
            private final String      serviceCenterAddress;
            private final boolean     replyPathPresent;
            private final String      pseudoSubject;
            private final long        sentTimestampMillis;
            private final long        serverTimestampMillis;
            private final long        receivedTimestampMillis;
  @Nullable private final GroupId     groupId;
            private final boolean     push;
            private final int         subscriptionId;
            private final long        expiresInMillis;
            private final boolean     unidentified;
  @Nullable private final String      serverGuid;

  public IncomingTextMessage(@NonNull RecipientId sender, @NonNull SmsMessage message, int subscriptionId) {
    this.message                 = message.getDisplayMessageBody();
    this.sender                  = sender;
    this.senderDeviceId          = SignalServiceAddress.DEFAULT_DEVICE_ID;
    this.protocol                = message.getProtocolIdentifier();
    this.serviceCenterAddress    = message.getServiceCenterAddress();
    this.replyPathPresent        = message.isReplyPathPresent();
    this.pseudoSubject           = message.getPseudoSubject();
    this.sentTimestampMillis     = message.getTimestampMillis();
    this.serverTimestampMillis   = -1;
    this.receivedTimestampMillis = System.currentTimeMillis();
    this.subscriptionId          = subscriptionId;
    this.expiresInMillis         = 0;
    this.groupId                 = null;
    this.push                    = false;
    this.unidentified            = false;
    this.serverGuid              = null;
  }

  public IncomingTextMessage(@NonNull RecipientId sender,
                             int senderDeviceId,
                             long sentTimestampMillis,
                             long serverTimestampMillis,
                             long receivedTimestampMillis,
                             String encodedBody,
                             Optional<GroupId> groupId,
                             long expiresInMillis,
                             boolean unidentified,
                             String serverGuid)
  {
    this.message                 = encodedBody;
    this.sender                  = sender;
    this.senderDeviceId          = senderDeviceId;
    this.protocol                = 31337;
    this.serviceCenterAddress    = "GCM";
    this.replyPathPresent        = true;
    this.pseudoSubject           = "";
    this.sentTimestampMillis     = sentTimestampMillis;
    this.serverTimestampMillis   = serverTimestampMillis;
    this.receivedTimestampMillis = receivedTimestampMillis;
    this.push                    = true;
    this.subscriptionId          = -1;
    this.expiresInMillis         = expiresInMillis;
    this.unidentified            = unidentified;
    this.groupId                 = groupId.orNull();
    this.serverGuid              = serverGuid;
  }

  public IncomingTextMessage(Parcel in) {
    this.message                 = in.readString();
    this.sender                  = in.readParcelable(IncomingTextMessage.class.getClassLoader());
    this.senderDeviceId          = in.readInt();
    this.protocol                = in.readInt();
    this.serviceCenterAddress    = in.readString();
    this.replyPathPresent        = (in.readInt() == 1);
    this.pseudoSubject           = in.readString();
    this.sentTimestampMillis     = in.readLong();
    this.serverTimestampMillis   = in.readLong();
    this.receivedTimestampMillis = in.readLong();
    this.groupId                 = GroupId.parseNullableOrThrow(in.readString());
    this.push                    = (in.readInt() == 1);
    this.subscriptionId          = in.readInt();
    this.expiresInMillis         = in.readLong();
    this.unidentified            = in.readInt() == 1;
    this.serverGuid              = in.readString();
  }

  public IncomingTextMessage(IncomingTextMessage base, String newBody) {
    this.message                 = newBody;
    this.sender                  = base.getSender();
    this.senderDeviceId          = base.getSenderDeviceId();
    this.protocol                = base.getProtocol();
    this.serviceCenterAddress    = base.getServiceCenterAddress();
    this.replyPathPresent        = base.isReplyPathPresent();
    this.pseudoSubject           = base.getPseudoSubject();
    this.sentTimestampMillis     = base.getSentTimestampMillis();
    this.serverTimestampMillis   = base.getServerTimestampMillis();
    this.receivedTimestampMillis = base.getReceivedTimestampMillis();
    this.groupId                 = base.getGroupId();
    this.push                    = base.isPush();
    this.subscriptionId          = base.getSubscriptionId();
    this.expiresInMillis         = base.getExpiresIn();
    this.unidentified            = base.isUnidentified();
    this.serverGuid              = base.getServerGuid();
  }

  public IncomingTextMessage(List<IncomingTextMessage> fragments) {
    StringBuilder body = new StringBuilder();

    for (IncomingTextMessage message : fragments) {
      body.append(message.getMessageBody());
    }

    this.message                 = body.toString();
    this.sender                  = fragments.get(0).getSender();
    this.senderDeviceId          = fragments.get(0).getSenderDeviceId();
    this.protocol                = fragments.get(0).getProtocol();
    this.serviceCenterAddress    = fragments.get(0).getServiceCenterAddress();
    this.replyPathPresent        = fragments.get(0).isReplyPathPresent();
    this.pseudoSubject           = fragments.get(0).getPseudoSubject();
    this.sentTimestampMillis     = fragments.get(0).getSentTimestampMillis();
    this.serverTimestampMillis   = fragments.get(0).getServerTimestampMillis();
    this.receivedTimestampMillis = fragments.get(0).getReceivedTimestampMillis();
    this.groupId                 = fragments.get(0).getGroupId();
    this.push                    = fragments.get(0).isPush();
    this.subscriptionId          = fragments.get(0).getSubscriptionId();
    this.expiresInMillis         = fragments.get(0).getExpiresIn();
    this.unidentified            = fragments.get(0).isUnidentified();
    this.serverGuid              = fragments.get(0).getServerGuid();
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

  public long getServerTimestampMillis() {
    return serverTimestampMillis;
  }

  public long getReceivedTimestampMillis() {
    return receivedTimestampMillis;
  }

  public String getPseudoSubject() {
    return pseudoSubject;
  }

  public String getMessageBody() {
    return message;
  }

  public RecipientId getSender() {
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

  public boolean isPreKeyBundle() {
    return isLegacyPreKeyBundle() || isContentPreKeyBundle();
  }

  public boolean isLegacyPreKeyBundle() {
    return false;
  }

  public boolean isContentPreKeyBundle() {
    return false;
  }

  public boolean isEndSession() {
    return false;
  }

  public boolean isPush() {
    return push;
  }

  public @Nullable GroupId getGroupId() {
    return groupId;
  }

  public boolean isGroup() {
    return false;
  }

  public boolean isJoined() {
    return false;
  }

  public boolean isIdentityUpdate() {
    return false;
  }

  public boolean isIdentityVerified() {
    return false;
  }

  public boolean isIdentityDefault() {
    return false;
  }

  /**
   * @return True iff the message is only a group leave of a single member.
   */
  public boolean isJustAGroupLeave() {
    return false;
  }

  public boolean isUnidentified() {
    return unidentified;
  }

  public @Nullable String getServerGuid() {
    return serverGuid;
  }

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
    out.writeString(groupId == null ? null : groupId.toString());
    out.writeInt(push ? 1 : 0);
    out.writeInt(subscriptionId);
    out.writeLong(expiresInMillis);
    out.writeInt(unidentified ? 1 : 0);
    out.writeString(serverGuid);
  }
}
