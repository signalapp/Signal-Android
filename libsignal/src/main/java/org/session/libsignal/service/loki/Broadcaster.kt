package org.session.libsignal.service.loki

interface Broadcaster {

    fun broadcast(event: String)
    fun broadcast(event: String, long: Long)
}
