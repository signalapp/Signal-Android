package org.thoughtcrime.securesms.loki.database

import android.content.ContentValues
import android.content.Context
import net.sqlcipher.Cursor
import org.session.libsession.messaging.jobs.*
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper
import org.thoughtcrime.securesms.jobmanager.impl.JsonDataSerializer
import org.thoughtcrime.securesms.loki.utilities.*

class SessionJobDatabase(context: Context, helper: SQLCipherOpenHelper) : Database(context, helper) {

    companion object {
        private const val sessionJobTable = "session_job_database"
        const val jobID = "job_id"
        const val jobType = "job_type"
        const val failureCount = "failure_count"
        const val serializedData = "serialized_data"
        @JvmStatic val createSessionJobTableCommand = "CREATE TABLE $sessionJobTable ($jobID INTEGER PRIMARY KEY, $jobType STRING, $failureCount INTEGER DEFAULT 0, $serializedData TEXT);"
    }

    fun persistJob(job: Job) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(4)
        val existing = database.get(sessionJobTable, "$jobID = ?", arrayOf(job.id!!)) { cursor ->
            cursor.count
        } ?: 0
        // When adding multiple jobs in rapid succession, timestamps might not be good enough as a unique ID. To
        // deal with this we keep track of the number of jobs with a given timestamp and that to the end of the
        // timestamp to make it a unique ID. We can't use a random number because we do still want to keep track
        // of the order in which the jobs were added.
        job.id += existing
        contentValues.put(jobID, job.id)
        contentValues.put(jobType, job.getFactoryKey())
        contentValues.put(failureCount, job.failureCount)
        contentValues.put(serializedData, SessionJobHelper.dataSerializer.serialize(job.serialize()))
        database.insertOrUpdate(sessionJobTable, contentValues, "$jobID = ?", arrayOf(jobID))
    }

    fun markJobAsSucceeded(job: Job) {
        databaseHelper.writableDatabase.delete(sessionJobTable, "$jobID = ?", arrayOf(job.id))
    }

    fun markJobAsFailed(job: Job) {
        databaseHelper.writableDatabase.delete(sessionJobTable, "$jobID = ?", arrayOf(job.id))
    }

    fun getAllPendingJobs(type: String): List<Job> {
        val database = databaseHelper.readableDatabase
        return database.getAll(sessionJobTable, "$jobType = ?", arrayOf(type)) { cursor ->
            jobFromCursor(cursor)
        }
    }

    fun getAttachmentUploadJob(attachmentID: Long): AttachmentUploadJob? {
        val database = databaseHelper.readableDatabase
        var result = mutableListOf<AttachmentUploadJob>()
        database.getAll(sessionJobTable, "$jobType = ?", arrayOf(AttachmentUploadJob.KEY)) { cursor ->
            result.add(jobFromCursor(cursor) as AttachmentUploadJob)
        }
        return result.firstOrNull { job -> job.attachmentID == attachmentID }
    }

    fun getMessageSendJob(messageSendJobID: String): MessageSendJob? {
        val database = databaseHelper.readableDatabase
        return database.get(sessionJobTable, "$jobID = ? AND $jobType = ?", arrayOf(messageSendJobID, MessageSendJob.KEY)) { cursor ->
            jobFromCursor(cursor) as MessageSendJob
        }
    }

    fun isJobCanceled(job: Job): Boolean {
        val database = databaseHelper.readableDatabase
        var cursor: android.database.Cursor? = null
        try {
            cursor = database.rawQuery("SELECT * FROM $sessionJobTable WHERE $jobID = ?", arrayOf(job.id))
            return cursor != null && cursor.moveToFirst()
        } catch (e: Exception) {
            // Do nothing
        }  finally {
            cursor?.close()
        }
        return false
    }

    private fun jobFromCursor(cursor: Cursor): Job {
        val type = cursor.getString(jobType)
        val data = SessionJobHelper.dataSerializer.deserialize(cursor.getString(serializedData))
        val job = SessionJobHelper.sessionJobInstantiator.instantiate(type, data)
        job.id = cursor.getString(jobID)
        job.failureCount = cursor.getInt(failureCount)
        return job
    }
}

object SessionJobHelper {
    val dataSerializer: Data.Serializer = JsonDataSerializer()
    val sessionJobInstantiator: SessionJobInstantiator = SessionJobInstantiator(SessionJobManagerFactories.getSessionJobFactories())
}