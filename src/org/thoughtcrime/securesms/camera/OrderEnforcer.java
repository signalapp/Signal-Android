package org.thoughtcrime.securesms.camera;

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class OrderEnforcer<E> {

  private final E[]                    stages;
  private final Map<E, Integer>        stageIndices;
  private final Map<E, List<Runnable>> actions;
  private final boolean[]              completion;

  public OrderEnforcer(@NonNull E... stages) {
    this.stages       = stages;
    this.stageIndices = new HashMap<>();
    this.actions      = new HashMap<>();
    this.completion   = new boolean[stages.length];

    for (int i = 0; i < stages.length; i++) {
      stageIndices.put(stages[i], i);
    }
  }

  public synchronized void run(@NonNull E stage, Runnable r) {
    if (isCompletedThrough(stage)) {
      r.run();
    } else {
      List<Runnable> stageActions = actions.containsKey(stage) ? actions.get(stage) : new CopyOnWriteArrayList<>();
      stageActions.add(r);

      actions.put(stage, stageActions);
    }
  }

  public synchronized void markCompleted(@NonNull E stage) {
    completion[stageIndices.get(stage)] = true;

    int i = 0;
    while (i < completion.length && completion[i]) {
      List<Runnable> stageActions = actions.get(stages[i]);
      if (stageActions != null) {
        for (Runnable r : stageActions) {
          r.run();
        }
        stageActions.clear();
      }
      i++;
    }
  }

  public synchronized void reset() {
    for (int i = 0; i < completion.length; i++) {
      completion[i] = false;
    }
    actions.clear();
  }

  private boolean isCompletedThrough(@NonNull E stage) {
    int index = stageIndices.get(stage);
    int i     = 0;

    while (i <= index && i < completion.length) {
      if (!completion[i]) {
        return false;
      }
      i++;
    }
    return true;
  }
}
