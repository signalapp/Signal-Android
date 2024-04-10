/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.animation.transitions

import android.content.Context
import android.util.AttributeSet

/**
 * Will only transition [android.widget.ImageView]s that contain a [androidx.core.graphics.drawable.RoundedBitmapDrawable].
 */
class CircleToSquareImageViewTransition(
  context: Context,
  attrs: AttributeSet
) : CircleSquareImageViewTransition(false)
