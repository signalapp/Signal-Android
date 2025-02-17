package org.thoughtcrime.securesms.database.model

data class IncomingSticker(
  val packId: String,
  val packKey: String,
  val packTitle: String,
  val packAuthor: String,
  val stickerId: Int,
  val emoji: String,
  val contentType: String?,
  val isCover: Boolean,
  val isInstalled: Boolean
)
