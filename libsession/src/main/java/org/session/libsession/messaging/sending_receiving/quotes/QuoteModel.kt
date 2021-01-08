package org.session.libsession.messaging.sending_receiving.quotes

import org.session.libsession.messaging.messages.visible.Attachment
import org.session.libsession.messaging.threads.Address

class QuoteModel(val id: Long,
                 val author: Address,
                 val text: String,
                 val missing: Boolean,
                 val attachments: List<Attachment>?) {
}
