package org.thoughtcrime.securesms.jobmanager;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class ConstraintInstantiator {

  private final Map<String, Constraint.Factory> constraintFactories;

  ConstraintInstantiator(@NonNull Map<String, Constraint.Factory> constraintFactories) {
    this.constraintFactories = new HashMap<>(constraintFactories);
  }

  public @NonNull Constraint instantiate(@NonNull String constraintFactoryKey) {
    if (constraintFactories.containsKey(constraintFactoryKey)) {
      return constraintFactories.get(constraintFactoryKey).create();
    } else {
      throw new IllegalStateException("Tried to instantiate a constraint with key '" + constraintFactoryKey + "', but no matching factory was found.");
    }
  }
}
