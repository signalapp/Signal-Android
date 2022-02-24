package org.thoughtcrime.securesms.util

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.fragment.app.Fragment

/**
 * "Mixin" to theme a Fragment with the given themeResId. This is making me wish Kotlin
 * had a stronger generic type system.
 */
object ThemedFragment {

  private const val UNSET = -1
  private const val THEME_RES_ID = "ThemedFragment::theme_res_id"

  @JvmStatic
  val Fragment.themeResId: Int
    get() = arguments?.getInt(THEME_RES_ID) ?: UNSET

  @JvmStatic
  fun Fragment.themedInflate(@LayoutRes layoutId: Int, inflater: LayoutInflater, container: ViewGroup?): View? {
    return if (themeResId != UNSET) {
      inflater.cloneInContext(ContextThemeWrapper(inflater.context, themeResId)).inflate(layoutId, container, false)
    } else {
      inflater.inflate(layoutId, container, false)
    }
  }

  @JvmStatic
  fun Fragment.withTheme(@StyleRes themeId: Int): Fragment {
    arguments = (arguments ?: Bundle()).apply {
      putInt(THEME_RES_ID, themeId)
    }
    return this
  }
}
