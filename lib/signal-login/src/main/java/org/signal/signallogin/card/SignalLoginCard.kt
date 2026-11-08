/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.signallogin.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.models.AccountEntropyPool
import org.signal.core.models.ServiceId
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.signallogin.R
import org.signal.signallogin.SignalLoginTestTags
import org.signal.signallogin.fonts.MonoTypeface
import java.util.UUID

/** Aspect ratio of the credential card artwork, so it scales with the available width. */
private const val CARD_ASPECT_RATIO = 360f / 220f

private val CARD_MAX_WIDTH = 360.dp

/** Number of masking dots shown in front of the revealed suffix of each credential. */
private const val MASK_DOT_COUNT = 4

/** Number of trailing characters of each credential that are left visible. */
private const val VISIBLE_SUFFIX_LENGTH = 4

// Vertical space within the card is split by weight, using the gaps from the design (in its 220dp-tall coordinates) so
// that everything scales together with the artwork.
private const val WORDMARK_WEIGHT = 80f
private const val PILL_GAP_WEIGHT = 28f
private const val BOTTOM_WEIGHT = 24f

/**
 * The Signal-branded card showing a Signal Login, masked down to the final few characters of each credential.
 *
 * @param aci The account key. Only the last few characters are shown.
 * @param aep The recovery key. Only the last few characters are shown.
 * @param onViewDetailsClicked Invoked when the user taps the "View details" pill on the card.
 */
@Composable
fun SignalLoginCard(
  aci: ServiceId.ACI,
  aep: AccountEntropyPool,
  onViewDetailsClicked: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .widthIn(max = CARD_MAX_WIDTH)
      .fillMaxWidth()
      .aspectRatio(CARD_ASPECT_RATIO)
  ) {
    Image(
      painter = painterResource(R.drawable.image_signal_login_card),
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      modifier = Modifier.fillMaxSize()
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp)
    ) {
      // The card artwork scales with the box, so the space it reserves for the baked-in "Signal" wordmark is
      // apportioned by weight rather than a fixed height.
      Spacer(modifier = Modifier.weight(WORDMARK_WEIGHT))

      Row(modifier = Modifier.fillMaxWidth()) {
        MaskedCredential(
          label = stringResource(R.string.SignalLoginCard__account),
          visibleSuffix = aci.toString().takeLast(VISIBLE_SUFFIX_LENGTH).uppercase(),
          modifier = Modifier.weight(1f)
        )

        MaskedCredential(
          label = stringResource(R.string.SignalLoginCard__recovery),
          visibleSuffix = aep.displayValue.takeLast(VISIBLE_SUFFIX_LENGTH),
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.weight(PILL_GAP_WEIGHT))

      ViewDetailsButton(
        onClick = onViewDetailsClicked,
        modifier = Modifier.align(Alignment.CenterHorizontally)
      )

      Spacer(modifier = Modifier.weight(BOTTOM_WEIGHT))
    }
  }
}

@Composable
private fun MaskedCredential(
  label: String,
  visibleSuffix: String,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = Color.White.copy(alpha = 0.6f)
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
      repeat(MASK_DOT_COUNT) {
        Box(
          modifier = Modifier
            .padding(end = 8.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(Color.White)
        )
      }

      Spacer(modifier = Modifier.width(4.dp))

      Text(
        text = visibleSuffix,
        style = MaterialTheme.typography.bodyMedium.copy(
          fontFamily = MonoTypeface.fontFamily(),
          fontSize = 15.sp,
          letterSpacing = 2.sp
        ),
        color = Color.White
      )
    }
  }
}

@Composable
private fun ViewDetailsButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(18.dp))
      .background(Color.White.copy(alpha = 0.2f))
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp, vertical = 8.dp)
      .testTag(SignalLoginTestTags.CARD_VIEW_DETAILS_BUTTON)
  ) {
    Text(
      text = stringResource(R.string.SignalLoginCard__view_details),
      style = MaterialTheme.typography.labelLarge,
      color = Color.White.copy(alpha = 0.96f)
    )
  }
}

@DayNightPreviews
@Composable
private fun SignalLoginCardPreview() {
  Previews.Preview {
    SignalLoginCard(
      aci = ServiceId.ACI.from(UUID.fromString("a6b28482-2e32-83d0-7f23-91360a4c2b91")),
      aep = AccountEntropyPool("uy38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t"),
      onViewDetailsClicked = {}
    )
  }
}
