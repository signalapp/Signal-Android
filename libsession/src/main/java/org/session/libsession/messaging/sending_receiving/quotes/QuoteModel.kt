package org.session.libsession.messaging.sending_receiving.quotes

import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.utilities.Address

data class QuoteModel(val id: Long,
                 val author: Address,
                 val text: String?,
                 val missing: Boolean,
                 val attachments: List<Attachment>?)
