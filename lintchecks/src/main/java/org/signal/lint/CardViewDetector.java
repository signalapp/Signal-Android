package org.signal.lint;

import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class CardViewDetector extends Detector implements Detector.UastScanner {

  static final Issue CARD_VIEW_USAGE = Issue.create("CardViewUsage",
                                                    "Utilizing CardView instead of MaterialCardView subclass",
                                                    "Signal utilizes MaterialCardView for more consistent and pleasant CardViews.",
                                                    Category.MESSAGES,
                                                    5,
                                                    Severity.WARNING,
                                                    new Implementation(CardViewDetector.class, Scope.JAVA_FILE_SCOPE));

  @Override
  public @Nullable List<String> getApplicableConstructorTypes() {
    return Collections.singletonList("androidx.cardview.widget.CardView");
  }

  @Override
  public void visitConstructor(JavaContext context, @NotNull UCallExpression call, @NotNull PsiMethod method) {
    JavaEvaluator evaluator = context.getEvaluator();

    if (evaluator.isMemberInClass(method, "androidx.cardview.widget.CardView")) {
      LintFix fix = quickFixIssueAlertDialogBuilder(call);
      context.report(CARD_VIEW_USAGE,
                     call,
                     context.getLocation(call),
                     "Using 'androidx.cardview.widget.CardView' instead of com.google.android.material.card.MaterialCardView",
                     fix);
    }
  }

  private LintFix quickFixIssueAlertDialogBuilder(@NotNull UCallExpression alertBuilderCall) {
    List<UExpression> arguments = alertBuilderCall.getValueArguments();
    UExpression       context   = arguments.get(0);

    String fixSource = "new com.google.android.material.card.MaterialCardView";

    //Context context, AttributeSet attrs, int defStyleAttr
    switch (arguments.size()) {
      case 1:
        fixSource += String.format("(%s)", context);
        break;
      case 2:
        UExpression attrs = arguments.get(1);
        fixSource += String.format("(%s, %s)", context, attrs);
        break;
      case 3:
        UExpression attributes = arguments.get(1);
        UExpression defStyleAttr = arguments.get(2);
        fixSource += String.format("(%s, %s, %s)", context, attributes, defStyleAttr);
        break;

      default:
        throw new IllegalStateException("MaterialAlertDialogBuilder overloads should have 1 or 2 arguments");
    }

    String               builderCallSource = alertBuilderCall.asSourceString();
    LintFix.GroupBuilder fixGrouper        = fix().group();
    fixGrouper.add(fix().replace().text(builderCallSource).shortenNames().reformat(true).with(fixSource).build());
    return fixGrouper.build();
  }
}