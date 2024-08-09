/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.attachments

import org.signal.core.util.IntSerializer

/**
 * Attachments/media can come from and go to multiple CDN locations depending on when and where
 * they were uploaded. This class represents the CDNs where attachments/media can live.
 */
enum class Cdn(private val value: Int) {
  S3(-1),
  CDN_0(0),
  CDN_2(2),
  CDN_3(3);

  val cdnNumber: Int
    get() {
      return when (this) {
        S3 -> -1
        CDN_0 -> 0
        CDN_2 -> 2
        CDN_3 -> 3
      }
    }

  fun serialize(): Int {
    return Serializer.serialize(this)
  }

  companion object Serializer : IntSerializer<Cdn> {
    override fun serialize(data: Cdn): Int {
      return data.value
    }

    override fun deserialize(data: Int): Cdn {
      return values().first { it.value == data }
    }

    fun fromCdnNumber(cdnNumber: Int): Cdn {
      return when (cdnNumber) {
        -1 -> S3
        0 -> CDN_0
        2 -> CDN_2
        3 -> CDN_3
        else -> throw UnsupportedOperationException("Invalid CDN number: $cdnNumber")
      }
    }
  }
}
