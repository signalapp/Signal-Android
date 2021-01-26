package org.session.libsignal.service.loki.utilities

import java.util.concurrent.Executors

object ThreadUtils {

    internal val executorPool = Executors.newCachedThreadPool()

    @JvmStatic
    fun queue(target: Runnable) {
        executorPool.execute(target)
    }

    fun queue(target: ()->Unit) {
        executorPool.execute(target)
    }

}