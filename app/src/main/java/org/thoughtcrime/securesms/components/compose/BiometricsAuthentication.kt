/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.compose

import android.content.Context
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.findFragment
import androidx.lifecycle.LifecycleOwner
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.BiometricDeviceLockContract
import org.thoughtcrime.securesms.DevicePinAuthEducationSheet

@Stable
class BiometricsAuthentication internal constructor(
  private val authenticateImpl: (onAuthenticated: () -> Unit) -> Unit,
  private val cancelImpl: () -> Unit
) {
  fun withBiometricsAuthentication(onAuthenticated: () -> Unit) {
    authenticateImpl(onAuthenticated)
  }

  fun cancelAuthentication() {
    cancelImpl()
  }
}

/**
 * A lightweight helper for prompting the user for biometric/device-credential authentication from Compose.
 *
 * Intended usage:
 *
 * - `val biometrics = rememberBiometricsAuthentication(...)`
 * - `onClick = { biometrics.withBiometricsAuthentication { performAction() } }`
 */
@Composable
fun rememberBiometricsAuthentication(
  promptTitle: String? = null,
  educationSheetMessage: String? = null,
  onAuthenticationFailed: (() -> Unit)? = null
): BiometricsAuthentication {
  if (LocalInspectionMode.current) {
    return remember {
      BiometricsAuthentication(
        authenticateImpl = { it.invoke() },
        cancelImpl = {}
      )
    }
  }

  val context = LocalContext.current
  val view = LocalView.current
  val host = remember(view, context) { resolveHost(context, view) }

  if (host == null) {
    error("FragmentActivity is required to use rememberBiometricsAuthentication()")
  }

  val resolvedTitle = promptTitle?.takeIf { it.isNotBlank() }
  check(resolvedTitle != null) {
    "promptTitle must be non-blank when using rememberBiometricsAuthentication()"
  }

  // Fallback to device credential confirmation when BiometricPrompt isn't available.
  var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
  val deviceCredentialLauncher = rememberLauncherForActivityResult(BiometricDeviceLockContract()) { result ->
    if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
      pendingAction?.invoke()
      pendingAction = null
    }
  }

  val biometricManager = remember(context) { BiometricManager.from(context) }

  val biometricPrompt = remember(host.activity, host.fragment, context) {
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationFailed() {
        onAuthenticationFailed?.invoke()
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        pendingAction?.invoke()
        pendingAction = null
      }

      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        onAuthenticationFailed?.invoke()
      }
    }

    host.fragment?.let { fragment ->
      BiometricPrompt(fragment, executor, callback)
    } ?: BiometricPrompt(host.activity, executor, callback)
  }

  val biometricDeviceAuthentication = remember(biometricManager, biometricPrompt) {
    // Prompt info is updated below on each call to `withBiometricsAuthentication`.
    val initialPromptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(" ")
      .build()
    BiometricDeviceAuthentication(biometricManager, biometricPrompt, initialPromptInfo)
  }

  val shouldShowEducationSheetForFlow = biometricDeviceAuthentication.shouldShowEducationSheet(context)

  fun authenticateOrFallback(promptTitleForPrompt: String) {
    val action = pendingAction ?: return
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(promptTitleForPrompt)
      .build()
    biometricDeviceAuthentication.updatePromptInfo(promptInfo)

    if (!biometricDeviceAuthentication.authenticate(context, true) {
        deviceCredentialLauncher.launch(promptTitleForPrompt)
      }
    ) {
      // If we cannot authenticate at all, preserve existing call-site behavior and just proceed.
      action.invoke()
      pendingAction = null
    }
  }

  // If the composable that owns this helper leaves composition (navigation, conditional UI, etc.),
  // ensure we don't keep an auth prompt open or deliver a stale callback later.
  DisposableEffect(biometricDeviceAuthentication) {
    onDispose {
      biometricDeviceAuthentication.cancelAuthentication()
      pendingAction = null
    }
  }

  return BiometricsAuthentication(
    authenticateImpl = { onAuthenticated ->
      pendingAction = onAuthenticated

      if (shouldShowEducationSheetForFlow && !educationSheetMessage.isNullOrBlank()) {
        DevicePinAuthEducationSheet.show(educationSheetMessage, host.fragmentManager)
        host.fragmentManager.setFragmentResultListener(
          DevicePinAuthEducationSheet.REQUEST_KEY,
          host.resultLifecycleOwner
        ) { _, _ ->
          authenticateOrFallback(resolvedTitle)
        }
      } else {
        authenticateOrFallback(resolvedTitle)
      }
    },
    cancelImpl = biometricDeviceAuthentication::cancelAuthentication
  )
}

@Stable
private data class Host(
  val activity: FragmentActivity,
  val fragment: Fragment?,
  val fragmentManager: FragmentManager,
  val resultLifecycleOwner: LifecycleOwner
)

private fun resolveHost(context: Context, view: View): Host? {
  val fragment = runCatching { view.findFragment<Fragment>() }.getOrNull()
  if (fragment != null) {
    return Host(
      activity = fragment.requireActivity(),
      fragment = fragment,
      fragmentManager = fragment.parentFragmentManager,
      resultLifecycleOwner = fragment.viewLifecycleOwner
    )
  }

  val activity = context as? FragmentActivity ?: return null
  return Host(
    activity = activity,
    fragment = null,
    fragmentManager = activity.supportFragmentManager,
    resultLifecycleOwner = activity
  )
}
