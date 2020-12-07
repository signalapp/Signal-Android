package org.session.libsession.messaging.sending_receiving

import nl.komponents.kovenant.Promise

import org.session.libsession.messaging.jobs.JobQueue
import org.session.libsession.messaging.jobs.MessageSendJob
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.messages.Message
import org.session.libsession.messaging.messages.visible.VisibleMessage

import org.session.libsignal.service.api.messages.SignalServiceAttachment

fun MessageSender.send(message: VisibleMessage, attachments: List<SignalServiceAttachment>, threadID: String) {
    prep(attachments, message)
    send(message, threadID)
}

fun MessageSender.send(message: Message, threadID: String) {
    message.threadID = threadID
    val destination = Destination.from(threadID)
    val job = MessageSendJob(message, destination)
    JobQueue.shared.add(job)
}

fun MessageSender.sendNonDurably(message: VisibleMessage, attachments: List<SignalServiceAttachment>, threadID: String): Promise<Unit, Exception> {
    prep(attachments, message)
    // TODO: Deal with attachments
    return sendNonDurably(message, threadID)
}

fun MessageSender.sendNonDurably(message: Message, threadID: String): Promise<Unit, Exception> {
    message.threadID = threadID
    val destination = Destination.from(threadID)
    return MessageSender.send(message, destination)
}