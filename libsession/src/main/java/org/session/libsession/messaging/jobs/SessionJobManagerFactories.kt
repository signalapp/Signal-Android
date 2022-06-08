package org.session.libsession.messaging.jobs

class SessionJobManagerFactories {

    companion object {

        fun getSessionJobFactories(): Map<String, Job.Factory<out Job>> {
            return mapOf(
                AttachmentDownloadJob.KEY to AttachmentDownloadJob.Factory(),
                AttachmentUploadJob.KEY to AttachmentUploadJob.Factory(),
                MessageReceiveJob.KEY to MessageReceiveJob.Factory(),
                MessageSendJob.KEY to MessageSendJob.Factory(),
                NotifyPNServerJob.KEY to NotifyPNServerJob.Factory(),
                TrimThreadJob.KEY to TrimThreadJob.Factory(),
                BatchMessageReceiveJob.KEY to BatchMessageReceiveJob.Factory(),
                GroupAvatarDownloadJob.KEY to GroupAvatarDownloadJob.Factory(),
                BackgroundGroupAddJob.KEY to BackgroundGroupAddJob.Factory(),
                OpenGroupDeleteJob.KEY to OpenGroupDeleteJob.Factory(),
            )
        }
    }
}