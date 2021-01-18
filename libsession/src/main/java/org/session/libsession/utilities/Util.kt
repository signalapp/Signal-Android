package org.session.libsession.utilities

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.StyleSpan
import org.session.libsession.messaging.threads.Address
import org.session.libsignal.libsignal.logging.Log
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object Util {
    @Volatile
    private var handler: Handler? = null

    fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    @JvmStatic
    fun assertMainThread() {
        if (!isMainThread()) {
            throw java.lang.AssertionError("Main-thread assertion failed.")
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copy(`in`: InputStream, out: OutputStream): Long {
        val buffer = ByteArray(8192)
        var read: Int
        var total: Long = 0
        while (`in`.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
            total += read.toLong()
        }
        `in`.close()
        out.close()
        return total
    }

    @JvmStatic
    fun uri(uri: String?): Uri? {
        return if (uri == null) null else Uri.parse(uri)
    }

    @JvmStatic
    fun runOnMain(runnable: Runnable) {
        if (isMainThread()) runnable.run()
        else getHandler()?.post(runnable)
    }

    @JvmStatic
    fun runOnMainDelayed(runnable: Runnable, delayMillis: Long) {
        getHandler()?.postDelayed(runnable, delayMillis)
    }

    @JvmStatic
    fun runOnMainSync(runnable: Runnable) {
        if (isMainThread()) {
            runnable.run()
        } else {
            val sync = CountDownLatch(1)
            runOnMain(Runnable {
                try {
                    runnable.run()
                } finally {
                    sync.countDown()
                }
            })
            try {
                sync.await()
            } catch (ie: InterruptedException) {
                throw java.lang.AssertionError(ie)
            }
        }
    }

    @JvmStatic
    fun cancelRunnableOnMain(runnable: Runnable) {
        getHandler()?.removeCallbacks(runnable)
    }

    private fun getHandler(): Handler? {
        if (handler == null) {
            synchronized(Util::class.java) {
                if (handler == null) {
                    handler = Handler(Looper.getMainLooper())
                }
            }
        }
        return handler
    }

    @JvmStatic
    fun wait(lock: Object, timeout: Long) {
        try {
            lock.wait(timeout)
        } catch (ie: InterruptedException) {
            throw AssertionError(ie)
        }
    }

    @JvmStatic
    fun newSingleThreadedLifoExecutor(): ExecutorService {
        val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingLifoQueue<Runnable>())
        executor.execute {
            Thread.currentThread().priority = Thread.MIN_PRIORITY
        }
        return executor
    }

    @JvmStatic
    fun join(list: Array<String?>, delimiter: String?): String {
        return join(Arrays.asList(*list), delimiter)
    }

    @JvmStatic
    fun join(list: Collection<String?>, delimiter: String?): String {
        val result = StringBuilder()
        var i = 0
        for (item in list) {
            result.append(item)
            if (++i < list.size) result.append(delimiter)
        }
        return result.toString()
    }

    @JvmStatic
    fun join(list: LongArray, delimeter: String?): String {
        val sb = java.lang.StringBuilder()
        for (j in list.indices) {
            if (j != 0) sb.append(delimeter)
            sb.append(list[j])
        }
        return sb.toString()
    }

    @JvmStatic
    fun equals(a: Any?, b: Any?): Boolean {
        return a === b || a != null && a == b
    }

    @JvmStatic
    fun hashCode(vararg objects: Any?): Int {
        return Arrays.hashCode(objects)
    }

    @JvmStatic
    fun <K, V> getOrDefault(map: Map<K, V>, key: K, defaultValue: V): V? {
        return if (map.containsKey(key)) map[key] else defaultValue
    }

    @JvmStatic
    @Throws(IOException::class)
    fun getStreamLength(`in`: InputStream): Long {
        val buffer = ByteArray(4096)
        var totalSize = 0
        var read: Int
        while (`in`.read(buffer).also { read = it } != -1) {
            totalSize += read
        }
        return totalSize.toLong()
    }

    @JvmStatic
    fun toIntExact(value: Long): Int {
        if (value.toInt().compareTo(value) != 0){
            throw ArithmeticException("integer overflow")
        }
        return value.toInt()
    }

    @JvmStatic
    fun close(closeable: Closeable) {
        try {
            closeable.close()
        } catch (e: IOException) {
            Log.w("Loki", e)
        }
    }

    @JvmStatic
    fun isOwnNumber(context: Context, address: Address): Boolean {
        return if (address.isGroup) false else TextSecurePreferences.getLocalNumber(context) == address.serialize()
    }

    @JvmStatic
    fun <T> partition(list: List<T>, partitionSize: Int): List<List<T>> {
        val results: MutableList<List<T>> = LinkedList()
        var index = 0
        while (index < list.size) {
            val subListSize = Math.min(partitionSize, list.size - index)
            results.add(list.subList(index, index + subListSize))
            index += partitionSize
        }
        return results
    }

    @JvmStatic
    fun toIsoString(bytes: ByteArray?): String {
        return String(bytes!!, StandardCharsets.ISO_8859_1)
    }

    @JvmStatic
    fun toIsoBytes(isoString: String): ByteArray {
        return isoString.toByteArray(StandardCharsets.ISO_8859_1)
    }

    @JvmStatic
    fun toUtf8Bytes(utf8String: String): ByteArray {
        return utf8String.toByteArray(StandardCharsets.UTF_8)
    }

    @JvmStatic
    @SuppressLint("NewApi")
    fun isDefaultSmsProvider(context: Context): Boolean {
        return context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readFully(`in`: InputStream?, buffer: ByteArray) {
        if (`in` == null) return
        readFully(`in`, buffer, buffer.size)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readFully(`in`: InputStream, buffer: ByteArray?, len: Int) {
        var offset = 0
        while (true) {
            val read = `in`.read(buffer, offset, len - offset)
            if (read == -1) throw EOFException("Stream ended early")
            offset += if (read + offset < len) read else return
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readFully(`in`: InputStream): ByteArray? {
        val bout = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var read: Int
        while (`in`.read(buffer).also { read = it } != -1) {
            bout.write(buffer, 0, read)
        }
        `in`.close()
        return bout.toByteArray()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readFullyAsString(`in`: InputStream): String? {
        return String(readFully(`in`)!!)
    }

    @JvmStatic
    fun getSecret(size: Int): String? {
        val secret = getSecretBytes(size)
        return Base64.encodeBytes(secret)
    }

    @JvmStatic
    fun getSecretBytes(size: Int): ByteArray {
        val secret = ByteArray(size)
        getSecureRandom().nextBytes(secret)
        return secret
    }

    @JvmStatic
    fun getSecureRandom(): SecureRandom {
        return SecureRandom()
    }

    @JvmStatic
    fun getFirstNonEmpty(vararg values: String?): String? {
        for (value in values) {
            if (!TextUtils.isEmpty(value)) {
                return value
            }
        }
        return ""
    }

    @JvmStatic
    fun isEmpty(collection: Collection<*>?): Boolean {
        return collection == null || collection.isEmpty()
    }

    @JvmStatic
    @TargetApi(VERSION_CODES.KITKAT)
    fun isLowMemory(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return VERSION.SDK_INT >= VERSION_CODES.KITKAT && activityManager.isLowRamDevice ||
                activityManager.largeMemoryClass <= 64
    }

    @JvmStatic
    fun <T> getRandomElement(elements: Array<T>): T {
        return elements[SecureRandom().nextInt(elements.size)]
    }

    @JvmStatic
    fun getBoldedString(value: String?): CharSequence {
        val spanned = SpannableString(value)
        spanned.setSpan(StyleSpan(Typeface.BOLD), 0,
                spanned.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spanned
    }

}