package org.session.libsignal.service.loki.utilities

interface Broadcaster {

    fun broadcast(event: String)
    fun broadcast(event: String, long: Long)
}
