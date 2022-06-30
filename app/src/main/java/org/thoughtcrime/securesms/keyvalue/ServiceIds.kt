package org.thoughtcrime.securesms.keyvalue

import com.google.protobuf.ByteString
import org.whispersystems.signalservice.api.push.ACI
import org.whispersystems.signalservice.api.push.PNI
import org.whispersystems.signalservice.api.util.UuidUtil
import java.util.UUID

/**
 * Helper for dealing with [ServiceId] matching when you only care that either of your
 * service ids match but don't care which one.
 */
data class ServiceIds(val aci: ACI, val pni: PNI?) {

  private val aciByteString: ByteString by lazy { UuidUtil.toByteString(aci.uuid()) }
  private val pniByteString: ByteString? by lazy { pni?.let { UuidUtil.toByteString(it.uuid()) } }

  fun matches(uuid: UUID): Boolean {
    return uuid == aci.uuid() || uuid == pni?.uuid()
  }

  fun matches(uuid: ByteString): Boolean {
    return uuid == aciByteString || uuid == pniByteString
  }
}
