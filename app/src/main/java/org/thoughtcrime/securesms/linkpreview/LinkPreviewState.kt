/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.linkpreview

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.Optional

@Parcelize
class LinkPreviewState private constructor(
  @JvmField val activeUrlForError: String?,
  @JvmField val isLoading: Boolean,
  private val hasLinks: Boolean,
  private val preview: LinkPreview?,
  @JvmField val error: LinkPreviewRepository.Error?
) : Parcelable {

  @IgnoredOnParcel
  @JvmField
  val linkPreview: Optional<LinkPreview> = Optional.ofNullable(preview)

  fun hasLinks(): Boolean {
    return hasLinks
  }

  fun hasContent(): Boolean {
    return isLoading || hasLinks
  }

  companion object {
    @JvmStatic
    fun forLoading(): LinkPreviewState {
      return LinkPreviewState(
        activeUrlForError = null,
        isLoading = true,
        hasLinks = false,
        preview = null,
        error = null
      )
    }

    @JvmStatic
    fun forPreview(linkPreview: LinkPreview): LinkPreviewState {
      return LinkPreviewState(
        activeUrlForError = null,
        isLoading = false,
        hasLinks = true,
        preview = linkPreview,
        error = null
      )
    }

    @JvmStatic
    fun forLinksWithNoPreview(activeUrlForError: String?, error: LinkPreviewRepository.Error): LinkPreviewState {
      return LinkPreviewState(
        activeUrlForError = activeUrlForError,
        isLoading = false,
        hasLinks = true,
        preview = null,
        error = error
      )
    }

    @JvmStatic
    fun forNoLinks(): LinkPreviewState {
      return LinkPreviewState(
        activeUrlForError = null,
        isLoading = false,
        hasLinks = false,
        preview = null,
        error = null
      )
    }
  }
}
