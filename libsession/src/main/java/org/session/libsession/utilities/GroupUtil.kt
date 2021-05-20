package org.session.libsession.utilities

import org.session.libsignal.messages.SignalServiceGroup
import org.session.libsignal.utilities.Hex
import java.io.IOException
import kotlin.jvm.Throws

object GroupUtil {
    const val CLOSED_GROUP_PREFIX = "__textsecure_group__!"
    const val OPEN_GROUP_PREFIX = "__loki_public_chat_group__!"

    @JvmStatic
    fun getEncodedOpenGroupID(groupID: ByteArray): String {
        return OPEN_GROUP_PREFIX + Hex.toStringCondensed(groupID)
    }

    @JvmStatic
    fun getEncodedClosedGroupID(groupID: ByteArray): String {
        return CLOSED_GROUP_PREFIX + Hex.toStringCondensed(groupID)
    }

    @JvmStatic
    fun getEncodedId(group: SignalServiceGroup): String {
        val groupId = group.groupId
        if (group.groupType == SignalServiceGroup.GroupType.PUBLIC_CHAT) {
            return getEncodedOpenGroupID(groupId)
        }
        return getEncodedClosedGroupID(groupId)
    }

    private fun splitEncodedGroupID(groupID: String): String {
        if (groupID.split("!").count() > 1) {
            return groupID.split("!", limit = 2)[1]
        }
        return groupID
    }

    @JvmStatic
    fun getDecodedGroupID(groupID: String): String {
        return String(getDecodedGroupIDAsData(groupID))
    }

    @JvmStatic
    fun getDecodedGroupIDAsData(groupID: String): ByteArray {
        return Hex.fromStringCondensed(splitEncodedGroupID(groupID))
    }

    fun isEncodedGroup(groupId: String): Boolean {
        return groupId.startsWith(CLOSED_GROUP_PREFIX) || groupId.startsWith(OPEN_GROUP_PREFIX)
    }

    @JvmStatic
    fun isOpenGroup(groupId: String): Boolean {
        return groupId.startsWith(OPEN_GROUP_PREFIX)
    }

    @JvmStatic
    fun isClosedGroup(groupId: String): Boolean {
        return groupId.startsWith(CLOSED_GROUP_PREFIX)
    }

    // NOTE: Signal group ID handling is weird. The ID is double encoded in the database, but not in a `GroupContext`.

    @JvmStatic
    @Throws(IOException::class)
    fun doubleEncodeGroupID(groupPublicKey: String): String {
        return getEncodedClosedGroupID(getEncodedClosedGroupID(Hex.fromStringCondensed(groupPublicKey)).toByteArray())
    }

    @JvmStatic
    fun doubleEncodeGroupID(groupID: ByteArray): String {
        return getEncodedClosedGroupID(getEncodedClosedGroupID(groupID).toByteArray())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun doubleDecodeGroupID(groupID: String): ByteArray {
        return getDecodedGroupIDAsData(getDecodedGroupID(groupID))
    }
}