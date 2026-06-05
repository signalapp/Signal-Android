/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

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

private data class SafetyTipSummary(
  @DrawableRes val icon: Int,
  @StringRes val titleText: Int,
  @StringRes val messageText: Int
)

private data class SafetyTipDetail(
  @DrawableRes val heroImage: Int,
  @StringRes val titleText: Int,
  @StringRes val messageText: Int
)

private val summaryTips = listOf(
  SafetyTipSummary(icon = R.drawable.safetytip_48_01, titleText = R.string.SafetyTips_summary_tip0_title, messageText = R.string.SafetyTips_summary_tip0_message),
  SafetyTipSummary(icon = R.drawable.safetytip_48_02, titleText = R.string.SafetyTips_summary_tip1_title, messageText = R.string.SafetyTips_summary_tip1_message),
  SafetyTipSummary(icon = R.drawable.safetytip_48_03, titleText = R.string.SafetyTips_summary_tip2_title, messageText = R.string.SafetyTips_summary_tip2_message)
)

private val detailTips = listOf(
  SafetyTipDetail(heroImage = R.drawable.safetytip_240_01, titleText = R.string.SafetyTips_detail_tip0_title, messageText = R.string.SafetyTips_detail_tip0_message),
  SafetyTipDetail(heroImage = R.drawable.safetytip_240_02, titleText = R.string.SafetyTips_detail_tip1_title, messageText = R.string.SafetyTips_detail_tip1_message),
  SafetyTipDetail(heroImage = R.drawable.safetytip_240_03, titleText = R.string.SafetyTips_detail_tip2_title, messageText = R.string.SafetyTips_detail_tip2_message),
  SafetyTipDetail(heroImage = R.drawable.safetytip_240_04, titleText = R.string.SafetyTips_detail_tip3_title, messageText = R.string.SafetyTips_detail_tip3_message),
  SafetyTipDetail(heroImage = R.drawable.safetytip_240_05, titleText = R.string.SafetyTips_detail_tip4_title, messageText = R.string.SafetyTips_detail_tip4_message),
  SafetyTipDetail(heroImage = R.drawable.safetytip_240_06, titleText = R.string.SafetyTips_detail_tip5_title, messageText = R.string.SafetyTips_detail_tip5_message)
)

@Composable
private fun SafetyTipsContent(forGroup: Boolean = false, modifier: Modifier = Modifier) {
  var showDetails by rememberSaveable { mutableStateOf(false) }

  if (showDetails) {
    SafetyTipsDetailContent(modifier = modifier)
  } else {
    SafetyTipsSummaryContent(
      onViewMore = { showDetails = true },
      modifier = modifier
    )
  }
}

@Composable
private fun SafetyTipsSummaryContent(
  onViewMore: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxWidth()
    ) {
      BottomSheets.Handle()
    }

    Column(
      modifier = modifier
        .fillMaxWidth()
        .weight(weight = 1f, fill = false)
        .verticalScroll(state = scrollState)
        .padding(horizontal = 36.dp)
    ) {
      Text(
        text = stringResource(id = R.string.SafetyTips_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
          .padding(top = 28.dp, bottom = 34.dp)
          .align(Alignment.CenterHorizontally)
      )

      summaryTips.forEach { tip ->
        SafetyTipSummaryRow(tip)
      }

      Spacer(Modifier.height(8.dp))

      Buttons.LargeTonal(
        onClick = onViewMore,
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(text = stringResource(id = R.string.SafetyTips_view_more))
      }

      Spacer(Modifier.height(36.dp))
    }
  }
}

@Composable
private fun SafetyTipSummaryRow(tip: SafetyTipSummary) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 40.dp),
    verticalAlignment = Alignment.Top
  ) {
    Image(
      painter = painterResource(id = tip.icon),
      contentDescription = null,
      modifier = Modifier.size(48.dp)
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 24.dp)
    ) {
      Text(
        text = stringResource(id = tip.titleText),
        style = MaterialTheme.typography.titleMedium
      )

      Text(
        text = stringResource(id = tip.messageText),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SafetyTipsDetailContent(modifier: Modifier = Modifier) {
  val size = remember { detailTips.size }
  val pagerState = rememberPagerState(pageCount = { size })
  val coroutineScope = rememberCoroutineScope()

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier.fillMaxWidth()
    ) {
      BottomSheets.Handle()
    }

    Column(
      modifier = modifier
        .fillMaxWidth()
        .weight(weight = 1f, fill = false)
    ) {
      HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = size,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.weight(weight = 1f, fill = false)
      ) { page ->
        SafetyTipDetailPage(detailTips[page])
      }
    }

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 24.dp, end = 24.dp, bottom = 36.dp, top = 16.dp)
    ) {
      if (pagerState.currentPage > 0) {
        IconButton(
          onClick = {
            coroutineScope.launch {
              pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
          },
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          ),
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
        ) {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_arrow_right_24),
            contentDescription = stringResource(R.string.SafetyTips_previous_tip),
            modifier = Modifier.graphicsLayer(scaleX = -1f)
          )
        }
      } else {
        Spacer(Modifier.size(48.dp))
      }

      Row(
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

      if (pagerState.currentPage < pagerState.pageCount - 1) {
        IconButton(
          onClick = {
            coroutineScope.launch {
              pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
          },
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          ),
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
        ) {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_arrow_right_24),
            contentDescription = stringResource(R.string.SafetyTips_next_tip)
          )
        }
      } else {
        Spacer(Modifier.size(48.dp))
      }
    }
  }
}

@Composable
private fun SafetyTipDetailPage(tip: SafetyTipDetail) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 36.dp)
  ) {
    Image(
      painter = painterResource(id = tip.heroImage),
      contentDescription = null,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp, bottom = 16.dp)
        .height(160.dp)
        .align(Alignment.CenterHorizontally)
    )

    Text(
      text = stringResource(id = tip.titleText),
      style = MaterialTheme.typography.titleMedium
    )

    Text(
      text = stringResource(id = tip.messageText),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 4.dp)
    )
  }
}

@DayNightPreviews
@Composable
private fun SafetyTipsSummaryPreview() {
  Previews.Preview {
    Surface {
      SafetyTipsSummaryContent(onViewMore = {})
    }
  }
}

@DayNightPreviews
@Composable
private fun SafetyTipsDetailPreview() {
  Previews.Preview {
    Surface {
      SafetyTipsDetailContent()
    }
  }
}

@DayNightPreviews
@Composable
private fun SafetyTipDetailPagePreview() {
  Previews.Preview {
    Surface {
      SafetyTipDetailPage(detailTips[0])
    }
  }
}
