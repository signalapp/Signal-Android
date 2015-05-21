package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.whispersystems.jobqueue.JobManager;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.api.push.ContactTokenDetails;


public abstract class PushReceivedJob extends ContextJob {

    private static final String TAG = PushReceivedJob.class.getSimpleName();

    protected PushReceivedJob(Context context, JobParameters parameters) {
        super(context, parameters);
    }

    public void handle(TextSecureEnvelope envelope, boolean sendExplicitReceipt) {
        if (!isActiveNumber(context, envelope.getSource())) {
            TextSecureDirectory directory           = TextSecureDirectory.getInstance(context);
            ContactTokenDetails contactTokenDetails = new ContactTokenDetails();
            contactTokenDetails.setNumber(envelope.getSource());

            directory.setNumber(contactTokenDetails, true);
        }

        if (envelope.isReceipt()) handleReceipt(envelope);
        else                      handleMessage(envelope, sendExplicitReceipt);
    }

    private void handleMessage(TextSecureEnvelope envelope, boolean sendExplicitReceipt) {
        JobManager jobManager = ApplicationContext.getInstance(context).getJobManager();
        long       messageId  = DatabaseFactory.getPushDatabase(context).insert(envelope);

        if (sendExplicitReceipt) {
            jobManager.add(new DeliveryReceiptJob(context, envelope.getSource(),
                    envelope.getTimestamp(),
                    envelope.getRelay()));
        }

        jobManager.add(new PushDecryptJob(context, messageId));
    }

    private void handleReceipt(TextSecureEnvelope envelope) {
        Log.w(TAG, String.format("Received receipt: (XXXXX, %d)", envelope.getTimestamp()));
        DatabaseFactory.getMmsSmsDatabase(context).incrementDeliveryReceiptCount(envelope.getSource(),
                envelope.getTimestamp());
    }

    private boolean isActiveNumber(Context context, String e164number) {
        boolean isActiveNumber;

        try {
            isActiveNumber = TextSecureDirectory.getInstance(context).isActiveNumber(e164number);
        } catch (NotInDirectoryException e) {
            isActiveNumber = false;
        }

        return isActiveNumber;
    }


}
