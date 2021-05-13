package org.session.libsession.utilities

import okhttp3.HttpUrl

object OpenGroupUrlParser {

    // Error
    sealed class Error(val description: String) : Exception(description) {
        class MalformedUrl() : Error("Malformed URL.")
        object NoRoomSpecified : Error("No room specified in the URL.")
        object NoPublicKeySpecified : Error("No public key specified in the URL.")
        object InvalidPublicKeyProvided : Error("Invalid public key provided.")
    }

    private const val suffix = "/"
    private const val queryPrefix = "public_key"

    fun parseUrl(stringUrl: String): OpenGroupRoom {
        // Url have to start with 'http://'
        val url = if (!stringUrl.startsWith("http")) "http://$stringUrl" else stringUrl
        // If the URL is malformed, it will throw an exception
        val httpUrl = HttpUrl.parse(url) ?: throw Error.MalformedUrl()

        val server = HttpUrl.Builder().scheme(httpUrl.scheme()).host(httpUrl.host()).port(httpUrl.port()).build().toString().removeSuffix(suffix)
        // Test if the room is specified in the URL
        val room = httpUrl.pathSegments().firstOrNull { !it.isNullOrEmpty() } ?: throw Error.NoRoomSpecified
        // Test if the query is specified in the URL
        val publicKey = httpUrl.queryParameter(queryPrefix) ?: throw Error.NoPublicKeySpecified
        // Public key must be 64 characters
        if (publicKey.length != 64) throw Error.InvalidPublicKeyProvided

        return OpenGroupRoom(server,room,publicKey)
    }

    fun trimParameter(stringUrl: String): String {
        return stringUrl.substringBefore("?$queryPrefix")
    }
}

class OpenGroupRoom(val server: String, val room: String, val serverPublicKey: String) {}
