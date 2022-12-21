package org.whispersystems.signalservice.api.profiles;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.zkgroup.InvalidInputException;
import org.signal.libsignal.zkgroup.profiles.ExpiringProfileKeyCredentialResponse;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.math.BigDecimal;
import java.util.List;

public class SignalServiceProfile {

  public enum RequestType {
    PROFILE,
    PROFILE_AND_CREDENTIAL
  }

  private static final String TAG = SignalServiceProfile.class.getSimpleName();

  @JsonProperty
  private String identityKey;

  @JsonProperty
  private String name;

  @JsonProperty
  private String about;

  @JsonProperty
  private String aboutEmoji;

  @JsonProperty
  private byte[] paymentAddress;

  @JsonProperty
  private String avatar;

  @JsonProperty
  private String unidentifiedAccess;

  @JsonProperty
  private boolean unrestrictedUnidentifiedAccess;

  @JsonProperty
  private Capabilities capabilities;

  @JsonProperty
  @JsonSerialize(using = JsonUtil.ServiceIdSerializer.class)
  @JsonDeserialize(using = JsonUtil.ServiceIdDeserializer.class)
  private ServiceId uuid;

  @JsonProperty
  private byte[] credential;

  @JsonProperty
  private List<Badge> badges;

  @JsonIgnore
  private RequestType requestType;

  public SignalServiceProfile() {}

  public String getIdentityKey() {
    return identityKey;
  }

  public String getName() {
    return name;
  }

  public String getAbout() {
    return about;
  }

  public String getAboutEmoji() {
    return aboutEmoji;
  }

  public byte[] getPaymentAddress() {
    return paymentAddress;
  }

  public String getAvatar() {
    return avatar;
  }

  public String getUnidentifiedAccess() {
    return unidentifiedAccess;
  }

  public boolean isUnrestrictedUnidentifiedAccess() {
    return unrestrictedUnidentifiedAccess;
  }

  public Capabilities getCapabilities() {
    return capabilities;
  }

  public List<Badge> getBadges() {
    return badges;
  }

  public ServiceId getServiceId() {
    return uuid;
  }

  public RequestType getRequestType() {
    return requestType;
  }

  public void setRequestType(RequestType requestType) {
    this.requestType = requestType;
  }

  public static class Badge {
    @JsonProperty
    private String id;

    @JsonProperty
    private String category;

    @JsonProperty
    private String name;

    @JsonProperty
    private String description;

    @JsonProperty
    private List<String> sprites6;

    @JsonProperty
    private BigDecimal expiration;

    @JsonProperty
    private boolean visible;

    @JsonProperty
    private long duration;

    public String getId() {
      return id;
    }

    public String getCategory() {
      return category;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public List<String> getSprites6() {
      return sprites6;
    }

    public BigDecimal getExpiration() {
      return expiration;
    }

    public boolean isVisible() {
      return visible;
    }

    /**
     * @return Duration badge is valid for, in seconds.
     */
    public long getDuration() {
      return duration;
    }
  }

  public static class Capabilities {
    @JsonProperty
    private boolean storage;

    @JsonProperty("gv1-migration")
    private boolean gv1Migration;

    @JsonProperty
    private boolean senderKey;

    @JsonProperty
    private boolean announcementGroup;

    @JsonProperty
    private boolean changeNumber;

    @JsonProperty
    private boolean stories;

    @JsonProperty
    private boolean giftBadges;

    @JsonProperty
    private boolean pnp;

    @JsonProperty
    private boolean paymentActivation;

    @JsonCreator
    public Capabilities() {}

    public Capabilities(boolean storage, boolean gv1Migration, boolean senderKey, boolean announcementGroup, boolean changeNumber, boolean stories, boolean giftBadges, boolean pnp, boolean paymentActivation) {
      this.storage           = storage;
      this.gv1Migration      = gv1Migration;
      this.senderKey         = senderKey;
      this.announcementGroup = announcementGroup;
      this.changeNumber      = changeNumber;
      this.stories           = stories;
      this.giftBadges        = giftBadges;
      this.pnp               = pnp;
      this.paymentActivation = paymentActivation;
    }

    public boolean isStorage() {
      return storage;
    }

    public boolean isGv1Migration() {
      return gv1Migration;
    }

    public boolean isSenderKey() {
      return senderKey;
    }

    public boolean isAnnouncementGroup() {
      return announcementGroup;
    }

    public boolean isChangeNumber() {
      return changeNumber;
    }

    public boolean isStories() {
      return stories;
    }

    public boolean isGiftBadges() {
      return giftBadges;
    }

    public boolean isPnp() {
      return pnp;
    }

    public boolean isPaymentActivation() {
      return paymentActivation;
    }
  }

  public ExpiringProfileKeyCredentialResponse getExpiringProfileKeyCredentialResponse() {
    if (credential == null) return null;

    try {
      return new ExpiringProfileKeyCredentialResponse(credential);
    } catch (InvalidInputException e) {
      Log.w(TAG, e);
      return null;
    }
  }
}
