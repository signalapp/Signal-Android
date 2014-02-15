/**
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.textsecure.push;

import android.os.Parcel;
import android.os.Parcelable;

import org.whispersystems.textsecure.push.PushMessageProtos.IncomingPushMessageSignal;

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

  private int          type;
  private String       source;
  private int          sourceDevice;
  private byte[]       message;
  private long         timestamp;
  private String       relay;

  private IncomingPushMessage(IncomingPushMessage message, byte[] body) {
    this.type         = message.type;
    this.source       = message.source;
    this.sourceDevice = message.sourceDevice;
    this.timestamp    = message.timestamp;
    this.relay        = message.relay;
    this.message      = body;
  }

  public IncomingPushMessage(IncomingPushMessageSignal signal) {
    this.type         = signal.getType().getNumber();
    this.source       = signal.getSource();
    this.sourceDevice = signal.getSourceDevice();
    this.message      = signal.getMessage().toByteArray();
    this.timestamp    = signal.getTimestamp();
    this.relay        = signal.getRelay();
  }

  public IncomingPushMessage(Parcel in) {
    this.type         = in.readInt();
    this.source       = in.readString();
    this.sourceDevice = in.readInt();

    if (in.readInt() == 1) {
      this.relay  = in.readString();
    }

    this.message = new byte[in.readInt()];
    in.readByteArray(this.message);
    this.timestamp = in.readLong();
  }

  public IncomingPushMessage(int type, String source, int sourceDevice,
                             byte[] body, long timestamp)
  {
    this.type         = type;
    this.source       = source;
    this.sourceDevice = sourceDevice;
    this.message      = body;
    this.timestamp    = timestamp;
  }

  public String getRelay() {
    return relay;
  }

  public long getTimestampMillis() {
    return timestamp;
  }

  public String getSource() {
    return source;
  }

  public int getSourceDevice() {
    return sourceDevice;
  }

  public byte[] getBody() {
    return message;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(type);
    dest.writeString(source);
    dest.writeInt(sourceDevice);
    dest.writeInt(relay == null ? 0 : 1);
    if (relay != null) {
      dest.writeString(relay);
    }
    dest.writeInt(message.length);
    dest.writeByteArray(message);
    dest.writeLong(timestamp);
  }

  public IncomingPushMessage withBody(byte[] body) {
    return new IncomingPushMessage(this, body);
  }

  public int getType() {
    return type;
  }

  public boolean isSecureMessage() {
    return getType() == IncomingPushMessageSignal.Type.CIPHERTEXT_VALUE;
  }

  public boolean isPreKeyBundle() {
    return getType() == IncomingPushMessageSignal.Type.PREKEY_BUNDLE_VALUE;
  }
}
