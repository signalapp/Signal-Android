/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.util.isNotNullOrBlank
import org.thoughtcrime.securesms.R
import org.signal.core.ui.R as CoreUiR

/**
 * A layout intended to display an in-app notification at the top of their screen,
 * and optionally allow them to take some action(s) in response.
 */
@Composable
fun DefaultBanner(
  title: String?,
  body: String,
  importance: Importance = Importance.NORMAL,
  onDismissListener: (() -> Unit)? = null,
  onHideListener: (() -> Unit)? = null,
  actions: List<Action> = emptyList(),
  showProgress: Boolean = false,
  progressText: String = "",
  progressPercent: Int = -1,
  paddingValues: PaddingValues
) {
  val isDarkTheme = isSystemInDarkTheme()
  Box(
    modifier = Modifier
      .padding(paddingValues)
      .clip(RoundedCornerShape(24.dp))
      .background(
        color = when (importance) {
          Importance.NORMAL -> colorResource(CoreUiR.color.signal_colorSurface2)
          Importance.ERROR -> colorResource(id = R.color.reminder_background)
        }
      )
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .defaultMinSize(minHeight = 54.dp)
    ) {
      Column {
        Row(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier
              .weight(1f)
              .padding(start = 16.dp, end = 16.dp, top = 16.dp)
          ) {
            if (title.isNotNullOrBlank()) {
              Text(
                text = title,
                color = when (importance) {
                  Importance.NORMAL -> MaterialTheme.colorScheme.onSurface
                  Importance.ERROR -> if (isDarkTheme) Color(0xFFC79869) else colorResource(id = CoreUiR.color.signal_light_colorOnSurface)
                },
                style = MaterialTheme.typography.bodyLarge
              )
            }

            Text(
              text = body,
              color = when (importance) {
                Importance.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                Importance.ERROR -> if (isDarkTheme) Color(0xFFC79869) else colorResource(id = CoreUiR.color.signal_light_colorOnSurface)
              },
              style = MaterialTheme.typography.bodyMedium
            )

            if (showProgress) {
              if (progressPercent >= 0) {
                LinearProgressIndicator(
                  progress = { progressPercent / 100f },
                  color = when (importance) {
                    Importance.NORMAL -> MaterialTheme.colorScheme.primary
                    Importance.ERROR -> colorResource(id = CoreUiR.color.signal_light_colorPrimary)
                  },
                  trackColor = MaterialTheme.colorScheme.primaryContainer,
                  modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                )
              } else {
                LinearProgressIndicator(
                  color = when (importance) {
                    Importance.NORMAL -> MaterialTheme.colorScheme.primary
                    Importance.ERROR -> colorResource(id = CoreUiR.color.signal_light_colorPrimary)
                  },
                  trackColor = MaterialTheme.colorScheme.primaryContainer,
                  modifier = Modifier.padding(vertical = 12.dp)
                )
              }
            }
            Text(
              text = progressText,
              style = MaterialTheme.typography.bodySmall,
              color = when (importance) {
                Importance.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                Importance.ERROR -> if (isDarkTheme) Color(0xFFC79869) else colorResource(id = CoreUiR.color.signal_light_colorOnSurface)
              }
            )
          }

          if (onDismissListener != null) {
            Box(modifier = Modifier.size(40.dp)) {
              IconButton(
                onClick = {
                  onHideListener?.invoke()
                  onDismissListener()
                },
                modifier = Modifier.size(40.dp)
              ) {
                Icon(
                  imageVector = SignalIcons.X.imageVector,
                  contentDescription = stringResource(id = R.string.InviteActivity_cancel),
                  tint = when (importance) {
                    Importance.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                    Importance.ERROR -> colorResource(id = CoreUiR.color.signal_colorOnSurface)
                  }
                )
              }
            }
          }
        }
        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 8.dp)
        ) {
          for (action in actions) {
            TextButton(
              onClick = action.onClick,
              colors = when (importance) {
                Importance.NORMAL -> ButtonDefaults.textButtonColors(
                  containerColor = colorResource(CoreUiR.color.signal_colorSurface5),
                  contentColor = MaterialTheme.colorScheme.onSurface
                )
                Importance.ERROR -> ButtonDefaults.textButtonColors(
                  containerColor = if (isDarkTheme) Color(0xFF392D22) else Color(0xFFF5E1B8),
                  contentColor = if (isDarkTheme) Color(0xFFC79869) else colorResource(id = CoreUiR.color.signal_colorNeutralInverse)
                )
              },
              modifier = Modifier.padding(horizontal = 8.dp)
            ) {
              Text(
                text = if (!action.isPluralizedLabel) {
                  stringResource(id = action.label)
                } else {
                  pluralStringResource(id = action.label, count = action.pluralQuantity)
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp)
              )
            }
          }
        }
      }
    }
  }
}

data class Action(val label: Int, val isPluralizedLabel: Boolean = false, val pluralQuantity: Int = 0, val onClick: () -> Unit)

enum class Importance {
  NORMAL, ERROR
}

@Composable
@DayNightPreviews
private fun BubblesOptOutPreview() {
  Previews.Preview {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.BubbleOptOutTooltip__description),
      actions = listOf(
        Action(R.string.BubbleOptOutTooltip__turn_off) {},
        Action(R.string.BubbleOptOutTooltip__not_now) {}
      ),
      onDismissListener = { },
      paddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    )
  }
}

@Composable
@DayNightPreviews
private fun ForcedUpgradePreview() {
  Previews.Preview {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today),
      importance = Importance.ERROR,
      onDismissListener = {},
      onHideListener = { },
      actions = listOf(Action(R.string.ExpiredBuildReminder_update_now) {}),
      paddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    )
  }
}

@Composable
@DayNightPreviews
private fun FullyLoadedErrorPreview() {
  val actions = listOf(
    Action(R.string.ExpiredBuildReminder_update_now) { },
    Action(R.string.BubbleOptOutTooltip__turn_off) { }
  )
  Previews.Preview {
    DefaultBanner(
      title = "Error",
      body = "Creating more errors.",
      importance = Importance.ERROR,
      onDismissListener = {},
      actions = actions,
      showProgress = true,
      progressText = "4 out of 10 errors created.",
      progressPercent = 40,
      paddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    )
  }
}
