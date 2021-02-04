package org.session.libsession.messaging.sending_receiving

import nl.komponents.kovenant.Promise
import org.session.libsession.messaging.MessagingConfiguration

import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsession.messaging.threads.Address

import org.session.libsignal.service.api.messages.SignalServiceAttachment

fun MessageSender.send(message: VisibleMessage, attachments: List<SignalServiceAttachment>, address: Address) {
    prep(attachments, message)
    send(message, address)
}

fun MessageSender.send(message: Message, address: Address) {
    val threadID = MessagingConfiguration.shared.storage.getOrCreateThreadIdFor(address)
    message.threadID = threadID
    val destination = Destination.from(address)
    val job = MessageSendJob(message, destination)
    JobQueue.shared.add(job)
}

fun MessageSender.sendNonDurably(message: VisibleMessage, attachments: List<SignalServiceAttachment>, address: Address): Promise<Unit, Exception> {
    prep(attachments, message)
    return sendNonDurably(message, address)
}

fun MessageSender.sendNonDurably(message: Message, address: Address): Promise<Unit, Exception> {
    val threadID = MessagingConfiguration.shared.storage.getOrCreateThreadIdFor(address)
    message.threadID = threadID
    val destination = Destination.from(address)
    return MessageSender.send(message, destination)
}