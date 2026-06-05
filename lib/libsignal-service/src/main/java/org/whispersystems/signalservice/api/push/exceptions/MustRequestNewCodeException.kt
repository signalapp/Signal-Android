package org.whispersystems.signalservice.api.push.exceptions

import org.signal.network.exceptions.NonSuccessfulResponseCodeException

class MustRequestNewCodeException : NonSuccessfulResponseCodeException(409)
