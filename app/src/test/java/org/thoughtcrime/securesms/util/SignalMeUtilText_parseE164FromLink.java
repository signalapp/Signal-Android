package org.thoughtcrime.securesms.util;

import android.app.Application;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.thoughtcrime.securesms.SignalStoreRule;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.AccountValues;

import java.util.Arrays;
import java.util.Collection;

import kotlin.Unit;

import static junit.framework.TestCase.assertEquals;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(manifest = Config.NONE, application = Application.class)
public class SignalMeUtilText_parseE164FromLink {

  private final String input;
  private final String output;

  @ParameterizedRobolectricTestRunner.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        { "https://signal.me/#p/+15555555555", "+15555555555" },
        { "https://signal.me/#p/5555555555",   null },
        { "https://signal.me",                 null },
        { "https://signal.me/#p/",             null },
        { "signal.me/#p/+15555555555",         null },
        { "sgnl://signal.me/#p/+15555555555",  "+15555555555" },
        { "sgnl://signal.me/#p/5555555555",    null },
        { "sgnl://signal.me",                  null },
        { "sgnl://signal.me/#p/",              null },
        { "",                                  null },
        { null,                                null }
    });
  }

  @Rule
  public SignalStoreRule signalStore = new SignalStoreRule(dataSet -> {
    dataSet.putString(AccountValues.KEY_E164, "+15555555555");
    return Unit.INSTANCE;
  });

  public SignalMeUtilText_parseE164FromLink(String input, String output) {
    this.input  = input;
    this.output = output;
  }

  @Test
  public void parse() {
    assertEquals(output, SignalMeUtil.parseE164FromLink(AppDependencies.getApplication(), input));
  }
}
