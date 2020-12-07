package org.session.libsession.utilities

object LKGroupUtilities {
    const val CLOSED_GROUP_PREFIX = "__textsecure_group__!"
    const val MMS_GROUP_PREFIX = "__signal_mms_group__!"
    const val OPEN_GROUP_PREFIX = "__loki_public_chat_group__!"

    fun getEncodedOpenGroupID(groupID: String): String {
        return OPEN_GROUP_PREFIX + groupID
    }

    fun getEncodedOpenGroupIDAsData(groupID: String): ByteArray {
        return (OPEN_GROUP_PREFIX + groupID).toByteArray()
    }

    fun getEncodedClosedGroupID(groupID: String): String {
        return CLOSED_GROUP_PREFIX + groupID
    }

    fun getEncodedClosedGroupIDAsData(groupID: String): ByteArray {
        return (CLOSED_GROUP_PREFIX + groupID).toByteArray()
    }

    fun getEncodedMMSGroupID(groupID: String): String {
        return MMS_GROUP_PREFIX + groupID
    }

    fun getEncodedMMSGroupIDAsData(groupID: String): ByteArray {
        return (MMS_GROUP_PREFIX + groupID).toByteArray()
    }

    fun getEncodedGroupID(groupID: ByteArray): String {
        return groupID.toString()
    }

    fun getDecodedGroupID(groupID: ByteArray): String {
        val encodedGroupID = groupID.toString()
        if (encodedGroupID.split("!").count() > 1) {
            return encodedGroupID.split("!")[1]
        }
        return encodedGroupID.split("!")[0]
    }

    fun getDecodedGroupIDAsData(groupID: ByteArray): ByteArray {
        return getDecodedGroupID(groupID).toByteArray()
    }
}