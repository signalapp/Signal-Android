package org.thoughtcrime.securesms.database.model

data class ReactionRecord(
    val id: Long = 0,
    val messageId: Long,
    val isMms: Boolean,
    val author: String,
    val emoji: String,
    val serverId: String = "",
    val count: Long = 0,
    val sortId: Long = 0,
    val dateSent: Long = 0,
    val dateReceived: Long = 0
)