package org.signal.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.signal.core.ui.theme.SignalTheme

object Buttons {

  private val largeButtonContentPadding = PaddingValues(
    horizontal = 24.dp,
    vertical = 12.dp
  )

  private val mediumButtonContentPadding = PaddingValues(
    horizontal = 24.dp,
    vertical = 10.dp
  )

  private val smallButtonContentPadding = PaddingValues(
    horizontal = 16.dp,
    vertical = 8.dp
  )

  @Composable
  fun LargePrimary(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = largeButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
  ) {
    Button(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      shape = shape,
      colors = colors,
      elevation = elevation,
      border = border,
      contentPadding = contentPadding,
      interactionSource = interactionSource,
      content = content
    )
  }

  @Composable
  fun LargeTonal(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = largeButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
  ) {
    FilledTonalButton(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      shape = shape,
      colors = colors,
      elevation = elevation,
      border = border,
      contentPadding = contentPadding,
      interactionSource = interactionSource,
      content = content
    )
  }

  @Composable
  fun MediumTonal(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = mediumButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
  ) {
    FilledTonalButton(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      shape = shape,
      colors = colors,
      elevation = elevation,
      border = border,
      contentPadding = contentPadding,
      interactionSource = interactionSource,
      content = content
    )
  }

  @Composable
  fun Small(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(
      containerColor = SignalTheme.colors.colorSurface2,
      contentColor = MaterialTheme.colorScheme.onSurface
    ),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = smallButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
  ) {
    Button(
      onClick = onClick,
      modifier = modifier.heightIn(min = 32.dp),
      enabled = enabled,
      shape = shape,
      colors = colors,
      elevation = elevation,
      border = border,
      contentPadding = contentPadding,
      interactionSource = interactionSource,
      content = {
        ProvideTextStyle(value = MaterialTheme.typography.labelMedium) {
          content()
        }
      }
    )
  }

  @Composable
  fun ActionButton(
    onClick: () -> Unit,
    @DrawableRes iconResId: Int,
    @StringRes labelResId: Int,
    modifier: Modifier = Modifier
  ) {
    ActionButton(
      onClick = onClick,
      label = stringResource(labelResId),
      modifier = modifier
    ) {
      Image(
        painter = painterResource(iconResId),
        contentDescription = null,
        modifier = Modifier.padding(16.dp),
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer)
      )
    }
  }

  @Composable
  fun ActionButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imageContent: @Composable () -> Unit
  ) {
    Column(
      modifier = modifier,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      FilledTonalIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.size(56.dp),
        enabled = enabled,
        content = imageContent
      )
      Text(
        text = label,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}

@Composable
private fun SampleBox(
  darkMode: Boolean,
  content: @Composable BoxScope.() -> Unit
) {
  SignalTheme(isDarkMode = darkMode) {
    Surface {
      Box(modifier = Modifier.padding(8.dp)) {
        content()
      }
    }
  }
}

@Preview(name = "Buttons.LargePrimaryButton")
@Composable
private fun LargePrimaryButtonPreview() {
  Column {
    Row {
      LargePrimaryButtonSample(darkMode = false, enabled = true)
      LargePrimaryButtonSample(darkMode = true, enabled = true)
    }

    Row {
      LargePrimaryButtonSample(darkMode = false, enabled = false)
      LargePrimaryButtonSample(darkMode = true, enabled = false)
    }
  }
}

@Composable
private fun LargePrimaryButtonSample(
  darkMode: Boolean,
  enabled: Boolean
) {
  SampleBox(darkMode) {
    Buttons.LargePrimary(
      onClick = {},
      enabled = enabled
    ) {
      Text("Button")
    }
  }
}

@Preview(name = "Buttons.LargeTonalButton")
@Composable
private fun LargeTonalButtonPreview() {
  Column {
    Row {
      LargeTonalButtonSample(darkMode = false, enabled = true)
      LargeTonalButtonSample(darkMode = true, enabled = true)
    }

    Row {
      LargeTonalButtonSample(darkMode = false, enabled = false)
      LargeTonalButtonSample(darkMode = true, enabled = false)
    }
  }
}

@Composable
private fun LargeTonalButtonSample(
  darkMode: Boolean,
  enabled: Boolean
) {
  SampleBox(darkMode) {
    Buttons.LargeTonal(
      onClick = {},
      enabled = enabled
    ) {
      Text("Button")
    }
  }
}

@Preview(name = "Buttons.MediumTonalButton")
@Composable
private fun MediumTonalButtonPreview() {
  Column {
    Row {
      MediumTonalButtonSample(darkMode = false, enabled = true)
      MediumTonalButtonSample(darkMode = true, enabled = true)
    }

    Row {
      MediumTonalButtonSample(darkMode = false, enabled = false)
      MediumTonalButtonSample(darkMode = true, enabled = false)
    }
  }
}

@Composable
private fun MediumTonalButtonSample(
  darkMode: Boolean,
  enabled: Boolean
) {
  SampleBox(darkMode) {
    Buttons.MediumTonal(
      onClick = {},
      enabled = enabled
    ) {
      Text("Button")
    }
  }
}

@Preview(name = "Buttons.SmallButton")
@Composable
private fun SmallButtonPreview() {
  Column {
    Row {
      SmallButtonSample(darkMode = false, enabled = true)
      SmallButtonSample(darkMode = true, enabled = true)
    }

    Row {
      SmallButtonSample(darkMode = false, enabled = false)
      SmallButtonSample(darkMode = true, enabled = false)
    }
  }
}

@Composable
private fun SmallButtonSample(
  darkMode: Boolean,
  enabled: Boolean
) {
  SampleBox(darkMode) {
    Buttons.Small(
      onClick = {},
      enabled = enabled
    ) {
      Text("Button")
    }
  }
}

@Preview(name = "Buttons.ActionButton")
@Composable
private fun ActionButtonPreview() {
  Column {
    Row {
      ActionButtonSample(darkMode = false, enabled = true)
      ActionButtonSample(darkMode = true, enabled = true)
    }

    Row {
      ActionButtonSample(darkMode = false, enabled = false)
      ActionButtonSample(darkMode = true, enabled = false)
    }
  }
}

@Composable
private fun ActionButtonSample(
  darkMode: Boolean,
  enabled: Boolean
) {
  SampleBox(darkMode = darkMode) {
    Buttons.ActionButton(
      onClick = {},
      enabled = enabled,
      label = "Share"
    ) {
      Icon(
        imageVector = Icons.Default.Share,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        contentDescription = null
      )
    }
  }
}
