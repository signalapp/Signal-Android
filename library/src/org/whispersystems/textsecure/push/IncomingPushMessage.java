package org.whispersystems.textsecure.push;

import org.whispersystems.textsecure.push.PushMessageProtos.IncomingPushMessageSignal;
import org.whispersystems.textsecure.push.PushMessageProtos.IncomingPushMessageSignal.AttachmentPointer;
import org.whispersystems.textsecure.util.Base64;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.LinkedList;
import java.util.List;

public class IncomingPushMessage implements PushMessage, Parcelable {

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
  private byte[]                      message;
  private List<PushAttachmentPointer> attachments;
  private long                        timestamp;

  public IncomingPushMessage(IncomingPushMessageSignal signal) {
    this.type         = signal.getType();
    this.source       = signal.getSource();
    this.destinations = signal.getDestinationsList();
    this.message      = signal.getMessage().toByteArray();
    this.timestamp    = signal.getTimestamp();
    this.attachments  = new LinkedList<PushAttachmentPointer>();

    List<AttachmentPointer> attachmentPointers = signal.getAttachmentsList();

    for (AttachmentPointer pointer : attachmentPointers) {
      this.attachments.add(new PushAttachmentPointer(pointer.getContentType(), pointer.getKey()));
    }
  }

  public IncomingPushMessage(Parcel in) {
    this.destinations = new LinkedList<String>();
    this.attachments  = new LinkedList<PushAttachmentPointer>();

    this.type   = in.readInt();
    this.source = in.readString();
    in.readStringList(destinations);
    this.message = new byte[in.readInt()];
    in.readByteArray(this.message);
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

  public byte[] getBody() {
    return message;
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
    dest.writeInt(type);
    dest.writeString(source);
    dest.writeStringList(destinations);
    dest.writeInt(message.length);
    dest.writeByteArray(message);
    dest.writeList(attachments);
    dest.writeLong(timestamp);
  }

  public int getType() {
    return type;
  }
}
