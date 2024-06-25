/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.migrations

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.signal.contacts.SystemContactsRepository
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.SyncSystemContactLinksJob

/**
 * This migration job is responsible for rebuilding the contact links for all contacts in the system. Contact links
 * refers to the raw contact table entries that are used to place links into the system contacts app to message,
 * voice, and video call a given contact on Signal. This job is necessary to ensure that all contacts have the correct
 * links in the system contacts app. At the time of writing, this job is adding the video call link and changing the
 * text of the voice call link to "Signal Voice Call" instead of "Signal Call". This job could be reused in the future
 * if other links are added or changed.
 */
internal class ContactLinkRebuildMigrationJob(parameters: Parameters = Parameters.Builder().build()) : MigrationJob(parameters) {
  companion object {
    private val TAG = Log.tag(ContactLinkRebuildMigrationJob::class.java)
    const val KEY = "ContactLinkRebuildMigrationJob"
  }

  override fun getFactoryKey(): String = KEY

  override fun isUiBlocking(): Boolean = false

  override fun performMigration() {
    val account = SystemContactsRepository.getOrCreateSystemAccount(context, BuildConfig.APPLICATION_ID, context.getString(R.string.app_name))
    if (account == null) {
      Log.w(TAG, "Failed to create an account!")
      return
    }

    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
      Log.w(TAG, "We don't have the right permissions to perform this migration!")
      return
    }

    SystemContactsRepository.addMessageAndCallLinksToContacts(
      context = context,
      config = SyncSystemContactLinksJob.buildContactLinkConfiguration(context, account),
      targetE164s = emptySet(),
      removeIfMissing = true
    )

    AppDependencies.jobManager.add(SyncSystemContactLinksJob())
  }

  override fun shouldRetry(e: Exception): Boolean = false

  class Factory : Job.Factory<ContactLinkRebuildMigrationJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): ContactLinkRebuildMigrationJob {
      return ContactLinkRebuildMigrationJob(parameters)
    }
  }
}
