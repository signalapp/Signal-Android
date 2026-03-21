/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.localbackuprestore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import android.graphics.Rect as ViewRect
import androidx.compose.ui.geometry.Rect as ComposeRect

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("NewApi")
@Composable
fun backupKeyAutoFillHelper(
  onFill: (String) -> Unit
): BackupKeyAutoFillHelper {
  val view = LocalView.current
  val context = LocalContext.current
  val autofill: Autofill? = LocalAutofill.current

  val node = remember { AutofillNode(autofillTypes = listOf(AutofillType.Password), onFill = onFill) }
  LocalAutofillTree.current += node

  return remember {
    object : BackupKeyAutoFillHelper(context) {
      override fun request() {
        if (node.boundingBox != null) {
          autofill?.requestAutofillForNode(node)
        }
      }

      override fun cancel() {
        autofill?.cancelAutofillForNode(node)
      }

      override fun requestDirectly() {
        val bounds = node.boundingBox?.let { ViewRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) }
        if (bounds != null) {
          autoFillManager?.requestAutofill(view, node.id, bounds)
        }
      }

      override fun updateNodeBounds(boundsInWindow: ComposeRect) {
        node.boundingBox = boundsInWindow
      }
    }
  }
}

fun Modifier.attachBackupKeyAutoFillHelper(helper: BackupKeyAutoFillHelper): Modifier {
  return this.then(
    Modifier
      .onFocusChanged {
        if (it.isFocused) {
          helper.request()
        } else {
          helper.cancel()
        }
      }
      .onGloballyPositioned {
        helper.updateNodeBounds(it.boundsInWindow())
      }
  )
}

abstract class BackupKeyAutoFillHelper(context: Context) {
  protected val autoFillManager: AutofillManager? = if (Build.VERSION.SDK_INT >= 26) {
    ContextCompat.getSystemService(context, AutofillManager::class.java)
  } else {
    null
  }

  fun onValueChanged(value: String) {
    if (value.isEmpty()) {
      requestDirectly()
    }
  }

  abstract fun request()
  abstract fun cancel()
  abstract fun requestDirectly()
  abstract fun updateNodeBounds(boundsInWindow: ComposeRect)
}
