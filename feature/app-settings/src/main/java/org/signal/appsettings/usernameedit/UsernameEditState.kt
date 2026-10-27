/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.appsettings.usernameedit

data class UsernameEditState(
  /** The part of the username before the dot, as the field shows it. */
  val nickname: String = "",
  /** The digits after the dot, as the field shows them. Either the user's own, or ones reserved for them. */
  val discriminator: String = "",
  /** Whether the field shows the discriminator at all, which it only does once there is one to show. */
  val showDiscriminator: Boolean = false,
  /** The username above the field: the one reserved for what's typed, or the user's current one. Null prompts them to choose one. */
  val username: String? = null,
  /** True while a username is being reserved for what's typed. */
  val reserving: Boolean = false,
  val error: UsernameError? = null,
  val button: Button = Button.SUBMIT_DISABLED,
  val dialog: Dialog = Dialog.None,
  val snackbar: Snackbar = Snackbar.None
) {

  /** The fields can't be edited while a save or delete is in flight. */
  val inputEnabled: Boolean
    get() = button != Button.SUBMIT_LOADING && button != Button.DELETE_LOADING

  override fun toString(): String {
    return "UsernameEditState(nicknameLength=${nickname.length}, discriminatorLength=${discriminator.length}, showDiscriminator=$showDiscriminator, hasUsername=${username != null}, reserving=$reserving, error=$error, button=$button, dialog=$dialog, snackbar=$snackbar)"
  }

  /** The one button at the bottom of the screen, which saves the username or, once the field is cleared, deletes it. */
  enum class Button {
    SUBMIT,
    SUBMIT_DISABLED,
    SUBMIT_LOADING,
    DELETE,
    DELETE_DISABLED,
    DELETE_LOADING
  }

  /** Why what's typed can't be used, shown below the field. */
  enum class UsernameError {
    TAKEN,
    TOO_SHORT,
    TOO_LONG,
    CANNOT_START_WITH_NUMBER,
    INVALID_CHARACTERS,
    INVALID_GENERIC,
    DISCRIMINATOR_NOT_AVAILABLE,
    DISCRIMINATOR_TOO_SHORT,
    DISCRIMINATOR_TOO_LONG,
    DISCRIMINATOR_HAS_INVALID_CHARACTERS,
    DISCRIMINATOR_CANNOT_BE_00,
    DISCRIMINATOR_CANNOT_START_WITH_0
  }

  sealed interface Dialog {
    data object None : Dialog

    /** Changing an existing username gives the user a new username link, so they have to agree first. */
    data object ConfirmReset : Dialog

    /** While recovering, saving the username the user already had needs confirming that it's what they meant. */
    data object ConfirmSameUsernameRecovery : Dialog
  }

  sealed interface Snackbar {
    data object None : Snackbar

    /** The username was taken by someone else before it could be saved. */
    data object Taken : Snackbar

    /** The service rejected the username. */
    data object Invalid : Snackbar

    data object NetworkFailure : Snackbar

    data object RateLimited : Snackbar
  }
}
