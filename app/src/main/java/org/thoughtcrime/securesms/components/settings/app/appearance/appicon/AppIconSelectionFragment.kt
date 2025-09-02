/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.appearance.appicon

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.fragment.findNavController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appearance.appicon.util.AppIconPreset
import org.thoughtcrime.securesms.components.settings.app.appearance.appicon.util.AppIconUtility
import org.thoughtcrime.securesms.compose.ComposeFragment
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class AppIconSelectionFragment : ComposeFragment() {
  private lateinit var appIconUtility: AppIconUtility

  override fun onAttach(context: Context) {
    super.onAttach(context)
    appIconUtility = AppIconUtility(context)
  }

  @Composable
  override fun FragmentContent() {
    Scaffolds.Settings(
      title = stringResource(id = R.string.preferences__app_icon),
      onNavigationClick = {
        findNavController().popBackStack()
      },
      navigationIcon = ImageVector.vectorResource(id = R.drawable.symbol_arrow_start_24),
      navigationContentDescription = stringResource(id = R.string.Material3SearchToolbar__close)
    ) { contentPadding: PaddingValues ->
      IconSelectionScreen(appIconUtility.currentAppIcon, ::updateAppIcon, ::openLearnMore, Modifier.padding(contentPadding))
    }
  }

  private fun updateAppIcon(preset: AppIconPreset) {
    if (!appIconUtility.isCurrentlySelected(preset)) {
      appIconUtility.setNewAppIcon(preset)
    }
  }

  private fun openLearnMore() {
    findNavController().safeNavigate(R.id.action_appIconSelectionFragment_to_appIconTutorialFragment)
  }

  companion object {
    val TAG = Log.tag(AppIconSelectionFragment::class.java)
  }
}

private const val LEARN_MORE_TAG = "learn_more"
private const val URL_TAG = "URL"
private const val COLUMN_COUNT = 4

/**
 * Screen allowing the user to view all the possible icon and select a new one to use.
 */
@Composable
fun IconSelectionScreen(activeIcon: AppIconPreset, onItemConfirmed: (AppIconPreset) -> Unit, onWarningClick: () -> Unit, modifier: Modifier = Modifier) {
  var showDialog: Boolean by remember { mutableStateOf(false) }
  var pendingIcon: AppIconPreset by remember {
    mutableStateOf(activeIcon)
  }

  if (showDialog) {
    ChangeIconDialog(
      pendingIcon = pendingIcon,
      onConfirm = {
        onItemConfirmed(pendingIcon)
        showDialog = false
      },
      onDismiss = {
        pendingIcon = activeIcon
        showDialog = false
      }
    )
  }

  Column(modifier = modifier.verticalScroll(rememberScrollState())) {
    Spacer(modifier = Modifier.size(12.dp))
    CaveatWarning(
      onClick = onWarningClick,
      modifier = Modifier.padding(horizontal = 24.dp)
    )
    Spacer(modifier = Modifier.size(12.dp))
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      enumValues<AppIconPreset>().toList().chunked(COLUMN_COUNT).map { it.toImmutableList() }.forEach { items ->
        IconRow(
          presets = items,
          isSelected = { it == pendingIcon },
          onItemClick = {
            pendingIcon = it
            showDialog = true
          }
        )
      }
    }
  }
}

@Composable
fun ChangeIconDialog(pendingIcon: AppIconPreset, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  AlertDialog(
    modifier = modifier,
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = onConfirm
      ) {
        Text(text = stringResource(id = R.string.preferences__app_icon_dialog_ok))
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss
      ) {
        Text(text = stringResource(id = R.string.preferences__app_icon_dialog_cancel))
      }
    },
    icon = {
      AppIcon(preset = pendingIcon, isSelected = false, onClick = {})
    },
    title = {
      Text(
        text = stringResource(id = R.string.preferences__app_icon_dialog_title, stringResource(id = pendingIcon.labelResId)),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineSmall
      )
    },
    text = {
      Text(
        text = stringResource(id = R.string.preferences__app_icon_dialog_description),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium
      )
    }
  )
}

/**
 * Composable rendering the one row of icons that the user may choose from.
 */
@Composable
fun IconRow(presets: ImmutableList<AppIconPreset>, isSelected: (AppIconPreset) -> Boolean, onItemClick: (AppIconPreset) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth()) {
    presets.forEach { preset ->
      val currentlySelected = isSelected(preset)
      IconGridElement(
        preset = preset,
        isSelected = currentlySelected,
        onClickHandler = {
          if (!currentlySelected) {
            onItemClick(preset)
          }
        },
        modifier = Modifier
          .padding(vertical = 18.dp)
          .weight(1f)
      )
    }
  }
}

/**
 * Composable rendering an individual icon inside that grid, including the black border of the selected icon.
 */
@Composable
fun IconGridElement(preset: AppIconPreset, isSelected: Boolean, onClickHandler: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val boxModifier = Modifier.size(64.dp)
    Box(
      modifier = if (isSelected) boxModifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else boxModifier
    ) {
      AppIcon(preset = preset, isSelected = isSelected, onClickHandler, modifier = Modifier.align(Alignment.Center))
    }
    Spacer(modifier = Modifier.size(8.dp))
    Text(
      text = stringResource(id = preset.labelResId),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/**
 * Composable rendering the icon and optionally a border, to indicate selection.
 */
@Composable
fun AppIcon(preset: AppIconPreset, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val bitmapSize by animateFloatAsState(
    targetValue = if (isSelected) 48f else 64f,
    label = "Icon Size",
    animationSpec = tween(durationMillis = 150, easing = CubicBezierEasing(0.17f, 0.17f, 0f, 1f))
  )
  val imageModifier = modifier
    .size(bitmapSize.dp)
    .graphicsLayer(
      shape = CircleShape,
      shadowElevation = if (isSelected) 4f else 8f,
      clip = true
    )
    .clickable(onClick = onClick)
  Image(
    painterResource(id = preset.iconPreviewResId),
    contentDescription = stringResource(id = preset.labelResId),
    modifier = imageModifier
  )
}

/**
 * A clickable "learn more" block of text.
 */
@Composable
fun CaveatWarning(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val learnMoreString = stringResource(R.string.preferences__app_icon_learn_more)
  val completeString = stringResource(R.string.preferences__app_icon_warning_learn_more)
  val learnMoreStartIndex = completeString.indexOf(learnMoreString).coerceAtLeast(0)
  val learnMoreEndIndex = learnMoreStartIndex + learnMoreString.length
  val doesStringEndWithLearnMore = learnMoreEndIndex >= completeString.lastIndex
  val annotatedText = buildAnnotatedString {
    withStyle(
      style = SpanStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    ) {
      append(completeString.substring(0, learnMoreStartIndex))
    }
    pushStringAnnotation(
      tag = URL_TAG,
      annotation = LEARN_MORE_TAG
    )
    withStyle(
      style = SpanStyle(
        color = MaterialTheme.colorScheme.primary
      )
    ) {
      append(learnMoreString)
    }
    pop()
    if (!doesStringEndWithLearnMore) {
      append(completeString.substring(learnMoreEndIndex, completeString.lastIndex))
    }
  }
  ClickableText(
    text = annotatedText,
    onClick = { onClick() },
    style = MaterialTheme.typography.bodyMedium,
    modifier = modifier
  )
}

@Preview(name = "Light Theme")
@Composable
private fun MainScreenPreviewLight() {
  SignalTheme(isDarkMode = false) {
    Surface {
      IconSelectionScreen(AppIconPreset.DEFAULT, onItemConfirmed = {}, onWarningClick = {})
    }
  }
}

@Preview(name = "Dark Theme")
@Composable
private fun MainScreenPreviewDark() {
  SignalTheme(isDarkMode = true) {
    Surface {
      IconSelectionScreen(AppIconPreset.DEFAULT, onItemConfirmed = {}, onWarningClick = {})
    }
  }
}
