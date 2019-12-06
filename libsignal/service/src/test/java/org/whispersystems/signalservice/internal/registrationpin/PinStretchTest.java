package org.whispersystems.signalservice.internal.registrationpin;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.whispersystems.signalservice.internal.util.Hex;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class PinStretchTest {

  private final String pin;
  private final byte[] expectedStretchedPin;
  private final byte[] expectedKeyPin1;
  private final byte[] pinKey2;
  private final byte[] expectedMasterKey;
  private final String expectedRegistrationLock;
  private final byte[] expectedKbsAccessKey;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[]{
        "12345",
        "4e84b9b2567e1999f665a4288fbc98a30fd7c4a6a1b504b07e56d4183107ff1d",
        "0191747f14295c6c2d42af3ff94d610b7899d5eb6cccd14c71aa314f70aaaf0f",
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        "892f2cab29c09b13718e5f06a3e4aa0dd42cd7e0b20c411668eed10bb06f72b2",
        "65cdbc33682f3be3c8809f54ed41c8f2f85cfce23b77d2a8b435ccff9681071d",
        "7a2d4f7974c4c2314bee8e68d62a03fd97af0ef6904ee1b912dcc900c19215ba"
      },
      new Object[]{
        "12345",
        "4e84b9b2567e1999f665a4288fbc98a30fd7c4a6a1b504b07e56d4183107ff1d",
        "0191747f14295c6c2d42af3ff94d610b7899d5eb6cccd14c71aa314f70aaaf0f",
        "abababababababababababababababababababababababababababababababab",
        "01198dc427cbf9c6b47f344654d75a263e53b992db73be44b201f357d072dc38",
        "bd1f4e129cc705c26c2fcebd3fbc6e7db60caade89e6c465c68ed60aeedbb0c3",
        "7a2d4f7974c4c2314bee8e68d62a03fd97af0ef6904ee1b912dcc900c19215ba"
      },
      new Object[]{
        "١٢٣٤٥",
        "4e84b9b2567e1999f665a4288fbc98a30fd7c4a6a1b504b07e56d4183107ff1d",
        "0191747f14295c6c2d42af3ff94d610b7899d5eb6cccd14c71aa314f70aaaf0f",
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        "892f2cab29c09b13718e5f06a3e4aa0dd42cd7e0b20c411668eed10bb06f72b2",
        "65cdbc33682f3be3c8809f54ed41c8f2f85cfce23b77d2a8b435ccff9681071d",
        "7a2d4f7974c4c2314bee8e68d62a03fd97af0ef6904ee1b912dcc900c19215ba"
      },
      new Object[]{
        "9876543210",
        "1ec376ca694b5c1fb185be3864343aaa08829833153f3a72813e3e48cb3579b9",
        "40f35cdc3f3325b037f9fedddd25c68b7ea9c3e50e6a1a81319c43263da7bec3",
        "abababababababababababababababababababababababababababababababab",
        "127a435c15be2528f4b735423f8ee558b789e8ea1f6fe64d144d5b21a87c4e06",
        "348d327acb823b54a988cf6bea647a154e21da25cbb121a115c13b871dccd548",
        "90aaa3156952db441a8c875e8e4abab3d48965df7f563fbfb39f567d1ec7354e",
      },
      new Object[]{
        "9876543210",
        "1ec376ca694b5c1fb185be3864343aaa08829833153f3a72813e3e48cb3579b9",
        "40f35cdc3f3325b037f9fedddd25c68b7ea9c3e50e6a1a81319c43263da7bec3",
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        "128833dbde1af3da703852b6b5a845e226fe9c7e069427b9c1e41279c0cdfb3a",
        "6be0b17899cfb5c4316b92acc7db3b6a2fa5b9a19ef3e58a1c84a4de49230aa6",
        "90aaa3156952db441a8c875e8e4abab3d48965df7f563fbfb39f567d1ec7354e",
      },
      new Object[]{
        "0123",
        "b9bc227d893edc7cade32d16ba210599f9e901c721bcad85ad458ab90432cbe7",
        "bb8c8fc51b705dcdce43467ad7417fa5f28708941bcc9682fc4123a006701567",
        "abababababababababababababababababababababababababababababababab",
        "ca94b0a7b26d44078ccfcb88fd67151d891b3b8eb8c65ab94d536c3cb0e1d7dd",
        "d182bde40ee91969192d5166fc871cd4bf5e261b090bbc707354bddb29fb8290",
        "c0ae6e108296e507ee9ebd7fd5d8564b8e644bd53d50a2fc7ab379aea8074a91"
      },
      new Object[]{
        "௦௧௨௩",
        "b9bc227d893edc7cade32d16ba210599f9e901c721bcad85ad458ab90432cbe7",
        "bb8c8fc51b705dcdce43467ad7417fa5f28708941bcc9682fc4123a006701567",
        "abababababababababababababababababababababababababababababababab",
        "ca94b0a7b26d44078ccfcb88fd67151d891b3b8eb8c65ab94d536c3cb0e1d7dd",
        "d182bde40ee91969192d5166fc871cd4bf5e261b090bbc707354bddb29fb8290",
        "c0ae6e108296e507ee9ebd7fd5d8564b8e644bd53d50a2fc7ab379aea8074a91"
      });
  }

  public PinStretchTest(String pin,
                        String expectedStretchedPin,
                        String expectedKeyPin1,
                        String pinKey2,
                        String expectedMasterKey,
                        String expectedRegistrationLock,
                        String expectedKbsAccessKey) throws IOException {
    this.pin                      = pin;
    this.expectedStretchedPin     = Hex.fromStringCondensed(expectedStretchedPin);
    this.expectedKeyPin1          = Hex.fromStringCondensed(expectedKeyPin1);
    this.pinKey2                  = Hex.fromStringCondensed(pinKey2);
    this.expectedMasterKey        = Hex.fromStringCondensed(expectedMasterKey);
    this.expectedRegistrationLock = expectedRegistrationLock;
    this.expectedKbsAccessKey     = Hex.fromStringCondensed(expectedKbsAccessKey);
  }

  @Test
  public void stretch_pin() throws InvalidPinException {
    PinStretcher.StretchedPin stretchedPin = PinStretcher.stretchPin(pin);

    assertArrayEquals(expectedStretchedPin, stretchedPin.getStretchedPin());
    assertArrayEquals(expectedKeyPin1, stretchedPin.getPinKey1());
    assertArrayEquals(expectedKbsAccessKey, stretchedPin.getKbsAccessKey());

    PinStretcher.MasterKey masterKey = stretchedPin.withPinKey2(pinKey2);

    assertArrayEquals(pinKey2, masterKey.getPinKey2());
    assertArrayEquals(expectedMasterKey, masterKey.getMasterKey());
    assertEquals(expectedRegistrationLock, masterKey.getRegistrationLock());

    assertArrayEquals(expectedStretchedPin, masterKey.getStretchedPin());
    assertArrayEquals(expectedKeyPin1, masterKey.getPinKey1());
    assertArrayEquals(expectedKbsAccessKey, masterKey.getKbsAccessKey());
  }
}
