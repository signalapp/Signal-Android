package org.session.libsession.utilities

import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.*
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
    fun uri(uri: String?): Uri? {
        return if (uri == null) null else Uri.parse(uri)
    }

    @JvmStatic
    fun runOnMain(runnable: Runnable) {
        if (isMainThread()) runnable.run()
        else getHandler()?.post(runnable)
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
    fun equals(a: Any?, b: Any?): Boolean {
        return a === b || a != null && a == b
    }

    @JvmStatic
    fun hashCode(vararg objects: Any?): Int {
        return Arrays.hashCode(objects)
    }

}