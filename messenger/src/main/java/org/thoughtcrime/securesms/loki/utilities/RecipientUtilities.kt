package org.thoughtcrime.securesms.loki.utilities

import android.content.Context
import org.thoughtcrime.securesms.database.Address
import org.thoughtcrime.securesms.recipients.Recipient

fun recipient(context: Context, publicKey: String): Recipient {
    return Recipient.from(context, Address.fromSerialized(publicKey), false)
}