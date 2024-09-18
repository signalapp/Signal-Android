package org.signal.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.theme.SignalTheme

@OptIn(ExperimentalMaterial3Api::class)
object Scaffolds {
  /**
   * @param titleContent The title area content. First parameter is the contentOffset.
   */
  @Composable
  fun Settings(
    title: String,
    onNavigationClick: () -> Unit,
    navigationIconPainter: Painter,
    modifier: Modifier = Modifier,
    navigationContentDescription: String? = null,
    titleContent: @Composable (Float, String) -> Unit = { _, title ->
      Text(text = title, style = MaterialTheme.typography.titleLarge)
    },
    snackbarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
  ) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
      snackbarHost = snackbarHost,
      topBar = {
        DefaultTopAppBar(
          title = title,
          titleContent = titleContent,
          onNavigationClick = onNavigationClick,
          navigationIconPainter = navigationIconPainter,
          navigationContentDescription = navigationContentDescription,
          actions = actions,
          scrollBehavior = scrollBehavior
        )
      },
      modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      content = content
    )
  }

  @Composable
  fun DefaultTopAppBar(
    title: String,
    titleContent: @Composable (Float, String) -> Unit,
    onNavigationClick: () -> Unit,
    navigationIconPainter: Painter,
    navigationContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  ) {
    TopAppBar(
      title = {
        titleContent(scrollBehavior.state.contentOffset, title)
      },
      navigationIcon = {
        IconButton(
          onClick = onNavigationClick,
          Modifier.padding(end = 16.dp)
        ) {
          Icon(
            painter = navigationIconPainter,
            contentDescription = navigationContentDescription
          )
        }
      },
      scrollBehavior = scrollBehavior,
      colors = TopAppBarDefaults.topAppBarColors(
        scrolledContainerColor = SignalTheme.colors.colorSurface2
      ),
      actions = actions
    )
  }
}

@Preview
@Composable
private fun SettingsScaffoldPreview() {
  SignalTheme(isDarkMode = false) {
    Scaffolds.Settings(
      "Settings Scaffold",
      onNavigationClick = {},
      navigationIconPainter = ColorPainter(Color.Black),
      actions = {
        IconButton(onClick = {}) {
          Icon(Icons.Default.Settings, contentDescription = null)
        }
      }
    ) { paddingValues ->
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .padding(paddingValues)
          .fillMaxSize()
      ) {
        Text("Content")
      }
    }
  }
}
