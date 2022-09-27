package org.signal.lint;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiField;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public final class RecipientIdDatabaseDetector extends Detector implements SourceCodeScanner {

  static final Issue RECIPIENT_ID_DATABASE_REFERENCE_ISSUE = Issue.create("RecipientIdDatabaseReferenceUsage",
                                                                          "Referencing a RecipientId in a database without implementing RecipientIdDatabaseReference.",
                                                                          "If you reference a RecipientId in a column, you need to be able to handle the remapping of one RecipientId to another, which RecipientIdDatabaseReference enforces.",
                                                                          Category.MESSAGES,
                                                                          5,
                                                                          Severity.ERROR,
                                                                          new Implementation(RecipientIdDatabaseDetector.class, Scope.JAVA_FILE_SCOPE));

  private static final Set<String> EXEMPTED_CLASSES = new HashSet<>() {{
    add("org.thoughtcrime.securesms.database.RecipientDatabase");
  }};


  @Override
  public List<Class<? extends UElement>> getApplicableUastTypes() {
    return Collections.singletonList(UClass.class);
  }

  @Override
  public UElementHandler createUastHandler(@NotNull JavaContext context) {
    return new UElementHandler() {
      @Override
      public void visitClass(@NotNull UClass node) {
        if (node.getQualifiedName() == null) {
          return;
        }

        if (node.getExtendsList() == null) {
          return;
        }

        if (EXEMPTED_CLASSES.contains(node.getQualifiedName())) {
          return;
        }

        boolean doesNotExtendDatabase = Arrays.stream(node.getExtendsList().getReferencedTypes()).noneMatch(t -> "Database".equals(t.getClassName()));
        if (doesNotExtendDatabase) {
          return;
        }

        boolean implementsReference = Arrays.stream(node.getInterfaces()).anyMatch(i -> "org.thoughtcrime.securesms.database.RecipientIdDatabaseReference".equals(i.getQualifiedName()));
        if (implementsReference) {
          return;
        }

        List<PsiField> recipientFields = Arrays.stream(node.getAllFields())
                                               .filter(f -> f.getType().equalsToText("java.lang.String"))
                                               .filter(f -> f.getName().toLowerCase().contains("recipient"))
                                               .collect(Collectors.toList());

        for (PsiField field : recipientFields) {
          context.report(RECIPIENT_ID_DATABASE_REFERENCE_ISSUE,
                         field,
                         context.getLocation(field),
                         "If you reference a RecipientId in your table, you must implement the RecipientIdDatabaseReference interface.",
                         null);
        }
      }
    };
  }
}