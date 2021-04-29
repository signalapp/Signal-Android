package org.session.libsession.messaging.jobs

class SessionJobInstantiator(private val jobFactories: Map<String, Job.Factory<out Job>>) {

    fun instantiate(jobFactoryKey: String, data: Data): Job {
        if (jobFactories.containsKey(jobFactoryKey)) {
            return jobFactories[jobFactoryKey]?.create(data) ?: throw IllegalStateException("Tried to instantiate a job with key '$jobFactoryKey', but no matching factory was found.")
        } else {
            throw IllegalStateException("Tried to instantiate a job with key '$jobFactoryKey', but no matching factory was found.")
        }
    }
}