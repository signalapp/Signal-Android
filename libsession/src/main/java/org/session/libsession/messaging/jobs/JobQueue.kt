package org.session.libsession.messaging.jobs

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsignal.utilities.logging.Log
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

class JobQueue : JobDelegate {
    private var hasResumedPendingJobs = false // Just for debugging
    private val jobTimestampMap = ConcurrentHashMap<Long, AtomicInteger>()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val multiDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
    private val scope = GlobalScope + SupervisorJob()
    private val queue = Channel<Job>(UNLIMITED)

    val timer = Timer()

    init {
        // Process jobs
        scope.launch(dispatcher) {
            while (isActive) {
                queue.receive().let { job ->
                    if (job.canExecuteParallel()) {
                        launch(multiDispatcher) {
                            job.delegate = this@JobQueue
                            job.execute()
                        }
                    } else {
                        job.delegate = this@JobQueue
                        job.execute()
                    }
                }
            }
        }
    }

    companion object {

        @JvmStatic
        val shared: JobQueue by lazy { JobQueue() }
    }

    private fun Job.canExecuteParallel(): Boolean {
        return this.javaClass in arrayOf(
            AttachmentUploadJob::class.java,
            AttachmentDownloadJob::class.java
        )
    }

    fun add(job: Job) {
        addWithoutExecuting(job)
        queue.offer(job) // offer always called on unlimited capacity
    }

    private fun addWithoutExecuting(job: Job) {
        // When adding multiple jobs in rapid succession, timestamps might not be good enough as a unique ID. To
        // deal with this we keep track of the number of jobs with a given timestamp and add that to the end of the
        // timestamp to make it a unique ID. We can't use a random number because we do still want to keep track
        // of the order in which the jobs were added.
        val currentTime = System.currentTimeMillis()
        jobTimestampMap.putIfAbsent(currentTime, AtomicInteger())
        job.id = currentTime.toString() + jobTimestampMap[currentTime]!!.getAndIncrement().toString()
        MessagingModuleConfiguration.shared.storage.persistJob(job)
    }

    fun resumePendingJobs() {
        if (hasResumedPendingJobs) {
            Log.d("Loki", "resumePendingJobs() should only be called once.")
            return
        }
        hasResumedPendingJobs = true
        val allJobTypes = listOf(
            AttachmentUploadJob.KEY,
            AttachmentDownloadJob.KEY,
            MessageReceiveJob.KEY,
            MessageSendJob.KEY,
            NotifyPNServerJob.KEY
        )
        allJobTypes.forEach { type ->
            val allPendingJobs = MessagingModuleConfiguration.shared.storage.getAllPendingJobs(type)
            val pendingJobs = mutableListOf<Job>()
            for ((id, job) in allPendingJobs) {
                if (job == null) {
                    // Job failed to deserialize, remove it from the DB
                    handleJobFailedPermanently(id)
                } else {
                    pendingJobs.add(job)
                }
            }
            pendingJobs.sortedBy { it.id }.forEach { job ->
                Log.i("Loki", "Resuming pending job of type: ${job::class.simpleName}.")
                queue.offer(job) // Offer always called on unlimited capacity
            }
        }
    }

    override fun handleJobSucceeded(job: Job) {
        val jobId = job.id ?: return
        MessagingModuleConfiguration.shared.storage.markJobAsSucceeded(jobId)
    }

    override fun handleJobFailed(job: Job, error: Exception) {
        job.failureCount += 1
        val storage = MessagingModuleConfiguration.shared.storage
        if (storage.isJobCanceled(job)) { return Log.i("Loki", "${job::class.simpleName} canceled.")}
        if (job.failureCount == job.maxFailureCount) {
            handleJobFailedPermanently(job, error)
        } else {
            storage.persistJob(job)
            val retryInterval = getRetryInterval(job)
            Log.i("Loki", "${job::class.simpleName} failed; scheduling retry (failure count is ${job.failureCount}).")
            timer.schedule(delay = retryInterval) {
                Log.i("Loki", "Retrying ${job::class.simpleName}.")
                queue.offer(job)
            }
        }
    }

    override fun handleJobFailedPermanently(job: Job, error: Exception) {
        val jobId = job.id ?: return
        handleJobFailedPermanently(jobId)
    }

    private fun handleJobFailedPermanently(jobId: String) {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.markJobAsFailed(jobId)
    }

    private fun getRetryInterval(job: Job): Long {
        // Arbitrary backoff factor...
        // try  1 delay: 0.5s
        // try  2 delay: 1s
        // ...
        // try  5 delay: 16s
        // ...
        // try 11 delay: 512s
        val maxBackoff = (10 * 60).toDouble() // 10 minutes
        return (1000 * 0.25 * min(maxBackoff, (2.0).pow(job.failureCount))).roundToLong()
    }
}