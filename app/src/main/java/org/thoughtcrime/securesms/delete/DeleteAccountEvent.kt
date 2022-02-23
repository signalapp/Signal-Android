package org.thoughtcrime.securesms.delete

/**
 * Account deletion event.
 *
 * @param type Specifies what type of event this is. Each type maps to a single class. This exists in order to facilitate
 *             legacy Java switch statement.
 */
sealed class DeleteAccountEvent(val type: Type) {
  object NoCountryCode : DeleteAccountEvent(Type.NO_COUNTRY_CODE)

  object NoNationalNumber : DeleteAccountEvent(Type.NO_NATIONAL_NUMBER)

  object NotAMatch : DeleteAccountEvent(Type.NOT_A_MATCH)

  object ConfirmDeletion : DeleteAccountEvent(Type.CONFIRM_DELETION)

  object PinDeletionFailed : DeleteAccountEvent(Type.PIN_DELETION_FAILED)

  object CancelSubscriptionFailed : DeleteAccountEvent(Type.CANCEL_SUBSCRIPTION_FAILED)

  object LeaveGroupsFailed : DeleteAccountEvent(Type.LEAVE_GROUPS_FAILED)

  object ServerDeletionFailed : DeleteAccountEvent(Type.SERVER_DELETION_FAILED)

  object LocalDataDeletionFailed : DeleteAccountEvent(Type.LOCAL_DATA_DELETION_FAILED)

  object LeaveGroupsFinished : DeleteAccountEvent(Type.LEAVE_GROUPS_FINISHED)

  object CancelingSubscription : DeleteAccountEvent(Type.CANCELING_SUBSCRIPTION)

  /**
   * Progress update for leaving groups
   *
   * @param totalCount The total number of groups we are attempting to leave
   * @param leaveCount The number of groups we have left so far
   */
  data class LeaveGroupsProgress(
    val totalCount: Int,
    val leaveCount: Int
  ) : DeleteAccountEvent(Type.LEAVE_GROUPS_PROGRESS)

  enum class Type {
    NO_COUNTRY_CODE,
    NO_NATIONAL_NUMBER,
    NOT_A_MATCH,
    CONFIRM_DELETION,
    LEAVE_GROUPS_FAILED,
    PIN_DELETION_FAILED,
    CANCELING_SUBSCRIPTION,
    CANCEL_SUBSCRIPTION_FAILED,
    SERVER_DELETION_FAILED,
    LOCAL_DATA_DELETION_FAILED,
    LEAVE_GROUPS_PROGRESS,
    LEAVE_GROUPS_FINISHED
  }
}
