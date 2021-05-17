package org.session.libsignal.utilities

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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

    fun getVideoUri(): Uri {
        return MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    }

    fun getAudioUri(): Uri {
        return MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    }

    fun getImageUri(): Uri {
        return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }

    fun getDownloadUri(): Uri {
        if (Build.VERSION.SDK_INT < 29) {
            return getLegacyUri(Environment.DIRECTORY_DOWNLOADS);
        } else {
            return MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        }
    }

    private fun getLegacyUri(directory: String): Uri {
        return Uri.fromFile(Environment.getExternalStoragePublicDirectory(directory))
    }

    @JvmStatic
    fun getCleanFileName(fileName: String?): String? {
        var fileName = fileName ?: return null
        fileName = fileName.replace('\u202D', '\uFFFD')
        fileName = fileName.replace('\u202E', '\uFFFD')
        return fileName
    }
}