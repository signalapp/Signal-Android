/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import org.signal.core.util.logging.Log
import org.signal.registration.util.SensitiveLog

/**
 * Injection point for dependencies needed by this module.
 *
 * @param sensitiveLogger A logger for logging sensitive material. The intention is this would only be used in the demo app for testing + debugging, while
 *   the actual app would just pass null.
 */
class RegistrationDependencies(
  val networkController: NetworkController,
  val storageController: StorageController,
  val sensitiveLogger: Log.Logger?
) {
  companion object {
    lateinit var dependencies: RegistrationDependencies

    fun provide(registrationDependencies: RegistrationDependencies) {
      dependencies = registrationDependencies
      SensitiveLog.init(dependencies.sensitiveLogger)
    }

    fun get(): RegistrationDependencies = dependencies
  }
}
