package org.whispersystems.signalservice.api.push

import okio.ByteString
import org.signal.libsignal.protocol.ServiceId.InvalidServiceIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.logging.Log
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.api.push.ServiceId.PNI
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID
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
    private const val TAG = "ServiceId"

    @JvmStatic
    fun fromLibSignal(serviceId: LibSignalServiceId): ServiceId {
      return when (serviceId) {
        is LibSignalAci -> ACI(serviceId)
        is LibSignalPni -> PNI(serviceId)
        else -> throw IllegalArgumentException("Unknown libsignal ServiceId type!")
      }
    }

    /** Parses a ServiceId serialized as a string. Returns null if the ServiceId is invalid. */
    @JvmOverloads
    @JvmStatic
    fun parseOrNull(raw: String?, logFailures: Boolean = true): ServiceId? {
      if (raw == null) {
        return null
      }

      return try {
        fromLibSignal(LibSignalServiceId.parseFromString(raw))
      } catch (e: IllegalArgumentException) {
        if (logFailures) {
          Log.w(TAG, "[parseOrNull(String)] Illegal argument!", e)
        }
        null
      } catch (e: InvalidServiceIdException) {
        if (logFailures) {
          Log.w(TAG, "[parseOrNull(String)] Invalid ServiceId!", e)
        }
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
        if (raw.size == 17) {
          fromLibSignal(LibSignalServiceId.parseFromFixedWidthBinary(raw))
        } else {
          fromLibSignal(LibSignalServiceId.parseFromBinary(raw))
        }
      } catch (e: IllegalArgumentException) {
        Log.w(TAG, "[parseOrNull(Bytes)] Illegal argument!", e)
        null
      } catch (e: InvalidServiceIdException) {
        Log.w(TAG, "[parseOrNull(Bytes)] Invalid ServiceId!", e)
        null
      }
    }

    /** Parses a ServiceId serialized as a ByteString. Returns null if the ServiceId is invalid. */
    @JvmStatic
    fun parseOrNull(bytes: okio.ByteString?): ServiceId? = parseOrNull(bytes?.toByteArray())

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

  fun toByteString(): ByteString = ByteString.of(*libSignalServiceId.toServiceIdBinary())

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
      fun fromLibSignal(aci: LibSignalAci): ACI = ACI(aci)

      @JvmStatic
      fun parseOrNull(raw: String?): ACI? = ServiceId.parseOrNull(raw).let { if (it is ACI) it else null }

      @JvmStatic
      fun parseOrNull(raw: ByteArray?): ACI? = ServiceId.parseOrNull(raw).let { if (it is ACI) it else null }

      @JvmStatic
      fun parseOrNull(bytes: ByteString): ACI? = parseOrNull(bytes.toByteArray())

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

      /** Parses a string as a PNI, regardless if the `PNI:` prefix is present or not. Only use this if you are certain that what you're reading is a PNI. */
      @JvmStatic
      fun parseOrNull(raw: String?): PNI? {
        return if (raw == null) {
          null
        } else if (raw.startsWith("PNI:")) {
          return parsePrefixedOrNull(raw)
        } else {
          val uuid = UuidUtil.parseOrNull(raw)
          if (uuid != null) {
            PNI(LibSignalPni(uuid))
          } else {
            null
          }
        }
      }

      /** Parse a byte array as a PNI, regardless if it has the type prefix byte present or not. Only use this if you are certain what you're reading is a PNI. */
      @JvmStatic
      fun parseOrNull(raw: ByteArray?): PNI? {
        return if (raw == null) {
          null
        } else if (raw.size == 17) {
          ServiceId.parseOrNull(raw).let { if (it is PNI) it else null }
        } else {
          val uuid = UuidUtil.parseOrNull(raw)
          if (uuid != null) {
            PNI(LibSignalPni(uuid))
          } else {
            null
          }
        }
      }

      /** Parses a [ByteString] as a PNI, regardless if the `PNI:` prefix is present or not. Only use this if you are certain that what you're reading is a PNI. */
      @JvmStatic
      fun parseOrNull(bytes: ByteString): PNI? = parseOrNull(bytes.toByteArray())

      /** Parses a string as a PNI, regardless if the `PNI:` prefix is present or not. Only use this if you are certain that what you're reading is a PNI. */
      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(raw: String?): PNI = parseOrNull(raw) ?: throw IllegalArgumentException("Invalid PNI!")

      /** Parse a byte array as a PNI, regardless if it has the type prefix byte present or not. Only use this if you are certain what you're reading is a PNI. */
      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(raw: ByteArray?): PNI = parseOrNull(raw) ?: throw IllegalArgumentException("Invalid PNI!")

      /** Parse a byte string as a PNI, regardless if it has the type prefix byte present or not. Only use this if you are certain what you're reading is a PNI. */
      @JvmStatic
      @Throws(IllegalArgumentException::class)
      fun parseOrThrow(bytes: ByteString): PNI = parseOrThrow(bytes.toByteArray())

      /** Parses a string as a PNI, expecting that the value has a `PNI:` prefix. If it does not have the prefix (or is otherwise invalid), this will return null. */
      fun parsePrefixedOrNull(raw: String?): PNI? = ServiceId.parseOrNull(raw).let { if (it is PNI) it else null }
    }

    override fun toString(): String = super.toString()

    /** String version without the PNI: prefix. This is only for specific proto fields. For application storage, prefer [toString]. */
    fun toStringWithoutPrefix(): String = rawUuid.toString()
  }
}
