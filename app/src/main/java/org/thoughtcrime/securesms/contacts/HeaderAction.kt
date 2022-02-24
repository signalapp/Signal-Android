package org.thoughtcrime.securesms.contacts

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * An action which can be attached to the first item in the list, but only if that item is a divider.
 */
class HeaderAction(@param:StringRes val label: Int, @param:DrawableRes val icon: Int, val action: Runnable) {
  constructor(@StringRes label: Int, action: Runnable) : this(label, 0, action) {}
}
