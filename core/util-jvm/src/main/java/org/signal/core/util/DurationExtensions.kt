/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util

import kotlin.time.Duration
import kotlin.time.DurationUnit

fun Duration.inRoundedMilliseconds(places: Int = 2) = this.toDouble(DurationUnit.MILLISECONDS).roundedString(places)
fun Duration.inRoundedMinutes(places: Int = 2) = this.toDouble(DurationUnit.MINUTES).roundedString(places)
fun Duration.inRoundedHours(places: Int = 2) = this.toDouble(DurationUnit.HOURS).roundedString(places)
fun Duration.inRoundedDays(places: Int = 2) = this.toDouble(DurationUnit.DAYS).roundedString(places)
