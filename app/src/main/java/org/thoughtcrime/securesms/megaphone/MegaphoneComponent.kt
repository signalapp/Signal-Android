/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.megaphone

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalPreview
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.Emojifier
import org.thoughtcrime.securesms.main.EmptyMegaphoneActionController
import org.thoughtcrime.securesms.megaphone.Megaphones.Event
import org.thoughtcrime.securesms.util.DynamicTheme
import kotlin.math.roundToInt

/**
 * Allows us to utilize our composeView from Java code.
 */
fun setContent(composeView: ComposeView, megaphone: Megaphone, megaphoneActionController: MegaphoneActionController) {
  composeView.setContent {
    SignalTheme(isDarkMode = DynamicTheme.isDarkTheme(composeView.context)) {
      MegaphoneComponent(
        megaphone,
        megaphoneActionController
      )
    }
  }
}

/**
 * Composable which replaces the whole builder pattern for megaphone views.
 */
@Composable
fun MegaphoneComponent(
  megaphone: Megaphone,
  megaphoneActionController: MegaphoneActionController,
  modifier: Modifier = Modifier
) {
  if (megaphone == Megaphone.NONE) {
    return
  }

  when (megaphone.style) {
    Megaphone.Style.ONBOARDING -> OnboardingMegaphone(
      megaphoneActionController = megaphoneActionController,
      modifier = modifier.fillMaxWidth()
    )

    Megaphone.Style.BASIC -> BasicMegaphone(
      megaphone = megaphone,
      megaphoneActionController = megaphoneActionController,
      modifier = modifier.fillMaxWidth()
    )

    Megaphone.Style.FULLSCREEN -> Unit
    Megaphone.Style.POPUP -> PopupMegaphone(
      megaphone = megaphone,
      megaphoneActionController = megaphoneActionController,
      modifier = modifier.fillMaxWidth()
    )
  }

  LaunchedEffect(megaphone) {
    megaphone.onVisibleListener?.onEvent(megaphone, megaphoneActionController)
  }
}

/**
 * Basic megaphone with up to two actions, no elevation, and an outline.
 */
@Composable
private fun BasicMegaphone(
  megaphone: Megaphone,
  megaphoneActionController: MegaphoneActionController,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Card(
    elevation = CardDefaults.outlinedCardElevation(),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.background
    ),
    modifier = Modifier
      .padding(8.dp)
      .then(modifier)
  ) {
    Column(
      modifier = Modifier
        .padding(horizontal = 8.dp)
        .padding(top = 16.dp, bottom = 8.dp)
    ) {
      MegaphoneCardContent(megaphone = megaphone)

      Row {
        Spacer(modifier = Modifier.weight(1f))

        if (megaphone.hasSecondaryButton()) {
          TextButton(
            onClick = { megaphone.secondaryButtonClickListener?.onEvent(megaphone, megaphoneActionController) }
          ) {
            Text(
              text = megaphone.secondaryButtonText?.resolve(context)!!
            )
          }
        }

        if (megaphone.canSnooze()) {
          TextButton(
            onClick = {
              megaphoneActionController.onMegaphoneSnooze(megaphone.event)
              megaphone.snoozeListener?.onEvent(megaphone, megaphoneActionController)
            }
          ) {
            Text(
              text = megaphone.buttonText?.resolve(context)!!
            )
          }
        } else if (megaphone.hasButton()) {
          TextButton(
            onClick = { megaphone.buttonClickListener?.onEvent(megaphone, megaphoneActionController) }
          ) {
            Text(
              text = megaphone.buttonText?.resolve(context)!!
            )
          }
        }
      }
    }
  }
}

/**
 * Elevated megaphone with no actions but does have a close button.
 */
@Composable
private fun PopupMegaphone(
  megaphone: Megaphone,
  megaphoneActionController: MegaphoneActionController,
  modifier: Modifier = Modifier
) {
  Card(
    elevation = CardDefaults.cardElevation(6.dp),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = colorResource(R.color.megaphone_background_color)),
    modifier = Modifier
      .padding(8.dp)
      .then(modifier)
  ) {
    Box {
      MegaphoneCardContent(
        megaphone = megaphone,
        modifier = Modifier
          .padding(horizontal = 8.dp)
          .padding(top = 16.dp, bottom = 8.dp)
      )

      IconButtons.IconButton(
        onClick = {
          if (megaphone.hasButton()) {
            megaphone.buttonClickListener?.onEvent(megaphone, megaphoneActionController)
          } else {
            megaphoneActionController.onMegaphoneCompleted(megaphone.event)
          }
        },
        size = 48.dp,
        modifier = Modifier.align(Alignment.TopEnd)
      ) {
        Icon(
          imageVector = ImageVector.vectorResource(R.drawable.ic_x_20),
          contentDescription = stringResource(R.string.Material3SearchToolbar__close)
        )
      }
    }
  }
}

/**
 * Shared card content including the image, title, and body.
 */
@Composable
private fun MegaphoneCardContent(
  megaphone: Megaphone,
  modifier: Modifier = Modifier
) {
  Row(modifier = modifier) {
    MegaphoneImage(
      megaphone = megaphone,
      modifier = Modifier.padding(start = 8.dp)
    )

    Column(
      modifier = Modifier.padding(start = 12.dp, end = 8.dp)
    ) {
      if (megaphone.title.hasText) {
        Emojifier(
          text = megaphone.title.resolve(LocalContext.current)!!
        ) { annotatedText, inlineContent ->
          Text(
            text = annotatedText,
            inlineContent = inlineContent,
            style = MaterialTheme.typography.bodyLarge
          )
        }
      }

      if (megaphone.body.hasText) {
        Emojifier(
          text = megaphone.body.resolve(LocalContext.current)!!
        ) { annotatedText, inlineContent ->
          Text(
            text = annotatedText,
            inlineContent = inlineContent,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }
  }
}

/**
 * An image, which is either backed by Lottie, a glide request or just a plain vector.
 */
@Composable
private fun MegaphoneImage(
  megaphone: Megaphone,
  modifier: Modifier = Modifier
) {
  val sharedModifier = modifier.size(64.dp)

  if (megaphone.imageRes != 0) {
    val context = LocalContext.current
    val drawable = remember(megaphone.imageRes) { ContextCompat.getDrawable(context, megaphone.imageRes) }

    Image(
      painter = rememberDrawablePainter(drawable),
      contentDescription = null,
      contentScale = ContentScale.Inside,
      modifier = sharedModifier
    )
  } else if (megaphone.imageRequestBuilder != null) {
    var drawable: Drawable? by remember {
      mutableStateOf(null)
    }

    val painter = rememberDrawablePainter(drawable)
    val size = with(LocalDensity.current) {
      64.dp.toPx().roundToInt()
    }

    LaunchedEffect(megaphone.imageRequestBuilder) {
      drawable = withContext(Dispatchers.IO) {
        megaphone.imageRequestBuilder?.submit(size, size)?.get()
      }
    }

    Image(
      painter = painter,
      contentDescription = null,
      contentScale = ContentScale.Inside,
      modifier = sharedModifier
    )
  } else if (megaphone.lottieRes != 0) {
    val lottieComposition by rememberLottieComposition(spec = LottieCompositionSpec.RawRes(megaphone.lottieRes))

    LottieAnimation(
      composition = lottieComposition,
      modifier = sharedModifier
    )
  }
}

@SignalPreview
@Composable
private fun BasicMegaphonePreview() {
  Previews.Preview {
    MegaphoneComponent(
      megaphone = rememberTestMegaphone(Event.PINS_FOR_ALL, Megaphone.Style.BASIC),
      megaphoneActionController = EmptyMegaphoneActionController
    )
  }
}

@SignalPreview
@Composable
private fun PopupMegaphonePreview() {
  Previews.Preview {
    MegaphoneComponent(
      megaphone = rememberTestMegaphone(Event.PIN_REMINDER, Megaphone.Style.POPUP),
      megaphoneActionController = EmptyMegaphoneActionController
    )
  }
}

/**
 * Testing only.
 */
@Composable
private fun rememberTestMegaphone(
  event: Event,
  style: Megaphone.Style
): Megaphone {
  return remember {
    Megaphone.Builder(event, style)
      .setImage(R.drawable.illustration_toggle_switch)
      .setTitle("Avengers HQ Destroyed!")
      .setBody("Where was the 'hero' Spider-Man during the battle?")
      .setActionButton("*sigh*") { _, _ -> }
      .setSecondaryButton("Remind me later") { _, _ -> }
      .build()
  }
}
