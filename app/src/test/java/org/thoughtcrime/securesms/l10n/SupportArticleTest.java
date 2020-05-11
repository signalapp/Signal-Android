package org.thoughtcrime.securesms.l10n;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public final class SupportArticleTest {

  private static final File    MAIN_STRINGS            = new File("src/main/res/values/strings.xml");
  private static final Pattern SUPPORT_ARTICLE         = Pattern.compile(".*:\\/\\/support.signal.org\\/.*articles\\/.*"        );
  private static final Pattern CORRECT_SUPPORT_ARTICLE = Pattern.compile("https:\\/\\/support.signal.org\\/hc\\/articles\\/\\d+");

  /**
   * Tests that support articles found in strings.xml:
   * <p>
   * - Do not have a locale mentioned in the url.
   * - Only have an article number, i.e. no trailing text.
   * - Are https.
   * - Are marked as translatable="false".
   */
  @Test
  public void ensure_format_and_translatable_state_of_all_support_article_urls() throws Exception {
    assertTrue(MAIN_STRINGS.exists());

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder        builder = factory.newDocumentBuilder();
    List<String>           errors  = new LinkedList<>();
    int                    seen    = 0;

    try (InputStream fileStream = new FileInputStream(MAIN_STRINGS)) {
      Document doc     = builder.parse(fileStream);
      NodeList strings = doc.getElementsByTagName("string");

      for (int i = 0; i < strings.getLength(); i++) {
        Node   stringNode = strings.item(i);
        String string     = stringNode.getTextContent();
        String stringName = stringName(stringNode);

        if (SUPPORT_ARTICLE.matcher(string).matches()) {
          seen++;

          if (!CORRECT_SUPPORT_ARTICLE.matcher(string).matches()) {
            errors.add(String.format("Article url format is not correct [%s] url: %s", stringName, string));
          }
          if (isTranslatable(stringNode)) {
            errors.add(String.format("Article string is translatable [%s], add translatable=\"false\"", stringName));
          }
        }
      }
    }

    assertThat(seen, greaterThan(0));
    assertThat(errors, is(Collections.emptyList()));
  }

  private static boolean isTranslatable(Node item) {
    if (item.hasAttributes()) {
      Node translatableAttribute = item.getAttributes().getNamedItem("translatable");
      return translatableAttribute == null || !"false".equals(translatableAttribute.getTextContent());
    }
    return true;
  }

  private static String stringName(Node item) {
    return item.getAttributes().getNamedItem("name").getTextContent();
  }
}
