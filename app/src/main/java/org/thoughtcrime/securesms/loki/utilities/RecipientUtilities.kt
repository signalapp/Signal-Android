package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.session.libsession.messaging.threads.Address
import org.session.libsession.messaging.threads.recipients.Recipient

fun recipient(context: Context, publicKey: String): Recipient {
    return Recipient.from(context, Address.fromSerialized(publicKey), false)
}