package org.signal.registration.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import org.signal.registration.R
import org.signal.registration.RegistrationDependencies

private const val DEBUG_TAP_TARGET = 8
private const val DEBUG_TAP_ANNOUNCE = 4

/**
 * Can be added to a composable such that if it tapped eight times, it will generate a debug log
 */
@Composable
fun Modifier.attachDebugLogHelper(): Modifier = composed {
  val context = LocalContext.current
  val resources = LocalResources.current

  var tapCount by remember { mutableIntStateOf(0) }
  var toast: Toast? = null

  clickable(
    indication = null,
    interactionSource = remember { MutableInteractionSource() }
  ) {
    tapCount++

    if (tapCount >= DEBUG_TAP_TARGET) {
      RegistrationDependencies.get().debugLogCallback?.invoke(context)
      tapCount = 0
    } else if (tapCount >= DEBUG_TAP_ANNOUNCE) {
      val remaining = DEBUG_TAP_TARGET - tapCount
      toast?.cancel()
      toast = Toast.makeText(
        context,
        resources.getQuantityString(R.plurals.RegistrationActivity_debug_log_hint, remaining, remaining),
        Toast.LENGTH_SHORT
      )
      toast?.show()
    }
  }
}
