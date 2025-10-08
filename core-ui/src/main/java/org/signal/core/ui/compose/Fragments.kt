/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.fragment.compose.FragmentState
import androidx.fragment.compose.rememberFragmentState
import org.signal.core.ui.compose.Fragments.Fragment

object Fragments {
  /**
   * Wraps an [Fragment], displaying the fragment at runtime or a placeholder in compose previews to avoid rendering errors that occur when
   * using [Fragment] in @Preview composables.
   */
  @Composable
  inline fun <reified T : Fragment> Fragment(
    modifier: Modifier = Modifier,
    fragmentState: FragmentState = rememberFragmentState(),
    arguments: Bundle = Bundle.EMPTY,
    noinline onUpdate: (T) -> Unit = { }
  ) {
    if (!LocalInspectionMode.current) {
      AndroidFragment(clazz = T::class.java, modifier, fragmentState, arguments, onUpdate)
    } else {
      Text(
        text = "[${T::class.simpleName}]",
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
          .fillMaxSize()
          .background(Color.Gray)
          .wrapContentSize(Alignment.Center)
      )
    }
  }

  /**
   * Wraps an [Fragment], displaying the fragment at runtime or a placeholder in compose previews to avoid rendering errors that occur when
   * using [Fragment] in @Preview composables.
   */
  @Composable
  fun <T : Fragment> Fragment(
    clazz: Class<T>,
    modifier: Modifier = Modifier,
    fragmentState: FragmentState = rememberFragmentState(),
    arguments: Bundle = Bundle.EMPTY,
    onUpdate: (T) -> Unit = { }
  ) {
    if (!LocalInspectionMode.current) {
      AndroidFragment(clazz = clazz, modifier, fragmentState, arguments, onUpdate)
    } else {
      Text(
        text = "[${clazz.simpleName}]",
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
          .fillMaxSize()
          .background(Color.Gray)
          .wrapContentSize(Alignment.Center)
      )
    }
  }
}
