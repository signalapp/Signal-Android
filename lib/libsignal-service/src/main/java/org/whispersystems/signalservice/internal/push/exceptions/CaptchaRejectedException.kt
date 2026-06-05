/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.push.exceptions

import org.signal.network.exceptions.NonSuccessfulResponseCodeException

/**
 * Indicates that the captcha we submitted was not accepted by the server.
 */
class CaptchaRejectedException : NonSuccessfulResponseCodeException(428, "Captcha rejected by server.")
