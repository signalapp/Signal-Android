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

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.support.multidex.MultiDexApplication;
import android.util.Log;

import com.google.android.gms.security.ProviderInstaller;

import org.thoughtcrime.securesms.crypto.PRNGFixes;
import org.thoughtcrime.securesms.dependencies.AxolotlStorageModule;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.dependencies.SignalCommunicationModule;
import org.thoughtcrime.securesms.jobs.CreateSignedPreKeyJob;
import org.thoughtcrime.securesms.jobs.GcmRefreshJob;
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob;
import org.thoughtcrime.securesms.jobs.persistence.EncryptingJobSerializer;
import org.thoughtcrime.securesms.jobs.requirements.MasterSecretRequirementProvider;
import org.thoughtcrime.securesms.jobs.requirements.MediaNetworkRequirementProvider;
import org.thoughtcrime.securesms.jobs.requirements.ServiceRequirementProvider;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.ExpiringMessageManager;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.service.UpdateApkRefreshListener;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.dependencies.DependencyInjector;
import org.whispersystems.jobqueue.requirements.NetworkRequirementProvider;
import org.whispersystems.libsignal.logging.SignalProtocolLoggerProvider;
import org.whispersystems.libsignal.util.AndroidSignalProtocolLogger;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.ObjectGraph;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends MultiDexApplication implements DependencyInjector {

  private static final String TAG = ApplicationContext.class.getName();

  private ExpiringMessageManager expiringMessageManager;
  private JobManager             jobManager;
  private ObjectGraph            objectGraph;

  private MediaNetworkRequirementProvider mediaNetworkRequirementProvider = new MediaNetworkRequirementProvider();

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    initializeRandomNumberFix();
    initializeLogging();
    initializeDependencyInjection();
    initializeJobManager();
    initializeExpiringMessageManager();
    initializeGcmCheck();
    initializeSignedPreKeyCheck();
    initializePeriodicTasks();
    initializeCircumvention();
    initializeSetVideoCapable();
    initializeWebRtc();
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

  public ExpiringMessageManager getExpiringMessageManager() {
    return expiringMessageManager;
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeLogging() {
    SignalProtocolLoggerProvider.setProvider(new AndroidSignalProtocolLogger());
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
    this.objectGraph = ObjectGraph.create(new SignalCommunicationModule(this, new SignalServiceNetworkAccess(this)),
                                          new AxolotlStorageModule(this));
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this)) {
      long nextSetTime = TextSecurePreferences.getGcmRegistrationIdLastSetTime(this) + TimeUnit.HOURS.toMillis(6);

      if (TextSecurePreferences.getGcmRegistrationId(this) == null || nextSetTime <= System.currentTimeMillis()) {
        this.jobManager.add(new GcmRefreshJob(this));
      }
    }
  }

  private void initializeSignedPreKeyCheck() {
    if (!TextSecurePreferences.isSignedPreKeyRegistered(this)) {
      jobManager.add(new CreateSignedPreKeyJob(this));
    }
  }

  private void initializeExpiringMessageManager() {
    this.expiringMessageManager = new ExpiringMessageManager(this);
  }

  private void initializePeriodicTasks() {
    RotateSignedPreKeyListener.schedule(this);
    DirectoryRefreshListener.schedule(this);

    if (BuildConfig.PLAY_STORE_DISABLED) {
      UpdateApkRefreshListener.schedule(this);
    }
  }

  private void initializeSetVideoCapable() {
    if (TextSecurePreferences.isPushRegistered(this) &&
        !TextSecurePreferences.isWebrtcCallingEnabled(this))
    {
      TextSecurePreferences.setWebrtcCallingEnabled(this, true);
      jobManager.add(new RefreshAttributesJob(this));
    }
  }

  private void initializeWebRtc() {
    Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
      add("D6503"); // Sony Xperia Z2 D6503
      add("ONE A2005"); // OnePlus 2
      add("MotoG3"); // Moto G (3rd Generation)
      add("Nexus 6P"); // Nexus 6p
      add("Pixel"); // Pixel #6241
      add("Pixel XL"); // Pixel XL #6241
      add("MI 4LTE"); // Xiami Mi4 #6241
      add("Redmi Note 3"); // Redmi Note 3 #6241
      add("SM-G900F"); // Samsung Galaxy S5 #6241
      add("g3_kt_kr"); // LG G3 #6241
      add("SM-G930F"); // Samsung Galaxy S7 #6241
      add("Xperia SP"); // Sony Xperia SP #6241
    }};

    Set<String> OPEN_SL_ES_BLACKLIST = new HashSet<String>() {{
      add("MI 4LTE"); // Xiami Mi4 #6241
    }};

    if (Build.VERSION.SDK_INT >= 11) {
      if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
        WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
      }

      if (OPEN_SL_ES_BLACKLIST.contains(Build.MODEL)) {
        WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
      }

      PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true);
    }
  }

  private void initializeCircumvention() {
    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        if (new SignalServiceNetworkAccess(ApplicationContext.this).isCensored(ApplicationContext.this)) {
          try {
            ProviderInstaller.installIfNeeded(ApplicationContext.this);
          } catch (Throwable t) {
            Log.w(TAG, t);
          }
        }
        return null;
      }
    };

    if (Build.VERSION.SDK_INT >= 11) task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    else                             task.execute();
  }

}
