package org.whispersystems.signalservice.api.push.exceptions

import org.signal.network.exceptions.NonSuccessfulResponseCodeException

class NoSuchSessionException : NonSuccessfulResponseCodeException(404)
