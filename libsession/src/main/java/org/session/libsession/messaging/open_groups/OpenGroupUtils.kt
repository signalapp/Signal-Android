package org.session.libsession.messaging.open_groups

fun String.migrateLegacyServerUrl() = if (contains(OpenGroupApi.legacyServerIP)) {
    OpenGroupApi.defaultServer
} else if (contains(OpenGroupApi.httpDefaultServer)) {
    OpenGroupApi.defaultServer
} else {
    this
}