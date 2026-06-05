/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.push.exceptions

import org.signal.network.exceptions.NonSuccessfulResponseCodeException

class InvalidRegistrationSessionIdException : NonSuccessfulResponseCodeException(400)
