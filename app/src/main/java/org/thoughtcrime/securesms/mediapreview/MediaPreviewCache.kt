package org.thoughtcrime.securesms.mediapreview

import android.graphics.drawable.Drawable

/**
 * Stores the bitmap for a thumbnail we are animating from via a shared
 * element transition. This prevents us from having to load anything on the
 * receiving end.
 */
object MediaPreviewCache {
  var drawable: Drawable? = null
}
