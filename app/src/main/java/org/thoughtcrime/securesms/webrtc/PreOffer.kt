package org.thoughtcrime.securesms.webrtc

import org.session.libsession.utilities.recipients.Recipient
import java.util.*

data class PreOffer(val callId: UUID, val recipient: Recipient)