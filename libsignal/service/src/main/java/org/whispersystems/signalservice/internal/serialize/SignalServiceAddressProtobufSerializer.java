package org.whispersystems.signalservice.internal.serialize;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.serialize.protos.AddressProto;

public final class SignalServiceAddressProtobufSerializer {

  private SignalServiceAddressProtobufSerializer() {
  }

  public static AddressProto toProtobuf(SignalServiceAddress signalServiceAddress) {
    AddressProto.Builder builder = AddressProto.newBuilder();

    builder.setUuid(signalServiceAddress.getServiceId().toByteString());

    if(signalServiceAddress.getNumber().isPresent()){
      builder.setE164(signalServiceAddress.getNumber().get());
    }

    return builder.build();
  }

  public static SignalServiceAddress fromProtobuf(AddressProto addressProto) {
    ServiceId        serviceId = ServiceId.parseOrThrow(addressProto.getUuid().toByteArray());
    Optional<String> number    = addressProto.hasE164()  ? Optional.of(addressProto.getE164()) : Optional.absent();

    return new SignalServiceAddress(serviceId, number);
  }
}
