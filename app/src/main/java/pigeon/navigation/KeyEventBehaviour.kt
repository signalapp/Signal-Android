package pigeon.navigation

import android.view.KeyEvent
import androidx.fragment.app.FragmentManager

open interface KeyEventBehaviour {
  fun dispatchKeyEvent(event: KeyEvent, fragmentManager: FragmentManager)
}