package org.thoughtcrime.securesms.util

import android.os.Build
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

object SystemWindowInsetsSetter {
  /**
   * Updates the view whenever a layout occurs to properly set the system bar insets. setPadding is safe here because it will only trigger an extra layout
   * call IF the values actually changed.
   */
  fun attach(view: View, lifecycleOwner: LifecycleOwner, @WindowInsetsCompat.Type.InsetsType insetType: Int = WindowInsetsCompat.Type.systemBars()) {
    val listener = view.doOnEachLayout {
      val insets: Insets? = ViewCompat.getRootWindowInsets(view)?.getInsets(insetType)

      if (Build.VERSION.SDK_INT > 29 && insets != null && !insets.isEmpty()) {
        view.post {
          view.setPadding(
            insets.left,
            insets.top,
            insets.right,
            insets.bottom
          )
        }
      } else {
        val top = if (insetType and WindowInsetsCompat.Type.statusBars() != 0) {
          ViewUtil.getStatusBarHeight(view)
        } else {
          0
        }

        val bottom = if (insetType and WindowInsetsCompat.Type.navigationBars() != 0) {
          ViewUtil.getNavigationBarHeight(view)
        } else {
          0
        }

        view.post {
          view.setPadding(
            0,
            top,
            0,
            bottom
          )
        }
      }
    }

    val lifecycleObserver = object : DefaultLifecycleObserver {
      override fun onDestroy(owner: LifecycleOwner) {
        view.removeOnLayoutChangeListener(listener)
      }
    }

    lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
  }

  private fun Insets.isEmpty(): Boolean {
    return (top + bottom + right + left) == 0
  }
}
