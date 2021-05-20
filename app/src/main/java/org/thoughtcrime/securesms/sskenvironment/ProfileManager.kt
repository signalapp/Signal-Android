package org.thoughtcrime.securesms.sskenvironment

import android.content.Context
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsession.utilities.SSKEnvironment
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobs.RetrieveProfileAvatarJob

class ProfileManager: SSKEnvironment.ProfileManagerProtocol {
    override fun setDisplayName(context: Context, recipient: Recipient, displayName: String) {
        DatabaseFactory.getLokiUserDatabase(context).setDisplayName(recipient.address.serialize(), displayName)
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