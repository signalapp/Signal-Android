package org.session.libsession.messaging.jobs

import kotlin.math.min
import kotlin.math.pow
import java.util.Timer

import org.session.libsession.messaging.MessagingConfiguration

import org.session.libsignal.libsignal.logging.Log
import kotlin.concurrent.schedule
import kotlin.math.roundToLong


class JobQueue : JobDelegate {
    private var hasResumedPendingJobs = false // Just for debugging

    companion object {
        val shared: JobQueue by lazy { JobQueue() }
    }

    fun add(job: Job) {
        addWithoutExecuting(job)
        job.execute()
    }

    fun addWithoutExecuting(job: Job) {
        job.id = System.currentTimeMillis().toString()
        MessagingConfiguration.shared.storage.persistJob(job)
        job.delegate = this
    }

    fun resumePendingJobs() {
        if (hasResumedPendingJobs) {
            Log.d("Loki", "resumePendingJobs() should only be called once.")
            return
        }
        hasResumedPendingJobs = true
        val allJobTypes = listOf(AttachmentDownloadJob.KEY, AttachmentDownloadJob.KEY, MessageReceiveJob.KEY, MessageSendJob.KEY, NotifyPNServerJob.KEY)
        allJobTypes.forEach { type ->
            val allPendingJobs = MessagingConfiguration.shared.storage.getAllPendingJobs(type)
            allPendingJobs.sortedBy { it.id }.forEach { job ->
                Log.i("Jobs", "Resuming pending job of type: ${job::class.simpleName}.")
                job.delegate = this
                job.execute()
            }
        }
    }

    override fun handleJobSucceeded(job: Job) {
        MessagingConfiguration.shared.storage.markJobAsSucceeded(job)
    }

    override fun handleJobFailed(job: Job, error: Exception) {
        job.failureCount += 1
        val storage = MessagingConfiguration.shared.storage
        if (storage.isJobCanceled(job)) { return Log.i("Jobs", "${job::class.simpleName} canceled.")}
        storage.persistJob(job)
        if (job.failureCount == job.maxFailureCount) {
            storage.markJobAsFailed(job)
        } else {
            val retryInterval = getRetryInterval(job)
            Log.i("Jobs", "${job::class.simpleName} failed; scheduling retry (failure count is ${job.failureCount}).")
            Timer().schedule(delay = retryInterval) {
                Log.i("Jobs", "Retrying ${job::class.simpleName}.")
                job.execute()
            }
        }
    }

    override fun handleJobFailedPermanently(job: Job, error: Exception) {
        job.failureCount += 1
        val storage = MessagingConfiguration.shared.storage
        storage.persistJob(job)
        storage.markJobAsFailed(job)
    }

    private fun getRetryInterval(job: Job): Long {
        // Arbitrary backoff factor...
        // try  1 delay:  0ms
        // try  2 delay:  190ms
        // ...
        // try  5 delay:  1300ms
        // ...
        // try 11 delay: 61310ms
        val backoffFactor = 1.9
        val maxBackoff = (60 * 60 * 1000).toDouble()
        return (100 * min(maxBackoff, backoffFactor.pow(job.failureCount))).roundToLong()
    }
}