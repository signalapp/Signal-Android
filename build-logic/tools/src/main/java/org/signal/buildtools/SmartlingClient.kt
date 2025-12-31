package org.signal.buildtools

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Client for interacting with the Smartling translation API.
 */
class SmartlingClient(
  private val userIdentifier: String,
  private val userSecret: String,
  private val projectId: String
) {

  private val client = OkHttpClient()
  private val jsonParser = JsonSlurper()

  /**
   * Authenticates with Smartling and returns an access token.
   */
  @Suppress("UNCHECKED_CAST")
  fun authenticate(): String {
    val jsonBody = JsonBuilder(
      mapOf(
        "userIdentifier" to userIdentifier,
        "userSecret" to userSecret
      )
    ).toString()

    val request = Request.Builder()
      .url("https://api.smartling.com/auth-api/v2/authenticate")
      .post(jsonBody.toRequestBody("application/json".toMediaType()))
      .build()

    val response = client.newCall(request).execute()
    val responseBody = response.body.string()

    if (!response.isSuccessful) {
      throw SmartlingException("Authentication failed with code ${response.code}: $responseBody")
    }

    val json = jsonParser.parseText(responseBody) as Map<String, Any>
    val responseObj = json["response"] as? Map<String, Any>
    val data = responseObj?.get("data") as? Map<String, Any>
    val accessToken = data?.get("accessToken") as? String

    return accessToken
      ?: throw SmartlingException("Failed to extract access token from response: $responseBody")
  }

  /**
   * Uploads a file to Smartling for translation.
   */
  fun uploadFile(authToken: String, file: File, fileUri: String): String {
    val requestBody = MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("file", file.name, file.asRequestBody("application/xml".toMediaType()))
      .addFormDataPart("fileUri", fileUri)
      .addFormDataPart("fileType", "android")
      .build()

    val request = Request.Builder()
      .url("https://api.smartling.com/files-api/v2/projects/$projectId/file")
      .header("Authorization", "Bearer $authToken")
      .post(requestBody)
      .build()

    val response = client.newCall(request).execute()
    val responseBody = response.body.string()

    if (!response.isSuccessful) {
      throw SmartlingException("Upload failed with code ${response.code}: $responseBody")
    }

    return responseBody
  }

  class SmartlingException(message: String) : RuntimeException(message)
}
