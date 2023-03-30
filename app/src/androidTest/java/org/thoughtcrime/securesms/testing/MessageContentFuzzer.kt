package org.thoughtcrime.securesms.testing

import com.google.protobuf.ByteString
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.model.toProtoByteString
import org.thoughtcrime.securesms.messages.TestMessage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.AttachmentPointer
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Content
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.DataMessage
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.jvmErasure
import kotlin.time.Duration.Companion.days

object MessageContentFuzzer {

  private val mediaTypes = listOf("image/png", "image/jpeg", "image/heic", "image/heif", "image/avif", "image/webp", "image/gif", "audio/aac", "audio/*", "video/mp4", "video/*", "text/x-vcard", "text/x-signal-plain", "application/x-signal-view-once", "*/*", "application/octet-stream")
  private val emojis = listOf("😂", "❤️", "🔥", "😍", "👀", "🤔", "🙏", "👍", "🤷", "🥺")

  private val random = Random(1)

  fun envelope(timestamp: Long): Envelope {
    return Envelope.newBuilder()
      .setTimestamp(timestamp)
      .setServerTimestamp(timestamp + 5)
      .setServerGuidBytes(UuidUtil.toByteString(UUID.randomUUID()))
      .build()
  }

  fun fuzzTextMessage(): Content {
    return Content.newBuilder()
      .setDataMessage(
        DataMessage.newBuilder().run {
          body = string()
          if (random.nextBoolean()) {
            expireTimer = random.nextInt(0..28.days.inWholeSeconds.toInt())
          }
          if (random.nextBoolean()) {
            addBodyRanges(
              SignalServiceProtos.BodyRange.newBuilder().run {
                start = 0
                length = 1
                style = SignalServiceProtos.BodyRange.Style.BOLD
                build()
              }
            )
          }
          build()
        }
      )
      .build()
  }

  fun fuzzMediaMessageWithBody(quoteAble: List<TestMessage> = emptyList()): Content {
    return Content.newBuilder()
      .setDataMessage(
        DataMessage.newBuilder().run {
          if (random.nextBoolean()) {
            body = string()
          }

          if (random.nextBoolean() && quoteAble.isNotEmpty()) {
            body = string()
            val quoted = quoteAble.random(random)
            quote = DataMessage.Quote.newBuilder().run {
              id = quoted.envelope.timestamp
              authorUuid = quoted.metadata.sourceServiceId.toString()
              text = quoted.content.dataMessage.body
              addAllAttachments(quoted.content.dataMessage.attachmentsList)
              addAllBodyRanges(quoted.content.dataMessage.bodyRangesList)
              type = DataMessage.Quote.Type.NORMAL
              build()
            }
          }

          if (random.nextFloat() < 0.1 && quoteAble.isNotEmpty()) {
            val quoted = quoteAble.random(random)
            quote = DataMessage.Quote.newBuilder().run {
              id = random.nextLong(quoted.envelope.timestamp - 1000000, quoted.envelope.timestamp)
              authorUuid = quoted.metadata.sourceServiceId.toString()
              text = quoted.content.dataMessage.body
              build()
            }
          }

          if (random.nextFloat() < 0.25) {
            val total = random.nextInt(1, 2)
            (0..total).forEach { _ -> addAttachments(attachmentPointer()) }
          }

          build()
        }
      )
      .build()
  }

  fun fuzzMediaMessageNoContent(previousMessages: List<TestMessage> = emptyList()): Content {
    return Content.newBuilder()
      .setDataMessage(
        DataMessage.newBuilder().run {
          if (random.nextFloat() < 0.25) {
            val reactTo = previousMessages.random(random)
            reaction = DataMessage.Reaction.newBuilder().run {
              emoji = emojis.random(random)
              remove = false
              targetAuthorUuid = reactTo.metadata.sourceServiceId.toString()
              targetSentTimestamp = reactTo.envelope.timestamp
              build()
            }
          }

          build()
        }
      ).build()
  }

  fun fuzzMediaMessageNoText(previousMessages: List<TestMessage> = emptyList()): Content {
    return Content.newBuilder()
      .setDataMessage(
        DataMessage.newBuilder().run {
          if (random.nextFloat() < 0.9) {
            sticker = DataMessage.Sticker.newBuilder().run {
              packId = byteString(length = 24)
              packKey = byteString(length = 128)
              stickerId = random.nextInt()
              data = attachmentPointer()
              emoji = emojis.random(random)
              build()
            }
          }
          build()
        }
      ).build()
  }

  fun string(length: Int = 10, allowNullString: Boolean = false): String {
    var string = ""

    if (allowNullString && random.nextBoolean()) {
      return string
    }

    for (i in 0 until length) {
      string += random.nextInt(65..90).toChar()
    }
    return string
  }

  fun byteString(length: Int = 512): ByteString {
    return random.nextBytes(512).toProtoByteString()
  }

  fun attachmentPointer(): SignalServiceProtos.AttachmentPointer {
    return SignalServiceProtos.AttachmentPointer.newBuilder().run {
      cdnKey = string()
      contentType = mediaTypes.random(random)
      key = byteString()
      size = random.nextInt(1024 * 1024 * 50)
      thumbnail = byteString()
      digest = byteString()
      fileName = string()
      flags = 0
      width = random.nextInt(until = 1024)
      height = random.nextInt(until = 1024)
      caption = string(allowNullString = true)
      blurHash = string()
      uploadTimestamp = random.nextLong()
      cdnNumber = 1

      build()
    }
  }

  fun fuzzServerDeliveredTimestamp(envelopeTimestamp: Long): Long {
    return envelopeTimestamp + 10
  }

  fun fuzzMetadata(source: RecipientId, destination: RecipientId): EnvelopeMetadata {
    return EnvelopeMetadata(
      sourceServiceId = Recipient.resolved(source).requireServiceId(),
      sourceE164 = null,
      sourceDeviceId = 1,
      sealedSender = true,
      groupId = null,
      destinationServiceId = Recipient.resolved(destination).requireServiceId()
    )
  }

  fun <T : Any> fuzzProto(protoClazz: KClass<T>) {
    val newBuilder: Any = protoClazz.declaredFunctions.first { it.name == "newBuilder" }.call()!!

    val setters: List<KFunction<*>> = newBuilder::class.functions.filter { it.name.startsWith("set") && !it.name.contains("Bytes") }

    for (setter in setters) {
      val type = setter.parameters[1].type.jvmErasure
      when {
        type == String::class -> setter.call(newBuilder, string())
        type == Int::class -> setter.call(newBuilder, random.nextInt())
        type == Long::class -> setter.call(newBuilder, random.nextLong())
        type == AttachmentPointer::class -> setter.call(newBuilder, attachmentPointer())
        type == Boolean::class -> setter.call(newBuilder, random.nextBoolean())
//        type.superclasses.contains(EnumLite::class) ->
        else -> Log.e("CODY", "WHAT!?!?!?! ${setter.parameters[1].type.jvmErasure}")
      }
    }

    Log.e("CODY", newBuilder::class.functions.first { it.name == "build" }.call(newBuilder).toString())
  }
}
