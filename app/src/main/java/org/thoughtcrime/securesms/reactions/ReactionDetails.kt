package org.thoughtcrime.securesms.reactions

import org.session.libsession.utilities.recipients.Recipient

/**
 * A UI model for a reaction in the [ReactionsDialogFragment]
 */
data class ReactionDetails(
  val sender: Recipient,
  val baseEmoji: String,
  val displayEmoji: String,
  val timestamp: Long,
  val serverId: String,
  val localId: Long,
  val isMms: Boolean,
  val count: Int
)
