package org.thoughtcrime.securesms.testing

import com.google.protobuf.ByteString
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2
import org.whispersystems.signalservice.internal.serialize.protos.AddressProto
import org.whispersystems.signalservice.internal.serialize.protos.MetadataProto
import org.whispersystems.signalservice.internal.serialize.protos.SignalServiceContentProto
import java.util.UUID
import kotlin.random.Random

class TestProtos private constructor() {
  fun address(
    uuid: UUID = UUID.randomUUID()
  ): AddressProto.Builder {
    return AddressProto.newBuilder()
      .setUuid(ServiceId.from(uuid).toByteString())
  }

  fun metadata(
    address: AddressProto = address().build(),
  ): MetadataProto.Builder {
    return MetadataProto.newBuilder()
      .setAddress(address)
  }

  fun groupContextV2(
    revision: Int = 0,
    masterKeyBytes: ByteArray = Random.Default.nextBytes(GroupMasterKey.SIZE)
  ): GroupContextV2.Builder {
    return GroupContextV2.newBuilder()
      .setRevision(revision)
      .setMasterKey(ByteString.copyFrom(masterKeyBytes))
  }

  fun storyContext(
    sentTimestamp: Long = Random.nextLong(),
    authorUuid: String = UUID.randomUUID().toString()
  ): DataMessage.StoryContext.Builder {
    return DataMessage.StoryContext.newBuilder()
      .setAuthorUuid(authorUuid)
      .setSentTimestamp(sentTimestamp)
  }

  fun dataMessage(): DataMessage.Builder {
    return DataMessage.newBuilder()
  }

  fun content(): SignalServiceProtos.Content.Builder {
    return SignalServiceProtos.Content.newBuilder()
  }

  fun serviceContent(
    localAddress: AddressProto = address().build(),
    metadata: MetadataProto = metadata().build()
  ): SignalServiceContentProto.Builder {
    return SignalServiceContentProto.newBuilder()
      .setLocalAddress(localAddress)
      .setMetadata(metadata)
  }

  companion object {
    fun <T> build(buildFn: TestProtos.() -> T): T {
      return TestProtos().buildFn()
    }
  }
}
