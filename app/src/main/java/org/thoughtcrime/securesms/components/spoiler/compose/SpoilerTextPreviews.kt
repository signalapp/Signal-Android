package org.thoughtcrime.securesms.components.spoiler.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews

@DayNightPreviews
@Composable
private fun SingleSpoilerPreview() {
  val spoilerState = rememberSpoilerState()
  val text = buildAnnotatedString {
    append("This is some normal text and ")
    val start = length
    append("this part is hidden")
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "spoiler-1", start, length)
    append(" until you tap it!")
  }

  Previews.Preview {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      SpoilerText(
        text = text,
        spoilerState = spoilerState,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun MultipleSpoilersPreview() {
  val spoilerState = rememberSpoilerState()
  val text = buildAnnotatedString {
    append("The answer to question 1 is ")
    val start1 = length
    append("42")
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "answer-1", start1, length)
    append(" and the answer to question 2 is ")
    val start2 = length
    append("the meaning of life")
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "answer-2", start2, length)
    append(".")
  }

  Previews.Preview {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      SpoilerText(
        text = text,
        spoilerState = spoilerState,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun MultilineSpoilerPreview() {
  val spoilerState = rememberSpoilerState()
  val text = buildAnnotatedString {
    append("Here's a really long spoiler: ")
    val start = length
    append("This is a very long piece of text that will definitely span multiple lines when displayed in a narrow container. It contains important plot details that you might not want to see until you're ready!")
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "long-spoiler", start, length)
    append(" Tap to reveal.")
  }

  Previews.Preview {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      SpoilerText(
        text = text,
        spoilerState = spoilerState,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun StyledSpoilersPreview() {
  val spoilerState = rememberSpoilerState()
  val text = buildAnnotatedString {
    append("Normal text, ")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
      append("bold text, ")
    }
    val start1 = length
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
      append("bold spoiler")
    }
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "styled-spoiler-1", start1, length)
    append(", and ")
    val start2 = length
    withStyle(SpanStyle(fontWeight = FontWeight.Light, fontSize = 14.sp)) {
      append("light spoiler")
    }
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "styled-spoiler-2", start2, length)
    append(".")
  }

  Previews.Preview {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      SpoilerText(
        text = text,
        spoilerState = spoilerState,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun ColoredSpoilersPreview() {
  val spoilerState = rememberSpoilerState()
  val text = buildAnnotatedString {
    append("This text has ")
    val start1 = length
    withStyle(SpanStyle(color = Color.Red)) {
      append("red spoiler")
    }
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "red-spoiler", start1, length)
    append(" and ")
    val start2 = length
    withStyle(SpanStyle(color = Color.Blue)) {
      append("blue spoiler")
    }
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "blue-spoiler", start2, length)
    append(".")
  }

  Previews.Preview {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      SpoilerText(
        text = text,
        spoilerState = spoilerState,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun SharedStatePreview() {
  val spoilerState = rememberSpoilerState()

  val text1 = buildAnnotatedString {
    append("First message with ")
    val start = length
    append("shared spoiler")
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "shared-spoiler", start, length)
  }

  val text2 = buildAnnotatedString {
    append("Second message with the same ")
    val start = length
    append("shared spoiler")
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "shared-spoiler", start, length)
  }

  Previews.Preview {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      SpoilerText(
        text = text1,
        spoilerState = spoilerState,
        style = MaterialTheme.typography.bodyLarge
      )
      SpoilerText(
        text = text2,
        spoilerState = spoilerState,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun EntireMessageSpoilerPreview() {
  val spoilerState = rememberSpoilerState()
  val text = buildAnnotatedString {
    val start = length
    append("This entire message is a spoiler! You need to tap anywhere on it to reveal the content.")
    addStringAnnotation(SPOILER_ANNOTATION_TAG, "entire-message", start, length)
  }

  Previews.Preview {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth()
    ) {
      SpoilerText(
        text = text,
        spoilerState = spoilerState,
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun DifferentSizesPreview() {
  val spoilerState = rememberSpoilerState()

  Previews.Preview {
    Column(
      modifier = Modifier
        .padding(16.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      SpoilerText(
        text = buildAnnotatedString {
          append("Small text with ")
          val start = length
          append("small spoiler")
          addStringAnnotation(SPOILER_ANNOTATION_TAG, "small", start, length)
        },
        spoilerState = spoilerState,
        fontSize = 12.sp
      )

      SpoilerText(
        text = buildAnnotatedString {
          append("Medium text with ")
          val start = length
          append("medium spoiler")
          addStringAnnotation(SPOILER_ANNOTATION_TAG, "medium", start, length)
        },
        spoilerState = spoilerState,
        fontSize = 16.sp
      )

      SpoilerText(
        text = buildAnnotatedString {
          append("Large text with ")
          val start = length
          append("large spoiler")
          addStringAnnotation(SPOILER_ANNOTATION_TAG, "large", start, length)
        },
        spoilerState = spoilerState,
        fontSize = 24.sp
      )
    }
  }
}
