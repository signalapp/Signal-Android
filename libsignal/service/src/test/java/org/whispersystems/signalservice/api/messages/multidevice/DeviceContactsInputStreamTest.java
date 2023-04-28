package org.whispersystems.signalservice.api.messages.multidevice;

import org.junit.Test;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.ecc.Curve;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class DeviceContactsInputStreamTest {

  @Test
  public void read() throws IOException, InvalidInputException {
    ByteArrayOutputStream      byteArrayOut  = new ByteArrayOutputStream();
    DeviceContactsOutputStream output        = new DeviceContactsOutputStream(byteArrayOut);
    SignalServiceAddress       addressFirst  = new SignalServiceAddress(ServiceId.from(UUID.randomUUID()), "+1404555555");
    SignalServiceAddress       addressSecond = new SignalServiceAddress(ServiceId.from(UUID.randomUUID()), "+1444555555");

    DeviceContact first = new DeviceContact(
        addressFirst,
        Optional.of("Teal'c"),
        Optional.empty(),
        Optional.of("ultramarine"),
        Optional.of(new VerifiedMessage(addressFirst, generateIdentityKey(), VerifiedMessage.VerifiedState.DEFAULT, System.currentTimeMillis())),
        Optional.of(generateProfileKey()),
        false,
        Optional.of(0),
        Optional.of(0),
        false
    );

    DeviceContact second = new DeviceContact(
        addressSecond,
        Optional.of("Bra'tac"),
        Optional.empty(),
        Optional.of("ultramarine"),
        Optional.of(new VerifiedMessage(addressSecond, generateIdentityKey(), VerifiedMessage.VerifiedState.DEFAULT, System.currentTimeMillis())),
        Optional.of(generateProfileKey()),
        false,
        Optional.of(0),
        Optional.of(0),
        false
    );

    output.write(first);
    output.write(second);

    output.close();

    ByteArrayInputStream byteArrayIn = new ByteArrayInputStream(byteArrayOut.toByteArray());

    DeviceContactsInputStream input      = new DeviceContactsInputStream(byteArrayIn);
    DeviceContact             readFirst  = input.read();
    DeviceContact             readSecond = input.read();

    assertEquals(first.getAddress(), readFirst.getAddress());
    assertEquals(first.getName(), readFirst.getName());
    assertEquals(first.getColor(), readFirst.getColor());
    assertEquals(first.getVerified().get().getIdentityKey(), readFirst.getVerified().get().getIdentityKey());
    assertEquals(first.isArchived(), readFirst.isArchived());

    assertEquals(second.getAddress(), readSecond.getAddress());
    assertEquals(second.getName(), readSecond.getName());
    assertEquals(second.getColor(), readSecond.getColor());
    assertEquals(second.getVerified().get().getIdentityKey(), readSecond.getVerified().get().getIdentityKey());
    assertEquals(second.isArchived(), readSecond.isArchived());
  }

  private static IdentityKey generateIdentityKey() {
    ECKeyPair djbKeyPair = Curve.generateKeyPair();
    return new IdentityKey(djbKeyPair.getPublicKey());
  }

  private static ProfileKey generateProfileKey() throws InvalidInputException {
     return new ProfileKey(Util.getSecretBytes(32));
  }
}
