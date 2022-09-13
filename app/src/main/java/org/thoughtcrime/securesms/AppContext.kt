package org.thoughtcrime.securesms

import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.jvm.asDispatcher
import org.session.libsignal.utilities.Log
import org.session.libsignal.utilities.ThreadUtils
import java.util.concurrent.Executors

object AppContext {

    fun configureKovenant() {
        Kovenant.context {
            callbackContext.dispatcher = Executors.newSingleThreadExecutor().asDispatcher()
            workerContext.dispatcher = ThreadUtils.executorPool.asDispatcher()
            multipleCompletion = { v1, v2 ->
                Log.d("Loki", "Promise resolved more than once (first with $v1, then with $v2); ignoring $v2.")
            }
        }
    }
}