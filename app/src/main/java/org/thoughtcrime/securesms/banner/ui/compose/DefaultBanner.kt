/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.banner.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.Previews
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
  importance: Importance = Importance.NORMAL,
  onDismissListener: (() -> Unit)? = null,
  onHideListener: (() -> Unit)? = null,
  actions: List<Action> = emptyList(),
  showProgress: Boolean = false,
  progressText: String = "",
  progressPercent: Int = -1,
  paddingValues: PaddingValues
) {
  Box(
    modifier = Modifier
      .padding(paddingValues)
      .clip(RoundedCornerShape(12.dp))
      .background(
        color = when (importance) {
          Importance.NORMAL -> MaterialTheme.colorScheme.surface
          Importance.ERROR -> colorResource(id = R.color.reminder_background)
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
      Column {
        Row(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier
              .weight(1f)
              .padding(start = 16.dp, top = 16.dp)
          ) {
            if (title.isNotNullOrBlank()) {
              Text(
                text = title,
                color = when (importance) {
                  Importance.NORMAL -> MaterialTheme.colorScheme.onSurface
                  Importance.ERROR -> colorResource(id = R.color.signal_light_colorOnSurface)
                },
                style = MaterialTheme.typography.bodyLarge
              )
            }

            Text(
              text = body,
              color = when (importance) {
                Importance.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                Importance.ERROR -> colorResource(id = R.color.signal_light_colorOnSurface)
              },
              style = MaterialTheme.typography.bodyMedium
            )

            if (showProgress) {
              if (progressPercent >= 0) {
                LinearProgressIndicator(
                  progress = { progressPercent / 100f },
                  color = when (importance) {
                    Importance.NORMAL -> MaterialTheme.colorScheme.primary
                    Importance.ERROR -> colorResource(id = R.color.signal_light_colorPrimary)
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
                    Importance.ERROR -> colorResource(id = R.color.signal_light_colorPrimary)
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
                Importance.ERROR -> colorResource(id = R.color.signal_light_colorOnSurface)
              }
            )
          }

          Box(modifier = Modifier.size(48.dp)) {
            if (onDismissListener != null) {
              IconButton(
                onClick = {
                  onHideListener?.invoke()
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
        }
        Row(
          horizontalArrangement = Arrangement.End,
          modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp)
        ) {
          for (action in actions) {
            TextButton(
              onClick = action.onClick,
              colors = when (importance) {
                Importance.NORMAL -> ButtonDefaults.textButtonColors()
                Importance.ERROR -> ButtonDefaults.textButtonColors(contentColor = colorResource(R.color.signal_light_colorPrimary))
              }
            ) {
              Text(
                text = if (!action.isPluralizedLabel) {
                  stringResource(id = action.label)
                } else {
                  pluralStringResource(id = action.label, count = action.pluralQuantity)
                }
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
@SignalPreview
private fun BubblesOptOutPreview() {
  Previews.Preview {
    DefaultBanner(
      title = null,
      body = stringResource(id = R.string.BubbleOptOutTooltip__description),
      actions = listOf(
        Action(R.string.BubbleOptOutTooltip__turn_off) {},
        Action(R.string.BubbleOptOutTooltip__not_now) {}
      ),
      paddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    )
  }
}

@Composable
@SignalPreview
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
@SignalPreview
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
