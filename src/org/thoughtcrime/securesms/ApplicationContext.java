/*
 * Copyright (C) 2013 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import org.thoughtcrime.securesms.crypto.PRNGFixes;
import org.thoughtcrime.securesms.database.CanonicalAddressDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.dependencies.AxolotlStorageModule;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.TextSecureCommunicationModule;
import org.thoughtcrime.securesms.jobs.GcmRefreshJob;
import org.thoughtcrime.securesms.jobs.persistence.EncryptingJobSerializer;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirementProvider;
import org.thoughtcrime.securesms.jobs.requirements.MediaNetworkRequirementProvider;
import org.thoughtcrime.securesms.jobs.requirements.ServiceRequirementProvider;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.dependencies.DependencyInjector;
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider;
import org.whispersystems.libaxolotl.logging.AxolotlLoggerProvider;
import org.whispersystems.libaxolotl.util.AndroidAxolotlLogger;

import dagger.ObjectGraph;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends Application implements DependencyInjector {
  private static final String TAG = ApplicationContext.class.getSimpleName();

  private JobManager  jobManager;
  private ObjectGraph objectGraph;

  private MediaNetworkRequirementProvider mediaNetworkRequirementProvider = new MediaNetworkRequirementProvider();

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    initializeDeveloperBuild();
    initializeRandomNumberFix();
    initializeLogging();
    initializeDependencyInjection();
    initializeJobManager();
    initializeGcmCheck();
    initializeCaches();
  }

  @Override
  public void injectDependencies(Object object) {
    if (object instanceof InjectableType) {
      objectGraph.inject(object);
    }
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  private void initializeDeveloperBuild() {
    if (BuildConfig.DEV_BUILD) {
//      LeakCanary.install(this);
      StrictMode.setThreadPolicy(new ThreadPolicy.Builder().detectAll()
                                                           .penaltyLog()
                                                           .build());
      StrictMode.setVmPolicy(new VmPolicy.Builder().detectAll().penaltyLog().build());
    }
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    AxolotlLoggerProvider.setProvider(new AndroidAxolotlLogger());
  }

  private void initializeJobManager() {
    this.jobManager = JobManager.newBuilder(this)
                                .withName("TextSecureJobs")
                                .withDependencyInjector(this)
                                .withJobSerializer(new EncryptingJobSerializer())
                                .withRequirementProviders(new MasterSecretRequirementProvider(this),
                                                          new ServiceRequirementProvider(this),
                                                          new NetworkRequirementProvider(this),
                                                          mediaNetworkRequirementProvider)
                                .withConsumerThreads(5)
                                .build();
  }

  public void notifyMediaControlEvent() {
    mediaNetworkRequirementProvider.notifyMediaControlEvent();
  }

  private void initializeDependencyInjection() {
    this.objectGraph = ObjectGraph.create(new TextSecureCommunicationModule(this),
                                          new AxolotlStorageModule(this));
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this) &&
        TextSecurePreferences.getGcmRegistrationId(this) == null)
    {
      this.jobManager.add(new GcmRefreshJob(this));
    }
  }

  private void initializeCaches() {
    new AsyncTask<Context,Void,Void>() {
      @Override protected Void doInBackground(Context... contexts) {
        final long    startMillis = System.currentTimeMillis();
        final Context context     = contexts[0];

        CanonicalAddressDatabase.getInstance(context).fillCache();
        TextSecureDirectory.getInstance(context).fillCache();
        if (TextSecurePreferences.isPushRegistered(context)) {
          try {
            PhoneNumberUtil.getInstance().parse(TextSecurePreferences.getLocalNumber(context), null);
          } catch (NumberParseException dgaf) {
            Log.w(TAG, "failed to warm libphonenumber with the localized phone number");
          }
        }
        Log.w(TAG, "cache warming took " + (System.currentTimeMillis() - startMillis) + "ms");
        return null;
      }
    }.execute(this);
  }
}
