package org.whispersystems.signalservice.internal.serialize;

import com.google.protobuf.ByteString;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceMetadata;
import org.whispersystems.signalservice.internal.serialize.protos.MetadataProto;

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
                                                 .setServerGuid(metadata.getServerGuid());

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
                                     Optional.fromNullable(metadata.getGroupId()).transform(ByteString::toByteArray));
  }
}
