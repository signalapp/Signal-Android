package org.whispersystems.signalservice.api.messages.multidevice;

import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;

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
    Optional<ACI>              aciFirst      = Optional.of(ACI.from(UUID.randomUUID()));
    Optional<String>           e164First     = Optional.of("+1404555555");
    Optional<ACI>              aciSecond     = Optional.of(ACI.from(UUID.randomUUID()));
    Optional<String>           e164Second    = Optional.of("+1444555555");

    DeviceContact first = new DeviceContact(
        aciFirst,
        e164First,
        Optional.of("Teal'c"),
        Optional.empty(),
        Optional.of(0),
        Optional.of(0),
        Optional.of(0)
    );

    DeviceContact second = new DeviceContact(
        aciSecond,
        e164Second,
        Optional.of("Bra'tac"),
        Optional.empty(),
        Optional.of(0),
        Optional.of(0),
        Optional.of(0)
    );

    output.write(first);
    output.write(second);

    output.close();

    ByteArrayInputStream byteArrayIn = new ByteArrayInputStream(byteArrayOut.toByteArray());

    DeviceContactsInputStream input      = new DeviceContactsInputStream(byteArrayIn);
    DeviceContact             readFirst  = input.read();
    DeviceContact             readSecond = input.read();

    assertEquals(first.getAci(), readFirst.getAci());
    assertEquals(first.getE164(), readFirst.getE164());
    assertEquals(first.getName(), readFirst.getName());

    assertEquals(second.getAci(), readSecond.getAci());
    assertEquals(second.getE164(), readSecond.getE164());
    assertEquals(second.getName(), readSecond.getName());
  }
}