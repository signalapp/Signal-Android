/*
 * *
 *  * Created by Hung Nguyen on 4/14/21 6:32 PM
 *  * Copyright (c) 2021 . All rights reserved.
 *  * Last modified 4/14/21 6:19 PM
 *
 */

package org.thoughtcrime.securesms.jobs;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

/**
 * Does a sync of our local recipient database with local system contacts
 *
 * This should be performed whenever a change is made locally
 */
public class SystemContactUpdateJob extends BaseJob {

    public static final String KEY = "SystemContactUpdateJob";

    private static final String TAG = Log.tag(SystemContactUpdateJob.class);

    public SystemContactUpdateJob() {
        this(new Job.Parameters.Builder()
                    .setQueue(KEY)
                    .setMaxAttempts(2)
                    .build());
    }

    private SystemContactUpdateJob(@NonNull Parameters parameters) {
        super(parameters);
    }

    @Override
    protected void onRun() throws Exception {
        Context ctx = context;
        if (ctx == null) return;
        DirectoryHelper.syncRecipientInfoWithUpdatedSystemContacts(ctx, TextSecurePreferences.getLastSystemContactSyncTime(ctx));
        TextSecurePreferences.setLastSystemContactSyncTime(ctx, System.currentTimeMillis());
    }

    @Override
    protected boolean onShouldRetry(@NonNull Exception e) {
        return true;
    }

    @NonNull
    @Override
    public Data serialize() {
        return new Data.Builder().build();
    }

    @NonNull
    @Override
    public String getFactoryKey() {
        return KEY;
    }

    @Override
    public void onFailure() {
    }

    public static final class Factory implements Job.Factory<SystemContactUpdateJob> {
        @Override
        public @NonNull SystemContactUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
            return new SystemContactUpdateJob(parameters);
        }
    }
}
