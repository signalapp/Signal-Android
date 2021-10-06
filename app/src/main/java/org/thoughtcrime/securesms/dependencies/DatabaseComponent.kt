package org.thoughtcrime.securesms.dependencies

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.session.libsession.database.MessageDataProvider
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.*
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseComponent {

    companion object {
        @JvmStatic
        fun get(context: Context) = ApplicationContext.getInstance(context).databaseComponent
    }

    fun openHelper(): SQLCipherOpenHelper

    fun smsDatabase(): SmsDatabase
    fun mmsDatabase(): MmsDatabase
    fun attachmentDatabase(): AttachmentDatabase
    fun mediaDatabase(): MediaDatabase
    fun threadDatabase(): ThreadDatabase
    fun mmsSmsDatabase(): MmsSmsDatabase
    fun draftDatabase(): DraftDatabase
    fun pushDatabase(): PushDatabase
    fun groupDatabase(): GroupDatabase
    fun recipientDatabase(): RecipientDatabase
    fun groupReceiptDatabase(): GroupReceiptDatabase
    fun searchDatabase(): SearchDatabase
    fun jobDatabase(): JobDatabase
    fun lokiAPIDatabase(): LokiAPIDatabase
    fun lokiMessageDatabase(): LokiMessageDatabase
    fun lokiThreadDatabase(): LokiThreadDatabase
    fun lokiUserDatabase(): LokiUserDatabase
    fun lokiBackupFilesDatabase(): LokiBackupFilesDatabase
    fun sessionJobDatabase(): SessionJobDatabase
    fun sessionContactDatabase(): SessionContactDatabase
    fun storage(): Storage
    fun attachmentProvider(): MessageDataProvider
}