package org.thoughtcrime.securesms.groups.v2.processing

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Test

class StateChainTest {
  private val stateChain = StateChain<Char, Int>(
    { state, delta ->
      if (delta == BAD_DELTA) return@StateChain null
      (state.code + delta).toChar()
    },
    { state1, state2 -> state1.code - state2.code },
    { state1, state2 -> state1 == state2 }
  )

  @Test
  fun push_one_state_pair() {
    stateChain.push('A', 0)

    assertThat(stateChain.list).containsExactly(stateChainPair('A', 0))
  }

  @Test
  fun push_two_state_pairs() {
    stateChain.push('A', 0)
    stateChain.push('B', 1)

    assertThat(stateChain.list)
      .containsExactly(stateChainPair('A', 0), stateChainPair('B', 1))
  }

  @Test
  fun push_two_state_pairs_null_first_delta() {
    stateChain.push('A', null)
    stateChain.push('B', 1)

    assertThat(stateChain.list)
      .containsExactly(stateChainPair('A', null), stateChainPair('B', 1))
  }

  @Test
  fun push_two_state_pairs_with_missing_delta() {
    stateChain.push('A', 0)
    stateChain.push('B', null)

    assertThat(stateChain.list)
      .containsExactly(stateChainPair('A', 0), stateChainPair('B', 1))
  }

  @Test
  fun push_two_state_pairs_with_missing_state() {
    stateChain.push('A', 0)
    stateChain.push(null, 1)

    assertThat(stateChain.list)
      .containsExactly(stateChainPair('A', 0), stateChainPair('B', 1))
  }

  @Test
  fun push_one_state_pairs_with_missing_state_and_delta() {
    stateChain.push(null, null)

    assertThat(stateChain.list).isEmpty()
  }

  @Test
  fun push_two_state_pairs_with_missing_state_and_delta() {
    stateChain.push('A', 0)
    stateChain.push(null, null)

    assertThat(stateChain.list).containsExactly(stateChainPair('A', 0))
  }

  @Test
  fun push_two_state_pairs_that_do_not_match() {
    stateChain.push('D', 0)
    stateChain.push('E', 2)

    assertThat(stateChain.list).containsExactly(
      stateChainPair('D', 0),
      stateChainPair('F', 2),
      stateChainPair('E', -1)
    )
  }

  @Test
  fun push_one_state_pair_null_delta() {
    stateChain.push('A', null)

    assertThat(stateChain.list).containsExactly(stateChainPair('A', null))
  }

  @Test
  fun push_two_state_pairs_with_no_diff() {
    stateChain.push('Z', null)
    stateChain.push('Z', 0)

    assertThat(stateChain.list).containsExactly(stateChainPair('Z', null))
  }

  @Test
  fun push_one_state_pair_null_state() {
    stateChain.push(null, 1)

    assertThat(stateChain.list).isEmpty()
  }

  @Test
  fun bad_delta_results_in_reconstruction() {
    stateChain.push('C', 0)
    stateChain.push('F', BAD_DELTA)

    assertThat(stateChain.list).containsExactly(stateChainPair('C', 0), stateChainPair('F', 3))
  }

  @Test
  fun bad_delta_and_no_state_results_in_change_ignore() {
    stateChain.push('C', 0)
    stateChain.push(null, BAD_DELTA)

    assertThat(stateChain.list).containsExactly(stateChainPair('C', 0))
  }

  companion object {
    private const val BAD_DELTA = 256

    private fun <A, B> stateChainPair(first: A & Any, second: B): StateChain.Pair<A & Any, B> {
      return StateChain.Pair<A & Any, B>(first, second)
    }
  }
}
