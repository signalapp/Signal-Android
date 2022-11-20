package org.thoughtcrime.securesms.mediasend

import android.content.Context
import androidx.annotation.WorkerThread

interface MediaTransform {

  @WorkerThread
  fun transform(context: Context, media: Media): Media
}