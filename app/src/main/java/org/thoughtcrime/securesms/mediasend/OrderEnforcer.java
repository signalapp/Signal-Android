package org.thoughtcrime.securesms.mediasend;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

@SuppressWarnings("ConstantConditions")
public class OrderEnforcer<E> {

  private final Map<E, StageDetails> stages = new LinkedHashMap<>();

  public OrderEnforcer(@NonNull E... stages) {
    for (E stage : stages) {
      this.stages.put(stage, new StageDetails());
    }
  }

  public synchronized void run(@NonNull E stage, Runnable r) {
    if (isCompletedThrough(stage)) {
      r.run();
    } else {
      stages.get(stage).addAction(r);
    }
  }

  public synchronized void markCompleted(@NonNull E stage) {
    stages.get(stage).markCompleted();

    for (E s : stages.keySet()) {
      StageDetails details = stages.get(s);

      if (details.isCompleted()) {
        while (details.hasAction()) {
          details.popAction().run();
        }
      } else {
        break;
      }
    }
  }

  public synchronized void reset() {
    for (StageDetails details : stages.values()) {
      details.reset();
    }
  }

  private boolean isCompletedThrough(@NonNull E stage) {
    for (E s : stages.keySet()) {
      if (s.equals(stage)) {
        return stages.get(s).isCompleted();
      } else if (!stages.get(s).isCompleted()) {
        return false;
      }
    }
    return false;
  }

  private static class StageDetails {
    private boolean         completed = false;
    private Stack<Runnable> actions   = new Stack<>();

    boolean hasAction() {
      return !actions.isEmpty();
    }

    @Nullable Runnable popAction() {
      return actions.pop();
    }

    void addAction(@NonNull Runnable runnable) {
      actions.push(runnable);
    }

    void reset() {
      actions.clear();
      completed = false;
    }

    boolean isCompleted() {
      return completed;
    }

    void markCompleted() {
      completed = true;
    }
  }
}
