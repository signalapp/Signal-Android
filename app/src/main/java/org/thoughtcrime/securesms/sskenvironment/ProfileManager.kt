package org.thoughtcrime.securesms.sskenvironment

import android.content.Context
import org.session.libsession.messaging.threads.recipients.Recipient
import org.session.libsession.utilities.SSKEnvironment
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob

class ProfileManager: SSKEnvironment.ProfileManagerProtocol {
    override fun setDisplayName(context: Context, recipient: Recipient, displayName: String) {
        val database = DatabaseFactory.getLokiUserDatabase(context)
        val publicKey = recipient.address.serialize()
        if (recipient.name == null) {
            // Migrate the profile name in LokiUserDatabase to recipient
            database.getDisplayName(publicKey)?.let { setProfileName(context, recipient, it) }
        }
        database.setDisplayName(publicKey, displayName)
    }

    override fun setProfileName(context: Context, recipient: Recipient, profileName: String) {
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setProfileName(recipient, profileName)
    }

    override fun setProfilePictureURL(context: Context, recipient: Recipient, profilePictureURL: String) {
        ApplicationContext.getInstance(context).jobManager.add(RetrieveProfileAvatarJob(recipient, profilePictureURL))
    }

    override fun setProfileKey(context: Context, recipient: Recipient, profileKey: ByteArray) {
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setProfileKey(recipient, profileKey)
    }

    override fun setUnidentifiedAccessMode(context: Context, recipient: Recipient, unidentifiedAccessMode: Recipient.UnidentifiedAccessMode) {
        val database = DatabaseFactory.getRecipientDatabase(context)
        database.setUnidentifiedAccessMode(recipient, unidentifiedAccessMode)
    }

    override fun updateOpenGroupProfilePicturesIfNeeded(context: Context) {
        ApplicationContext.getInstance(context).updateOpenGroupProfilePicturesIfNeeded()
    }
}