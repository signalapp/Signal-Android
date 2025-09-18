package org.thoughtcrime.securesms.stickers

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
class StickerLocator(
  @JvmField
  val packId: String,
  @JvmField
  val packKey: String,
  @JvmField
  val stickerId: Int,
  @JvmField
  val emoji: String?
) : Parcelable
