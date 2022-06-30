package org.thoughtcrime.securesms.database.model

import org.signal.core.util.LongSerializer

/**
 * A list can explicit ([ONLY_WITH]) where only members of the list can send or exclusionary ([ALL_EXCEPT]) where
 * all connections are sent the story except for those members of the list. [ALL] is all of your Signal Connections.
 */
enum class DistributionListPrivacyMode(private val code: Long) {
  ONLY_WITH(0),
  ALL_EXCEPT(1),
  ALL(2);

  val isBlockList: Boolean
    get() = this != ONLY_WITH

  fun serialize(): Long {
    return code
  }

  companion object Serializer : LongSerializer<DistributionListPrivacyMode> {
    override fun serialize(data: DistributionListPrivacyMode): Long {
      return data.serialize()
    }

    override fun deserialize(data: Long): DistributionListPrivacyMode {
      return when (data) {
        ONLY_WITH.code -> ONLY_WITH
        ALL_EXCEPT.code -> ALL_EXCEPT
        ALL.code -> ALL
        else -> throw AssertionError("Unknown privacy mode: $data")
      }
    }
  }
}
