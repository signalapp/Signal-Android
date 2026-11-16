/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.signal.core.ui.R
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Units a [Pickers.DurationDialog] can offer, with the range of values selectable for each.
 */
enum class DurationPickerUnit(val minValue: Int, val maxValue: Int, val unitDuration: Duration, @param:StringRes val label: Int) {
  SECONDS(1, 59, 1.seconds, R.string.CustomExpireTimerSelectorView__seconds),
  MINUTES(1, 59, 1.minutes, R.string.CustomExpireTimerSelectorView__minutes),
  HOURS(1, 23, 1.hours, R.string.CustomExpireTimerSelectorView__hours),
  DAYS(1, 6, 1.days, R.string.CustomExpireTimerSelectorView__days),
  WEEKS(1, 4, 7.days, R.string.CustomExpireTimerSelectorView__weeks)
}

object Pickers {

  const val TEST_TAG_DURATION_VALUE_WHEEL = "duration-value-wheel"
  const val TEST_TAG_DURATION_UNIT_WHEEL = "duration-unit-wheel"

  private val WheelItemHeight = 48.dp
  private val WheelItemPadding = 8.dp
  private val MinWheelWidth = 48.dp

  /**
   * A scrolling wheel that snaps to one of [items], the Compose counterpart to a framework `NumberPicker`. The selected
   * item sits in the middle of three visible rows. Tapping an item scrolls it into the middle and selects it.
   */
  @Composable
  fun Wheel(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
  ) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    LaunchedEffect(listState) {
      snapshotFlow { listState.isScrollInProgress }
        .distinctUntilChanged()
        .filter { inProgress -> !inProgress }
        .collect {
          val settled = listState.centeredItemIndex() ?: return@collect
          if (settled != currentSelectedIndex) {
            currentOnSelectedIndexChange(settled)
          }
        }
    }

    LaunchedEffect(selectedIndex, items.size) {
      if (listState.centeredItemIndex() != selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
      }
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier
        .widthIn(min = MinWheelWidth)
        .height(WheelItemHeight * 3)
    ) {
      LazyColumn(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(vertical = WheelItemHeight),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        itemsIndexed(items) { index, item ->
          val isSelected = index == selectedIndex
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .height(WheelItemHeight)
              .fillMaxWidth()
              .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = { currentOnSelectedIndexChange(index) }
              )
          ) {
            Text(
              text = item,
              textAlign = TextAlign.Center,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
              color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis,
              autoSize = TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = MaterialTheme.typography.titleMedium.fontSize
              ),
              modifier = Modifier.padding(horizontal = WheelItemPadding)
            )
          }
        }
      }

      HorizontalDivider(modifier = Modifier.padding(bottom = WheelItemHeight).fillMaxWidth())
      HorizontalDivider(modifier = Modifier.padding(top = WheelItemHeight).fillMaxWidth())
    }
  }

  /**
   * Dialog for picking a custom duration as a value and a unit, e.g. "3 hours". The units offered are limited to
   * [units], and the value range follows whichever unit is selected.
   */
  @Composable
  fun DurationDialog(
    title: String,
    initialDuration: Duration,
    onConfirm: (Duration) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    units: List<DurationPickerUnit> = DurationPickerUnit.entries,
    confirm: String = stringResource(R.string.ExpireTimerSettingsFragment__set),
    dismiss: String = stringResource(android.R.string.cancel)
  ) {
    val initialSelection = remember(initialDuration, units) { DurationSelection.from(initialDuration, units) }
    var unitIndex by rememberSaveable(initialSelection) { mutableIntStateOf(units.indexOf(initialSelection.unit)) }
    var value by rememberSaveable(initialSelection) { mutableIntStateOf(initialSelection.value) }

    val unit = units[unitIndex]

    Dialogs.BaseAlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(text = title) },
      text = {
        DurationWheels(
          units = units,
          unitIndex = unitIndex,
          value = value,
          onUnitIndexChange = { newIndex ->
            unitIndex = newIndex
            value = value.coerceIn(units[newIndex].minValue, units[newIndex].maxValue)
          },
          onValueChange = { value = it }
        )
      },
      confirmButton = {
        TextButton(
          onClick = { onConfirm(unit.unitDuration * value) },
          modifier = Modifier.testTag(Dialogs.TEST_TAG_ALERT_DIALOG_CONFIRM_BUTTON)
        ) {
          Text(text = confirm)
        }
      },
      dismissButton = {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier.testTag(Dialogs.TEST_TAG_ALERT_DIALOG_DISMISS_BUTTON)
        ) {
          Text(text = dismiss)
        }
      },
      modifier = modifier
    )
  }

  /** The body of [DurationDialog]: a wheel for the value beside a wheel for the unit. */
  @VisibleForTesting
  @Composable
  internal fun DurationWheels(
    units: List<DurationPickerUnit>,
    unitIndex: Int,
    value: Int,
    onUnitIndexChange: (Int) -> Unit,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
  ) {
    val unit = units[unitIndex]
    val values = (unit.minValue..unit.maxValue).toList()
    val labels = units.map { stringResource(it.label) }
    val labelWidth = widestWheelItem(labels)

    BoxWithConstraints(
      modifier = modifier
        .fillMaxWidth()
        .heightIn(min = WheelItemHeight * 3)
    ) {
      val spacing = 16.dp
      val available = maxWidth - spacing
      val unitWidth = maxOf(available / 2, minOf(labelWidth, available - MinWheelWidth))

      Row(
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Wheel(
          items = values.map { it.toString() },
          selectedIndex = values.indexOf(value),
          onSelectedIndexChange = { onValueChange(values[it]) },
          modifier = Modifier
            .width(available - unitWidth)
            .testTag(TEST_TAG_DURATION_VALUE_WHEEL)
        )

        Wheel(
          items = labels,
          selectedIndex = unitIndex,
          onSelectedIndexChange = onUnitIndexChange,
          modifier = Modifier
            .width(unitWidth)
            .testTag(TEST_TAG_DURATION_UNIT_WHEEL)
        )
      }
    }
  }

  /** How wide a [Wheel] has to be to show the widest of [items] at full size. */
  @Composable
  private fun widestWheelItem(items: List<String>): Dp {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    val density = LocalDensity.current

    return remember(items, style, density) {
      val widestPx = items.maxOfOrNull { textMeasurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
      with(density) { widestPx.toDp() } + WheelItemPadding * 2
    }
  }

  private fun LazyListState.centeredItemIndex(): Int? {
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return layoutInfo.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index
  }

  private data class DurationSelection(val unit: DurationPickerUnit, val value: Int) {
    companion object {
      /** Picks the smallest unit that can express [duration] within its range, as the framework picker did. */
      fun from(duration: Duration, units: List<DurationPickerUnit>): DurationSelection {
        if (duration <= Duration.ZERO) {
          return DurationSelection(units.first(), units.first().minValue)
        }

        val unit = units.firstOrNull { (duration / it.unitDuration) < it.maxValue } ?: units.last()
        val value = (duration / unit.unitDuration).toInt().coerceIn(unit.minValue, unit.maxValue)
        return DurationSelection(unit, value)
      }
    }
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun DurationWheelsPreview() {
  Surface {
    Pickers.DurationWheels(
      units = listOf(DurationPickerUnit.MINUTES, DurationPickerUnit.HOURS, DurationPickerUnit.DAYS),
      unitIndex = 1,
      value = 3,
      onUnitIndexChange = {},
      onValueChange = {}
    )
  }
}
