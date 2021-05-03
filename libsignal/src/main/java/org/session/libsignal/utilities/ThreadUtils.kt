package org.session.libsignal.utilities

import java.util.concurrent.*

object ThreadUtils {
    val executorPool = Executors.newCachedThreadPool()

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