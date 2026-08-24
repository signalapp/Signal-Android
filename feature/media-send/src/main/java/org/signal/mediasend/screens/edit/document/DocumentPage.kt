/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.mediasend.screens.edit.document

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.bytes
import org.signal.mediasend.EditorState
import org.signal.mediasend.R
import org.signal.core.ui.R as CoreUiR

/**
 * Longer file types are left off the document icon rather than overflowing it.
 */
private const val MAX_EXTENSION_LENGTH = 4

/**
 * Displays a document, which cannot be edited: its file type, name, and size.
 */
@Composable
fun DocumentPage(
  document: EditorState.Document,
  modifier: Modifier = Modifier
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = modifier
  ) {
    Box(contentAlignment = Alignment.Center) {
      Image(
        painter = painterResource(id = CoreUiR.drawable.ic_document_large),
        contentDescription = null,
        modifier = Modifier.defaultMinSize(minWidth = 70.dp, minHeight = 94.dp)
      )

      if (document.extension.length <= MAX_EXTENSION_LENGTH) {
        Text(
          text = document.extension,
          style = if (document.extension.length < MAX_EXTENSION_LENGTH) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
          color = colorResource(id = CoreUiR.color.signal_light_colorOnSurface)
        )
      }
    }

    Text(
      text = document.fileName ?: stringResource(R.string.DocumentPage__unnamed_file),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.MiddleEllipsis,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 20.dp, bottom = 4.dp)
        .padding(horizontal = 16.dp)
    )

    Text(
      text = document.fileSize.bytes.toUnitString(),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@DayNightPreviews
@Composable
private fun DocumentPagePreview() {
  Previews.Preview {
    DocumentPage(
      document = EditorState.Document(fileName = "thoughts.pdf", fileSize = 12_582, extension = "pdf"),
      modifier = Modifier.fillMaxSize()
    )
  }
}

@DayNightPreviews
@Composable
private fun DocumentPageUnnamedPreview() {
  Previews.Preview {
    DocumentPage(
      document = EditorState.Document(fileName = null, fileSize = 0, extension = ""),
      modifier = Modifier.fillMaxSize()
    )
  }
}
