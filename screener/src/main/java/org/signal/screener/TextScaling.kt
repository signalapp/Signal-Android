/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.screener

internal class TextSize(val px: Float, val description: String)

/**
 * Compares each text node's size at a larger font scale against the same node in the matching 1x config.
 */
internal fun findUnscaledText(configs: List<StressConfig>, textSizesByConfig: Map<StressConfig, Map<String, TextSize>>): List<LayoutProblem> {
  return configs
    .filter { it.fontScale > 1f }
    .flatMap { config ->
      val baseline = configs.firstOrNull { it.fontScale == 1f && it.windowSize == config.windowSize && it.layoutDirection == config.layoutDirection && it.locale == config.locale }
        ?: return@flatMap emptyList()
      val baselineSizes = textSizesByConfig.getValue(baseline)

      textSizesByConfig.getValue(config).mapNotNull { (path, scaled) ->
        val unscaled = baselineSizes[path] ?: return@mapNotNull null
        if (scaled.px <= unscaled.px) {
          LayoutProblem(
            check = LayoutCheck.TEXT_DOES_NOT_SCALE,
            config = config,
            nodePath = path,
            message = "Text does not grow with the system font scale (${unscaled.px}px at 1x, ${scaled.px}px at ${config.fontScale}x): ${scaled.description}"
          )
        } else {
          null
        }
      }
    }
}
