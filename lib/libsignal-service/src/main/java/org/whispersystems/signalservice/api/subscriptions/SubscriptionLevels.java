package org.whispersystems.signalservice.api.subscriptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Available subscription levels.
 */
public final class SubscriptionLevels {

  /**
   * Reserved level for boost badge.
   */
  public static final String BOOST_LEVEL = "1";

  private final Map<String, Level> levels;

  @JsonCreator
  public SubscriptionLevels(@JsonProperty("levels") Map<String, Level> levels) {
    this.levels = levels;
  }

  public Map<String, Level> getLevels() {
    return levels;
  }

  /**
   * An available subscription level
   */
  public static final class Level {
    private final String                     name;
    private final SignalServiceProfile.Badge badge;
    private final Map<String, BigDecimal>    currencies;

    @JsonCreator
    public Level(@JsonProperty("name") String name,
                 @JsonProperty("badge") SignalServiceProfile.Badge badge,
                 @JsonProperty("currencies") Map<String, BigDecimal> currencies)
    {
      this.name       = name;
      this.badge      = badge;
      this.currencies = currencies;
    }

    public String getName() {
      return name;
    }

    public SignalServiceProfile.Badge getBadge() {
      return badge;
    }

    public Map<String, BigDecimal> getCurrencies() {
      return currencies;
    }
  }
}
