package org.whispersystems.signalservice.api.push.exceptions

import org.signal.network.exceptions.NonSuccessfulResponseCodeException

class AlreadyVerifiedException : NonSuccessfulResponseCodeException(409)
