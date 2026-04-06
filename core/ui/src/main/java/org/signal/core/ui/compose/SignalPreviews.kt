/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Only generates a dark preview. Useful for screens that are only ever rendered in dark mode (like calling).
 */
@Preview(name = "night mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class NightPreview()

@Preview(name = "day mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@NightPreview
annotation class DayNightPreviews

@Preview(name = "phone portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=360dp,height=640dp,orientation=portrait")
annotation class PhonePortraitDayPreview

@Preview(name = "phone landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=640dp,height=360dp,orientation=landscape")
annotation class PhoneLandscapeDayPreview

@PhonePortraitDayPreview
@PhoneLandscapeDayPreview
annotation class PhoneDayPreviews

@Preview(name = "phone portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=360dp,height=640dp,orientation=portrait")
annotation class PhonePortraitNightPreview

@Preview(name = "phone landscape (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=640dp,height=360dp,orientation=landscape")
annotation class PhoneLandscapeNightPreview

@PhonePortraitNightPreview
@PhoneLandscapeNightPreview
annotation class PhoneNightPreviews

@PhoneDayPreviews
@PhoneLandscapeNightPreview
annotation class PhonePreviews

@Preview(name = "small foldable portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=620dp,height=720dp,orientation=portrait")
annotation class SmallFoldablePortraitDayPreview

@Preview(name = "small foldable landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=720dp,height=620dp,orientation=landscape")
annotation class SmallFoldableLandscapeDayPreview

@SmallFoldablePortraitDayPreview
@SmallFoldableLandscapeDayPreview
annotation class SmallFoldableDayPreviews

@Preview(name = "small foldable portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=620dp,height=720dp,orientation=portrait")
annotation class SmallFoldablePortraitNightPreview

@Preview(name = "small foldable landscape (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=720dp,height=620dp,orientation=landscape")
annotation class SmallFoldableLandscapeNightPreview

@SmallFoldablePortraitNightPreview
@SmallFoldableLandscapeNightPreview
annotation class SmallFoldableNightPreviews

@SmallFoldableDayPreviews
@SmallFoldableLandscapeNightPreview
annotation class SmallFoldablePreviews

@Preview(name = "foldable portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=850dp,height=881dp,orientation=portrait")
annotation class FoldablePortraitDayPreview

@Preview(name = "foldable landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=881dp,height=850dp,orientation=landscape")
annotation class FoldableLandscapeDayPreview

@FoldablePortraitDayPreview
@FoldableLandscapeDayPreview
annotation class FoldableDayPreviews

@Preview(name = "foldable portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=850dp,height=881dp,orientation=portrait")
annotation class FoldablePortraitNightPreview

@Preview(name = "foldable landscape (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=881dp,height=850dp,orientation=landscape")
annotation class FoldableLandscapeNightPreview

@FoldablePortraitNightPreview
@FoldableLandscapeNightPreview
annotation class FoldableNightPreviews

@FoldableDayPreviews
@FoldableLandscapeNightPreview
annotation class FoldablePreviews

@Preview(name = "tablet portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=840dp,height=1280dp,orientation=portrait")
annotation class TabletPortraitDayPreview

@Preview(name = "tablet landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = "spec:width=1280dp,height=840dp,orientation=landscape")
annotation class TabletLandscapeDayPreview

@TabletPortraitDayPreview
@TabletLandscapeDayPreview
annotation class TabletDayPreviews

@Preview(name = "tablet portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=1280dp,height=840dp,orientation=portrait")
annotation class TabletPortraitNightPreview

@Preview(name = "tablet landscape (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = "spec:width=840dp,height=1280dp,orientation=landscape")
annotation class TabletLandscapeNightPreview

@TabletPortraitNightPreview
@TabletLandscapeNightPreview
annotation class TabletNightPreviews

@TabletDayPreviews
@TabletLandscapeNightPreview
annotation class TabletPreviews

@PhoneNightPreviews
@SmallFoldableNightPreviews
@FoldableNightPreviews
@TabletNightPreviews
annotation class AllNightPreviews

@PhonePreviews
@SmallFoldablePreviews
@FoldablePreviews
@TabletPreviews
annotation class AllDevicePreviews

@Preview(name = "large font", fontScale = 2f)
annotation class LargeFontPreviews
