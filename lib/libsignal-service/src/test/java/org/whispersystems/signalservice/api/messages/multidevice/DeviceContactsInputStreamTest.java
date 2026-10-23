package org.whispersystems.signalservice.api.messages.multidevice;

import org.junit.Test;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.core.models.ServiceId.ACI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
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

  @Test
  public void read_closingAnAvatarStream_doesNotCloseTheContactsStream() throws IOException {
    ByteArrayOutputStream      byteArrayOut = new ByteArrayOutputStream();
    DeviceContactsOutputStream output       = new DeviceContactsOutputStream(byteArrayOut);
    byte[]                     avatarData   = new byte[] { 1, 2, 3, 4, 5 };

    DeviceContact first = new DeviceContact(
        Optional.of(ACI.from(UUID.randomUUID())),
        Optional.of("+1404555555"),
        Optional.of("Teal'c"),
        Optional.of(new DeviceContactAvatar(new ByteArrayInputStream(avatarData), avatarData.length, "image/*")),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );

    DeviceContact second = new DeviceContact(
        Optional.of(ACI.from(UUID.randomUUID())),
        Optional.of("+1444555555"),
        Optional.of("Bra'tac"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );

    output.write(first);
    output.write(second);
    output.close();

    DeviceContactsInputStream input     = new DeviceContactsInputStream(new FailsAfterCloseInputStream(new ByteArrayInputStream(byteArrayOut.toByteArray())));
    DeviceContact             readFirst = input.read();

    InputStream avatarStream = readFirst.getAvatar().get().getInputStream();
    assertArrayEquals(avatarData, avatarStream.readAllBytes());
    avatarStream.close();

    DeviceContact readSecond = input.read();

    assertEquals(second.getAci(), readSecond.getAci());
    assertEquals(second.getName(), readSecond.getName());
  }

  @Test
  public void read_contactWithOnlyE164() throws IOException {
    ByteArrayOutputStream      byteArrayOut = new ByteArrayOutputStream();
    DeviceContactsOutputStream output       = new DeviceContactsOutputStream(byteArrayOut);

    DeviceContact e164Only = new DeviceContact(
        Optional.empty(),
        Optional.of("+1404555555"),
        Optional.of("Teal'c"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );

    output.write(e164Only);
    output.close();

    DeviceContactsInputStream input = new DeviceContactsInputStream(new ByteArrayInputStream(byteArrayOut.toByteArray()));
    DeviceContact             read  = input.read();

    assertEquals(Optional.empty(), read.getAci());
    assertEquals(e164Only.getE164(), read.getE164());
    assertEquals(e164Only.getName(), read.getName());
  }

  @Test
  public void read_skipsAnUnreadAvatar() throws IOException {
    ByteArrayOutputStream      byteArrayOut = new ByteArrayOutputStream();
    DeviceContactsOutputStream output       = new DeviceContactsOutputStream(byteArrayOut);
    byte[]                     avatarData   = new byte[] { 1, 2, 3, 4, 5 };

    DeviceContact first = new DeviceContact(
        Optional.of(ACI.from(UUID.randomUUID())),
        Optional.empty(),
        Optional.empty(),
        Optional.of(new DeviceContactAvatar(new ByteArrayInputStream(avatarData), avatarData.length, "image/*")),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );

    DeviceContact second = new DeviceContact(
        Optional.of(ACI.from(UUID.randomUUID())),
        Optional.empty(),
        Optional.of("Bra'tac"),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );

    output.write(first);
    output.write(second);
    output.close();

    DeviceContactsInputStream input = new DeviceContactsInputStream(new ByteArrayInputStream(byteArrayOut.toByteArray()));
    input.read();
    DeviceContact readSecond = input.read();

    assertEquals(second.getAci(), readSecond.getAci());
    assertEquals(second.getName(), readSecond.getName());
  }

  private static class FailsAfterCloseInputStream extends FilterInputStream {
    private boolean closed = false;

    FailsAfterCloseInputStream(InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      if (closed) throw new IOException("Stream Closed");
      return super.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (closed) throw new IOException("Stream Closed");
      return super.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }
}