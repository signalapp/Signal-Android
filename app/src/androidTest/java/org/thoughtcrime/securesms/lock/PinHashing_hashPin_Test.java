package org.thoughtcrime.securesms.lock;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.signal.core.util.Hex;
import org.signal.libsignal.svr2.PinHash;
import org.whispersystems.signalservice.api.kbs.KbsData;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.kbs.PinHashUtil;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class PinHashing_hashPin_Test {

  @Test
  public void argon2_hashed_pin_password() throws IOException {
    String    pin       = "password";
    byte[]    salt      = Hex.fromStringCondensed("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    MasterKey masterKey = new MasterKey(Hex.fromStringCondensed("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f"));

    PinHash hashedPin = PinHashUtil.hashPin(pin, salt);
    KbsData kbsData   = PinHashUtil.createNewKbsData(hashedPin, masterKey);

    assertArrayEquals(hashedPin.accessKey(), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("ab7e8499d21f80a6600b3b9ee349ac6d72c07e3359fe885a934ba7aa844429f8"), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("3f33ce58eb25b40436592a30eae2a8fabab1899095f4e2fba6e2d0dc43b4a2d9cac5a3931748522393951e0e54dec769"), kbsData.getCipherText());
    assertEquals(masterKey, kbsData.getMasterKey());

    String localPinHash = PinHashUtil.localPinHash(pin);
    assertTrue(PinHashUtil.verifyLocalPinHash(localPinHash, pin));
  }

  @Test
  public void argon2_hashed_pin_another_password() throws IOException {
    String    pin       = "anotherpassword";
    byte[]    salt      = Hex.fromStringCondensed("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f");
    MasterKey masterKey = new MasterKey(Hex.fromStringCondensed("88a787415a2ecd79da0d1016a82a27c5c695c9a19b88b0aa1d35683280aa9a67"));

    PinHash hashedPin = PinHashUtil.hashPin(pin, salt);
    KbsData kbsData   = PinHashUtil.createNewKbsData(hashedPin, masterKey);

    assertArrayEquals(hashedPin.accessKey(), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("301d9dd1e96f20ce51083f67d3298fd37b97525de8324d5e12ed2d407d3d927b"), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("9d9b05402ea39c17ff1c9298c8a0e86784a352aa02a74943bf8bcf07ec0f4b574a5b786ad0182c8d308d9eb06538b8c9"), kbsData.getCipherText());
    assertEquals(masterKey, kbsData.getMasterKey());

    String localPinHash = PinHashUtil.localPinHash(pin);
    assertTrue(PinHashUtil.verifyLocalPinHash(localPinHash, pin));
  }

  @Test
  public void argon2_hashed_pin_password_with_spaces_diacritics_and_non_arabic_numerals() throws IOException {
    String    pin       = " Pass६örd ";
    byte[]    salt      = Hex.fromStringCondensed("cba811749042b303a6a7efa5ccd160aea5e3ea243c8d2692bd13d515732f51a8");
    MasterKey masterKey = new MasterKey(Hex.fromStringCondensed("9571f3fde1e58588ba49bcf82be1b301ca3859a6f59076f79a8f47181ef952bf"));

    PinHash hashedPin = PinHashUtil.hashPin(pin, salt);
    KbsData kbsData   = PinHashUtil.createNewKbsData(hashedPin, masterKey);

    assertArrayEquals(hashedPin.accessKey(), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("ab645acdccc1652a48a34b2ac6926340ff35c03034013f68760f20013f028dd8"), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("11c0ba1834db15e47c172f6c987c64bd4cfc69c6047dd67a022afeec0165a10943f204d5b8f37b3cb7bab21c6dfc39c8"), kbsData.getCipherText());
    assertEquals(masterKey, kbsData.getMasterKey());

    assertEquals("577939bccb2b6638c39222d5a97998a867c5e154e30b82cc120f2dd07a3de987", kbsData.getMasterKey().deriveRegistrationLock());

    String localPinHash = PinHashUtil.localPinHash(pin);
    assertTrue(PinHashUtil.verifyLocalPinHash(localPinHash, pin));
  }

  @Test
  public void argon2_hashed_pin_password_with_just_non_arabic_numerals() throws IOException {
    String    pin       = " ६१८ ";
    byte[]    salt      = Hex.fromStringCondensed("717dc111a98423a57196512606822fca646c653facd037c10728f14ba0be2ab3");
    MasterKey masterKey = new MasterKey(Hex.fromStringCondensed("0432d735b32f66d0e3a70d4f9cc821a8529521a4937d26b987715d8eff4e4c54"));

    PinHash hashedPin = PinHashUtil.hashPin(pin, salt);
    KbsData kbsData   = PinHashUtil.createNewKbsData(hashedPin, masterKey);

    assertArrayEquals(hashedPin.accessKey(), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("d2fedabd0d4c17a371491c9722578843a26be3b4923e28d452ab2fc5491e794b"), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("877ef871ef1fc668401c717ef21aa12e8523579fb1ff4474b76f28c2293537c80cc7569996c9e0229bea7f378e3a824e"), kbsData.getCipherText());
    assertEquals(masterKey, kbsData.getMasterKey());

    assertEquals("23a75cb1df1a87df45cc2ed167c2bdc85ab1220b847c88761b0005cac907fce5", kbsData.getMasterKey().deriveRegistrationLock());

    String localPinHash = PinHashUtil.localPinHash(pin);
    assertTrue(PinHashUtil.verifyLocalPinHash(localPinHash, pin));
  }
}
