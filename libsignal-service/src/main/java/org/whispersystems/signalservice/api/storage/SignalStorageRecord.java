package org.whispersystems.signalservice.api.storage;


import org.jetbrains.annotations.NotNull;
import org.whispersystems.signalservice.internal.storage.protos.CallLinkRecord;

import java.util.Objects;
import java.util.Optional;

public class SignalStorageRecord implements SignalRecord {

  private final StorageId                                   id;
  private final Optional<SignalStoryDistributionListRecord> storyDistributionList;
  private final Optional<SignalContactRecord>               contact;
  private final Optional<SignalGroupV1Record>               groupV1;
  private final Optional<SignalGroupV2Record>               groupV2;
  private final Optional<SignalAccountRecord>               account;
  private final Optional<SignalCallLinkRecord>              callLink;

  public static SignalStorageRecord forStoryDistributionList(SignalStoryDistributionListRecord storyDistributionList) {
    return forStoryDistributionList(storyDistributionList.getId(), storyDistributionList);
  }

  public static SignalStorageRecord forStoryDistributionList(StorageId key, SignalStoryDistributionListRecord storyDistributionList) {
    return new SignalStorageRecord(key, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(storyDistributionList), Optional.empty());
  }

  public static SignalStorageRecord forContact(SignalContactRecord contact) {
    return forContact(contact.getId(), contact);
  }

  public static SignalStorageRecord forContact(StorageId key, SignalContactRecord contact) {
    return new SignalStorageRecord(key, Optional.of(contact), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public static SignalStorageRecord forGroupV1(SignalGroupV1Record groupV1) {
    return forGroupV1(groupV1.getId(), groupV1);
  }

  public static SignalStorageRecord forGroupV1(StorageId key, SignalGroupV1Record groupV1) {
    return new SignalStorageRecord(key, Optional.empty(), Optional.of(groupV1), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public static SignalStorageRecord forGroupV2(SignalGroupV2Record groupV2) {
    return forGroupV2(groupV2.getId(), groupV2);
  }

  public static SignalStorageRecord forGroupV2(StorageId key, SignalGroupV2Record groupV2) {
    return new SignalStorageRecord(key, Optional.empty(), Optional.empty(), Optional.of(groupV2), Optional.empty(), Optional.empty(), Optional.empty());
  }

  public static SignalStorageRecord forAccount(SignalAccountRecord account) {
    return forAccount(account.getId(), account);
  }

  public static SignalStorageRecord forAccount(StorageId key, SignalAccountRecord account) {
    return new SignalStorageRecord(key, Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(account), Optional.empty(), Optional.empty());
  }

  @NotNull
  public static SignalStorageRecord forCallLink(@NotNull SignalCallLinkRecord callLink) {
    return forCallLink(callLink.getId(), callLink);
  }

  @NotNull
  public static SignalStorageRecord forCallLink(StorageId key, @NotNull SignalCallLinkRecord callLink) {
    return new SignalStorageRecord(key, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(callLink));
  }

  public static SignalStorageRecord forUnknown(StorageId key) {
    return new SignalStorageRecord(key, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }


  private SignalStorageRecord(StorageId id,
                              Optional<SignalContactRecord> contact,
                              Optional<SignalGroupV1Record> groupV1,
                              Optional<SignalGroupV2Record> groupV2,
                              Optional<SignalAccountRecord> account,
                              Optional<SignalStoryDistributionListRecord> storyDistributionList,
                              Optional<SignalCallLinkRecord> callLink)
  {
    this.id                    = id;
    this.contact               = contact;
    this.groupV1               = groupV1;
    this.groupV2               = groupV2;
    this.account               = account;
    this.storyDistributionList = storyDistributionList;
    this.callLink              = callLink;
  }

  @Override
  public StorageId getId() {
    return id;
  }

  @Override
  public SignalStorageRecord asStorageRecord() {
    return this;
  }

  @Override
  public String describeDiff(SignalRecord other) {
    return "Diffs not supported.";
  }

  public int getType() {
    return id.getType();
  }

  public Optional<SignalContactRecord> getContact() {
    return contact;
  }

  public Optional<SignalGroupV1Record> getGroupV1() {
    return groupV1;
  }

  public Optional<SignalGroupV2Record> getGroupV2() {
    return groupV2;
  }

  public Optional<SignalAccountRecord> getAccount() {
    return account;
  }

  public Optional<SignalStoryDistributionListRecord> getStoryDistributionList() {
    return storyDistributionList;
  }

  public Optional<SignalCallLinkRecord> getCallLink() {
    return callLink;
  }

  public boolean isUnknown() {
    return !contact.isPresent() && !groupV1.isPresent() && !groupV2.isPresent() && !account.isPresent() && !storyDistributionList.isPresent() && !callLink.isPresent();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignalStorageRecord that = (SignalStorageRecord) o;
    return Objects.equals(id, that.id) &&
           Objects.equals(contact, that.contact) &&
           Objects.equals(groupV1, that.groupV1) &&
           Objects.equals(groupV2, that.groupV2) &&
           Objects.equals(storyDistributionList, that.storyDistributionList) &&
           Objects.equals(callLink, that.callLink);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, contact, groupV1, groupV2, storyDistributionList, callLink);
  }
}
