/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.database

import android.database.Cursor
import org.signal.core.util.Base64
import org.signal.core.util.requireBlob
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.spinner.ColumnTransformer

object KyberKeyTransformer : ColumnTransformer {
  override fun matches(tableName: String?, columnName: String): Boolean {
    return tableName == KyberPreKeyTable.TABLE_NAME && columnName == KyberPreKeyTable.SERIALIZED
  }

  override fun transform(tableName: String?, columnName: String, cursor: Cursor): String? {
    val record = KyberPreKeyRecord(cursor.requireBlob(columnName))
    return "ID: ${record.id}\nTimestamp: ${record.timestamp}\nPublicKey: ${Base64.encodeWithoutPadding(record.keyPair.publicKey.serialize())}\nPrivateKey: ${Base64.encodeWithoutPadding(record.keyPair.secretKey.serialize())}\nSignature: ${Base64.encodeWithoutPadding(record.signature)}"
  }
}
