package org.thoughtcrime.securesms.testutil;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.signal.core.util.logging.Log;

import java.util.ArrayList;
import java.util.List;

public final class LogRecorder extends Log.Logger {

  private final List<Entry> verbose     = new ArrayList<>();
  private final List<Entry> debug       = new ArrayList<>();
  private final List<Entry> information = new ArrayList<>();
  private final List<Entry> warnings    = new ArrayList<>();
  private final List<Entry> errors      = new ArrayList<>();
  private final List<Entry> wtf         = new ArrayList<>();

  @Override
  public void v(String tag, String message, Throwable t, boolean keepLonger) {
    verbose.add(new Entry(tag, message, t));
  }

  @Override
  public void d(String tag, String message, Throwable t, boolean keepLonger) {
    debug.add(new Entry(tag, message, t));
  }

  @Override
  public void i(String tag, String message, Throwable t, boolean keepLonger) {
    information.add(new Entry(tag, message, t));
  }

  @Override
  public void w(String tag, String message, Throwable t, boolean keepLonger) {
    warnings.add(new Entry(tag, message, t));
  }

  @Override
  public void e(String tag, String message, Throwable t, boolean keepLonger) {
    errors.add(new Entry(tag, message, t));
  }

  @Override
  public void flush() {
  }

  public List<Entry> getVerbose() {
    return verbose;
  }

  public List<Entry> getDebug() {
    return debug;
  }

  public List<Entry> getInformation() {
    return information;
  }

  public List<Entry> getWarnings() {
    return warnings;
  }

  public List<Entry> getErrors() {
    return errors;
  }

  public List<Entry> getWtf() {
    return wtf;
  }

  public static final class Entry {
    private final String    tag;
    private final String    message;
    private final Throwable throwable;

    private Entry(String tag, String message, Throwable throwable) {
      this.tag       = tag;
      this.message   = message;
      this.throwable = throwable;
    }

    public String getTag() {
      return tag;
    }

    public String getMessage() {
      return message;
    }

    public Throwable getThrowable() {
      return throwable;
    }
  }

  @SafeVarargs
  public static <T> Matcher<T> hasMessages(T... messages) {
    return new BaseMatcher<T>() {

      @Override
      public void describeTo(Description description) {
        description.appendValueList("[", ", ", "]", messages);
      }

      @Override
      public void describeMismatch(Object item, Description description) {
        @SuppressWarnings("unchecked")
        List<Entry>       list     = (List<Entry>) item;
        ArrayList<String> messages = new ArrayList<>(list.size());

        for (Entry e : list) {
          messages.add(e.message);
        }

        description.appendText("was ").appendValueList("[", ", ", "]", messages);
      }

      @Override
      public boolean matches(Object item) {
        @SuppressWarnings("unchecked")
        List<Entry> list = (List<Entry>) item;

        if (list.size() != messages.length) {
          return false;
        }

        for (int i = 0; i < messages.length; i++) {
          if (!list.get(i).message.equals(messages[i])) {
            return false;
          }
        }

        return true;
      }
    };
  }
}
