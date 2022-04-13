package org.whispersystems.signalservice.internal.serialize;

import com.google.protobuf.ByteString;

import org.whispersystems.signalservice.api.messages.SignalServiceMetadata;
import org.whispersystems.signalservice.internal.serialize.protos.MetadataProto;

import java.util.Optional;

public final class SignalServiceMetadataProtobufSerializer {

  private SignalServiceMetadataProtobufSerializer() {
  }

  public static MetadataProto toProtobuf(SignalServiceMetadata metadata) {
    MetadataProto.Builder builder = MetadataProto.newBuilder()
                                                 .setAddress(SignalServiceAddressProtobufSerializer.toProtobuf(metadata.getSender()))
                                                 .setSenderDevice(metadata.getSenderDevice())
                                                 .setNeedsReceipt(metadata.isNeedsReceipt())
                                                 .setTimestamp(metadata.getTimestamp())
                                                 .setServerReceivedTimestamp(metadata.getServerReceivedTimestamp())
                                                 .setServerDeliveredTimestamp(metadata.getServerDeliveredTimestamp())
                                                 .setServerGuid(metadata.getServerGuid())
                                                 .setDestinationUuid(metadata.getDestinationUuid());

    if (metadata.getGroupId().isPresent()) {
      builder.setGroupId(ByteString.copyFrom(metadata.getGroupId().get()));
    }

    return builder.build();
  }

  public static SignalServiceMetadata fromProtobuf(MetadataProto metadata) {
    return new SignalServiceMetadata(SignalServiceAddressProtobufSerializer.fromProtobuf(metadata.getAddress()),
                                     metadata.getSenderDevice(),
                                     metadata.getTimestamp(),
                                     metadata.getServerReceivedTimestamp(),
                                     metadata.getServerDeliveredTimestamp(),
                                     metadata.getNeedsReceipt(),
                                     metadata.getServerGuid(),
                                     Optional.ofNullable(metadata.getGroupId()).map(ByteString::toByteArray),
                                     metadata.getDestinationUuid());
  }
}
