/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AccountAttributes {

  @JsonProperty
  private String  signalingKey;

  @JsonProperty
  private int     registrationId;

  @JsonProperty
  private boolean voice;

  @JsonProperty
  private boolean video;

  @JsonProperty
  private boolean fetchesMessages;

  @JsonProperty
  private String pin;

  @JsonProperty
  private String registrationLock;

  @JsonProperty
  private byte[] unidentifiedAccessKey;

  @JsonProperty
  private boolean unrestrictedUnidentifiedAccess;

  @JsonProperty
  private boolean discoverableByPhoneNumber;

  @JsonProperty
  private Capabilities capabilities;

  @JsonProperty
  private String name;

  @JsonProperty
  private int pniRegistrationId;

  public AccountAttributes(String signalingKey,
                           int registrationId,
                           boolean fetchesMessages,
                           String pin,
                           String registrationLock,
                           byte[] unidentifiedAccessKey,
                           boolean unrestrictedUnidentifiedAccess,
                           Capabilities capabilities,
                           boolean discoverableByPhoneNumber,
                           String name,
                           int pniRegistrationId)
  {
    this.signalingKey                   = signalingKey;
    this.registrationId                 = registrationId;
    this.voice                          = true;
    this.video                          = true;
    this.fetchesMessages                = fetchesMessages;
    this.pin                            = pin;
    this.registrationLock               = registrationLock;
    this.unidentifiedAccessKey          = unidentifiedAccessKey;
    this.unrestrictedUnidentifiedAccess = unrestrictedUnidentifiedAccess;
    this.capabilities                   = capabilities;
    this.discoverableByPhoneNumber      = discoverableByPhoneNumber;
    this.name                           = name;
    this.pniRegistrationId              = pniRegistrationId;
  }

  public AccountAttributes() {}

  public String getSignalingKey() {
    return signalingKey;
  }

  public int getRegistrationId() {
    return registrationId;
  }

  public boolean isVoice() {
    return voice;
  }

  public boolean isVideo() {
    return video;
  }

  public boolean isFetchesMessages() {
    return fetchesMessages;
  }

  public String getPin() {
    return pin;
  }

  public String getRegistrationLock() {
    return registrationLock;
  }

  public byte[] getUnidentifiedAccessKey() {
    return unidentifiedAccessKey;
  }

  public boolean isUnrestrictedUnidentifiedAccess() {
    return unrestrictedUnidentifiedAccess;
  }

  public boolean isDiscoverableByPhoneNumber() {
    return discoverableByPhoneNumber;
  }

  public Capabilities getCapabilities() {
    return capabilities;
  }

  public String getName() {
    return name;
  }

  public int getPniRegistrationId() {
    return pniRegistrationId;
  }

  public static class Capabilities {
    @JsonProperty
    private boolean uuid;

    @JsonProperty("gv2-3")
    private boolean gv2;

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

    @JsonCreator
    public Capabilities() {}

    public Capabilities(boolean uuid, boolean gv2, boolean storage, boolean gv1Migration, boolean senderKey, boolean announcementGroup, boolean changeNumber, boolean stories, boolean giftBadges, boolean pnp) {
      this.uuid              = uuid;
      this.gv2               = gv2;
      this.storage           = storage;
      this.gv1Migration      = gv1Migration;
      this.senderKey         = senderKey;
      this.announcementGroup = announcementGroup;
      this.changeNumber      = changeNumber;
      this.stories           = stories;
      this.giftBadges        = giftBadges;
      this.pnp               = pnp;
    }

    public boolean isUuid() {
      return uuid;
    }

    public boolean isGv2() {
      return gv2;
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
  }
}
