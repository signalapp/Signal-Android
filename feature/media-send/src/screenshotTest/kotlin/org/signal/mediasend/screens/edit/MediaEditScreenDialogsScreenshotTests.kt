/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.RtlPreview

class MediaEditScreenDialogsScreenshotTests {
  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun DiscardEditsConfirmationDialogPreview() {
    Previews.Preview {
      MediaEditScreenDialogs.DiscardEditsConfirmationDialog(
        onDiscard = {},
        onDismiss = {}
      )
    }
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun AddToGroupStoryConfirmationDialogPreview() {
    Previews.Preview {
      MediaEditScreenDialogs.AddToGroupStoryConfirmationDialog(
        groupName = "Signal Android",
        onAddToStory = {},
        onDeny = {},
        onDismissRequest = {}
      )
    }
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun SaveToStorageConfirmationDialogPreview() {
    Previews.Preview {
      MediaEditScreenDialogs.SaveToStorageConfirmationDialog(
        onSave = {},
        onDismissRequest = {}
      )
    }
  }

  @PreviewTest
  @DayNightPreviews
  @RtlPreview
  @Composable
  fun SavingToStorageProgressDialogPreview() {
    Previews.Preview {
      MediaEditScreenDialogs.SavingToStorageProgressDialog()
    }
  }
}
