package org.whispersystems.signalservice.internal.serialize;

import org.junit.Test;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.serialize.protos.AddressProto;

import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public final class SignalServiceAddressProtobufSerializerTest {

  @Test
  public void serialize_and_deserialize_uuid_address() {
    SignalServiceAddress address      = new SignalServiceAddress(ACI.from(UUID.randomUUID()), Optional.empty());
    AddressProto         addressProto = org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.toProtobuf(address);
    SignalServiceAddress deserialized = org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.fromProtobuf(addressProto);

    assertEquals(address, deserialized);
  }

  @Test
  public void serialize_and_deserialize_both_address() {
    SignalServiceAddress address      = new SignalServiceAddress(ACI.from(UUID.randomUUID()), Optional.of("+15552345678"));
    AddressProto         addressProto = org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.toProtobuf(address);
    SignalServiceAddress deserialized = org.whispersystems.signalservice.internal.serialize.SignalServiceAddressProtobufSerializer.fromProtobuf(addressProto);

    assertEquals(address, deserialized);
  }
}
