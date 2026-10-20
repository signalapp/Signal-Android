/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.ui.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.signal.core.ui.R
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.ui.detailPaneMaxContentWidth
import org.signal.core.ui.horizontalPartitionDefaultSpacerSize
import org.signal.core.ui.listPaneDefaultPreferredWidth
import org.signal.core.ui.rememberIsSplitPane

@OptIn(ExperimentalMaterial3Api::class)
object Scaffolds {

  /**
   * Simple scaffold with a TopAppBar containing a navigation icon and optional title.
   *
   * @param onNavigationClick Callback when navigation icon is clicked.
   * @param navigationIconRes Drawable resource for the navigation icon.
   * @param navigationContentDescription Content description for the navigation icon.
   * @param title Optional title text for the app bar.
   * @param modifier Modifier for the scaffold.
   * @param content Content to display in the scaffold.
   */
  @Composable
  fun Default(
    onNavigationClick: () -> Unit,
    @DrawableRes navigationIconRes: Int,
    navigationContentDescription: String? = null,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
  ) {
    Scaffold(
      modifier = modifier,
      topBar = {
        TopAppBar(
          title = {
            if (title != null) {
              Text(text = title)
            }
          },
          navigationIcon = {
            IconButton(onClick = onNavigationClick) {
              Icon(
                painter = painterResource(navigationIconRes),
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = navigationContentDescription
              )
            }
          }
        )
      },
      content = content
    )
  }

  /**
   * Settings scaffold that takes an icon as an ImageVector.
   *
   * @param titleContent The title area content. First parameter is the contentOffset.
   */
  @Composable
  fun Settings(
    title: String,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    titleContent: @Composable (Float, String) -> Unit = { _, title ->
      Text(text = title, style = MaterialTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto))
    },
    snackbarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
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
          navigationIcon = navigationIcon,
          navigationContentDescription = navigationContentDescription,
          actions = actions,
          scrollBehavior = scrollBehavior
        )
      },
      bottomBar = bottomBar,
      modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
      content = content
    )
  }

  /**
   * Top app bar that takes an ImageVector navigation icon.
   */
  @Composable
  fun DefaultTopAppBar(
    title: String,
    titleContent: @Composable (Float, String) -> Unit,
    onNavigationClick: () -> Unit,
    navigationIcon: ImageVector?,
    navigationContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  ) {
    DefaultTopAppBar(
      title = title,
      titleContent = titleContent,
      navigationIconContent = {
        if (navigationIcon != null) {
          IconButton(
            onClick = onNavigationClick,
            Modifier.padding(end = 16.dp)
          ) {
            Icon(
              imageVector = navigationIcon,
              contentDescription = navigationContentDescription ?: stringResource(R.string.DefaultTopAppBar__navigate_up_content_description)
            )
          }
        }
      },
      actions = actions,
      scrollBehavior = scrollBehavior
    )
  }

  /**
   * Top app bar that takes composable navigation icon.
   */
  @Composable
  fun DefaultTopAppBar(
    title: String,
    titleContent: @Composable (Float, String) -> Unit,
    navigationIconContent: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  ) {
    TopAppBar(
      title = { titleContent(scrollBehavior.state.contentOffset, title) },
      navigationIcon = navigationIconContent,
      scrollBehavior = scrollBehavior,
      colors = TopAppBarDefaults.topAppBarColors(
        scrolledContainerColor = SignalTheme.colors.colorSurface2
      ),
      actions = actions
    )
  }

  /**
   * Scaffold with a top app bar whose title moves into a pane beside [primaryContent] on split-pane windows (tablets,
   * foldables, landscape), with [primaryContent] capped at [detailPaneMaxContentWidth] there.
   */
  @Composable
  fun TitlePane(
    title: String,
    onNavigateUpClick: () -> Unit,
    topAppBarActions: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState,
    primaryContent: @Composable () -> Unit,
    floatingActionButton: (@Composable () -> Unit)? = null
  ) {
    val isSplitPane = LocalResources.current.rememberIsSplitPane()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.systemBars,
      topBar = {
        DefaultTopAppBar(
          title = if (!isSplitPane) title else "",
          titleContent = { _, titleText -> Text(text = titleText, style = MaterialTheme.typography.titleLarge) },
          navigationIcon = SignalIcons.ArrowStart.imageVector,
          navigationContentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description),
          onNavigationClick = onNavigateUpClick,
          actions = { topAppBarActions() }
        )
      },
      snackbarHost = {
        SnackbarHost(snackbarHostState)
      }
    ) { paddingValues ->
      if (isSplitPane) {
        TitlePaneSplitLayout(
          title = title,
          windowSizeClass = windowSizeClass,
          modifier = Modifier.padding(paddingValues)
        ) {
          Box(modifier = Modifier.widthIn(max = windowSizeClass.detailPaneMaxContentWidth)) {
            primaryContent()
            FloatingActionButtonContainer(floatingActionButton)
          }
        }
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
          primaryContent()
          FloatingActionButtonContainer(floatingActionButton)
        }
      }
    }
  }
}

/**
 * Places the screen title beside [content], capping the title pane at [listPaneDefaultPreferredWidth] and splitting
 * the available width evenly below twice that.
 */
@Composable
private fun TitlePaneSplitLayout(
  title: String,
  windowSizeClass: WindowSizeClass,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val spacerWidth = windowSizeClass.horizontalPartitionDefaultSpacerSize

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val titlePaneWidth = ((maxWidth - spacerWidth) / 2).coerceAtMost(windowSizeClass.listPaneDefaultPreferredWidth)

    Row(modifier = Modifier.fillMaxSize()) {
      ScreenTitlePane(
        title = title,
        windowSizeClass = windowSizeClass,
        modifier = Modifier
          .width(titlePaneWidth)
          .fillMaxHeight()
      )

      Spacer(modifier = Modifier.width(spacerWidth))

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
      ) {
        content()
      }
    }
  }
}

/**
 * Displays the screen title for split-pane UIs on tablets and foldable devices.
 */
@Composable
private fun ScreenTitlePane(
  title: String,
  windowSizeClass: WindowSizeClass,
  modifier: Modifier = Modifier
) {
  Text(
    text = title,
    style = MaterialTheme.typography.headlineLarge,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = modifier
      .padding(
        start = if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) 80.dp else 20.dp,
        end = 20.dp,
        bottom = 12.dp
      )
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.FloatingActionButtonContainer(
  button: (@Composable () -> Unit)?
) {
  if (button != null) {
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .imePadding()
        .padding(
          start = 16.dp,
          end = 16.dp,
          bottom = if (WindowInsets.isImeVisible) 0.dp else 16.dp
        )
    ) {
      button()
    }
  }
}

@BreakpointPreviews
@Composable
private fun TitlePaneScaffoldPreview() {
  Previews.Preview {
    Scaffolds.TitlePane(
      title = "Screen Title",
      onNavigateUpClick = {},
      topAppBarActions = {},
      snackbarHostState = SnackbarHostState(),
      primaryContent = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Gray),
          contentAlignment = Alignment.Center
        ) {
          Text(text = "primaryContent")
        }
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun SettingsScaffoldPreview() {
  Previews.Preview {
    val vector = remember {
      ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
      ).build()
    }

    Scaffolds.Settings(
      "Settings Scaffold",
      onNavigationClick = {},
      navigationIcon = vector,
      actions = {
        IconButton(onClick = {}) {
          Icon(painterResource(android.R.drawable.ic_menu_camera), contentDescription = null)
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

@DayNightPreviews
@Composable
private fun SettingsScaffoldNoNavIconPreview() {
  Previews.Preview {
    Scaffolds.Settings(
      "Settings Scaffold",
      onNavigationClick = {},
      actions = {
        IconButton(onClick = {}) {
          Icon(painterResource(android.R.drawable.ic_menu_camera), contentDescription = null)
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
