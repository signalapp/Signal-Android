package org.signal.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
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
}

@Composable
private fun LargePrimaryButtonPreview(
  darkMode: Boolean,
  enabled: Boolean
) {
  SignalTheme(isDarkMode = darkMode) {
    Buttons.LargePrimary(
      onClick = {},
      enabled = enabled
    ) {
      Text("Button")
    }
  }
}

@Composable
private fun LargeTonalButtonPreview(
  darkMode: Boolean,
  enabled: Boolean
) {
  SignalTheme(isDarkMode = darkMode) {
    Buttons.LargeTonal(
      onClick = {},
      enabled = enabled
    ) {
      Text("Button")
    }
  }
}

@Composable
private fun MediumTonalButtonPreview(
  darkMode: Boolean,
  enabled: Boolean
) {
  SignalTheme(isDarkMode = darkMode) {
    Buttons.MediumTonal(
      onClick = {},
      enabled = enabled
    ) {
      Text("Button")
    }
  }
}

@Composable
private fun SmallButtonPreview(
  darkMode: Boolean,
  enabled: Boolean
) {
  SignalTheme(isDarkMode = darkMode) {
    Buttons.Small(
      onClick = {},
      enabled = enabled
    ) {
      Text("Button")
    }
  }
}

@Preview
@Composable
private fun LargePrimaryButtonPreview() {
  Column {
    Row {
      LargePrimaryButtonPreview(darkMode = false, enabled = true)
      Spacer(modifier = Modifier.width(10.dp))
      LargePrimaryButtonPreview(darkMode = true, enabled = true)
    }

    Row {
      LargePrimaryButtonPreview(darkMode = false, enabled = false)
      Spacer(modifier = Modifier.width(10.dp))
      LargePrimaryButtonPreview(darkMode = true, enabled = false)
    }
  }
}

@Preview
@Composable
private fun LargeTonalButtonPreview() {
  Column {
    Row {
      LargeTonalButtonPreview(darkMode = false, enabled = true)
      Spacer(modifier = Modifier.width(10.dp))
      LargeTonalButtonPreview(darkMode = true, enabled = true)
    }

    Row {
      LargeTonalButtonPreview(darkMode = false, enabled = false)
      Spacer(modifier = Modifier.width(10.dp))
      LargeTonalButtonPreview(darkMode = true, enabled = false)
    }
  }
}

@Preview
@Composable
private fun MediumTonalButtonPreview() {
  Column {
    Row {
      MediumTonalButtonPreview(darkMode = false, enabled = true)
      Spacer(modifier = Modifier.width(10.dp))
      MediumTonalButtonPreview(darkMode = true, enabled = true)
    }

    Row {
      MediumTonalButtonPreview(darkMode = false, enabled = false)
      Spacer(modifier = Modifier.width(10.dp))
      MediumTonalButtonPreview(darkMode = true, enabled = false)
    }
  }
}

@Preview
@Composable
private fun SmallButtonPreview() {
  Column {
    Row {
      SmallButtonPreview(darkMode = false, enabled = true)
      Spacer(modifier = Modifier.width(10.dp))
      SmallButtonPreview(darkMode = true, enabled = true)
    }

    Row {
      SmallButtonPreview(darkMode = false, enabled = false)
      Spacer(modifier = Modifier.width(10.dp))
      SmallButtonPreview(darkMode = true, enabled = false)
    }
  }
}
