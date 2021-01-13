package org.thoughtcrime.securesms.loki.utilities

import org.session.libsession.messaging.threads.recipients.Recipient

data class ProfilePictureModifiedEvent(val recipient: Recipient)