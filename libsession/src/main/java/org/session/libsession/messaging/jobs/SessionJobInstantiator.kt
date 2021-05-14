package org.session.libsession.messaging.jobs

import org.session.libsession.messaging.utilities.Data

class SessionJobInstantiator(private val jobFactories: Map<String, Job.Factory<out Job>>) {

    fun instantiate(jobFactoryKey: String, data: Data): Job? {
        if (jobFactories.containsKey(jobFactoryKey)) {
            return jobFactories[jobFactoryKey]?.create(data)
        } else {
            return null
        }
    }
}