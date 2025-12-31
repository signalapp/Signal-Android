/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

/**
 * Injection point for dependencies needed by this module.
 */
class RegistrationDependencies(
  val networkController: NetworkController,
  val storageController: StorageController
) {
  companion object {
    lateinit var dependencies: RegistrationDependencies

    fun provide(registrationDependencies: RegistrationDependencies) {
      dependencies = registrationDependencies
    }

    fun get(): RegistrationDependencies = dependencies
  }
}
