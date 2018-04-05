package org.thoughtcrime.securesms.jobs.requirements;


import android.content.Context;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.dependencies.ContextDependent;
import org.whispersystems.jobqueue.requirements.Requirement;

public class SqlCipherMigrationRequirement implements Requirement, ContextDependent {

  @SuppressWarnings("unused")
  private static final String TAG = SqlCipherMigrationRequirement.class.getSimpleName();

  private transient Context context;

  public SqlCipherMigrationRequirement(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return !TextSecurePreferences.getNeedsSqlCipherMigration(context);
  }
}
