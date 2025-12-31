/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.sample.debug

import org.signal.registration.NetworkController
import org.signal.registration.NetworkController.RegistrationLockResponse
import org.signal.registration.NetworkController.RegistrationNetworkResult
import org.signal.registration.NetworkController.SessionMetadata
import org.signal.registration.NetworkController.SvrCredentials
import org.signal.registration.NetworkController.ThirdPartyServiceErrorResponse
import java.io.IOException
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Mock data factory for debug overrides.
 * Uses reflection to automatically discover NetworkController methods and their error types.
 * Only provides failure/error options - success cases are not overridable.
 */
object DebugNetworkMockData {

  // ============================================
  // Mock data for error type constructor params
  // ============================================

  private val mockSessionMetadata = SessionMetadata(
    id = "mock-session-id-12345",
    nextSms = System.currentTimeMillis() + 60_000,
    nextCall = System.currentTimeMillis() + 120_000,
    nextVerificationAttempt = System.currentTimeMillis() + 30_000,
    allowedToRequestCode = true,
    requestedInformation = emptyList(),
    verified = false
  )

  private val mockSvrCredentials = SvrCredentials(
    username = "mock-svr-username",
    password = "mock-svr-password"
  )

  private val mockRegistrationLockResponse = RegistrationLockResponse(
    timeRemaining = 86400000L,
    svr2Credentials = mockSvrCredentials
  )

  // ============================================
  // Data Classes
  // ============================================

  data class ResultOption(
    val name: String,
    val displayName: String,
    val createResult: () -> Any
  )

  data class MethodOverrideInfo(
    val methodName: String,
    val displayName: String,
    val options: List<ResultOption>
  )

  // ============================================
  // Reflection-based Method Discovery
  // ============================================

  val allMethods: List<MethodOverrideInfo> by lazy {
    discoverMethods()
  }

  private fun discoverMethods(): List<MethodOverrideInfo> {
    return NetworkController::class.memberFunctions
      .filter { it.returnType.isRegistrationNetworkResult() }
      .map { function ->
        val methodName = function.name
        val (_, errorType) = extractResultTypes(function.returnType)
        val options = buildOptionsForMethod(methodName, errorType)
        MethodOverrideInfo(methodName, methodName, options)
      }
      .sortedBy { it.methodName }
  }

  private fun KType.isRegistrationNetworkResult(): Boolean {
    return this.jvmErasure == RegistrationNetworkResult::class
  }

  private fun extractResultTypes(returnType: KType): Pair<KClass<*>?, KClass<*>?> {
    val typeArgs = returnType.arguments
    val successType = typeArgs.getOrNull(0)?.type?.jvmErasure
    val errorType = typeArgs.getOrNull(1)?.type?.jvmErasure
    return successType to errorType
  }

  private fun buildOptionsForMethod(
    methodName: String,
    errorType: KClass<*>?
  ): List<ResultOption> {
    val options = mutableListOf<ResultOption>()

    // Always add "Unset" first
    options.add(ResultOption("unset", "Unset") { Unit })

    // Add error options from sealed subclasses
    if (errorType != null) {
      val errorOptions = generateErrorOptions(errorType)
      options.addAll(errorOptions)
    }

    // Always add NetworkError and ApplicationError
    options.add(
      ResultOption("network_error", "NetworkError") {
        RegistrationNetworkResult.NetworkError(IOException("Mock network error"))
      }
    )
    options.add(
      ResultOption("application_error", "ApplicationError") {
        RegistrationNetworkResult.ApplicationError(RuntimeException("Mock application error"))
      }
    )

    return options
  }

  private fun generateErrorOptions(errorType: KClass<*>): List<ResultOption> {
    return errorType.sealedSubclasses.mapNotNull { subclass ->
      val instance = createMockInstance(subclass)
      if (instance != null) {
        val name = subclass.simpleName?.toSnakeCase() ?: return@mapNotNull null
        val displayName = subclass.simpleName ?: return@mapNotNull null
        ResultOption(name, displayName) {
          RegistrationNetworkResult.Failure(instance)
        }
      } else {
        null
      }
    }
  }

  private fun createMockInstance(klass: KClass<*>): Any? {
    // Try object instance first (for data objects)
    klass.objectInstance?.let { return it }

    // Try primary constructor
    val constructor = klass.primaryConstructor ?: return null

    return try {
      if (constructor.parameters.isEmpty()) {
        constructor.call()
      } else {
        val args = constructor.valueParameters.associateWith { param ->
          createMockValueForParameter(param)
        }
        constructor.callBy(args)
      }
    } catch (e: Exception) {
      null
    }
  }

  private fun createMockValueForParameter(param: KParameter): Any? {
    val type = param.type.jvmErasure

    return when {
      type == String::class -> "Mock ${param.name}"
      type == Int::class -> 3
      type == Long::class -> 60000L
      type == Boolean::class -> false
      type == Duration::class -> 60.seconds
      type == SessionMetadata::class -> mockSessionMetadata
      type == SvrCredentials::class -> mockSvrCredentials
      type == RegistrationLockResponse::class -> mockRegistrationLockResponse
      type == ThirdPartyServiceErrorResponse::class -> ThirdPartyServiceErrorResponse(
        reason = "Mock third party error",
        permanentFailure = false
      )
      param.type.isMarkedNullable -> null
      else -> null
    }
  }

  private fun String.toSnakeCase(): String {
    return this.fold(StringBuilder()) { acc, c ->
      if (c.isUpperCase() && acc.isNotEmpty()) {
        acc.append('_')
      }
      acc.append(c.lowercase())
    }.toString()
  }
}
