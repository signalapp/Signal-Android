package org.thoughtcrime.securesms.backup

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import android.preference.PreferenceManager.getDefaultSharedPreferencesName
import org.session.libsession.utilities.TextSecurePreferences
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.backup.FullBackupImporter.PREF_PREFIX_TYPE_BOOLEAN
import org.thoughtcrime.securesms.backup.FullBackupImporter.PREF_PREFIX_TYPE_INT
import java.util.*

object BackupPreferences {
    // region Backup related
    fun getBackupRecords(context: Context): List<BackupProtos.SharedPreference> {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefsFileName: String
        prefsFileName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getDefaultSharedPreferencesName(context)
        } else {
            context.packageName + "_preferences"
        }
        val prefList: LinkedList<BackupProtos.SharedPreference> = LinkedList<BackupProtos.SharedPreference>()
        addBackupEntryInt(prefList, preferences, prefsFileName, TextSecurePreferences.LOCAL_REGISTRATION_ID_PREF)
        addBackupEntryString(prefList, preferences, prefsFileName, TextSecurePreferences.LOCAL_NUMBER_PREF)
        addBackupEntryString(prefList, preferences, prefsFileName, TextSecurePreferences.PROFILE_NAME_PREF)
        addBackupEntryString(prefList, preferences, prefsFileName, TextSecurePreferences.PROFILE_AVATAR_URL_PREF)
        addBackupEntryInt(prefList, preferences, prefsFileName, TextSecurePreferences.PROFILE_AVATAR_ID_PREF)
        addBackupEntryString(prefList, preferences, prefsFileName, TextSecurePreferences.PROFILE_KEY_PREF)
        addBackupEntryBoolean(prefList, preferences, prefsFileName, TextSecurePreferences.IS_USING_FCM)
        return prefList
    }

    private fun addBackupEntryString(
            outPrefList: MutableList<BackupProtos.SharedPreference>,
            prefs: SharedPreferences,
            prefFileName: String,
            prefKey: String,
    ) {
        val value = prefs.getString(prefKey, null)
        if (value == null) {
            logBackupEntry(prefKey, false)
            return
        }
        outPrefList.add(BackupProtos.SharedPreference.newBuilder()
                .setFile(prefFileName)
                .setKey(prefKey)
                .setValue(value)
                .build())
        logBackupEntry(prefKey, true)
    }

    private fun addBackupEntryInt(
            outPrefList: MutableList<BackupProtos.SharedPreference>,
            prefs: SharedPreferences,
            prefFileName: String,
            prefKey: String,
    ) {
        val value = prefs.getInt(prefKey, -1)
        if (value == -1) {
            logBackupEntry(prefKey, false)
            return
        }
        outPrefList.add(BackupProtos.SharedPreference.newBuilder()
                .setFile(prefFileName)
                .setKey(PREF_PREFIX_TYPE_INT + prefKey) // The prefix denotes the type of the preference.
                .setValue(value.toString())
                .build())
        logBackupEntry(prefKey, true)
    }

    private fun addBackupEntryBoolean(
            outPrefList: MutableList<BackupProtos.SharedPreference>,
            prefs: SharedPreferences,
            prefFileName: String,
            prefKey: String,
    ) {
        if (!prefs.contains(prefKey)) {
            logBackupEntry(prefKey, false)
            return
        }
        outPrefList.add(BackupProtos.SharedPreference.newBuilder()
                .setFile(prefFileName)
                .setKey(PREF_PREFIX_TYPE_BOOLEAN + prefKey) // The prefix denotes the type of the preference.
                .setValue(prefs.getBoolean(prefKey, false).toString())
                .build())
        logBackupEntry(prefKey, true)
    }

    private fun logBackupEntry(prefName: String, wasIncluded: Boolean) {
        val sb = StringBuilder()
        sb.append("Backup preference ")
        sb.append(if (wasIncluded) "+ " else "- ")
        sb.append('\"').append(prefName).append("\" ")
        if (!wasIncluded) {
            sb.append("(is empty and not included)")
        }
        Log.d("Loki", sb.toString())
    } // endregion
}