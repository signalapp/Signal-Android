package org.thoughtcrime.securesms.messagerequests;

/**
 * An enum representing the possible message request states a user can be in.
 */
public enum MessageRequestState {
  /** No message request necessary */
  NONE,

  /** No message request necessary as the user was hidden after accepting*/
  NONE_HIDDEN,

  /** A user is blocked */
  BLOCKED_INDIVIDUAL,

  /** A group is blocked */
  BLOCKED_GROUP,

  /** An individual conversation that existed pre-message-requests but doesn't have profile sharing enabled */
  LEGACY_INDIVIDUAL,

  /** A V1 group conversation that is no longer allowed, because we've forced GV2 on. */
  DEPRECATED_GROUP_V1,

  /** An invite response is needed for a V2 group */
  GROUP_V2_INVITE,

  /** A message request is needed for a V2 group */
  GROUP_V2_ADD,

  /** A message request is needed for an individual */
  INDIVIDUAL,

  /** A message request is needed for an individual since they have been hidden */
  INDIVIDUAL_HIDDEN
}
