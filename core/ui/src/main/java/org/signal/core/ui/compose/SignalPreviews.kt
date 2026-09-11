/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

private const val PHONE_PORTRAIT = "spec:width=360dp,height=780dp,orientation=portrait"
private const val PHONE_LANDSCAPE = "spec:width=780dp,height=360dp,orientation=landscape"
private const val SMALL_FOLDABLE_PORTRAIT = "spec:width=620dp,height=720dp,orientation=portrait"
private const val SMALL_FOLDABLE_LANDSCAPE = "spec:width=720dp,height=620dp,orientation=landscape"
private const val FOLDABLE_PORTRAIT = "spec:width=850dp,height=881dp,orientation=portrait"
private const val FOLDABLE_LANDSCAPE = "spec:width=881dp,height=850dp,orientation=landscape"
private const val SMALL_TABLET_LANDSCAPE = "spec:width=960dp,height=600dp,orientation=landscape"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,orientation=portrait"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,orientation=landscape"

/**
 * Only generates a dark preview. Useful for screens that are only ever rendered in dark mode (like calling).
 */
@Preview(name = "night mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class NightPreview()

@Preview(name = "day mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@NightPreview
annotation class DayNightPreviews

/**
 * Renders with English strings in a right-to-left layout. The Arab script forces RTL while the language stays
 * English, so these screenshots don't churn when new translations land.
 */
@Preview(name = "rtl", locale = "b+en+Arab")
annotation class RtlPreview

@Preview(name = "phone portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = PHONE_PORTRAIT)
annotation class PhonePortraitDayPreview

@Preview(name = "phone portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = PHONE_PORTRAIT)
annotation class PhonePortraitNightPreview

@Preview(name = "phone landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = PHONE_LANDSCAPE)
annotation class PhoneLandscapeDayPreview

@Preview(name = "phone landscape (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = PHONE_LANDSCAPE)
annotation class PhoneLandscapeNightPreview

@Preview(name = "small foldable portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = SMALL_FOLDABLE_PORTRAIT)
annotation class SmallFoldablePortraitDayPreview

@Preview(name = "small foldable landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = SMALL_FOLDABLE_LANDSCAPE)
annotation class SmallFoldableLandscapeDayPreview

@Preview(name = "foldable portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = FOLDABLE_PORTRAIT)
annotation class FoldablePortraitDayPreview

@Preview(name = "foldable portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = FOLDABLE_PORTRAIT)
annotation class FoldablePortraitNightPreview

@Preview(name = "foldable landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = FOLDABLE_LANDSCAPE)
annotation class FoldableLandscapeDayPreview

/**
 * A small tablet in landscape: expanded width, but the shortest height of any non-phone window, which is where
 * fixed-height content runs out of room first.
 */
@Preview(name = "small tablet landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = SMALL_TABLET_LANDSCAPE)
annotation class SmallTabletLandscapeDayPreview

@Preview(name = "tablet portrait (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = TABLET_PORTRAIT)
annotation class TabletPortraitDayPreview

@Preview(name = "tablet portrait (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = TABLET_PORTRAIT)
annotation class TabletPortraitNightPreview

@Preview(name = "tablet landscape (day)", uiMode = Configuration.UI_MODE_NIGHT_NO, device = TABLET_LANDSCAPE)
annotation class TabletLandscapeDayPreview

@Preview(name = "tablet landscape (night)", uiMode = Configuration.UI_MODE_NIGHT_YES, device = TABLET_LANDSCAPE)
annotation class TabletLandscapeNightPreview

/**
 * The device sizes worth eyeballing while working on a typical screen or component: a phone in both orientations,
 * an unfolded foldable, and a tablet in both orientations, plus a dark-mode pass over one size per device.
 */
@PhonePortraitDayPreview
@PhoneLandscapeDayPreview
@FoldablePortraitDayPreview
@TabletPortraitDayPreview
@TabletLandscapeDayPreview
@PhonePortraitNightPreview
@FoldablePortraitNightPreview
@TabletLandscapeNightPreview
annotation class AllDevicePreviews

/**
 * The night-mode counterpart to [AllDevicePreviews], for screens that are only ever rendered in dark mode
 * (like calling and the camera).
 */
@PhonePortraitNightPreview
@PhoneLandscapeNightPreview
@FoldablePortraitNightPreview
@TabletPortraitNightPreview
@TabletLandscapeNightPreview
annotation class AllNightPreviews

/**
 * Every distinct WindowBreakpoint state, so that scaffolds and other layouts that switch structure on window size
 * can be checked at each one. Reserved for those layouts -- use [AllDevicePreviews] for ordinary screens.
 *
 * The two foldable sizes straddle the expanded-width breakpoint (840dp): the small pair stays in medium width,
 * the large pair crosses into expanded, which is what drives things like navigation rail vs. bar.
 */
@PhonePortraitDayPreview
@PhoneLandscapeDayPreview
@SmallFoldablePortraitDayPreview
@SmallFoldableLandscapeDayPreview
@FoldablePortraitDayPreview
@FoldableLandscapeDayPreview
@TabletPortraitDayPreview
@TabletLandscapeDayPreview
annotation class BreakpointPreviews

/**
 * The screenshot test matrix, sized to catch UI regressions rather than to enumerate configurations: each device
 * in both orientations, a dark-mode pass over one representative size per device, and a right-to-left pass.
 */
@PhonePortraitDayPreview
@PhoneLandscapeDayPreview
@FoldablePortraitDayPreview
@FoldableLandscapeDayPreview
@TabletPortraitDayPreview
@TabletLandscapeDayPreview
@PhonePortraitNightPreview
@FoldablePortraitNightPreview
@TabletLandscapeNightPreview
@RtlPreview
annotation class ScreenshotPreviews

@TabletPortraitDayPreview
@TabletLandscapeDayPreview
@TabletPortraitNightPreview
@TabletLandscapeNightPreview
annotation class TabletPreviews

@Preview(name = "large font", fontScale = 2f)
annotation class LargeFontPreviews
