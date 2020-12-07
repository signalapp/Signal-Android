package org.session.libsession.database

import org.session.libsession.database.dto.DatabaseAttachmentDTO

interface MessageDataProvider {

    fun getAttachment(uniqueID: String): DatabaseAttachmentDTO?

}