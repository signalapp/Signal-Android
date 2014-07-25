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

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;

import org.thoughtcrime.securesms.crypto.PRNGFixes;
import org.thoughtcrime.securesms.jobs.ContextInjector;
import org.thoughtcrime.securesms.jobs.GcmRefreshJob;
import org.thoughtcrime.securesms.jobs.JobLogger;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Will be called once when the TextSecure process is created.
 *
 * We're using this as an insertion point to patch up the Android PRNG disaster,
 * to initialize the job manager, and to check for GCM registration freshness.
 *
 * @author Moxie Marlinspike
 */
public class ApplicationContext extends Application {

  private JobManager jobManager;

  public static ApplicationContext getInstance(Context context) {
    return (ApplicationContext)context.getApplicationContext();
  }

  @Override
  public void onCreate() {
    initializeRandomNumberFix();
    initializeJobManager();
    initializeGcmCheck();
  }

  public JobManager getJobManager() {
    return jobManager;
  }

  private void initializeRandomNumberFix() {
    PRNGFixes.apply();
  }

  private void initializeJobManager() {
    Configuration configuration = new Configuration.Builder(this)
        .minConsumerCount(1)
        .injector(new ContextInjector(this))
        .customLogger(new JobLogger())
        .build();

    this.jobManager = new JobManager(this, configuration);
  }

  private void initializeGcmCheck() {
    if (TextSecurePreferences.isPushRegistered(this) &&
        TextSecurePreferences.getGcmRegistrationId(this) == null)
    {
      this.jobManager.addJob(new GcmRefreshJob());
    }
  }

}
