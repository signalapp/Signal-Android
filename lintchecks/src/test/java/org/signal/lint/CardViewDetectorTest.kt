package org.signal.lint

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Scanner

class CardViewDetectorTest {
  @Test
  fun cardViewUsed_LogCardViewUsage_1_arg() {
    TestLintTask.lint()
      .files(
        cardViewStub,
        java(
          """
          package foo;
          import androidx.cardview.widget.CardView;
          public class Example {
            public void buildCardView() {
              new CardView(context);
            }
          }
          """.trimIndent()
        )
      )
      .issues(CardViewDetector.CARD_VIEW_USAGE)
      .run()
      .expect(
        """
        src/foo/Example.java:5: Warning: Using 'androidx.cardview.widget.CardView' instead of com.google.android.material.card.MaterialCardView [CardViewUsage]
            new CardView(context);
            ~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
            Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.card.MaterialCardView(context):
            @@ -5 +5
            -     new CardView(context);
            +     new com.google.android.material.card.MaterialCardView(context);
            """.trimIndent()
      )
  }

  @Test
  fun cardViewUsed_LogCardViewUsage_2_arg() {
    TestLintTask.lint()
      .files(
        cardViewStub,
        java(
          """
          package foo;
          import androidx.cardview.widget.CardView;
          public class Example {
            public void buildCardView() {
              new CardView(context, attrs);
            }
          }
          """.trimIndent()
        )
      )
      .issues(CardViewDetector.CARD_VIEW_USAGE)
      .run()
      .expect(
      """
        src/foo/Example.java:5: Warning: Using 'androidx.cardview.widget.CardView' instead of com.google.android.material.card.MaterialCardView [CardViewUsage]
            new CardView(context, attrs);
            ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.card.MaterialCardView(context, attrs):
        @@ -5 +5
        -     new CardView(context, attrs);
        +     new com.google.android.material.card.MaterialCardView(context, attrs);
        """.trimIndent()
      )
  }

  @Test
  fun cardViewUsed_withAssignment_LogCardViewUsage_1_arg() {
    TestLintTask.lint()
      .files(
        cardViewStub,
        java(
          """
          package foo;
          import androidx.cardview.widget.CardView;
          public class Example {
            public void buildCardView() {
              CardView cardView = new CardView(context)
                                              ;
            }
          }
          """.trimIndent()
        )
      )
      .issues(CardViewDetector.CARD_VIEW_USAGE)
      .run()
      .expect(
      """
        src/foo/Example.java:5: Warning: Using 'androidx.cardview.widget.CardView' instead of com.google.android.material.card.MaterialCardView [CardViewUsage]
            CardView cardView = new CardView(context)
                                ~~~~~~~~~~~~~~~~~~~~~
        0 errors, 1 warnings
        """.trimIndent()
      )
      .expectFixDiffs(
        """
        Fix for src/foo/Example.java line 5: Replace with new com.google.android.material.card.MaterialCardView(context):
        @@ -5 +5
        -     CardView cardView = new CardView(context)
        +     CardView cardView = new com.google.android.material.card.MaterialCardView(context)
        """.trimIndent()
      )
  }

  companion object {
    private val cardViewStub = kotlin(readResourceAsString("CardViewStub.kt"))

    private fun readResourceAsString(@Suppress("SameParameterValue") resourceName: String): String {
      val inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resourceName)
      assertNotNull(inputStream)
      val scanner = Scanner(inputStream!!).useDelimiter("\\A")
      assertTrue(scanner.hasNext())
      return scanner.next()
    }
  }
}
