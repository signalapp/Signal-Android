package org.session.libsignal.libsignal.loki

enum class SessionResetStatus(val rawValue: Int) {
    NONE(0),
    IN_PROGRESS(1),
    REQUEST_RECEIVED(2)
}
