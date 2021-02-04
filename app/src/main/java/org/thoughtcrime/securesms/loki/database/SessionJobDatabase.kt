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
        private val sessionJobTable = "loki_thread_session_reset_database"
        val jobID = "job_id"
        val jobType = "job_type"
        val failureCount = "failure_count"
        val serializedData = "serialized_data"
        @JvmStatic val createSessionJobTableCommand = "CREATE TABLE $sessionJobTable ($jobID INTEGER PRIMARY KEY, $jobType STRING, $failureCount INTEGER DEFAULT 0, $serializedData TEXT);"
    }

    fun persistJob(job: Job) {
        val database = databaseHelper.writableDatabase
        val contentValues = ContentValues(2)
        contentValues.put(jobID, job.id)
        contentValues.put(jobType, job.getFactoryKey())
        contentValues.put(failureCount, job.failureCount)
        contentValues.put(serializedData, SessionJobHelper.dataSerializer.serialize(job.serialize()))
        database.insertOrUpdate(sessionJobTable, contentValues, "$jobID = ?", arrayOf(jobID.toString()))
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
        return result.first { job -> job.attachmentID == attachmentID }
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

class SessionJobHelper() {

    companion object {
        val dataSerializer: Data.Serializer = JsonDataSerializer()
        val sessionJobInstantiator: SessionJobInstantiator = SessionJobInstantiator(SessionJobManagerFactories.getSessionJobFactories())
    }
}