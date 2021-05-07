package org.thoughtcrime.securesms.sskenvironment

import android.content.Context
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.SSKEnvironment
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob

class ProfileManager: SSKEnvironment.ProfileManagerProtocol {
    override fun setDisplayName(context: Context, recipient: Recipient, displayName: String) {
        val database = DatabaseFactory.getLokiUserDatabase(context)
        val sessionID = recipient.address.serialize()
        database.setDisplayName(sessionID, displayName)
        // New API
        val contactDatabase = DatabaseFactory.getSessionContactDatabase(context)
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        if (contact.nickname != displayName) {
            contact.nickname = displayName
            contactDatabase.setContact(contact)
        }
    }

    override fun setProfileName(context: Context, recipient: Recipient, profileName: String) {
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setProfileName(recipient, profileName)
        recipient.notifyListeners()
        // New API
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseFactory.getSessionContactDatabase(context)
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        if (contact.name != profileName) {
            contact.name = profileName
            contactDatabase.setContact(contact)
        }
    }

    override fun setProfilePictureURL(context: Context, recipient: Recipient, profilePictureURL: String) {
        ApplicationContext.getInstance(context).jobManager.add(RetrieveProfileAvatarJob(recipient, profilePictureURL))
    }

    override fun setProfileKey(context: Context, recipient: Recipient, profileKey: ByteArray) {
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setProfileKey(recipient, profileKey)
        // New API
        val sessionID = recipient.address.serialize()
        val contactDatabase = DatabaseFactory.getSessionContactDatabase(context)
        var contact = contactDatabase.getContactWithSessionID(sessionID)
        if (contact == null) contact = Contact(sessionID)
        if (!contact.profilePictureEncryptionKey.contentEquals(profileKey)) {
            contact.profilePictureEncryptionKey = profileKey
            contactDatabase.setContact(contact)
        }
    }

    override fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode) {
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setUnidentifiedAccessMode(recipient, unidentifiedAccessMode)
    }

    override fun updateOpenGroupProfilePicturesIfNeeded(context: Context) {
        ApplicationContext.getInstance(context).updateOpenGroupProfilePicturesIfNeeded()
    }
}