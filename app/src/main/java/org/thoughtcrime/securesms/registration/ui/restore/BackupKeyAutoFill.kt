/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

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

/**
 * Provide a compose friendly way to autofill the backup key from a password manager.
 */
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

      /**
       * Call when need to manually prompt auto-fill options when text field is empty. For some reason calling
       * [request] like we do for on focus changes is not enough.
       */
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

/**
 * Attach a [BackupKeyAutoFillHelper] return from [backupKeyAutoFillHelper] to setup the default
 * callbacks needed to make requests on the view's behalf.
 */
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

/**
 * Weird compose-interop abstract class to let us return something to the caller of [backupKeyAutoFillHelper]
 * and capture inner compose data to implement the methods that need various compose provided things.
 */
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
