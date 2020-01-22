package org.thoughtcrime.securesms.lock;

import org.junit.Test;
import org.thoughtcrime.securesms.util.Hex;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.KbsData;
import org.whispersystems.signalservice.api.kbs.MasterKey;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class PinHashing_hashPin_Test {

  @Test
  public void argon2_hashed_pin_password() throws IOException {
    byte[]    backupId  = Hex.fromStringCondensed("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    MasterKey masterKey = new MasterKey(Hex.fromStringCondensed("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f"));

    HashedPin hashedPin = PinHashing.hashPin("password", () -> backupId);
    KbsData   kbsData   = hashedPin.createNewKbsData(masterKey);

    assertArrayEquals(Hex.fromStringCondensed("ab7e8499d21f80a6600b3b9ee349ac6d72c07e3359fe885a934ba7aa844429f8"), hashedPin.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("ab7e8499d21f80a6600b3b9ee349ac6d72c07e3359fe885a934ba7aa844429f8"), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("3f33ce58eb25b40436592a30eae2a8fabab1899095f4e2fba6e2d0dc43b4a2d9cac5a3931748522393951e0e54dec769"), kbsData.getCipherText());
    assertEquals(masterKey, kbsData.getMasterKey());
  }

  @Test
  public void argon2_hashed_pin_another_password() throws IOException {
    byte[]    backupId  = Hex.fromStringCondensed("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f");
    MasterKey masterKey = new MasterKey(Hex.fromStringCondensed("88a787415a2ecd79da0d1016a82a27c5c695c9a19b88b0aa1d35683280aa9a67"));

    HashedPin hashedPin = PinHashing.hashPin("anotherpassword ", () -> backupId);
    KbsData   kbsData   = hashedPin.createNewKbsData(masterKey);

    assertArrayEquals(Hex.fromStringCondensed("301d9dd1e96f20ce51083f67d3298fd37b97525de8324d5e12ed2d407d3d927b"), hashedPin.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("301d9dd1e96f20ce51083f67d3298fd37b97525de8324d5e12ed2d407d3d927b"), kbsData.getKbsAccessKey());
    assertArrayEquals(Hex.fromStringCondensed("9d9b05402ea39c17ff1c9298c8a0e86784a352aa02a74943bf8bcf07ec0f4b574a5b786ad0182c8d308d9eb06538b8c9"), kbsData.getCipherText());
    assertEquals(masterKey, kbsData.getMasterKey());
  }
}
