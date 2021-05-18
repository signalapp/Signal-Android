package org.session.libsession.messaging.file_server

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.snode.OnionRequestAPI
import org.session.libsignal.utilities.HTTP
import org.session.libsignal.utilities.Base64
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log

object FileServerAPIV2 {

    private const val SERVER_PUBLIC_KEY = "7cb31905b55cd5580c686911debf672577b3fb0bff81df4ce2d5c4cb3a7aaa69"
    const val SERVER = "http://88.99.175.227"

    sealed class Error(message: String) : Exception(message) {
        object ParsingFailed : Error("Invalid response.")
        object InvalidURL : Error("Invalid URL.")
    }

    data class Request(
            val verb: HTTP.Verb,
            val endpoint: String,
            val queryParameters: Map<String, String> = mapOf(),
            val parameters: Any? = null,
            val headers: Map<String, String> = mapOf(),
            /**
         * Always `true` under normal circumstances. You might want to disable
         * this when running over Lokinet.
         */
        val useOnionRouting: Boolean = true
    )

    private fun createBody(parameters: Any?): RequestBody? {
        if (parameters == null) return null
        val parametersAsJSON = JsonUtil.toJson(parameters)
        return RequestBody.create(MediaType.get("application/json"), parametersAsJSON)
    }

    private fun send(request: Request): Promise<Map<*, *>, Exception> {
        val url = HttpUrl.parse(SERVER) ?: return Promise.ofFail(OpenGroupAPIV2.Error.InvalidURL)
        val urlBuilder = HttpUrl.Builder()
            .scheme(url.scheme())
            .host(url.host())
            .port(url.port())
            .addPathSegments(request.endpoint)
        if (request.verb == HTTP.Verb.GET) {
            for ((key, value) in request.queryParameters) {
                urlBuilder.addQueryParameter(key, value)
            }
        }
        val requestBuilder = okhttp3.Request.Builder()
            .url(urlBuilder.build())
            .headers(Headers.of(request.headers))
        when (request.verb) {
            HTTP.Verb.GET -> requestBuilder.get()
            HTTP.Verb.PUT -> requestBuilder.put(createBody(request.parameters)!!)
            HTTP.Verb.POST -> requestBuilder.post(createBody(request.parameters)!!)
            HTTP.Verb.DELETE -> requestBuilder.delete(createBody(request.parameters))
        }
        if (request.useOnionRouting) {
            return OnionRequestAPI.sendOnionRequest(requestBuilder.build(), SERVER, SERVER_PUBLIC_KEY).fail { e ->
                Log.e("Loki", "File server request failed.", e)
            }
        } else {
            return Promise.ofFail(IllegalStateException("It's currently not allowed to send non onion routed requests."))
        }
    }

    fun upload(file: ByteArray): Promise<Long, Exception> {
        val base64EncodedFile = Base64.encodeBytes(file)
        val parameters = mapOf( "file" to base64EncodedFile )
        val request = Request(verb = HTTP.Verb.POST, endpoint = "files", parameters = parameters)
        return send(request).map { json ->
            json["result"] as? Long ?: throw OpenGroupAPIV2.Error.ParsingFailed
        }
    }

    fun download(file: Long): Promise<ByteArray, Exception> {
        val request = Request(verb = HTTP.Verb.GET, endpoint = "files/$file")
        return send(request).map { json ->
            val base64EncodedFile = json["result"] as? String ?: throw Error.ParsingFailed
            Base64.decode(base64EncodedFile) ?: throw Error.ParsingFailed
        }
    }
}