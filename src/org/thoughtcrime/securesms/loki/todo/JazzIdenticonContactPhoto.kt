package org.thoughtcrime.securesms.loki.todo

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import network.loki.messenger.R
import org.thoughtcrime.securesms.contacts.avatars.FallbackContactPhoto

class JazzIdenticonContactPhoto(val hexEncodedPublicKey: String) : FallbackContactPhoto {
  override fun asDrawable(context: Context, color: Int): Drawable {
    return asDrawable(context, color, false)
  }

  override fun asDrawable(context: Context, color: Int, inverted: Boolean): Drawable {
    val targetSize = context.resources.getDimensionPixelSize(R.dimen.contact_photo_target_size)
    return JazzIdenticonDrawable(targetSize, targetSize, hexEncodedPublicKey)
  }

  override fun asCallCard(context: Context): Drawable? {
    return AppCompatResources.getDrawable(context, R.drawable.ic_person_large)
  }
}