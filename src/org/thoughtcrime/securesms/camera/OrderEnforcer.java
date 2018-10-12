package org.thoughtcrime.securesms.camera;

import android.support.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
      stages.get(stage).getActions().add(r);
    }
  }

  public synchronized void markCompleted(@NonNull E stage) {
    stages.get(stage).setCompleted(true);

    for (E s : stages.keySet()) {
      StageDetails details = stages.get(s);

      if (details.isCompleted()) {
        for (Runnable r : details.getActions()) {
          r.run();
        }
        details.getActions().clear();
      } else {
        break;
      }
    }
  }

  public synchronized void reset() {
    for (StageDetails details : stages.values()) {
      details.setCompleted(false);
      details.getActions().clear();
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
    private boolean        completed = false;
    private List<Runnable> actions   = new CopyOnWriteArrayList<>();

    @NonNull List<Runnable> getActions() {
      return actions;
    }

    boolean isCompleted() {
      return completed;
    }

    void setCompleted(boolean completed) {
      this.completed = completed;
    }
  }
}
