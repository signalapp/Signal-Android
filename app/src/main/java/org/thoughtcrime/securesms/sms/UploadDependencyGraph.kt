package org.thoughtcrime.securesms.sms

import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.attachments.AttachmentId
import org.thoughtcrime.securesms.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JobManager
import org.thoughtcrime.securesms.jobs.AttachmentCompressionJob
import org.thoughtcrime.securesms.jobs.AttachmentCopyJob
import org.thoughtcrime.securesms.jobs.AttachmentUploadJob
import org.thoughtcrime.securesms.jobs.ResumableUploadSpecJob
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage

/**
 * Helper alias for working with JobIds.
 */
private typealias JobId = String

/**
 * Represents message send dependencies on attachments. Allows for the consumption of the job queue
 * in a way in which repeated access will return an empty list.
 *
 * @param dependencyMap Maps an OutgoingMediaMessage to all of the Attachments it depends on.
 * @param deferredJobQueue A list of job chains that can be executed on the job manager when ready (outside of a database transaction).
 */
class UploadDependencyGraph private constructor(
  val dependencyMap: Map<OutgoingMediaMessage, List<Node>>,
  private val deferredJobQueue: List<JobManager.Chain>
) {

  /**
   * Contains the dependency job id as well as the attachment the job is working on.
   */
  data class Node(
    val jobId: JobId,
    val attachmentId: AttachmentId
  )

  /**
   * A generic attachment key which is unique given the attachment AND it's transform properties.
   */
  private data class AttachmentKey<A : Attachment>(
    val attachment: A,
    private val transformProperties: AttachmentTable.TransformProperties = attachment.transformProperties
  )

  private var hasConsumedJobQueue = false

  /**
   * Returns the list of chains exactly once.
   */
  fun consumeDeferredQueue(): List<JobManager.Chain> {
    if (hasConsumedJobQueue) {
      return emptyList()
    }

    synchronized(this) {
      if (hasConsumedJobQueue) {
        return emptyList()
      }

      hasConsumedJobQueue = true
      return deferredJobQueue
    }
  }

  companion object {

    @JvmField
    val EMPTY = UploadDependencyGraph(emptyMap(), emptyList())

    /**
     * Allows representation of a unique database attachment by its internal id and its transform properties.
     */
    private fun DatabaseAttachment.asDatabaseAttachmentKey(): AttachmentKey<DatabaseAttachment> {
      return AttachmentKey(this, this.transformProperties)
    }

    /**
     * Allows representation of a unique URI attachment by its internal Uri and its transform properties.
     */
    private fun UriAttachment.asUriAttachmentKey(): AttachmentKey<UriAttachment> {
      return AttachmentKey(this, transformProperties)
    }

    /**
     * Given a list of outgoing media messages, give me a mapping of those messages to their dependent attachments and set of deferred
     * job chains that can be executed to upload and copy the required jobs.
     *
     * This should be run within a database transaction, but does not enforce on itself. There is no direct access here to the database,
     * instead that is isolated within the passed parameters.
     *
     * @param messages The list of outgoing messages
     * @param jobManager The JobManager instance
     * @param insertAttachmentForPreUpload A method which will create a new database row for a given attachment.
     */
    @JvmStatic
    @WorkerThread
    fun create(
      messages: List<OutgoingMediaMessage>,
      jobManager: JobManager,
      insertAttachmentForPreUpload: (Attachment) -> DatabaseAttachment
    ): UploadDependencyGraph {
      return buildDependencyGraph(buildAttachmentMap(messages, insertAttachmentForPreUpload), jobManager, insertAttachmentForPreUpload)
    }

    /**
     * Produce a mapping of AttachmentKey{DatabaseAttachment,TransformProperties} -> Set<OutgoingMediaMessage>
     * This map represents which messages require a specific attachment.
     */
    private fun buildAttachmentMap(messages: List<OutgoingMediaMessage>, insertAttachmentForPreUpload: (Attachment) -> DatabaseAttachment): Map<AttachmentKey<DatabaseAttachment>, Set<OutgoingMediaMessage>> {
      val attachmentMap = mutableMapOf<AttachmentKey<DatabaseAttachment>, Set<OutgoingMediaMessage>>()
      val preUploadCache = mutableMapOf<AttachmentKey<UriAttachment>, DatabaseAttachment>()

      for (message in messages) {
        val attachmentList: List<Attachment> = message.attachments +
          message.linkPreviews.mapNotNull { it.thumbnail.orElse(null) } +
          message.sharedContacts.mapNotNull { it.avatar?.attachment }

        val uniqueAttachments: Set<AttachmentKey<Attachment>> = attachmentList.map { AttachmentKey(it, it.transformProperties) }.toSet()

        for (attachmentKey in uniqueAttachments) {
          when (val attachment = attachmentKey.attachment) {
            is DatabaseAttachment -> {
              val messageIdList: Set<OutgoingMediaMessage> = attachmentMap.getOrDefault(attachment.asDatabaseAttachmentKey(), emptySet())
              attachmentMap[attachment.asDatabaseAttachmentKey()] = messageIdList + message
            }
            is UriAttachment -> {
              val dbAttachmentKey: AttachmentKey<DatabaseAttachment> = preUploadCache.getOrPut(attachment.asUriAttachmentKey()) { insertAttachmentForPreUpload(attachment) }.asDatabaseAttachmentKey()
              val messageIdList: Set<OutgoingMediaMessage> = attachmentMap.getOrDefault(dbAttachmentKey, emptySet())
              attachmentMap[dbAttachmentKey] = messageIdList + message
            }
            else -> {
              error("Unsupported attachment subclass - ${attachment::class.java}")
            }
          }
        }
      }

      return attachmentMap
    }

    /**
     * Builds out the [UploadDependencyGraph] which collects dependency information for a given set of messages.
     * Each attachment will be uploaded exactly once and copied N times, where N is the number of messages in its set, minus 1 (the upload)
     * The resulting object contains a list of jobs that a subsequent send job can depend on, as well as a list of Chains which can be
     * enqueued to perform uploading. Since a send job can depend on multiple chains, it's cleaner to give back a mapping of
     * [OutgoingMediaMessage] -> [List<Node>] instead of forcing the caller to try to weave new jobs into the original chains.
     *
     * Each chain consists of:
     *  1. Compression job
     *  1. Resumable upload spec job
     *  1. Attachment upload job
     *  1. O to 1 copy jobs
     */
    private fun buildDependencyGraph(
      attachmentIdToOutgoingMessagesMap: Map<AttachmentKey<DatabaseAttachment>, Set<OutgoingMediaMessage>>,
      jobManager: JobManager,
      insertAttachmentForPreUpload: (Attachment) -> DatabaseAttachment
    ): UploadDependencyGraph {
      val resultMap = mutableMapOf<OutgoingMediaMessage, List<Node>>()
      val jobQueue = mutableListOf<JobManager.Chain>()

      for ((attachmentKey, messages) in attachmentIdToOutgoingMessagesMap) {
        val (uploadJobId, uploadChain) = createAttachmentUploadChain(jobManager, attachmentKey.attachment)
        val uploadMessage: OutgoingMediaMessage = messages.first()
        val copyMessages: List<OutgoingMediaMessage> = messages.drop(1)

        val uploadMessageDependencies: List<Node> = resultMap.getOrDefault(uploadMessage, emptyList())
        resultMap[uploadMessage] = uploadMessageDependencies + Node(uploadJobId, attachmentKey.attachment.attachmentId)

        if (copyMessages.isNotEmpty()) {
          val copyAttachments: Map<OutgoingMediaMessage, AttachmentId> = copyMessages.associateWith { insertAttachmentForPreUpload(attachmentKey.attachment).attachmentId }
          val copyJob = AttachmentCopyJob(attachmentKey.attachment.attachmentId, copyAttachments.values.toList())

          copyAttachments.forEach { (message, attachmentId) ->
            val copyMessageDependencies: List<Node> = resultMap.getOrDefault(message, emptyList())
            resultMap[message] = copyMessageDependencies + Node(copyJob.id, attachmentId)
          }

          uploadChain.then(copyJob)
        }

        jobQueue.add(uploadChain)
      }

      return UploadDependencyGraph(resultMap, jobQueue)
    }

    /**
     * Creates the minimum necessary upload chain for the given attachment. This includes compression, grabbing the resumable upload spec,
     * and the upload job itself.
     */
    private fun createAttachmentUploadChain(jobManager: JobManager, databaseAttachment: DatabaseAttachment): Pair<JobId, JobManager.Chain> {
      val compressionJob: Job = AttachmentCompressionJob.fromAttachment(databaseAttachment, false, -1)
      val resumableUploadSpecJob: Job = ResumableUploadSpecJob()
      val uploadJob: Job = AttachmentUploadJob(databaseAttachment.attachmentId)

      return uploadJob.id to jobManager
        .startChain(compressionJob)
        .then(resumableUploadSpecJob)
        .then(uploadJob)
    }
  }
}
