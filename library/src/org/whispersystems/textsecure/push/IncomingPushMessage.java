package org.whispersystems.textsecure.push;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.LinkedList;
import java.util.List;

public class IncomingPushMessage implements Parcelable {

  public static final Parcelable.Creator<IncomingPushMessage> CREATOR = new Parcelable.Creator<IncomingPushMessage>() {
    @Override
    public IncomingPushMessage createFromParcel(Parcel in) {
      return new IncomingPushMessage(in);
    }

    @Override
    public IncomingPushMessage[] newArray(int size) {
      return new IncomingPushMessage[size];
    }
  };

  private int                         type;
  private String                      source;
  private List<String>                destinations;
  private String                      messageText;
  private List<PushAttachmentPointer> attachments;
  private long                        timestamp;

  public IncomingPushMessage(String source, List<String> destinations, String messageText,
                             int type, List<PushAttachmentPointer> attachments, long timestamp)
  {
    this.type         = type;
    this.source       = source;
    this.destinations = destinations;
    this.messageText  = messageText;
    this.attachments  = attachments;
    this.timestamp    = timestamp;
  }

  public IncomingPushMessage(Parcel in) {
    this.destinations = new LinkedList<String>();
    this.attachments  = new LinkedList<PushAttachmentPointer>();

    this.source = in.readString();
    in.readStringList(destinations);
    this.messageText = in.readString();
    in.readList(attachments, PushAttachmentPointer.class.getClassLoader());
    this.timestamp = in.readLong();
  }

  public long getTimestampMillis() {
    return timestamp;
  }

  public String getSource() {
    return source;
  }

  public List<PushAttachmentPointer> getAttachments() {
    return attachments;
  }

  public String getMessageText() {
    return messageText;
  }

  public List<String> getDestinations() {
    return destinations;
  }

  public boolean hasAttachments() {
    return getAttachments() != null && !getAttachments().isEmpty();
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(source);
    dest.writeStringList(destinations);
    dest.writeString(messageText);
    dest.writeList(attachments);
    dest.writeLong(timestamp);
  }

  public int getType() {
    return type;
  }
}
