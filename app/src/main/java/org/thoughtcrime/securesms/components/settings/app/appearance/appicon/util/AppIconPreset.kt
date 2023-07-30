/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon.util

import android.content.ComponentName
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.thoughtcrime.securesms.R

enum class AppIconPreset(private val componentName: String, @DrawableRes val iconPreviewResId: Int, @StringRes val labelResId: Int) {
  DEFAULT(".RoutingActivity", R.drawable.ic_app_icon_default_top_preview, R.string.app_name),
  WHITE(".RoutingActivityAltWhite", R.drawable.ic_app_icon_signal_white_top_preview, R.string.app_name),
  COLOR(".RoutingActivityAltColor", R.drawable.ic_app_icon_signal_color_top_preview, R.string.app_name),
  DARK(".RoutingActivityAltDark", R.drawable.ic_app_icon_signal_dark_top_preview, R.string.app_name),
  DARK_VARIANT(".RoutingActivityAltDarkVariant", R.drawable.ic_app_icon_signal_dark_variant_top_preview, R.string.app_name),
  CHAT(".RoutingActivityAltChat", R.drawable.ic_app_icon_chat_top_preview, R.string.app_name),
  BUBBLES(".RoutingActivityAltBubbles", R.drawable.ic_app_icon_bubbles_top_preview, R.string.app_name),
  YELLOW(".RoutingActivityAltYellow", R.drawable.ic_app_icon_yellow_top_preview, R.string.app_name),
  NEWS(".RoutingActivityAltNews", R.drawable.ic_app_icon_news_top_preview, R.string.app_icon_label_news),
  NOTES(".RoutingActivityAltNotes", R.drawable.ic_app_icon_notes_top_preview, R.string.app_icon_label_notes),
  WEATHER(".RoutingActivityAltWeather", R.drawable.ic_app_icon_weather_top_preview, R.string.app_icon_label_weather),
  WAVES(".RoutingActivityAltWaves", R.drawable.ic_app_icon_waves_top_preview, R.string.app_icon_label_waves);

  fun getComponentName(context: Context): ComponentName {
    val applicationContext = context.applicationContext
    return ComponentName(applicationContext, "org.thoughtcrime.securesms" + componentName)
  }
}
