package org.thoughtcrime.securesms.mediapreview

import android.graphics.Bitmap

/**
 * Stores the bitmap for a thumbnail we are animating from via a shared
 * element transition. This prevents us from having to load anything on the
 * receiving end.
 */
object MediaPreviewCache {
  var bitmap: Bitmap? = null
}
