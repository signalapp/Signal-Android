package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Response JSON for a call to /v1/subscriptions/configuration
 */
public class SubscriptionsConfiguration {

  public static final String PAYPAL     = "PAYPAL";
  public static final String CARD       = "CARD";
  public static final String SEPA_DEBIT = "SEPA_DEBIT";
  public static final String IDEAL      = "IDEAL";

  public static final int               BOOST_LEVEL        = 1;
  public static final int               GIFT_LEVEL         = 100;
  public static final int               BACKUPS_LEVEL      = 201;
  public static final HashSet<Integer>  SUBSCRIPTION_LEVELS = new HashSet<>(Arrays.asList(500, 1000, 2000));

  @JsonProperty("currencies")
  private Map<String, CurrencyConfiguration> currencies;

  @JsonProperty("levels")
  private Map<Integer, LevelConfiguration> levels;

  @JsonProperty("sepaMaximumEuros")
  private BigDecimal sepaMaximumEuros;

  @JsonProperty("backup")
  private BackupConfiguration backupConfiguration;

  public static class CurrencyConfiguration {
    @JsonProperty("minimum")
    private BigDecimal minimum;

    @JsonProperty("oneTime")
    private Map<Integer, List<BigDecimal>> oneTime;

    @JsonProperty("subscription")
    private Map<Integer, BigDecimal> subscription;

    @JsonProperty("backupSubscription")
    private Map<Integer, BigDecimal> backupSubscription;

    @JsonProperty("supportedPaymentMethods")
    private Set<String> supportedPaymentMethods;

    public BigDecimal getMinimum() {
      return minimum;
    }

    public Map<Integer, List<BigDecimal>> getOneTime() {
      return oneTime;
    }

    public Map<Integer, BigDecimal> getSubscription() {
      return subscription;
    }

    public Map<Integer, BigDecimal> getBackupSubscription() {
      return backupSubscription;
    }

    public Set<String> getSupportedPaymentMethods() {
      return supportedPaymentMethods;
    }
  }

  public static class LevelConfiguration {

    @JsonProperty("badge")
    private SignalServiceProfile.Badge badge;

    public SignalServiceProfile.Badge getBadge() {
      return badge;
    }
  }

  public static class BackupConfiguration {
    @JsonProperty("levels")
    private Map<Integer, BackupLevelConfiguration> backupLevelConfigurationMap;

    @JsonProperty("backupFreeTierMediaDays")
    private int freeTierMediaDays;

    public Map<Integer, BackupLevelConfiguration> getBackupLevelConfigurationMap() {
      return backupLevelConfigurationMap;
    }

    public int getFreeTierMediaDays() {
      return freeTierMediaDays;
    }
  }

  public static class BackupLevelConfiguration {
    @JsonProperty("storageAllowanceBytes")
    private long storageAllowanceBytes;

    public long getStorageAllowanceBytes() {
      return storageAllowanceBytes;
    }
  }

  public Map<String, CurrencyConfiguration> getCurrencies() {
    return currencies;
  }

  public Map<Integer, LevelConfiguration> getLevels() {
    return levels;
  }

  public BigDecimal getSepaMaximumEuros() {
    return sepaMaximumEuros;
  }

  public BackupConfiguration getBackupConfiguration() {
    return backupConfiguration;
  }
}