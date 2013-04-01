package org.thoughtcrime.securesms.sms;

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SmsMessage;

import org.thoughtcrime.securesms.gcm.IncomingGcmMessage;

public class TextMessage implements Parcelable {

  public static final Parcelable.Creator<TextMessage> CREATOR = new Parcelable.Creator<TextMessage>() {
    @Override
    public TextMessage createFromParcel(Parcel in) {
      return new TextMessage(in);
    }

    @Override
    public TextMessage[] newArray(int size) {
      return new TextMessage[size];
    }
  };

  private final String  message;
  private final String  sender;
  private final int     protocol;
  private final String  serviceCenterAddress;
  private final boolean replyPathPresent;
  private final String  pseudoSubject;
  private final long    sentTimestampMillis;

  public TextMessage(SmsMessage message) {
    this.message              = message.getDisplayMessageBody();
    this.sender               = message.getDisplayOriginatingAddress();
    this.protocol             = message.getProtocolIdentifier();
    this.serviceCenterAddress = message.getServiceCenterAddress();
    this.replyPathPresent     = message.isReplyPathPresent();
    this.pseudoSubject        = message.getPseudoSubject();
    this.sentTimestampMillis  = message.getTimestampMillis();
  }

  public TextMessage(IncomingGcmMessage message) {
    this.message              = message.getMessageText();
    this.sender               = message.getSource();
    this.protocol             = 31337;
    this.serviceCenterAddress = "GCM";
    this.replyPathPresent     = true;
    this.pseudoSubject        = "";
    this.sentTimestampMillis  = message.getTimestampMillis();
  }

  public TextMessage(Parcel in) {
    this.message              = in.readString();
    this.sender               = in.readString();
    this.protocol             = in.readInt();
    this.serviceCenterAddress = in.readString();
    this.replyPathPresent     = (in.readInt() == 1);
    this.pseudoSubject        = in.readString();
    this.sentTimestampMillis  = in.readLong();
  }

  public long getSentTimestampMillis() {
    return sentTimestampMillis;
  }

  public String getPseudoSubject() {
    return pseudoSubject;
  }

  public String getMessage() {
    return message;
  }

  public String getSender() {
    return sender;
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

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(message);
    out.writeString(sender);
    out.writeInt(protocol);
    out.writeString(serviceCenterAddress);
    out.writeInt(replyPathPresent ? 1 : 0);
    out.writeString(pseudoSubject);
    out.writeLong(sentTimestampMillis);
  }
}
