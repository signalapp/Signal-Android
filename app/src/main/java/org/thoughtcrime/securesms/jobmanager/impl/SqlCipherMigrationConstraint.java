package org.thoughtcrime.securesms.jobmanager.impl;

import android.app.job.JobInfo;
import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Constraint;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

public class SqlCipherMigrationConstraint implements Constraint {

  public static final String KEY = "SqlCipherMigrationConstraint";

  private final Context application;

  private SqlCipherMigrationConstraint(@NonNull Context application) {
    this.application = application;
  }

  @Override
  public boolean isMet() {
    return !TextSecurePreferences.getNeedsSqlCipherMigration(application);
  }

  @NonNull
  @Override
  public String getFactoryKey() {
    return KEY;
  }

  @Override
  public void applyToJobInfo(@NonNull JobInfo.Builder jobInfoBuilder) {
  }

  public static final class Factory implements Constraint.Factory<SqlCipherMigrationConstraint> {

    private final Context application;

    public Factory(@NonNull Context application) {
      this.application = application;
    }

    @Override
    public SqlCipherMigrationConstraint create() {
      return new SqlCipherMigrationConstraint(application);
    }
  }
}
