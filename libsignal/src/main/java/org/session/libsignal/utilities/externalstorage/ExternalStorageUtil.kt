package org.session.libsignal.utilities.externalstorage

import android.content.Context
import android.os.Environment
import java.io.File

object ExternalStorageUtil {
    const val DIRECTORY_BACKUPS = "Backups"

    /** @see Context.getExternalFilesDir
     */
    @Throws(NoExternalStorageException::class)
    fun getDir(context: Context, type: String?): File {
        return context.getExternalFilesDir(type)
                ?: throw NoExternalStorageException("External storage dir is currently unavailable: $type")
    }

    @Throws(NoExternalStorageException::class)
    fun getBackupDir(context: Context): File {
        return getDir(context, DIRECTORY_BACKUPS)
    }

    @Throws(NoExternalStorageException::class)
    fun getVideoDir(context: Context): File {
        return getDir(context, Environment.DIRECTORY_MOVIES)
    }

    @Throws(NoExternalStorageException::class)
    fun getAudioDir(context: Context): File {
        return getDir(context, Environment.DIRECTORY_MUSIC)
    }

    @JvmStatic
    @Throws(NoExternalStorageException::class)
    fun getImageDir(context: Context): File {
        return getDir(context, Environment.DIRECTORY_PICTURES)
    }

    @Throws(NoExternalStorageException::class)
    fun getDownloadDir(context: Context): File {
        return getDir(context, Environment.DIRECTORY_DOWNLOADS)
    }

    fun getCacheDir(context: Context): File? {
        return context.externalCacheDir
    }

    @JvmStatic
    fun getCleanFileName(fileName: String?): String? {
        var fileName = fileName ?: return null
        fileName = fileName.replace('\u202D', '\uFFFD')
        fileName = fileName.replace('\u202E', '\uFFFD')
        return fileName
    }
}