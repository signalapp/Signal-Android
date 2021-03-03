@file:JvmName("FcmUtils")
package org.thoughtcrime.securesms.loki.utilities

import com.google.android.gms.tasks.Task
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import kotlinx.coroutines.*


fun getFcmInstanceId(body: (Task<InstanceIdResult>)->Unit): Job = MainScope().launch(Dispatchers.IO) {
    val task = FirebaseInstanceId.getInstance().instanceId
    while (!task.isComplete && isActive) {
        // wait for task to complete while we are active
    }
    if (!isActive) return@launch // don't 'complete' task if we were canceled
    withContext(Dispatchers.Main) {
        body(task)
    }
}