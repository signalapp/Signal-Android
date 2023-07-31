package org.whispersystems.signalservice.api.push

import com.google.protobuf.ByteString
import org.signal.libsignal.protocol.ServiceId.InvalidServiceIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.whispersystems.signalservice.api.util.UuidUtil
import java.lang.IllegalArgumentException
import java.util.UUID
import kotlin.jvm.Throws
import org.signal.libsignal.protocol.ServiceId as LibSignalServiceId
import org.signal.libsignal.protocol.ServiceId.Aci as LibSignalAci
import org.signal.libsignal.protocol.ServiceId.Pni as LibSignalPni

/**
 * A wrapper around a UUID that represents an identifier for an account. Today, that is either an [ACI] or a [PNI].
 * However, that doesn't mean every [ServiceId] is an *instance* of one of those classes. In reality, we often
 * do not know which we have. And it shouldn't really matter.
 *
 * The only times you truly know, and the only times you should actually care, is during CDS refreshes or specific inbound messages
 * that link them together.
 */
sealed class ServiceId(val libSignalServiceId: LibSignalServiceId) {
  companion object {
    @JvmStatic
    fun fromLibSignal(serviceId: LibSignalServiceId): ServiceId {
      return when (serviceId) {
        is LibSignalAci -> ACI(serviceId)
        is LibSignalPni -> PNI(serviceId)
        else -> throw IllegalArgumentException("Unknown libsignal ServiceId type!")
      }
    }

    /** Parses a ServiceId serialized as a string. Returns null if the ServiceId is invalid. */
    @JvmStatic
    fun parseOrNull(raw: String?): ServiceId? {
      if (raw == null) {
        return null
      }

      return try {
        fromLibSignal(LibSignalServiceId.parseFromString(raw))
      } catch (e: IllegalArgumentException) {
        null
      } catch (e: InvalidServiceIdException) {
        null
      }
    }

    /** Parses a ServiceId serialized as a byte array. Returns null if the ServiceId is invalid. */
    @JvmStatic
    fun parseOrNull(raw: ByteArray?): ServiceId? {
      if (raw == null) {
        return null
      }

      return try {
        fromLibSignal(LibSignalServiceId.parseFromBinary(raw))
      } catch (e: IllegalArgumentException) {
        null
      } catch (e: InvalidServiceIdException) {
        null
      }
    }

    /** Parses a ServiceId serialized as a ByteString. Returns null if the ServiceId is invalid. */
    @JvmStatic
    fun parseOrNull(bytes: ByteString): ServiceId? = parseOrNull(bytes.toByteArray())

    /** Parses a ServiceId serialized as a string. Crashes if the ServiceId is invalid. */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun parseOrThrow(raw: String): ServiceId = parseOrNull(raw) ?: throw IllegalArgumentException("Invalid ServiceId!")

    /** Parses a ServiceId serialized as a byte array. Crashes if the ServiceId is invalid. */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun parseOrThrow(raw: ByteArray): ServiceId = parseOrNull(raw) ?: throw IllegalArgumentException("Invalid ServiceId!")

    /** Parses a ServiceId serialized as a ByteString. Crashes if the ServiceId is invalid. */
    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun parseOrThrow(bytes: ByteString): ServiceId = parseOrThrow(bytes.toByteArray())
  }

  val rawUuid: UUID = libSignalServiceId.rawUUID

  val isUnknown: Boolean = rawUuid == UuidUtil.UNKNOWN_UUID

  val isValid: Boolean = !isUnknown

  fun toProtocolAddress(deviceId: Int): SignalProtocolAddress = SignalProtocolAddress(libSignalServiceId.toServiceIdString(), deviceId)

  fun toByteString(): ByteString = ByteString.copyFrom(libSignalServiceId.toServiceIdBinary())

  fun toByteArray(): ByteArray = libSignalServiceId.toServiceIdBinary()

  fun logString(): String = libSignalServiceId.toLogString()

  /**
   * A serialized string that can be parsed via [parseOrThrow], for instance.
   * Basically ACI's are just normal UUIDs, and PNI's are UUIDs with a `PNI:` prefix.
   */
  override fun toString(): String = libSignalServiceId.toServiceIdString()

  data class ACI(val libSignalAci: LibSignalAci) : ServiceId(libSignalAci) {
    companion object {
      @JvmField
      val UNKNOWN = from(UuidUtil.UNKNOWN_UUID)

      @JvmStatic
      fun from(uuid: UUID): ACI = ACI(LibSignalAci(uuid))

      @JvmStatic
      fun parseOrNull(raw: String?): ACI? = ServiceId.parseOrNull(raw).let { if (it is ACI) it else null }

      @JvmStatic
      fun parseOrNull(raw: ByteArray?): ACI? = ServiceId.parseOrNull(raw).let { if (it is ACI) it else null }

      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(raw: String?): ACI = parseOrNull(raw) ?: throw IllegalArgumentException("Invalid ACI!")

      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(raw: ByteArray?): ACI = parseOrNull(raw) ?: throw IllegalArgumentException("Invalid ACI!")

      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(bytes: ByteString): ACI = parseOrThrow(bytes.toByteArray())

      @JvmStatic
      fun parseOrUnknown(bytes: ByteString?): ACI = UuidUtil.fromByteStringOrNull(bytes)?.let { from(it) } ?: UNKNOWN

      @JvmStatic
      fun parseOrUnknown(raw: String?): ACI = parseOrNull(raw) ?: UNKNOWN
    }

    override fun toString(): String = super.toString()
  }

  data class PNI(val libSignalPni: LibSignalPni) : ServiceId(libSignalPni) {
    companion object {
      @JvmField
      var UNKNOWN = from(UuidUtil.UNKNOWN_UUID)

      @JvmStatic
      fun from(uuid: UUID): PNI = PNI(LibSignalPni(uuid))

      @JvmStatic
      fun parseOrNull(raw: String?): PNI? = ServiceId.parseOrNull(raw).let { if (it is PNI) it else null }

      /** Parses a plain UUID (without the `PNI:` prefix) as a PNI. Be certain that whatever you pass to this is for sure a PNI! */
      @JvmStatic
      fun parseUnPrefixedOrNull(raw: String?): PNI? {
        val uuid = UuidUtil.parseOrNull(raw)
        return if (uuid != null) {
          PNI(LibSignalPni(uuid))
        } else {
          null
        }
      }

      @JvmStatic
      fun parseOrNull(raw: ByteArray?): PNI? = ServiceId.parseOrNull(raw).let { if (it is PNI) it else null }

      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(raw: String?): PNI = parseOrNull(raw) ?: throw IllegalArgumentException("Invalid PNI!")

      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(raw: ByteArray?): PNI = parseOrNull(raw) ?: throw IllegalArgumentException("Invalid PNI!")

      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(bytes: ByteString): PNI = parseOrThrow(bytes.toByteArray())

      /** Parses a plain UUID (without the `PNI:` prefix) as a PNI. Be certain that whatever you pass to this is for sure a PNI! */
      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseUnPrefixedOrThrow(raw: String?): PNI = parseUnPrefixedOrNull(raw) ?: throw IllegalArgumentException("Invalid PNI!")
    }

    override fun toString(): String = super.toString()
  }
}
