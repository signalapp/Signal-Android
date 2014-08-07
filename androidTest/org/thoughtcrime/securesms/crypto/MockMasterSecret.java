package org.thoughtcrime.securesms.crypto;

import android.os.Parcel;

import org.whispersystems.textsecure.crypto.MasterSecret;

/**
 * Created by kaonashi on 8/6/14.
 */
public class MockMasterSecret {

  public static byte[] ENCRYPTION_KEY = new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                                                   (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b, (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f};

  public static byte[] MAC_KEY        = new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05, (byte) 0x06, (byte) 0x07,
                                                   (byte) 0x08, (byte) 0x09, (byte) 0x0a, (byte) 0x0b, (byte) 0x0c, (byte) 0x0d, (byte) 0x0e, (byte) 0x0f,
                                                   (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13};

  public static MasterSecret create() {
    Parcel parcel = null;
    try {
      parcel = Parcel.obtain();
      parcel.writeInt(ENCRYPTION_KEY.length);
      parcel.writeByteArray(ENCRYPTION_KEY);
      parcel.writeInt(MAC_KEY.length);
      parcel.writeByteArray(MAC_KEY);
      parcel.setDataPosition(0);

      return MasterSecret.CREATOR.createFromParcel(parcel);
    } finally {
      if (parcel != null) parcel.recycle();
    }
  }
}
