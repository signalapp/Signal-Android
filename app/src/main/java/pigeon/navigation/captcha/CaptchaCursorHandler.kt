package pigeon.navigation.captcha

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

class CaptchaCursorHandler(private val captchaWebView: WebView?, private val mouseCursor: View?) {

  private val cursorMarginX = 4f
  private val cursorMarginY = 7f
  private val cursorMove = 7f
  private val viewCursorStep = 10

  fun onKeyDown(keyCode: Int, action: Int) {
    if (captchaWebView == null) {
      return
    }

    if (mouseCursor == null) {
      return
    }

    var x: Float = mouseCursor.x
    var y: Float = mouseCursor.y
    when (keyCode) {
      KeyEvent.KEYCODE_0 -> {
        captchaWebView.reload()
      }
      KeyEvent.KEYCODE_2 -> {
        if (action == KeyEvent.ACTION_UP) return
        y -= cursorMove
        if (y + cursorMarginY < 0) {
          captchaWebView.scrollBy(0, -viewCursorStep)
          mouseCursor.y = -cursorMarginY
        } else {
          mouseCursor.y = y
        }
      }
      KeyEvent.KEYCODE_4 -> {
        if (action == KeyEvent.ACTION_UP) return
        x -= cursorMove
        mouseCursor.x = if (x + cursorMarginX < 0) -cursorMarginX else x
      }
      KeyEvent.KEYCODE_6 -> {
        if (action == KeyEvent.ACTION_UP) return
        x += cursorMove
        mouseCursor.x = if (x + cursorMarginX > 320 - 20) 320 - cursorMarginX - 20 else x
      }
      KeyEvent.KEYCODE_8 -> {
        if (action == KeyEvent.ACTION_UP) return
        y += cursorMove
        if (y > 240 - cursorMarginY - 20) {
          captchaWebView.scrollBy(0, viewCursorStep)
          mouseCursor.y = 240 - cursorMarginY - 20
        } else {
          mouseCursor.y = y
        }
      }
      KeyEvent.KEYCODE_5 -> {
        captchaWebView
          .dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            action,
            mouseCursor.x + cursorMarginX,
            mouseCursor.y + cursorMarginY,
            0))
      }
      KeyEvent.KEYCODE_APOSTROPHE -> {
        if (captchaWebView.canGoBack()) {
          captchaWebView.goBack()
        }
      }
    }
  }
}