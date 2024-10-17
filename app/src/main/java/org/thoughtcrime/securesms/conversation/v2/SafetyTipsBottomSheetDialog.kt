/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.launch
import org.signal.core.ui.BottomSheets
import org.signal.core.ui.Buttons
import org.signal.core.ui.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeBottomSheetDialogFragment

/**
 * Shows tips about typical spam and fraud messages.
 */
class SafetyTipsBottomSheetDialog : ComposeBottomSheetDialogFragment() {
  companion object {
    private const val FOR_GROUP_ARG = "for_group"

    fun show(fragmentManager: FragmentManager, forGroup: Boolean) {
      SafetyTipsBottomSheetDialog()
        .apply {
          arguments = bundleOf(
            FOR_GROUP_ARG to forGroup
          )
        }
        .show(fragmentManager, "SAFETY_TIPS")
    }
  }

  override val peekHeightPercentage: Float = 1f

  @Composable
  override fun SheetContent() {
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    SafetyTipsContent(
      forGroup = requireArguments().getBoolean(FOR_GROUP_ARG, false),
      modifier = Modifier.nestedScroll(nestedScrollInterop)
    )
  }
}

data class SafetyTipData(
  @DrawableRes val heroImage: Int,
  @StringRes val titleText: Int,
  @StringRes val messageText: Int
)

private val tips = listOf(
  SafetyTipData(heroImage = R.drawable.safety_tip1, titleText = R.string.SafetyTips_tip1_title, messageText = R.string.SafetyTips_tip1_message),
  SafetyTipData(heroImage = R.drawable.safety_tip2, titleText = R.string.SafetyTips_tip2_title, messageText = R.string.SafetyTips_tip2_message),
  SafetyTipData(heroImage = R.drawable.safety_tip3, titleText = R.string.SafetyTips_tip3_title, messageText = R.string.SafetyTips_tip3_message),
  SafetyTipData(heroImage = R.drawable.safety_tip4, titleText = R.string.SafetyTips_tip4_title, messageText = R.string.SafetyTips_tip4_message)
)

@Preview
@Composable
private fun SafetyTipsContentPreview() {
  SignalTheme {
    Surface {
      SafetyTipsContent()
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SafetyTipsContent(forGroup: Boolean = false, modifier: Modifier = Modifier) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.fillMaxWidth()
  ) {
    BottomSheets.Handle()
  }

  val size = remember { tips.size }
  val pagerState = rememberPagerState(
    pageCount = { size }
  )
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .weight(weight = 1f, fill = false)
        .padding(top = 22.dp)
        .verticalScroll(state = scrollState)
    ) {
      Text(
        text = stringResource(id = R.string.SafetyTips_title),
        style = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
        modifier = Modifier
          .padding(start = 24.dp, end = 24.dp, bottom = 4.dp, top = 26.dp)
          .fillMaxWidth()
      )

      Text(
        text = if (forGroup) stringResource(id = R.string.SafetyTips_subtitle_group) else stringResource(id = R.string.SafetyTips_subtitle_individual),
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = Modifier
          .padding(start = 36.dp, end = 36.dp)
          .fillMaxWidth()
      )

      HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = size,
        modifier = Modifier.padding(top = 24.dp)
      ) {
        SafetyTip(tips[it])
      }

      Row(
        Modifier
          .fillMaxWidth()
          .padding(top = 20.dp),
        horizontalArrangement = Arrangement.Center
      ) {
        repeat(pagerState.pageCount) { iteration ->
          val color = if (pagerState.currentPage == iteration) {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
          } else {
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
          }
          Box(
            modifier = Modifier
              .padding(3.dp)
              .clip(CircleShape)
              .background(color)
              .size(8.dp)
          )
        }
      }
    }

    Surface(
      shadowElevation = if (scrollState.canScrollForward) 8.dp else 0.dp,
      modifier = Modifier.fillMaxWidth(),
      color = SignalTheme.colors.colorSurface1,
      contentColor = MaterialTheme.colorScheme.onSurface
    ) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .padding(start = 24.dp, end = 24.dp, bottom = 36.dp, top = 24.dp)
          .fillMaxWidth()
      ) {
        val coroutineScope = rememberCoroutineScope()

        TextButton(
          onClick = {
            coroutineScope.launch {
              pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
          },
          enabled = pagerState.currentPage > 0,
          modifier = Modifier
        ) {
          Text(text = stringResource(id = R.string.SafetyTips_previous_tip))
        }

        Buttons.LargeTonal(
          onClick = {
            coroutineScope.launch {
              pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
          },
          enabled = pagerState.currentPage + 1 < pagerState.pageCount
        ) {
          Text(text = stringResource(id = R.string.SafetyTips_next_tip))
        }
      }
    }
  }
}

@Preview(name = "Light Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SafetyTipPreview() {
  SignalTheme {
    Surface {
      SafetyTip(tips[0])
    }
  }
}

@Composable
private fun SafetyTip(safetyTip: SafetyTipData) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = colorResource(id = R.color.safety_tip_background),
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 24.dp, end = 24.dp)
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .fillMaxWidth()
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorResource(id = R.color.safety_tip_image_background),
        modifier = Modifier
          .padding(12.dp)
          .fillMaxWidth()
      ) {
        Image(
          painter = painterResource(id = safetyTip.heroImage),
          contentDescription = null,
          modifier = Modifier
            .padding(16.dp)
        )
      }

      Text(
        text = stringResource(id = safetyTip.titleText),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
          .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp)
      )

      Text(
        text = stringResource(id = safetyTip.messageText),
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center),
        modifier = Modifier
          .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
      )
    }
  }
}
