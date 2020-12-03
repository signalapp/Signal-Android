package org.session.libsession.messaging.messages

sealed class Destination {

    class Contact(val publicKey: String) : Destination()
    class ClosedGroup(val groupPublicKey: String) : Destination()
    class OpenGroup(val channel: Long, val server: String) : Destination()

    companion object {
        //TODO need to implement the equivalent to TSThread and then implement from(...)
    }
}