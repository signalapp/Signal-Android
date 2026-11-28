/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import org.signal.core.ui.R

object Chips {

  /**
   * A chip for something the user has picked, such as a contact, which tapping removes.
   *
   * @param avatar Drawn at the start of the chip, clipped to a circle.
   */
  @Composable
  fun Removable(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null
  ) {
    val removeLabel = stringResource(R.string.Chips__remove_s, label)

    InputChip(
      selected = false,
      onClick = onRemove,
      label = {
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
      },
      avatar = avatar?.let {
        {
          Box(
            modifier = Modifier
              .size(InputChipDefaults.AvatarSize)
              .clip(CircleShape)
          ) {
            it()
          }
        }
      },
      trailingIcon = {
        Icon(
          imageVector = SignalIcons.X.imageVector,
          contentDescription = null,
          modifier = Modifier.size(InputChipDefaults.IconSize)
        )
      },
      modifier = modifier.semantics {
        onClick(label = removeLabel) {
          onRemove()
          true
        }
      }
    )
  }
}

@PreviewWrapper(SignalPreviewWrapper::class)
@DayNightPreviews
@Composable
private fun RemovableChipPreview() {
  Row {
    Chips.Removable(label = "Miles Morales", onRemove = {})
    Chips.Removable(label = "Gwen Stacy", onRemove = {})
  }
}
