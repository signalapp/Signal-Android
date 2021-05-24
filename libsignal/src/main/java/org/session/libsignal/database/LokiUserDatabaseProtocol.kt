package org.session.libsignal.database

interface LokiUserDatabaseProtocol {

    fun getDisplayName(publicKey: String): String?
    fun getProfilePictureURL(publicKey: String): String?
}
