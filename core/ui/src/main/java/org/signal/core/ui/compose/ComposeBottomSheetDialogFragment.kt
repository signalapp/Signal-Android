/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.signal.core.ui.FixedRoundedCornerBottomSheetDialogFragment
import org.signal.core.ui.compose.theme.SignalTheme
import kotlin.math.roundToInt

/**
 * Base class for bottom sheets whose content is entirely Compose.
 * Provides [LocalFragmentManager] and [SignalTheme] to the composition, and delegates
 * content rendering to the abstract [SheetContent] composable.
 */
abstract class ComposeBottomSheetDialogFragment : FixedRoundedCornerBottomSheetDialogFragment() {

  private val sheetState = BottomSheetHostState(
    onExpand = { (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED },
    onDismiss = ::dismissWithAnimation
  )
  private val bottomSheet: View?
    get() = view?.parent as? View

  // Global, since BottomSheetBehavior offsets the sheet only after the sheet lays out.
  private val overflowLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { bottomSheet?.let(sheetState::updateOverflow) }

  protected open val forceDarkTheme = false

  protected open val applyImePadding = true

  /**
   * Whether [SheetContent] scrolls vertically, so the sheet expands and collapses along with it while still dragging
   * from non-interactive areas. Use this rather than wiring [rememberNestedScrollInteropConnection] in [SheetContent].
   */
  protected open val hasScrollingContent = false

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        val isDark = if (forceDarkTheme) {
          true
        } else {
          LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        CompositionLocalProvider(LocalFragmentManager provides childFragmentManager) {
          SignalTheme(isDarkMode = isDark) {
            CompositionLocalProvider(LocalBottomSheetHostState provides sheetState) {
              // No navigation bar padding here: BottomSheetBehavior already bottom-pads the sheet
              // for the navigation bar (paddingBottomSystemWindowInsets) once the window is
              // edge-to-edge, so padding again would double up. The keyboard inset excludes the
              // navigation bar for the same reason.
              Surface(
                modifier = if (applyImePadding) Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)) else Modifier,
                shape = RoundedCornerShape(cornerRadius.dp, cornerRadius.dp),
                color = SignalTheme.colors.colorSurface1,
                contentColor = MaterialTheme.colorScheme.onSurface
              ) {
                if (hasScrollingContent) {
                  ScrollingSheetContent { SheetContent() }
                } else {
                  SheetContent()
                }
              }
            }
          }
        }
      }
    }
  }

  /** Slides the sheet down, which closes the dialog once it's hidden, rather than removing it at once. */
  private fun dismissWithAnimation() {
    val behavior = (dialog as? BottomSheetDialog)?.behavior
    if (behavior != null && behavior.isHideable) {
      behavior.state = BottomSheetBehavior.STATE_HIDDEN
    } else {
      dismissAllowingStateLoss()
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

    sheetState.isExpanded = dialog.behavior.state == BottomSheetBehavior.STATE_EXPANDED
    dialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
      override fun onStateChanged(bottomSheet: View, newState: Int) {
        sheetState.isExpanded = newState == BottomSheetBehavior.STATE_EXPANDED
        sheetState.updateOverflow(bottomSheet)
      }

      override fun onSlide(bottomSheet: View, slideOffset: Float) = sheetState.updateOverflow(bottomSheet)
    })

    return dialog
  }

  // The view isn't in the sheet until the dialog starts.
  override fun onStart() {
    super.onStart()
    bottomSheet?.viewTreeObserver?.addOnGlobalLayoutListener(overflowLayoutListener)
  }

  override fun onStop() {
    bottomSheet?.viewTreeObserver?.removeOnGlobalLayoutListener(overflowLayoutListener)
    super.onStop()
  }

  @Composable
  abstract fun SheetContent()
}

/**
 * The position of the enclosing sheet, for content that follows it. Driven by [ComposeBottomSheetDialogFragment] and
 * by [BottomSheets.BottomSheet].
 *
 * @param onExpand Takes the sheet to full height.
 * @param onDismiss Animates the sheet away and closes it.
 */
@Stable
class BottomSheetHostState(
  private val onExpand: () -> Unit = {},
  private val onDismiss: () -> Unit = {}
) {

  /** How far the sheet extends below its window, which it does when not fully expanded. */
  var overflowBottomPx by mutableIntStateOf(0)
    private set

  /** Whether the sheet is settled at full height. */
  var isExpanded by mutableStateOf(false)
    internal set

  fun expand() = onExpand()

  /** Closes the sheet with its slide-down animation, for content that ends the sheet itself, such as from a button. */
  fun dismiss() = onDismiss()

  internal fun updateOverflow(bottomSheet: View) {
    val windowHeight = (bottomSheet.parent as? View)?.height ?: return
    setOverflow(bottomSheet.bottom - windowHeight)
  }

  /** @param sheetContent The sheet's content, whose bottom passes the bottom of its window while the sheet is part way up. */
  internal fun updateOverflow(sheetContent: LayoutCoordinates) {
    val windowHeight = sheetContent.findRootCoordinates().size.height
    setOverflow((sheetContent.positionInRoot().y + sheetContent.size.height).roundToInt() - windowHeight)
  }

  private fun setOverflow(overflowPx: Int) {
    val overflow = overflowPx.coerceAtLeast(0)
    if (overflow != overflowBottomPx) {
      overflowBottomPx = overflow
      // Apply now so content following the sheet moves in the same frame.
      Snapshot.sendApplyNotifications()
    }
  }
}

/** The enclosing sheet's [BottomSheetHostState], or a static one outside a sheet. */
val LocalBottomSheetHostState = staticCompositionLocalOf { BottomSheetHostState() }

@Composable
private fun ScrollingSheetContent(content: @Composable () -> Unit) {
  Box(modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())) {
    // Behind the content, catching drags on non-interactive areas and passing them all to the sheet.
    Spacer(
      modifier = Modifier
        .matchParentSize()
        .scrollable(state = rememberScrollableState { 0f }, orientation = Orientation.Vertical)
    )

    content()
  }
}
