package org.thoughtcrime.securesms.conversation.clicklisteners

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.compose.ComposeDialogFragment
import org.thoughtcrime.securesms.conversation.clicklisteners.PollVotesFragment.Companion.MAX_INITIAL_VOTER_COUNT
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.polls.Voter
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment that shows the results for a given poll.
 */
class PollVotesFragment : ComposeDialogFragment(), RecipientBottomSheetDialogFragment.Callback {

  companion object {
    const val MAX_INITIAL_VOTER_COUNT = 5
    const val RESULT_KEY = "PollVotesFragment"
    const val POLL_VOTES_FRAGMENT_TAG = "PollVotesFragment"

    private val TAG = Log.tag(PollVotesFragment::class.java)
    private const val ARG_POLL_ID = "poll_id"

    fun create(pollId: Long, fragmentManager: FragmentManager) {
      return PollVotesFragment().apply {
        arguments = bundleOf(ARG_POLL_ID to pollId)
      }.show(fragmentManager, POLL_VOTES_FRAGMENT_TAG)
    }
  }

  private val viewModel: PollVotesViewModel by viewModel {
    PollVotesViewModel(requireArguments().getLong(ARG_POLL_ID))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setStyle(STYLE_NO_FRAME, R.style.Signal_DayNight_Dialog_FullScreen)
  }

  @Composable
  override fun DialogContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffolds.Settings(
      title = stringResource(if (state.poll?.hasEnded == true) R.string.Poll__poll_results else R.string.Poll__poll_details),
      onNavigationClick = this::dismissAllowingStateLoss,
      navigationIcon = ImageVector.vectorResource(id = R.drawable.symbol_x_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { paddingValues ->
      if (state.poll == null) {
        return@Settings
      }
      Surface(modifier = Modifier.padding(paddingValues)) {
        Column {
          PollResultsScreen(
            state,
            onEndPoll = {
              setFragmentResult(RESULT_KEY, bundleOf(RESULT_KEY to true))
              dismissAllowingStateLoss()
            },
            onRecipientClick = { id ->
              RecipientBottomSheetDialogFragment.show(childFragmentManager, id, null)
            }
          )
        }
      }
    }
  }

  override fun onRecipientBottomSheetDismissed() = Unit

  override fun onMessageClicked() {
    dismissAllowingStateLoss()
  }
}

@Composable
private fun PollResultsScreen(
  state: PollVotesState,
  onEndPoll: () -> Unit = {},
  onRecipientClick: (RecipientId) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val expandedOptions = remember { mutableStateListOf<Int>() }
  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = modifier
        .fillMaxWidth()
    ) {
      item {
        Spacer(Modifier.size(16.dp))
        Text(
          text = stringResource(R.string.Poll__question),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.horizontalGutters()
        )
        TextField(
          value = state.poll!!.question,
          onValueChange = {},
          modifier = Modifier
            .padding(top = 12.dp, bottom = 24.dp)
            .horizontalGutters()
            .fillMaxWidth(),
          colors = TextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledIndicatorColor = Color.Transparent
          ),
          shape = RoundedCornerShape(8.dp),
          enabled = false
        )
      }

      itemsIndexed(state.pollOptions) { index, option ->
        PollOptionSection(
          option = option,
          onRecipientClick = onRecipientClick,
          index = index,
          isExpanded = expandedOptions.contains(index)
        ) { expandedOptions.add(it) }
        if (index != state.pollOptions.lastIndex) {
          Dividers.Default()
        } else if (!state.poll!!.hasEnded) {
          Spacer(Modifier.size(72.dp))
        }
      }
    }
    if (state.isAuthor && !state.poll!!.hasEnded) {
      Row(
        modifier = Modifier
          .background(MaterialTheme.colorScheme.surface)
          .padding(16.dp)
          .align(Alignment.BottomCenter)
      ) {
        Buttons.MediumTonal(onClick = onEndPoll, modifier = Modifier.fillMaxWidth()) {
          Text(text = stringResource(id = R.string.Poll__end_poll))
        }
      }
    }
  }
}

@Composable
private fun PollOptionSection(
  option: PollOptionModel,
  onRecipientClick: (RecipientId) -> Unit,
  index: Int,
  isExpanded: Boolean,
  onExpand: (Int) -> Unit
) {
  val context = LocalContext.current
  Row(
    modifier = Modifier
      .padding(vertical = 12.dp)
      .horizontalGutters(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(text = option.pollOption.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
    if (option.hasMostVotes) {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_favorite_fill_16),
        contentDescription = stringResource(R.string.Poll__poll_winner)
      )
    }
    if (option.voters.isNotEmpty()) {
      Text(text = pluralStringResource(R.plurals.Poll__num_votes, option.voters.size, option.voters.size), style = MaterialTheme.typography.bodyLarge)
    }
  }

  if (option.voters.isEmpty()) {
    Text(
      text = stringResource(R.string.Poll__no_votes),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.horizontalGutters()
    )
  } else if (!isExpanded && option.voters.size > MAX_INITIAL_VOTER_COUNT) {
    option.voters.subList(0, MAX_INITIAL_VOTER_COUNT).forEach { recipient ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = { onRecipientClick(recipient.id) })
          .padding(vertical = 12.dp)
          .horizontalGutters()
      ) {
        AvatarImage(
          recipient = recipient,
          modifier = Modifier
            .padding(end = 16.dp)
            .size(40.dp)
        )
        Text(text = if (recipient.isSelf) stringResource(id = R.string.Recipient_you) else recipient.getShortDisplayName(context))
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .clickable { onExpand(index) }
        .padding(vertical = 12.dp)
        .horizontalGutters()
        .fillMaxWidth()
    ) {
      Image(
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        imageVector = ImageVector.vectorResource(id = R.drawable.symbol_chevron_down_24),
        contentDescription = stringResource(R.string.Poll__see_all),
        modifier = Modifier
          .size(40.dp)
          .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
          .padding(8.dp)
      )
      Text(text = stringResource(R.string.Poll__see_all), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
    }
  } else {
    option.voters.forEach { recipient ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = { onRecipientClick(recipient.id) })
          .padding(vertical = 12.dp)
          .horizontalGutters()
      ) {
        AvatarImage(
          recipient = recipient,
          modifier = Modifier
            .padding(end = 16.dp)
            .size(40.dp)
        )
        Text(text = if (recipient.isSelf) stringResource(id = R.string.Recipient_you) else recipient.getShortDisplayName(context))
      }
    }
  }
  Spacer(Modifier.size(16.dp))
}

@DayNightPreviews
@Composable
private fun PollResultsScreenPreview() {
  Previews.Preview {
    PollResultsScreen(
      state = PollVotesState(
        PollRecord(
          id = 1,
          question = "How do you feel about finished compose previews?",
          pollOptions = listOf(
            PollOption(1, "Yay", listOf(Voter(1, 1), Voter(12, 1), Voter(3, 1))),
            PollOption(2, "Ok", listOf(Voter(2, 1), Voter(4, 1))),
            PollOption(3, "Nay", emptyList())
          ),
          allowMultipleVotes = false,
          hasEnded = true,
          authorId = 1,
          messageId = 1
        ),
        isAuthor = true
      )
    )
  }
}
