package org.thoughtcrime.securesms.webrtc

class PeerConnectionException: Exception {
    constructor(error: String?): super(error)
    constructor(throwable: Throwable): super(throwable)
}