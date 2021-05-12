package org.session.libsession.messaging.jobs

class SessionJobInstantiator(private val jobFactories: Map<String, Job.Factory<out Job>>) {

    fun instantiate(jobFactoryKey: String, data: Data): Job? {
        if (jobFactories.containsKey(jobFactoryKey)) {
            return jobFactories[jobFactoryKey]?.create(data)
        } else {
            return null
        }
    }
}