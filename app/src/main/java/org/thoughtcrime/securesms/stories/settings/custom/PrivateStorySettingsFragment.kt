package org.thoughtcrime.securesms.stories.settings.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.Texts
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.components.WrapperDialogFragment
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.compose.SignalTheme
import org.thoughtcrime.securesms.database.model.DistributionListId
import org.thoughtcrime.securesms.database.model.DistributionListPrivacyMode
import org.thoughtcrime.securesms.database.model.DistributionListRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.rememberRecipientField
import org.thoughtcrime.securesms.stories.dialogs.StoryDialogs
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.whispersystems.signalservice.api.push.DistributionId
import java.util.UUID

/**
 * Settings fragment for private stories.
 */
class PrivateStorySettingsFragment : ComposeFragment() {

  private val viewModel: PrivateStorySettingsViewModel by viewModels(
    factoryProducer = {
      PrivateStorySettingsViewModel.Factory(PrivateStorySettingsFragmentArgs.fromBundle(requireArguments()).distributionListId, PrivateStorySettingsRepository())
    }
  )

  private val distributionListId: DistributionListId
    get() = PrivateStorySettingsFragmentArgs.fromBundle(requireArguments()).distributionListId

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.observeAsState()
    val callback = remember { DefaultPrivateStorySettingsScreenCallback(viewModel, distributionListId) }

    state?.let { currentState ->
      SignalTheme {
        PrivateStorySettingsScreen(
          state = currentState,
          callback = callback
        )
      }
    }
  }

  inner class DefaultPrivateStorySettingsScreenCallback(
    private val viewModel: PrivateStorySettingsViewModel,
    private val distributionListId: DistributionListId
  ) : PrivateStorySettingsScreenCallback {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onEditClick() {
      val action = PrivateStorySettingsFragmentDirections.actionPrivateStorySettingsToEditStoryNameFragment(distributionListId, viewModel.getName())
      findNavController().navigate(action)
    }

    override fun onAddViewerClick() {
      findNavController().safeNavigate(PrivateStorySettingsFragmentDirections.actionPrivateStorySettingsToEditStoryViewers(distributionListId))
    }

    override fun onRepliesAndReactionsToggle(enabled: Boolean) {
      viewModel.setRepliesAndReactionsEnabled(enabled)
    }

    override fun onDeleteStoryClick(storyName: String) {
      StoryDialogs.deleteDistributionList(requireContext(), storyName) {
        viewModel.delete().subscribe { findNavController().popBackStack() }
      }
    }

    override fun onRemoveRecipientClick(recipientId: RecipientId, displayName: String) {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.PrivateStorySettingsFragment__remove_s, displayName))
        .setMessage(R.string.PrivateStorySettingsFragment__this_person_will_no_longer)
        .setPositiveButton(R.string.PrivateStorySettingsFragment__remove) { _, _ -> viewModel.remove(recipientId) }
        .setNegativeButton(android.R.string.cancel) { _, _ -> }
        .show()
    }
  }

  class Dialog : WrapperDialogFragment() {
    override fun getWrappedFragment(): Fragment {
      return NavHostFragment.create(R.navigation.private_story_settings, requireArguments())
    }
  }

  companion object {
    fun createAsDialog(distributionListId: DistributionListId): DialogFragment {
      return Dialog().apply {
        arguments = PrivateStorySettingsFragmentArgs.Builder(distributionListId).build().toBundle()
      }
    }
  }
}

@Composable
private fun PrivateStorySettingsScreen(
  state: PrivateStorySettingsState,
  callback: PrivateStorySettingsScreenCallback
) {
  Scaffolds.Settings(
    title = state.privateStory?.name.orEmpty(),
    onNavigationClick = callback::onNavigationClick,
    navigationIcon = ImageVector.vectorResource(id = R.drawable.symbol_arrow_start_24),
    actions = {
      IconButton(onClick = callback::onEditClick) {
        Icon(
          imageVector = ImageVector.vectorResource(id = R.drawable.symbol_edit_24),
          contentDescription = stringResource(R.string.EditPrivateStoryNameFragment__edit_story_name)
        )
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier.padding(paddingValues)
    ) {
      if (state.privateStory != null) {
        item {
          Texts.SectionHeader(text = stringResource(R.string.MyStorySettingsFragment__who_can_view_this_story))
        }

        item {
          PrivateStoryItem.AddViewer(onClick = callback::onAddViewerClick)
        }

        items(state.privateStory.members, key = { it }) { member ->
          RecipientRow(member, callback::onRemoveRecipientClick, modifier = Modifier.animateItem())
        }

        item {
          Dividers.Default()
        }

        item {
          Texts.SectionHeader(text = stringResource(R.string.MyStorySettingsFragment__replies_amp_reactions))
        }

        item {
          Rows.ToggleRow(
            text = stringResource(R.string.MyStorySettingsFragment__allow_replies_amp_reactions),
            label = stringResource(R.string.MyStorySettingsFragment__let_people_who_can_view_your_story_react_and_reply),
            checked = state.areRepliesAndReactionsEnabled,
            onCheckChanged = { callback.onRepliesAndReactionsToggle(it) }
          )
        }

        item {
          Dividers.Default()
        }

        item {
          Rows.TextRow(
            text = stringResource(R.string.PrivateStorySettingsFragment__delete_custom_story),
            foregroundTint = colorResource(R.color.signal_colorError),
            onClick = { callback.onDeleteStoryClick(state.privateStory.name) }
          )
        }
      }
    }
  }

  if (state.isActionInProgress) {
    Dialogs.IndeterminateProgressDialog()
  }
}

@Composable
private fun RecipientRow(recipientId: RecipientId, onClick: (RecipientId, String) -> Unit, modifier: Modifier = Modifier) {
  val displayName by rememberDisplayName(recipientId)
  val callback = remember(displayName) { { onClick(recipientId, displayName) } }

  Rows.TextRow(
    text = {
      Text(text = displayName)
    },
    icon = {
      AvatarImage(
        recipientId = recipientId,
        contentDescription = null,
        modifier = Modifier
          .size(40.dp)
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
      )
    },
    onClick = callback,
    modifier = modifier
  )
}

@Composable
private fun rememberDisplayName(recipientId: RecipientId): State<String> {
  if (LocalInspectionMode.current) {
    return remember { mutableStateOf("Recipient ${recipientId.toLong()}") }
  }

  val context = LocalContext.current
  return rememberRecipientField(recipientId) { getDisplayName(context) }
}

@DayNightPreviews
@Composable
private fun PrivateStorySettingsScreenPreview() {
  Previews.Preview {
    PrivateStorySettingsScreen(
      state = PrivateStorySettingsState(
        privateStory = DistributionListRecord(
          id = DistributionListId.from(1L),
          name = "Close Friends",
          distributionId = DistributionId.from(UUID.randomUUID()),
          allowsReplies = true,
          rawMembers = listOf(
            RecipientId.from(1L),
            RecipientId.from(2L),
            RecipientId.from(3L)
          ),
          members = listOf(
            RecipientId.from(1L),
            RecipientId.from(2L),
            RecipientId.from(3L)
          ),
          deletedAtTimestamp = 0L,
          isUnknown = false,
          privacyMode = DistributionListPrivacyMode.ONLY_WITH
        ),
        areRepliesAndReactionsEnabled = true,
        isActionInProgress = false
      ),
      callback = PrivateStorySettingsScreenCallback.Empty
    )
  }
}

interface PrivateStorySettingsScreenCallback {
  fun onNavigationClick()
  fun onEditClick()
  fun onAddViewerClick()
  fun onRepliesAndReactionsToggle(enabled: Boolean)
  fun onDeleteStoryClick(storyName: String)
  fun onRemoveRecipientClick(recipientId: RecipientId, displayName: String)

  object Empty : PrivateStorySettingsScreenCallback {
    override fun onNavigationClick() = Unit
    override fun onEditClick() = Unit
    override fun onAddViewerClick() = Unit
    override fun onRepliesAndReactionsToggle(enabled: Boolean) = Unit
    override fun onDeleteStoryClick(storyName: String) = Unit
    override fun onRemoveRecipientClick(recipientId: RecipientId, displayName: String) = Unit
  }
}
