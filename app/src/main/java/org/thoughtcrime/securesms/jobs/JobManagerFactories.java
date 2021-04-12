package org.thoughtcrime.securesms.jobs;

import android.app.Application;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.jobmanager.Constraint;
import org.thoughtcrime.securesms.jobmanager.ConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.CellServiceConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.CellServiceConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraintObserver;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkOrCellServiceConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.SqlCipherMigrationConstraint;
import org.thoughtcrime.securesms.jobmanager.impl.SqlCipherMigrationConstraintObserver;
import org.thoughtcrime.securesms.loki.api.PrepareAttachmentAudioExtrasJob;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JobManagerFactories {

  private static Collection<String> factoryKeys = new ArrayList<>();

  public static Map<String, Job.Factory> getJobFactories(@NonNull Application application) {
    HashMap<String, Job.Factory> factoryHashMap = new HashMap<String, Job.Factory>() {{
      put(AvatarDownloadJob.KEY,                     new AvatarDownloadJob.Factory());
      put(LocalBackupJob.KEY,                        new LocalBackupJob.Factory());
      put(RetrieveProfileAvatarJob.KEY,              new RetrieveProfileAvatarJob.Factory(application));
      put(TrimThreadJob.KEY,                         new TrimThreadJob.Factory());
      put(UpdateApkJob.KEY,                          new UpdateApkJob.Factory());
      put(PrepareAttachmentAudioExtrasJob.KEY,       new PrepareAttachmentAudioExtrasJob.Factory());
    }};
    factoryKeys.addAll(factoryHashMap.keySet());
    return factoryHashMap;
  }

  public static Map<String, Constraint.Factory> getConstraintFactories(@NonNull Application application) {
    return new HashMap<String, Constraint.Factory>() {{
      put(CellServiceConstraint.KEY,          new CellServiceConstraint.Factory(application));
      put(NetworkConstraint.KEY,              new NetworkConstraint.Factory(application));
      put(NetworkOrCellServiceConstraint.KEY, new NetworkOrCellServiceConstraint.Factory(application));
      put(SqlCipherMigrationConstraint.KEY,   new SqlCipherMigrationConstraint.Factory(application));
    }};
  }

  public static List<ConstraintObserver> getConstraintObservers(@NonNull Application application) {
    return Arrays.asList(new CellServiceConstraintObserver(application),
                         new NetworkConstraintObserver(application),
                         new SqlCipherMigrationConstraintObserver());
  }

  public static boolean hasFactoryForKey(String factoryKey) {
    return factoryKeys.contains(factoryKey);
  }
}
