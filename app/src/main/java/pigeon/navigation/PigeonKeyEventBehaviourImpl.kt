package pigeon.navigation

import android.view.KeyEvent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.fragments.CaptchaFragment

class PigeonKeyEventBehaviourImpl : KeyEventBehaviour {
  override fun dispatchKeyEvent(event: KeyEvent, fragmentManager: FragmentManager) {
    val navFragment: Fragment = fragmentManager.findFragmentById(R.id.nav_host_fragment) ?: return
    val fragment = navFragment.childFragmentManager.primaryNavigationFragment

    when (event.keyCode) {
      KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_0 -> {

        if (fragment is CaptchaFragment) {
          fragment.onKeyDown(event.keyCode, event.action)
          return
        }
      }
    }
  }
}