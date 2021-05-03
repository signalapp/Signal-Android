package org.session.libsession.utilities

import java.net.MalformedURLException
import java.net.URL

object OpenGroupUrlParser {

    // Error
    sealed class Error(val description: String) : Exception(description) {
        class MalformedUrl(message: String?) : Error("Malformed URL: $message.")
        object NoRoomSpecified : Error("No room specified in the URL.")
        object NoPublicKeySpecified : Error("No public key specified in the URL.")
        object WrongQuery : Error("'public_key' argument is missing.")
        object InvalidPublicKeyProvided : Error("Invalid public key provided.")
    }

    private const val pathPrefix = "/"
    private const val queryPrefix = "public_key="

    fun parseUrl(url: String): OpenGroupRoom {
        // If the URL is malformed, it will throw an exception
        val url = try { URL(url) } catch (e: MalformedURLException) { throw Error.MalformedUrl(e.message) }

        val host = url.host
        // Test if the room is specified in the URL
        val room = if (!url.path.isNullOrEmpty()) url.path.removePrefix(pathPrefix) else throw Error.NoRoomSpecified
        // Test if the query is specified in the URL
        val query = if (!url.query.isNullOrEmpty()) url.query else throw Error.NoPublicKeySpecified
        // Test if 'public_key' is specified in the URL
        val publicKey = if (query.contains(queryPrefix)) url.query.removePrefix(queryPrefix) else throw Error.WrongQuery
        // Public key must be 64 characters
        if (publicKey.length != 64) throw Error.InvalidPublicKeyProvided

        return OpenGroupRoom(host,room,publicKey)
    }
}

class OpenGroupRoom(val serverHost: String, val room: String, val serverPublicKey: String) {}
