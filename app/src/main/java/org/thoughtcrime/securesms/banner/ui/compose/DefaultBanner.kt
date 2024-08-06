/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.ui.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.SignalPreview
import org.signal.core.util.isNotNullOrBlank
import org.thoughtcrime.securesms.R

/**
 * A layout intended to display an in-app notification at the top of their screen,
 * and optionally allow them to take some action(s) in response.
 */
@Composable
fun DefaultBanner(
  title: String?,
  body: String,
  importance: Importance,
  isDismissible: Boolean,
  onDismissListener: () -> Unit,
  onHideListener: () -> Unit,
  @DrawableRes icon: Int? = null,
  actions: List<Action> = emptyList(),
  showProgress: Boolean = false,
  progressText: String = "",
  progressPercent: Int = -1
) {
  Box(
    modifier = Modifier
      .background(
        color = when (importance) {
          Importance.NORMAL -> MaterialTheme.colorScheme.surface
          Importance.ERROR, Importance.TERMINAL -> colorResource(id = R.color.reminder_background)
        }
      )
      .border(
        width = 1.dp,
        color = colorResource(id = R.color.signal_colorOutline_38),
        shape = RoundedCornerShape(12.dp)
      )
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .defaultMinSize(minHeight = 74.dp)
    ) {
      if (icon != null) {
        Box(
          modifier = Modifier
            .padding(horizontal = 12.dp)
            .size(48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            painter = painterResource(id = icon),
            contentDescription = stringResource(id = R.string.ReminderView_icon_content_description),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.5.dp)
          )
        }
      }
      Column {
        Row(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier
              .padding(12.dp)
              .weight(1f)
          ) {
            if (title.isNotNullOrBlank()) {
              Text(
                text = title,
                color = when (importance) {
                  Importance.NORMAL -> MaterialTheme.colorScheme.onSurface
                  Importance.ERROR, Importance.TERMINAL -> colorResource(id = R.color.signal_light_colorOnSurface)
                },
                style = MaterialTheme.typography.bodyLarge
              )
            }

            Text(
              text = body,
              color = when (importance) {
                Importance.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                Importance.ERROR, Importance.TERMINAL -> colorResource(id = R.color.signal_light_colorOnSurface)
              },
              style = MaterialTheme.typography.bodyMedium
            )

            if (showProgress) {
              if (progressPercent >= 0) {
                LinearProgressIndicator(
                  progress = { progressPercent / 100f },
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.primaryContainer,
                  modifier = Modifier.padding(vertical = 12.dp)
                )
              } else {
                LinearProgressIndicator(
                  color = MaterialTheme.colorScheme.primary,
                  trackColor = MaterialTheme.colorScheme.primaryContainer,
                  modifier = Modifier.padding(vertical = 12.dp)
                )
              }
              Text(
                text = progressText,
                style = MaterialTheme.typography.bodySmall,
                color = when (importance) {
                  Importance.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                  Importance.ERROR, Importance.TERMINAL -> colorResource(id = R.color.signal_light_colorOnSurface)
                }
              )
            }
          }

          if (isDismissible) {
            IconButton(
              onClick = {
                onHideListener()
                onDismissListener()
              },
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                painter = painterResource(id = R.drawable.symbol_x_24),
                contentDescription = stringResource(id = R.string.InviteActivity_cancel)
              )
            }
          }
        }
        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier.fillMaxWidth()
        ) {
          for (action in actions) {
            TextButton(onClick = action.onClick) {
              Text(text = stringResource(id = action.label))
            }
          }
        }
      }
    }
  }
}

data class Action(@StringRes val label: Int, val onClick: () -> Unit)

enum class Importance {
  NORMAL, ERROR, TERMINAL
}

@Composable
@SignalPreview
private fun ForcedUpgradePreview() {
  DefaultBanner(
    title = null,
    body = stringResource(id = R.string.OutdatedBuildReminder_your_version_of_signal_will_expire_today),
    importance = Importance.TERMINAL,
    isDismissible = false,
    actions = listOf(Action(R.string.ExpiredBuildReminder_update_now) {}),
    onHideListener = { },
    onDismissListener = {}
  )
}

@Composable
@SignalPreview
private fun FullyLoadedErrorPreview() {
  val actions = listOf(
    Action(R.string.ExpiredBuildReminder_update_now) { },
    Action(R.string.BubbleOptOutTooltip__turn_off) { }
  )
  DefaultBanner(
    icon = R.drawable.symbol_error_circle_24,
    title = "Error",
    body = "Creating more errors.",
    importance = Importance.ERROR,
    isDismissible = true,
    actions = actions,
    showProgress = true,
    progressText = "4 out of 10 errors created.",
    progressPercent = 40,
    onHideListener = { },
    onDismissListener = {}
  )
}

@Composable
@SignalPreview
private fun FullyLoadedTerminalPreview() {
  val actions = listOf(
    Action(R.string.ExpiredBuildReminder_update_now) { },
    Action(R.string.BubbleOptOutTooltip__turn_off) { }
  )
  DefaultBanner(
    icon = R.drawable.symbol_error_circle_24,
    title = "Terminal",
    body = "This is a terminal state.",
    importance = Importance.TERMINAL,
    isDismissible = true,
    actions = actions,
    showProgress = true,
    progressText = "93% terminated",
    progressPercent = 93,
    onHideListener = { },
    onDismissListener = {}
  )
}
