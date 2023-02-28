package org.signal.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
object Scaffolds {
  @Composable
  fun Settings(
    title: String,
    onNavigationClick: () -> Unit,
    painter: Painter,
    modifier: Modifier = Modifier,
    navigationContentDescription: String? = null,
    content: @Composable (PaddingValues) -> Unit
  ) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = title,
              style = MaterialTheme.typography.titleLarge
            )
          },
          navigationIcon = {
            IconButton(
              onClick = onNavigationClick,
              Modifier.padding(end = 16.dp)
            ) {
              Icon(
                painter = painter,
                contentDescription = navigationContentDescription
              )
            }
          }
        )
      },
      modifier = modifier,
      content = content
    )
  }
}
