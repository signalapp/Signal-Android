/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.runtime.Composable
import org.junit.Test
import org.signal.core.ui.SignalLayoutStressTest

/**
 * Stresses the body of [Pickers.DurationDialog]. The dialog itself renders in its own window, which ignores the stress
 * configs, so its content is checked here on its own.
 */
class PickersLayoutTest : SignalLayoutStressTest() {

  private val allUnits = DurationPickerUnit.entries
  private val screenLockUnits = listOf(DurationPickerUnit.MINUTES, DurationPickerUnit.HOURS, DurationPickerUnit.DAYS)

  @Test
  fun `smallest unit with a two digit value`() = assertPassesLayoutChecks {
    Wheels(units = allUnits, unit = DurationPickerUnit.SECONDS, value = 59)
  }

  @Test
  fun `middle unit`() = assertPassesLayoutChecks {
    Wheels(units = allUnits, unit = DurationPickerUnit.HOURS, value = 23)
  }

  @Test
  fun `largest unit at the end of the list`() = assertPassesLayoutChecks {
    Wheels(units = allUnits, unit = DurationPickerUnit.WEEKS, value = 4)
  }

  @Test
  fun `a subset of units`() = assertPassesLayoutChecks {
    Wheels(units = screenLockUnits, unit = DurationPickerUnit.DAYS, value = 6)
  }

  @Composable
  private fun Wheels(units: List<DurationPickerUnit>, unit: DurationPickerUnit, value: Int) {
    Pickers.DurationWheels(
      units = units,
      unitIndex = units.indexOf(unit),
      value = value,
      onUnitIndexChange = {},
      onValueChange = {}
    )
  }
}
