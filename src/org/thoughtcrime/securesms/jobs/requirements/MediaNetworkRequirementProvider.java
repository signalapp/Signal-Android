package org.thoughtcrime.securesms.jobs.requirements;

import org.whispersystems.jobqueue.requirements.RequirementListener;
import org.whispersystems.jobqueue.requirements.RequirementProvider;

public class MediaNetworkRequirementProvider implements RequirementProvider {

    private RequirementListener listener;

    public void notifyMediaControlEvent() {
        if (listener != null) listener.onRequirementStatusChanged();
    }

    @Override
    public void setListener(RequirementListener listener) {
        this.listener = listener;
    }
}