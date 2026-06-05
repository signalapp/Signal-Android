package org.thoughtcrime.securesms.database.model

import org.thoughtcrime.securesms.megaphone.Megaphones

data class MegaphoneRecord(
  val event: Megaphones.Event,
  val interactionCount: Int,
  val lastInteractionTime: Long,
  val firstVisible: Long,
  val lastVisible: Long,
  val finished: Boolean
)
