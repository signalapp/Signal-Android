package org.thoughtcrime.securesms.groups.v2.processing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Maintains a chain of state pairs:
 * <pre>
 * {@code
 *  (S1, Delta1),
 *  (S2, Delta2),
 *  (S3, Delta3)
 * }
 * </pre>
 * Such that the states always include all deltas.
 * <pre>
 * {@code
 *  (S1, _),
 *  (S1 + Delta2, Delta2),
 *  (S1 + Delta2 + Delta3, Delta3),
 * }
 * </pre>
 * <p>
 * If a pushed delta does not correct create the new state (tested by {@link StateEquality}), a new
 * delta and state is inserted like so:
 * <pre>
 * {@code
 * (PreviousState, PreviousDelta),
 * (PreviousState + NewDelta, NewDelta),
 * (NewState, PreviousState + NewDelta - NewState),
 * }
 * </pre>
 * That is it keeps both the newly supplied delta and state, but creates an interim state and delta.
 *
 * The + function is supplied by {@link AddDelta} and the - function is supplied by {@link SubtractStates}.
 */
public final class StateChain<State, Delta> {

  private final AddDelta<State, Delta>       add;
  private final SubtractStates<State, Delta> subtract;
  private final StateEquality<State>         stateEquality;

  private final List<Pair<State, Delta>> pairs = new LinkedList<>();

  public StateChain(@NonNull AddDelta<State, Delta> add,
                    @NonNull SubtractStates<State, Delta> subtract,
                    @NonNull StateEquality<State> stateEquality)
  {
    this.add           = add;
    this.subtract      = subtract;
    this.stateEquality = stateEquality;
  }

  public void push(@Nullable State state, @Nullable Delta delta) {
    if (delta == null && state == null) return;

    boolean bothSupplied = state != null && delta != null;
    State latestState = getLatestState();

    if (latestState == null && state == null) return;

    if (latestState != null) {
      if (delta == null) {

        delta = subtract.subtract(state, latestState);
      }

      if (state == null) {
        state = add.add(latestState, delta);

        if (state == null) return;
      }

      if (bothSupplied) {
        State calculatedState = add.add(latestState, delta);

        if (calculatedState == null) {
          push(state, null);
          return;
        } else if (!stateEquality.equals(state, calculatedState)) {
          push(null, delta);
          push(state, null);
          return;
        }
      }
    }

    if (latestState == null || !stateEquality.equals(latestState, state)) {
      pairs.add(new Pair<>(state, delta));
    }
  }

  public @Nullable State getLatestState() {
    int size = pairs.size();

    return size == 0 ? null : pairs.get(size - 1).getState();
  }

  public List<Pair<State, Delta>> getList() {
    return new ArrayList<>(pairs);
  }

  public static final class Pair<State, Delta> {
    @NonNull  private final State state;
    @Nullable private final Delta delta;

    Pair(@NonNull State state, @Nullable Delta delta) {
      this.state = state;
      this.delta = delta;
    }

    public @NonNull State getState() {
      return state;
    }

    public @Nullable Delta getDelta() {
      return delta;
    }

    @Override
    public String toString() {
      return String.format("(%s, %s)", state, delta);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Pair<?, ?> other = (Pair<?, ?>) o;

      return state.equals(other.state) &&
               Objects.equals(delta, other.delta);
    }

    @Override
    public int hashCode() {
      int result = state.hashCode();
      result = 31 * result + (delta != null ? delta.hashCode() : 0);
      return result;
    }
  }

  interface AddDelta<State, Delta> {

    /**
     * Add {@param delta} to {@param state} and return the new {@link State}.
     * <p>
     * If this returns null, then the delta could not be applied and will be ignored.
     */
    @Nullable State add(@NonNull State state, @NonNull Delta delta);
  }

  interface SubtractStates<State, Delta> {

    /**
     * Finds a delta = {@param stateB} - {@param stateA}
     * such that {@param stateA} + {@link Delta} = {@param stateB}.
     */
    @NonNull Delta subtract(@NonNull State stateB, @NonNull State stateA);
  }

  interface StateEquality<State> {

    boolean equals(@NonNull State stateA, @NonNull State stateB);
  }
}
