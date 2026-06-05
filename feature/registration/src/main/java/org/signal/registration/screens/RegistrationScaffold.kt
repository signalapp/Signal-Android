/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.rememberWindowBreakpoint

object RegistrationScaffold {
  private val smallLayoutParams = Params.OnePane(
    headerSlotHeight = 24.dp,
    edgeInset = 24.dp,
    paneVerticalInset = 24.dp,
    paneHorizontalInset = 24.dp,
    maxButtonWidth = 320.dp
  )

  private val mediumLayoutParams = Params.TwoPane(
    headerSlotHeight = 64.dp,
    edgeInset = 24.dp,
    paneTopInset = 16.dp,
    paneBottomInset = 24.dp,
    paneOuterInset = 24.dp,
    paneInnerInset = 24.dp,
    maxButtonWidth = 320.dp
  )

  private val largeWidthLayoutParams = Params.TwoPane(
    headerSlotHeight = 64.dp,
    edgeInset = 32.dp,
    paneTopInset = 64.dp,
    paneBottomInset = 64.dp,
    paneOuterInset = 128.dp,
    paneInnerInset = 64.dp,
    maxButtonWidth = 412.dp
  )

  private val largeHeightLayoutParams = Params.OnePane(
    headerSlotHeight = 64.dp,
    edgeInset = 32.dp,
    paneVerticalInset = 64.dp,
    paneHorizontalInset = 128.dp,
    maxButtonWidth = 320.dp
  )

  sealed interface Params {
    val headerSlotHeight: Dp
    val edgeInset: Dp
    val maxButtonWidth: Dp

    val footerPadding
      get() = PaddingValues(
        top = 16.dp,
        bottom = edgeInset,
        start = 16.dp,
        end = edgeInset
      )

    data class OnePane(
      override val headerSlotHeight: Dp,
      private val paneVerticalInset: Dp,
      private val paneHorizontalInset: Dp,
      override val edgeInset: Dp,
      override val maxButtonWidth: Dp
    ) : Params {
      fun panePadding(hasHeader: Boolean) = PaddingValues(
        top = if (hasHeader) paneVerticalInset else headerSlotHeight + paneVerticalInset,
        bottom = paneVerticalInset,
        start = paneHorizontalInset,
        end = paneHorizontalInset
      )
    }

    data class TwoPane(
      override val headerSlotHeight: Dp,
      private val paneTopInset: Dp,
      private val paneBottomInset: Dp,
      private val paneOuterInset: Dp,
      private val paneInnerInset: Dp,
      override val edgeInset: Dp,
      override val maxButtonWidth: Dp
    ) : Params {

      fun firstPanePadding(hasHeader: Boolean): PaddingValues = PaddingValues(
        top = if (hasHeader) paneTopInset else headerSlotHeight + paneTopInset,
        bottom = paneBottomInset,
        start = paneOuterInset,
        end = paneInnerInset
      )

      fun secondPanePadding(hasHeader: Boolean): PaddingValues = PaddingValues(
        top = if (hasHeader) paneTopInset else headerSlotHeight + paneTopInset,
        bottom = paneBottomInset,
        start = paneInnerInset,
        end = paneOuterInset
      )
    }
  }

  @Composable
  fun rememberLayoutParams(): Params {
    return when (val breakpoint = rememberWindowBreakpoint()) {
      is WindowBreakpoint.Small -> smallLayoutParams
      is WindowBreakpoint.Medium -> mediumLayoutParams
      is WindowBreakpoint.Large -> if (breakpoint.isWidthExpanded) largeWidthLayoutParams else largeHeightLayoutParams
    }
  }

  @Composable
  fun FooterSurface(
    isContentScrolledUnder: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
  ) {
    Surface(
      modifier = modifier.fillMaxWidth(),
      shadowElevation = if (isContentScrolledUnder) 8.dp else 0.dp,
      content = content
    )
  }
}

/**
 * Scaffold for registration flow screens.
 */
@Composable
fun RegistrationScaffold(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
  topBar: (@Composable () -> Unit)? = null,
  footer: (@Composable () -> Unit)? = null
) {
  SubcomposeLayout(modifier = modifier.imePadding()) { constraints ->
    val footerPlaceables = footer?.let {
      subcompose("footer", it).map { m -> m.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    } ?: emptyList()
    val footerHeight = footerPlaceables.maxOfOrNull { it.height } ?: 0

    val headerPlaceables = topBar?.let {
      subcompose("header", it).map { m -> m.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    } ?: emptyList()
    val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0

    val contentHeight = (constraints.maxHeight - footerHeight - headerHeight).coerceAtLeast(0)
    val contentPlaceables = subcompose("content", content).map { m ->
      m.measure(constraints.copy(minHeight = contentHeight, maxHeight = contentHeight))
    }

    layout(constraints.maxWidth, constraints.maxHeight) {
      headerPlaceables.forEach { it.placeRelative(0, 0) }
      contentPlaceables.forEach { it.placeRelative(0, headerHeight) }
      footerPlaceables.forEach { it.placeRelative(0, contentHeight + headerHeight) }
    }
  }
}

/**
 * One-pane variant of [RegistrationScaffold] for windows with limited width.
 */
@Composable
fun OnePaneRegistrationScaffold(
  modifier: Modifier = Modifier,
  params: RegistrationScaffold.Params.OnePane,
  topBar: (@Composable () -> Unit)? = null,
  footer: (@Composable () -> Unit)? = null,
  content: @Composable (PaddingValues) -> Unit
) {
  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    topBar = topBar,
    footer = footer,
    content = { content(params.panePadding(hasHeader = topBar != null)) }
  )
}

/**
 * Two-pane variant of [RegistrationScaffold] for medium and large-width breakpoints.
 */
@Composable
fun TwoPaneRegistrationScaffold(
  modifier: Modifier = Modifier,
  params: RegistrationScaffold.Params.TwoPane,
  topBar: (@Composable () -> Unit)? = null,
  footer: (@Composable () -> Unit)? = null,
  firstPane: @Composable RowScope.(PaddingValues) -> Unit,
  secondPane: @Composable RowScope.(PaddingValues) -> Unit
) {
  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    topBar = topBar,
    footer = footer,
    content = {
      Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier.fillMaxSize()
      ) {
        firstPane(params.firstPanePadding(hasHeader = topBar != null))
        secondPane(params.secondPanePadding(hasHeader = topBar != null))
      }
    }
  )
}

@Composable
private fun PreviewPane(
  label: String,
  paddingValues: PaddingValues,
  outerColor: Color,
  innerColor: Color,
  modifier: Modifier = Modifier
) {
  Text(
    modifier = modifier
      .fillMaxHeight()
      .background(outerColor)
      .padding(paddingValues)
      .background(innerColor)
      .wrapContentHeight(Alignment.CenterVertically),
    text = label,
    textAlign = TextAlign.Center,
    fontSize = 30.sp,
    color = Color.Black
  )
}

@AllDevicePreviews
@Composable
private fun RegistrationScaffoldPreview() = Previews.Preview {
  when (val params = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> {
      OnePaneRegistrationScaffold(
        params = params,
        topBar = {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Green)
              .padding(16.dp),
            text = "topBar",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = Color.Black
          )
        },
        content = { paddingValues ->
          PreviewPane(
            label = "content",
            paddingValues = paddingValues,
            outerColor = Color.Red,
            innerColor = Color.Yellow,
            modifier = Modifier.fillMaxWidth()
          )
        },
        footer = {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Green)
              .padding(16.dp),
            text = "footer",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = Color.Black
          )
        }
      )
    }

    is RegistrationScaffold.Params.TwoPane -> {
      TwoPaneRegistrationScaffold(
        modifier = Modifier.fillMaxSize(),
        params = params,
        topBar = {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Green)
              .padding(16.dp),
            text = "topBar",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = Color.Black
          )
        },
        firstPane = { paddingValues ->
          PreviewPane(
            label = "firstPane",
            paddingValues = paddingValues,
            outerColor = Color.Red,
            innerColor = Color.Yellow,
            modifier = Modifier.weight(1f)
          )
        },
        secondPane = { paddingValues ->
          PreviewPane(
            label = "secondPane",
            paddingValues = paddingValues,
            outerColor = Color.Blue,
            innerColor = Color.Yellow,
            modifier = Modifier.weight(1f)
          )
        },
        footer = {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Green)
              .padding(16.dp),
            text = "footer",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = Color.Black
          )
        }
      )
    }
  }
}
