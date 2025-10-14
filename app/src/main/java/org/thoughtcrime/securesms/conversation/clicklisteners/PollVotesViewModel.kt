package org.thoughtcrime.securesms.conversation.clicklisteners

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.signal.core.util.concurrent.SignalDispatchers
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * View model for [PollVotesFragment] which allows you to see results for a given poll.
 */
class PollVotesViewModel(pollId: Long) : ViewModel() {

  companion object {
    private val TAG = Log.tag(PollVotesViewModel::class)
  }

  private val _state = MutableStateFlow(PollVotesState())
  val state = _state.asStateFlow()

  init {
    loadPollInfo(pollId)
  }

  private fun loadPollInfo(pollId: Long) {
    viewModelScope.launch(SignalDispatchers.IO) {
      val poll = SignalDatabase.polls.getPollFromId(pollId)!!
      _state.update {
        it.copy(
          poll = poll,
          pollOptions = poll.pollOptions.map { option ->
            PollOptionModel(
              pollOption = option,
              voters = Recipient.resolvedList(option.voters.map { voter -> RecipientId.from(voter.id) })
            )
          },
          isAuthor = poll.authorId == Recipient.self().id.toLong()
        )
      }
    }
  }
}

data class PollVotesState(
  val poll: PollRecord? = null,
  val pollOptions: List<PollOptionModel> = emptyList(),
  val isAuthor: Boolean = false
)

data class PollOptionModel(
  val pollOption: PollOption,
  val voters: List<Recipient> = emptyList()
)
