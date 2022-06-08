package org.session.libsession.messaging.jobs

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsignal.utilities.Log
import java.util.Timer
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
    private val rxDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val rxMediaDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    private val openGroupDispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
    private val txDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()
    private val queue = Channel<Job>(UNLIMITED)
    private val pendingJobIds = mutableSetOf<String>()
    private val pendingTrimThreadIds = mutableSetOf<Long>()

    private val openGroupChannels = mutableMapOf<String, Channel<Job>>()

    val timer = Timer()

    private fun CoroutineScope.processWithOpenGroupDispatcher(
        channel: Channel<Job>,
        dispatcher: CoroutineDispatcher,
        name: String
    ) = launch(dispatcher) {
        for (job in channel) {
            if (!isActive) break
            val openGroupId = when (job) {
                is BatchMessageReceiveJob -> job.openGroupID
                is OpenGroupDeleteJob -> job.openGroupId
                is TrimThreadJob -> job.openGroupId
                is BackgroundGroupAddJob -> job.openGroupId
                is GroupAvatarDownloadJob -> "${job.server}.${job.room}"
                else -> null
            }
            if (openGroupId.isNullOrEmpty()) {
                Log.e("OpenGroupDispatcher", "Open Group ID was null on ${job.javaClass.simpleName}")
                handleJobFailedPermanently(job, NullPointerException("Open Group ID was null"))
            } else {
                val groupChannel = if (!openGroupChannels.containsKey(openGroupId)) {
                    Log.d("OpenGroupDispatcher", "Creating $openGroupId channel")
                    val newGroupChannel = Channel<Job>(UNLIMITED)
                    launch(dispatcher) {
                        for (groupJob in newGroupChannel) {
                            if (!isActive) break
                            groupJob.process(name)
                        }
                    }
                    openGroupChannels[openGroupId] = newGroupChannel
                    newGroupChannel
                } else {
                    Log.d("OpenGroupDispatcher", "Re-using channel")
                    openGroupChannels[openGroupId]!!
                }
                Log.d("OpenGroupDispatcher", "Sending to channel $groupChannel")
                groupChannel.send(job)
            }
        }
    }

    private fun CoroutineScope.processWithDispatcher(
        channel: Channel<Job>,
        dispatcher: CoroutineDispatcher,
        name: String,
        asynchronous: Boolean = true
    ) = launch(dispatcher) {
        for (job in channel) {
            if (!isActive) break
            if (asynchronous) {
                launch(dispatcher) {
                    job.process(name)
                }
            } else {
                job.process(name)
            }
        }
    }

    private fun Job.process(dispatcherName: String) {
        Log.d(dispatcherName,"processJob: ${javaClass.simpleName}")
        delegate = this@JobQueue
        execute()
    }

    init {
        // Process jobs
        scope.launch {
            val rxQueue = Channel<Job>(capacity = UNLIMITED)
            val txQueue = Channel<Job>(capacity = UNLIMITED)
            val mediaQueue = Channel<Job>(capacity = UNLIMITED)
            val openGroupQueue = Channel<Job>(capacity = UNLIMITED)

            val receiveJob = processWithDispatcher(rxQueue, rxDispatcher, "rx", asynchronous = false)
            val txJob = processWithDispatcher(txQueue, txDispatcher, "tx")
            val mediaJob = processWithDispatcher(mediaQueue, rxMediaDispatcher, "media")
            val openGroupJob = processWithOpenGroupDispatcher(openGroupQueue, openGroupDispatcher, "openGroup")

            while (isActive) {
                if (queue.isEmpty && pendingTrimThreadIds.isNotEmpty()) {
                    // process trim thread jobs
                    val pendingThreads = pendingTrimThreadIds.toList()
                    pendingTrimThreadIds.clear()
                    for (thread in pendingThreads) {
                        Log.d("Loki", "Trimming thread $thread")
                        queue.trySend(TrimThreadJob(thread, null))
                    }
                }
                when (val job = queue.receive()) {
                    is NotifyPNServerJob, is AttachmentUploadJob, is MessageSendJob -> {
                        txQueue.send(job)
                    }
                    is AttachmentDownloadJob -> {
                        mediaQueue.send(job)
                    }
                    is GroupAvatarDownloadJob,
                    is BackgroundGroupAddJob,
                    is OpenGroupDeleteJob -> {
                        openGroupQueue.send(job)
                    }
                    is MessageReceiveJob, is TrimThreadJob,
                    is BatchMessageReceiveJob -> {
                        if ((job is BatchMessageReceiveJob && !job.openGroupID.isNullOrEmpty())
                            || (job is TrimThreadJob && !job.openGroupId.isNullOrEmpty())) {
                            openGroupQueue.send(job)
                        } else {
                            rxQueue.send(job)
                        }
                    }
                    else -> {
                        throw IllegalStateException("Unexpected job type.")
                    }
                }
            }

            // The job has been cancelled
            receiveJob.cancel()
            txJob.cancel()
            mediaJob.cancel()
            openGroupJob.cancel()
        }
    }

    companion object {

        @JvmStatic
        val shared: JobQueue by lazy { JobQueue() }
    }

    fun queueThreadForTrim(threadId: Long) {
        pendingTrimThreadIds += threadId
    }

    fun add(job: Job) {
        addWithoutExecuting(job)
        queue.trySend(job) // offer always called on unlimited capacity
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

    fun resumePendingSendMessage(job: Job) {
        val id = job.id ?: run {
            Log.e("Loki", "tried to resume pending send job with no ID")
            return
        }
        if (!pendingJobIds.add(id)) {
            Log.e("Loki","tried to re-queue pending/in-progress job")
            return
        }
        queue.trySend(job)
        Log.d("Loki", "resumed pending send message $id")
    }

    fun resumePendingJobs(typeKey: String) {
        val allPendingJobs = MessagingModuleConfiguration.shared.storage.getAllPendingJobs(typeKey)
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
            queue.trySend(job) // Offer always called on unlimited capacity
        }
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
            NotifyPNServerJob.KEY,
            BatchMessageReceiveJob.KEY,
            GroupAvatarDownloadJob.KEY,
            BackgroundGroupAddJob.KEY,
            OpenGroupDeleteJob.KEY,
        )
        allJobTypes.forEach { type ->
            resumePendingJobs(type)
        }
    }

    override fun handleJobSucceeded(job: Job) {
        val jobId = job.id ?: return
        MessagingModuleConfiguration.shared.storage.markJobAsSucceeded(jobId)
        pendingJobIds.remove(jobId)
    }

    override fun handleJobFailed(job: Job, error: Exception) {
        // Canceled
        val storage = MessagingModuleConfiguration.shared.storage
        if (storage.isJobCanceled(job)) {
            return Log.i("Loki", "${job::class.simpleName} canceled.")
        }
        // Message send jobs waiting for the attachment to upload
        if (job is MessageSendJob && error is MessageSendJob.AwaitingAttachmentUploadException) {
            Log.i("Loki", "Message send job waiting for attachment upload to finish.")
            return
        }

        // Batch message receive job, re-queue non-permanently failed jobs
        if (job is BatchMessageReceiveJob && job.failureCount <= 0) {
            val replacementParameters = job.failures.toList()
            if (replacementParameters.isNotEmpty()) {
                val newJob = BatchMessageReceiveJob(replacementParameters, job.openGroupID)
                newJob.failureCount = job.failureCount + 1
                add(newJob)
            }
        }

        // Regular job failure
        job.failureCount += 1

        if (job.failureCount >= job.maxFailureCount) {
            handleJobFailedPermanently(job, error)
        } else {
            storage.persistJob(job)
            val retryInterval = getRetryInterval(job)
            Log.i("Loki", "${job::class.simpleName} failed; scheduling retry (failure count is ${job.failureCount}).")
            timer.schedule(delay = retryInterval) {
                Log.i("Loki", "Retrying ${job::class.simpleName}.")
                queue.trySend(job)
            }
        }
    }

    override fun handleJobFailedPermanently(job: Job, error: Exception) {
        val jobId = job.id ?: return
        handleJobFailedPermanently(jobId)
    }

    private fun handleJobFailedPermanently(jobId: String) {
        val storage = MessagingModuleConfiguration.shared.storage
        storage.markJobAsFailedPermanently(jobId)
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

    private fun Job.isSend() = this is MessageSendJob || this is AttachmentUploadJob

}