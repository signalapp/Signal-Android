/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import android.content.Context
import org.signal.core.util.logging.Log
import org.signal.registration.util.SensitiveLog

/**
 * Injection point for dependencies needed by this module.
 *
 * @param isPhoneNumberlessRegistrationAvailable Whether the in-progress phone-numberless registration flow (registering
 *   with a purchased Signal Login instead of a phone number) is offered. The screens are always present in the
 *   navigation graph; this only gates them off at runtime while the flow is unfinished. Also gates linking to an
 *   account that has no phone number.
 * @param sensitiveLogger A logger for logging sensitive material. The intention is this would only be used in the demo app for testing + debugging, while
 *   the actual app would just pass null.
 * @param debugLogCallback Callback to launch the debug log viewer. The actual app provides the real implementation.
 * @param proxyConfigCallback Callback to launch the proxy configuration settings. The actual app provides the real implementation.
 * @param contactSupportController Lets the user contact support, optionally attaching a debug log. The actual app provides the real implementation.
 */
class RegistrationDependencies(
  val networkController: NetworkController,
  val storageController: StorageController,
  val isLinkAndSyncAvailable: Boolean,
  val isPhoneNumberlessRegistrationAvailable: Boolean,
  val sensitiveLogger: Log.Logger?,
  val debugLogCallback: ((Context) -> Unit)?,
  val proxyConfigCallback: ((Context) -> Unit)?,
  val contactSupportController: ContactSupportController?
) {
  companion object {
    lateinit var dependencies: RegistrationDependencies

    @JvmStatic
    fun provide(registrationDependencies: RegistrationDependencies) {
      dependencies = registrationDependencies
      SensitiveLog.init(dependencies.sensitiveLogger)
    }

    fun get(): RegistrationDependencies = dependencies
  }
}
