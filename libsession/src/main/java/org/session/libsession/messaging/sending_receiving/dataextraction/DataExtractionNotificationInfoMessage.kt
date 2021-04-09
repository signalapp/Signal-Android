package org.session.libsession.messaging.sending_receiving.dataextraction

class DataExtractionNotificationInfoMessage {

    enum class Kind {
        SCREENSHOT,
        MEDIASAVED
    }

    var kind: Kind? = null

    constructor(kind: Kind?) {
        this.kind = kind
    }

}
