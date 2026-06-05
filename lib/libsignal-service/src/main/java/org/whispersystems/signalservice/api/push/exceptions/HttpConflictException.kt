package org.whispersystems.signalservice.api.push.exceptions

import org.signal.network.exceptions.NonSuccessfulResponseCodeException

class HttpConflictException : NonSuccessfulResponseCodeException(409)
