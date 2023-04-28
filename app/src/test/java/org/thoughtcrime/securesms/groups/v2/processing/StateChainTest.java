package org.thoughtcrime.securesms.groups.v2.processing;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class StateChainTest {

  private static final int BAD_DELTA = 256;

  private final StateChain<Character, Integer> stateChain = new StateChain<>(
                                                                    (c, d) -> {
                                                                      if (d == BAD_DELTA) return null;
                                                                      return (char) (c + d);
                                                                    },
                                                                    (a, b) -> a - b,
                                                                    (a, b)->a==b);

  @Test
  public void push_one_state_pair() {
    stateChain.push('A', 0);

    assertThat(stateChain.getList(), is(singletonList(pair('A', 0))));
  }

  @Test
  public void push_two_state_pairs() {
    stateChain.push('A', 0);
    stateChain.push('B', 1);

    assertThat(stateChain.getList(), is(asList(pair('A', 0),
                                               pair('B', 1))));
  }

  @Test
  public void push_two_state_pairs_null_first_delta() {
    stateChain.push('A', null);
    stateChain.push('B', 1);

    assertThat(stateChain.getList(), is(asList(pair('A', null),
                                               pair('B', 1))));
  }

  @Test
  public void push_two_state_pairs_with_missing_delta() {
    stateChain.push('A', 0);
    stateChain.push('B', null);

    assertThat(stateChain.getList(), is(asList(pair('A', 0),
                                               pair('B', 1))));
  }

  @Test
  public void push_two_state_pairs_with_missing_state() {
    stateChain.push('A', 0);
    stateChain.push(null, 1);

    assertThat(stateChain.getList(), is(asList(pair('A', 0),
                                               pair('B', 1))));
  }

  @Test
  public void push_one_state_pairs_with_missing_state_and_delta() {
    stateChain.push(null, null);

    assertThat(stateChain.getList(), is(emptyList()));
  }

    @Test
  public void push_two_state_pairs_with_missing_state_and_delta() {
    stateChain.push('A', 0);
    stateChain.push(null, null);

    assertThat(stateChain.getList(), is(singletonList(pair('A', 0))));
  }

  @Test
  public void push_two_state_pairs_that_do_not_match() {
    stateChain.push('D', 0);
    stateChain.push('E', 2);

    assertThat(stateChain.getList(), is(asList(pair('D', 0),
                                               pair('F', 2),
                                               pair('E', -1))));
  }

  @Test
  public void push_one_state_pair_null_delta() {
    stateChain.push('A', null);

    assertThat(stateChain.getList(), is(singletonList(pair('A', null))));
  }

  @Test
  public void push_two_state_pairs_with_no_diff() {
    stateChain.push('Z', null);
    stateChain.push('Z', 0);

    assertThat(stateChain.getList(), is(singletonList(pair('Z', null))));
  }

  @Test
  public void push_one_state_pair_null_state() {
    stateChain.push(null, 1);

    assertThat(stateChain.getList(), is(emptyList()));
  }

  @Test
  public void bad_delta_results_in_reconstruction() {
    stateChain.push('C', 0);
    stateChain.push('F', BAD_DELTA);

    assertThat(stateChain.getList(), is(asList(pair('C', 0),
                                               pair('F', 3))));
  }

  @Test
  public void bad_delta_and_no_state_results_in_change_ignore() {
    stateChain.push('C', 0);
    stateChain.push(null, BAD_DELTA);

    assertThat(stateChain.getList(), is(singletonList(pair('C', 0))));
  }

  private static StateChain.Pair<Character, Integer> pair(char c, Integer i) {
    return new StateChain.Pair<>(c, i);
  }
}
