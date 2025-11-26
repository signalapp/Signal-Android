package org.thoughtcrime.securesms.components.settings.app.internal

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.util.DynamicTheme

/**
 * Internal tool for testing various story-related dialogs.
 */
class InternalStoryDialogLauncherFragment : ComposeFragment() {

  @Composable
  override fun FragmentContent() {
    val callback = remember { DefaultInternalStoryDialogLauncherCallback() }

    SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(LocalContext.current)) {
      InternalStoryDialogLauncherScreen(
        callback = callback
      )
    }
  }

  /**
   * Default callback implementation that launches story dialogs and displays toast notifications.
   */
  inner class DefaultInternalStoryDialogLauncherCallback : InternalStoryDialogLauncherScreenCallback {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onRemoveGroupStoryClick() {
      StoryDialogs.removeGroupStory(requireContext(), "Family") {
        Toast.makeText(requireContext(), "Remove group story", Toast.LENGTH_SHORT).show()
      }
    }

    override fun onRetrySendClick() {
      StoryDialogs.resendStory(requireContext()) {
        Toast.makeText(requireContext(), "Retry send", Toast.LENGTH_SHORT).show()
      }
    }

    override fun onStoryOrProfileSelectorClick() {
      StoryDialogs.displayStoryOrProfileImage(
        context = requireContext(),
        onViewStory = { Toast.makeText(requireContext(), R.string.StoryDialogs__view_story, Toast.LENGTH_SHORT).show() },
        onViewAvatar = { Toast.makeText(requireContext(), R.string.StoryDialogs__view_profile_photo, Toast.LENGTH_SHORT).show() }
      )
    }

    override fun onHideStoryClick() {
      StoryDialogs.hideStory(requireContext(), "Spiderman") {
        Toast.makeText(requireContext(), "Hide story", Toast.LENGTH_SHORT).show()
      }
    }

    override fun onTurnOffStoriesClick() {
      StoryDialogs.disableStories(requireContext(), false) {
        Toast.makeText(requireContext(), "Turn off stories", Toast.LENGTH_SHORT).show()
      }
    }

    override fun onTurnOffStoriesWithStoriesOnDiskClick() {
      StoryDialogs.disableStories(requireContext(), true) {
        Toast.makeText(requireContext(), "Turn off stories (with stories on disk)", Toast.LENGTH_SHORT).show()
      }
    }

    override fun onDeleteCustomStoryClick() {
      StoryDialogs.deleteDistributionList(requireContext(), "Family") {
        Toast.makeText(requireContext(), "Delete custom story", Toast.LENGTH_SHORT).show()
      }
    }
  }
}

/**
 * Screen displaying a list of story dialogs available for testing.
 */
@Composable
fun InternalStoryDialogLauncherScreen(
  callback: InternalStoryDialogLauncherScreenCallback
) {
  Scaffolds.Settings(
    title = stringResource(R.string.preferences__internal_stories_dialog_launcher),
    onNavigationClick = callback::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(R.drawable.symbol_arrow_start_24)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.padding(paddingValues)
    ) {
      item {
        Rows.TextRow(
          text = "Remove group story",
          modifier = Modifier.clickable(onClick = callback::onRemoveGroupStoryClick)
        )
      }

      item {
        Rows.TextRow(
          text = "Retry send",
          modifier = Modifier.clickable(onClick = callback::onRetrySendClick)
        )
      }

      item {
        Rows.TextRow(
          text = "Story or profile selector",
          modifier = Modifier.clickable(onClick = callback::onStoryOrProfileSelectorClick)
        )
      }

      item {
        Rows.TextRow(
          text = "Hide story",
          modifier = Modifier.clickable(onClick = callback::onHideStoryClick)
        )
      }

      item {
        Rows.TextRow(
          text = "Turn off stories",
          modifier = Modifier.clickable(onClick = callback::onTurnOffStoriesClick)
        )
      }

      item {
        Rows.TextRow(
          text = "Turn off stories (with stories on disk)",
          modifier = Modifier.clickable(onClick = callback::onTurnOffStoriesWithStoriesOnDiskClick)
        )
      }

      item {
        Rows.TextRow(
          text = "Delete custom story",
          modifier = Modifier.clickable(onClick = callback::onDeleteCustomStoryClick)
        )
      }
    }
  }
}

/**
 * Callback interface for [InternalStoryDialogLauncherScreen] interactions.
 */
interface InternalStoryDialogLauncherScreenCallback {
  fun onNavigationClick()
  fun onRemoveGroupStoryClick()
  fun onRetrySendClick()
  fun onStoryOrProfileSelectorClick()
  fun onHideStoryClick()
  fun onTurnOffStoriesClick()
  fun onTurnOffStoriesWithStoriesOnDiskClick()
  fun onDeleteCustomStoryClick()

  object Empty : InternalStoryDialogLauncherScreenCallback {
    override fun onNavigationClick() = Unit
    override fun onRemoveGroupStoryClick() = Unit
    override fun onRetrySendClick() = Unit
    override fun onStoryOrProfileSelectorClick() = Unit
    override fun onHideStoryClick() = Unit
    override fun onTurnOffStoriesClick() = Unit
    override fun onTurnOffStoriesWithStoriesOnDiskClick() = Unit
    override fun onDeleteCustomStoryClick() = Unit
  }
}

@DayNightPreviews
@Composable
private fun InternalStoryDialogLauncherScreenPreview() {
  Previews.Preview {
    InternalStoryDialogLauncherScreen(
      callback = InternalStoryDialogLauncherScreenCallback.Empty
    )
  }
}
