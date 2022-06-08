package org.session.libsignal.utilities

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object ThreadUtils {

    val executorPool: ExecutorService = Executors.newCachedThreadPool()

    @JvmStatic
    fun queue(target: Runnable) {
        executorPool.execute(target)
    }

    fun queue(target: () -> Unit) {
        executorPool.execute(target)
    }

    @JvmStatic
    fun newDynamicSingleThreadedExecutor(): ExecutorService {
        val executor = ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, LinkedBlockingQueue())
        executor.allowCoreThreadTimeOut(true)
        return executor
    }
}