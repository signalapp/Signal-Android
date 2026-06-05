package org.whispersystems.signalservice.api.push.exceptions

import org.signal.network.exceptions.NonSuccessfulResponseCodeException

/**
 * Exception representing that the submitted information was not accepted (e.g. the push challenge token or captcha did not match)
 */
class TokenNotAcceptedException : NonSuccessfulResponseCodeException(403)
