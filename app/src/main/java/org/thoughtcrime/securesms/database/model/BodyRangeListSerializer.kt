package org.thoughtcrime.securesms.database.model

import org.signal.core.util.StringSerializer
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.util.Base64

object BodyRangeListSerializer : StringSerializer<BodyRangeList> {
  override fun serialize(data: BodyRangeList): String = Base64.encodeBytes(data.encode())
  override fun deserialize(data: String): BodyRangeList = BodyRangeList.ADAPTER.decode(Base64.decode(data))
}

fun BodyRangeList.serialize(): String {
  return BodyRangeListSerializer.serialize(this)
}
