package org.thoughtcrime.securesms.sskenvironment

import android.content.Context
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.SSKEnvironment
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob

class ProfileManager : SSKEnvironment.ProfileManagerProtocol {

    override fun setNickname(context: Context, recipient: Recipient, nickname: String?) {
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseFactory.getSessionContactDatabase(context)
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseFactory.getStorage(context).getThreadId(recipient.address)
        if (contact.nickname != nickname) {
            contact.nickname = nickname
            contactDatabase.setContact(contact)
        }
    }

    override fun setName(context: Context, recipient: Recipient, name: String) {
        // New API
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseFactory.getSessionContactDatabase(context)
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseFactory.getStorage(context).getThreadId(recipient.address)
        if (contact.name != name) {
            contact.name = name
            contactDatabase.setContact(contact)
        }
        // Old API
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setProfileName(recipient, name)
        recipient.notifyListeners()
    }

    override fun setProfilePictureURL(context: Context, recipient: Recipient, profilePictureURL: String) {
        val job = RetrieveProfileAvatarJob(recipient, profilePictureURL)
        ApplicationContext.getInstance(context).jobManager.add(job)
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseFactory.getSessionContactDatabase(context)
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseFactory.getStorage(context).getThreadId(recipient.address)
        if (contact.profilePictureURL != profilePictureURL) {
            contact.profilePictureURL = profilePictureURL
            contactDatabase.setContact(contact)
        }
    }

    override fun setProfileKey(context: Context, recipient: Recipient, profileKey: ByteArray) {
        // New API
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseFactory.getSessionContactDatabase(context)
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        contact.threadID = DatabaseFactory.getStorage(context).getThreadId(recipient.address)
        if (!contact.profilePictureEncryptionKey.contentEquals(profileKey)) {
            contact.profilePictureEncryptionKey = profileKey
            contactDatabase.setContact(contact)
        }
        // Old API
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setProfileKey(recipient, profileKey)
    }

    override fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode) {
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setUnidentifiedAccessMode(recipient, unidentifiedAccessMode)
    }
}