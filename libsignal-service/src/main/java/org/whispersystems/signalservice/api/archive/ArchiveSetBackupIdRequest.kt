/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.api.archive

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.signal.core.util.Base64
import org.signal.libsignal.zkgroup.backups.BackupAuthCredentialRequest

/**
 * Represents the request body when setting the archive backupId.
 */
class ArchiveSetBackupIdRequest(
  @JsonProperty
  @JsonSerialize(using = BackupAuthCredentialRequestSerializer::class)
  val messagesBackupAuthCredentialRequest: BackupAuthCredentialRequest,
  @JsonProperty
  @JsonSerialize(using = BackupAuthCredentialRequestSerializer::class)
  val mediaBackupAuthCredentialRequest: BackupAuthCredentialRequest
) {
  class BackupAuthCredentialRequestSerializer : JsonSerializer<BackupAuthCredentialRequest>() {
    override fun serialize(value: BackupAuthCredentialRequest, gen: JsonGenerator, serializers: SerializerProvider) {
      gen.writeString(Base64.encodeWithPadding(value.serialize()))
    }
  }
}
