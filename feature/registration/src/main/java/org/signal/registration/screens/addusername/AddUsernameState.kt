/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.addusername

import org.signal.core.util.censor
import org.signal.libsignal.usernames.Username

/**
 * State for the optional username entry screen.
 */
data class AddUsernameState(
  /** The nickname (the part of the username before the discriminator) as typed by the user. */
  val username: String = "",
  /** The discriminator (the digits after the nickname), either server-assigned or typed by the user. */
  val discriminator: String = "",
  /** True when [discriminator] was typed by the user, meaning we reserve that exact discriminator rather than letting the service pick one. */
  val isDiscriminatorUserSet: Boolean = false,
  /** Whether the discriminator field is shown. It stays hidden until the service has assigned a discriminator to display. */
  val showDiscriminator: Boolean = false,
  /** Set when the entered username fails validation, describing why. */
  val validationError: ValidationError? = null,
  /** The reserved username (nickname + discriminator) for the entered nickname, once one has been reserved. */
  val reservation: Username? = null,
  /** True while a reservation request for the entered nickname is in flight. */
  val isReserving: Boolean = false,
  val showSpinner: Boolean = false,
  val dialogs: Dialogs = Dialogs()
) {
  /** Whether the entered nickname is complete enough to submit. */
  val isSubmittable: Boolean
    get() = !showSpinner && !isReserving && username.isNotBlank() && validationError == null && reservation != null

  /** The discriminator to reserve, or null when the service should assign one. */
  val requestedDiscriminator: String?
    get() = discriminator.takeIf { isDiscriminatorUserSet }

  override fun toString(): String = "AddUsernameState(username=${username.censor()}, discriminator=${discriminator.censor()}, isDiscriminatorUserSet=$isDiscriminatorUserSet, showDiscriminator=$showDiscriminator, validationError=$validationError, reservation=${reservation?.username?.censor()}, isReserving=$isReserving, showSpinner=$showSpinner, dialogs=$dialogs)"

  enum class ValidationError {
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
    CANNOT_START_WITH_DIGIT,

    /** The nickname is valid, but no username could be reserved for it. */
    NOT_AVAILABLE,

    DISCRIMINATOR_TOO_SHORT,
    DISCRIMINATOR_TOO_LONG,
    DISCRIMINATOR_CANNOT_BE_00,
    DISCRIMINATOR_CANNOT_START_WITH_ZERO,

    /** The nickname and user-chosen discriminator are both valid, but that pairing is already taken. */
    DISCRIMINATOR_NOT_AVAILABLE
  }

  data class Dialogs(
    /** Explains what the digits after the username are for. */
    val learnMore: Boolean = false,
    /** Confirms the user wants to skip choosing a username. */
    val confirmSkip: Boolean = false,
    val networkError: Boolean = false,
    val unknownError: Boolean = false,
    /** The reserved username was claimed by someone else before it could be confirmed. */
    val usernameUnavailable: Boolean = false,
    /** The reservation expired before the user submitted it. */
    val reservationLapsed: Boolean = false,
    val rateLimited: Boolean = false
  )
}
