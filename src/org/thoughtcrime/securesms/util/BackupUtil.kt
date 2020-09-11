package org.thoughtcrime.securesms.util

import android.content.Context
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.BackupFileRecord
import org.whispersystems.libsignal.util.ByteUtil
import java.security.SecureRandom
import java.util.*

object BackupUtil {

    @JvmStatic
    fun getLastBackupTimeString(context: Context, locale: Locale): String {
        val timestamp = DatabaseFactory.getLokiBackupFilesDatabase(context).getLastBackupFileTime()
        if (timestamp == null) {
            return context.getString(R.string.BackupUtil_never)
        }
        return DateUtils.getExtendedRelativeTimeSpanString(context, locale, timestamp.time)
    }

    @JvmStatic
    fun getLastBackup(context: Context): BackupFileRecord? {
        return DatabaseFactory.getLokiBackupFilesDatabase(context).getLastBackupFile()
    }

    @JvmStatic
    fun generateBackupPassphrase(): Array<String> {
        val result = arrayOfNulls<String>(6)
        val random = ByteArray(30)
        SecureRandom().nextBytes(random)
        for (i in 0..5) {
            result[i] = String.format("%05d", ByteUtil.byteArray5ToLong(random, i * 5) % 100000)
        }
        @Suppress("UNCHECKED_CAST")
        return result as Array<String>
    }
}