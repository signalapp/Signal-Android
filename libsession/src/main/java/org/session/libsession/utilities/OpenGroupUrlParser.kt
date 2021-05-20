package org.session.libsession.utilities

import okhttp3.HttpUrl

object OpenGroupUrlParser {

    sealed class Error(val description: String) : Exception(description) {
        object MalformedURL : Error("Malformed URL.")
        object NoRoom : Error("No room specified in the URL.")
        object NoPublicKey : Error("No public key specified in the URL.")
        object InvalidPublicKey : Error("Invalid public key provided.")
    }

    private const val suffix = "/"
    private const val queryPrefix = "public_key"

    fun parseUrl(string: String): V2OpenGroupInfo {
        // URL has to start with 'http://'
        val urlWithPrefix = if (!string.startsWith("http")) "http://$string" else string
        // If the URL is malformed, throw an exception
        val url = HttpUrl.parse(urlWithPrefix) ?: throw Error.MalformedURL
        // Parse components
        val server = HttpUrl.Builder().scheme(url.scheme()).host(url.host()).port(url.port()).build().toString().removeSuffix(suffix)
        val room = url.pathSegments().firstOrNull { !it.isNullOrEmpty() } ?: throw Error.NoRoom
        val publicKey = url.queryParameter(queryPrefix) ?: throw Error.NoPublicKey
        if (publicKey.length != 64) throw Error.InvalidPublicKey
        // Return
        return V2OpenGroupInfo(server,room,publicKey)
    }

    fun trimQueryParameter(string: String): String {
        return string.substringBefore("?$queryPrefix")
    }
}

class V2OpenGroupInfo(val server: String, val room: String, val serverPublicKey: String)
