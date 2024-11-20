package org.thoughtcrime.securesms.util;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.testutil.EmptyLogger;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class RemoteExpirationTest_getTimeUntilDeprecation {

  private final String json;
  private final long   currentDate;
  private final String currentVersion;
  private final long   timeUntilExpiration;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        // Null json, invalid
        { null, DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.0", -1 },

        // Empty json, no expiration
        { "[]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.0", -1 },

        // Badly formatted minVersion, no expiration
        { "[ {\"minVersion\": \"1.1\", \"iso8601\": \"2020-01-01T00:00:01Z\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.1", -1 },

        // Badly formatted date, no expiration
        { "[ {\"minVersion\": \"1.1.1\", \"iso8601\": \"20-01T00:00:01Z\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.1", -1 },

        // Missing minVersion, no expiration
        { "[ {\"iso8601\": \"20-01T00:00:01Z\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.1", -1 },

        // Missing date, no expiration
        { "[ {\"minVersion\": \"1.1.1\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.1", -1 },

        // Missing expiration and date, no expiration
        { "[ {} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.1", -1 },

        // Invalid inner object, no expiration
        { "[ { \"a\": 1 } ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.1", -1 },

        // Invalid json, no expiration
        { "[ {", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.1", -1 },

        // We meet the min version, no expiration
        { "[ {\"minVersion\": \"1.1.1\", \"iso8601\": \"2020-01-01T00:00:01Z\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.1", -1 },

        // We exceed the min version, no expiration
        { "[ {\"minVersion\": \"1.1.1\", \"iso8601\": \"2020-01-01T00:00:01Z\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.2", -1 },

        // We expire in 1 second
        { "[ {\"minVersion\": \"1.1.1\", \"iso8601\": \"2020-01-01T00:00:01Z\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.0", 1000 },

        // We have already expired
        { "[ {\"minVersion\": \"1.1.1\", \"iso8601\": \"2020-01-01T00:00:01Z\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:02Z"), "1.1.0", 0 },

        // Use the closest expiration when multiple ones are listed
        { "[ {\"minVersion\": \"1.1.2\", \"iso8601\": \"2020-02-01T00:00:00Z\"}," +
            "{\"minVersion\": \"1.1.3\", \"iso8601\": \"2020-03-01T00:00:00Z\"}," +
            "{\"minVersion\": \"1.1.1\", \"iso8601\": \"2020-01-01T00:00:01Z\"} ]", DateUtils.parseIso8601("2020-01-01T00:00:00Z"), "1.1.0", 1000 },
    });
  }

  public RemoteExpirationTest_getTimeUntilDeprecation(String json, long currentDate, String currentVersion, long timeUntilExpiration) {
    this.json                = json;
    this.currentDate         = currentDate;
    this.currentVersion      = currentVersion;
    this.timeUntilExpiration = timeUntilExpiration;
  }

  @BeforeClass
  public static void setup() {
    Log.initialize(new EmptyLogger());
  }

  @Test
  public void getTimeUntilExpiration() {
    assertEquals(timeUntilExpiration, RemoteDeprecation.getTimeUntilDeprecation(json, currentDate, currentVersion));
  }
}
