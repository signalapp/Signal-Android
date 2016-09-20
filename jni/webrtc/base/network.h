/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_NETWORK_H_
#define WEBRTC_BASE_NETWORK_H_

#include <deque>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/ipaddress.h"
#include "webrtc/base/networkmonitor.h"
#include "webrtc/base/messagehandler.h"
#include "webrtc/base/sigslot.h"

#if defined(WEBRTC_POSIX)
struct ifaddrs;
#endif  // defined(WEBRTC_POSIX)

namespace rtc {

extern const char kPublicIPv4Host[];
extern const char kPublicIPv6Host[];

class IfAddrsConverter;
class Network;
class NetworkMonitorInterface;
class Thread;

static const uint16_t kNetworkCostMax = 999;
static const uint16_t kNetworkCostHigh = 900;
static const uint16_t kNetworkCostUnknown = 50;
static const uint16_t kNetworkCostLow = 10;
static const uint16_t kNetworkCostMin = 0;

// By default, ignore loopback interfaces on the host.
const int kDefaultNetworkIgnoreMask = ADAPTER_TYPE_LOOPBACK;

// Makes a string key for this network. Used in the network manager's maps.
// Network objects are keyed on interface name, network prefix and the
// length of that prefix.
std::string MakeNetworkKey(const std::string& name, const IPAddress& prefix,
                           int prefix_length);

class DefaultLocalAddressProvider {
 public:
  virtual ~DefaultLocalAddressProvider() = default;
  // The default local address is the local address used in multi-homed endpoint
  // when the any address (0.0.0.0 or ::) is used as the local address. It's
  // important to check the return value as a IP family may not be enabled.
  virtual bool GetDefaultLocalAddress(int family, IPAddress* ipaddr) const = 0;
};

// Generic network manager interface. It provides list of local
// networks.
//
// Every method of NetworkManager (including the destructor) must be called on
// the same thread, except for the constructor which may be called on any
// thread.
//
// This allows constructing a NetworkManager subclass on one thread and
// passing it into an object that uses it on a different thread.
class NetworkManager : public DefaultLocalAddressProvider {
 public:
  typedef std::vector<Network*> NetworkList;

  // This enum indicates whether adapter enumeration is allowed.
  enum EnumerationPermission {
    ENUMERATION_ALLOWED,  // Adapter enumeration is allowed. Getting 0 network
                          // from GetNetworks means that there is no network
                          // available.
    ENUMERATION_BLOCKED,  // Adapter enumeration is disabled.
                          // GetAnyAddressNetworks() should be used instead.
  };

  NetworkManager();
  ~NetworkManager() override;

  // Called when network list is updated.
  sigslot::signal0<> SignalNetworksChanged;

  // Indicates a failure when getting list of network interfaces.
  sigslot::signal0<> SignalError;

  // This should be called on the NetworkManager's thread before the
  // NetworkManager is used. Subclasses may override this if necessary.
  virtual void Initialize() {}

  // Start/Stop monitoring of network interfaces
  // list. SignalNetworksChanged or SignalError is emitted immediately
  // after StartUpdating() is called. After that SignalNetworksChanged
  // is emitted whenever list of networks changes.
  virtual void StartUpdating() = 0;
  virtual void StopUpdating() = 0;

  // Returns the current list of networks available on this machine.
  // StartUpdating() must be called before this method is called.
  // It makes sure that repeated calls return the same object for a
  // given network, so that quality is tracked appropriately. Does not
  // include ignored networks.
  virtual void GetNetworks(NetworkList* networks) const = 0;

  // return the current permission state of GetNetworks()
  virtual EnumerationPermission enumeration_permission() const;

  // "AnyAddressNetwork" is a network which only contains single "any address"
  // IP address.  (i.e. INADDR_ANY for IPv4 or in6addr_any for IPv6). This is
  // useful as binding to such interfaces allow default routing behavior like
  // http traffic.
  // TODO(guoweis): remove this body when chromium implements this.
  virtual void GetAnyAddressNetworks(NetworkList* networks) {}

  // Dumps the current list of networks in the network manager.
  virtual void DumpNetworks() {}
  bool GetDefaultLocalAddress(int family, IPAddress* ipaddr) const override;

  struct Stats {
    int ipv4_network_count;
    int ipv6_network_count;
    Stats() {
      ipv4_network_count = 0;
      ipv6_network_count = 0;
    }
  };
};

// Base class for NetworkManager implementations.
class NetworkManagerBase : public NetworkManager {
 public:
  NetworkManagerBase();
  ~NetworkManagerBase() override;

  void GetNetworks(std::vector<Network*>* networks) const override;
  void GetAnyAddressNetworks(NetworkList* networks) override;
  bool ipv6_enabled() const { return ipv6_enabled_; }
  void set_ipv6_enabled(bool enabled) { ipv6_enabled_ = enabled; }

  void set_max_ipv6_networks(int networks) { max_ipv6_networks_ = networks; }
  int max_ipv6_networks() { return max_ipv6_networks_; }

  EnumerationPermission enumeration_permission() const override;

  bool GetDefaultLocalAddress(int family, IPAddress* ipaddr) const override;

 protected:
  typedef std::map<std::string, Network*> NetworkMap;
  // Updates |networks_| with the networks listed in |list|. If
  // |network_map_| already has a Network object for a network listed
  // in the |list| then it is reused. Accept ownership of the Network
  // objects in the |list|. |changed| will be set to true if there is
  // any change in the network list.
  void MergeNetworkList(const NetworkList& list, bool* changed);

  // |stats| will be populated even if |*changed| is false.
  void MergeNetworkList(const NetworkList& list,
                        bool* changed,
                        NetworkManager::Stats* stats);

  void set_enumeration_permission(EnumerationPermission state) {
    enumeration_permission_ = state;
  }

  void set_default_local_addresses(const IPAddress& ipv4,
                                   const IPAddress& ipv6);

 private:
  friend class NetworkTest;

  Network* GetNetworkFromAddress(const rtc::IPAddress& ip) const;

  EnumerationPermission enumeration_permission_;

  NetworkList networks_;
  int max_ipv6_networks_;

  NetworkMap networks_map_;
  bool ipv6_enabled_;

  std::unique_ptr<rtc::Network> ipv4_any_address_network_;
  std::unique_ptr<rtc::Network> ipv6_any_address_network_;

  IPAddress default_local_ipv4_address_;
  IPAddress default_local_ipv6_address_;
  // We use 16 bits to save the bandwidth consumption when sending the network
  // id over the Internet. It is OK that the 16-bit integer overflows to get a
  // network id 0 because we only compare the network ids in the old and the new
  // best connections in the transport channel.
  uint16_t next_available_network_id_ = 1;
};

// Basic implementation of the NetworkManager interface that gets list
// of networks using OS APIs.
class BasicNetworkManager : public NetworkManagerBase,
                            public MessageHandler,
                            public sigslot::has_slots<> {
 public:
  BasicNetworkManager();
  ~BasicNetworkManager() override;

  void StartUpdating() override;
  void StopUpdating() override;

  void DumpNetworks() override;

  // MessageHandler interface.
  void OnMessage(Message* msg) override;
  bool started() { return start_count_ > 0; }

  // Sets the network ignore list, which is empty by default. Any network on the
  // ignore list will be filtered from network enumeration results.
  void set_network_ignore_list(const std::vector<std::string>& list) {
    network_ignore_list_ = list;
  }

#if defined(WEBRTC_LINUX)
  // Sets the flag for ignoring non-default routes.
  void set_ignore_non_default_routes(bool value) {
    ignore_non_default_routes_ = true;
  }
#endif

 protected:
#if defined(WEBRTC_POSIX)
  // Separated from CreateNetworks for tests.
  void ConvertIfAddrs(ifaddrs* interfaces,
                      IfAddrsConverter* converter,
                      bool include_ignored,
                      NetworkList* networks) const;
#endif  // defined(WEBRTC_POSIX)

  // Creates a network object for each network available on the machine.
  bool CreateNetworks(bool include_ignored, NetworkList* networks) const;

  // Determines if a network should be ignored. This should only be determined
  // based on the network's property instead of any individual IP.
  bool IsIgnoredNetwork(const Network& network) const;

  // This function connects a UDP socket to a public address and returns the
  // local address associated it. Since it binds to the "any" address
  // internally, it returns the default local address on a multi-homed endpoint.
  IPAddress QueryDefaultLocalAddress(int family) const;

 private:
  friend class NetworkTest;

  // Creates a network monitor and listens for network updates.
  void StartNetworkMonitor();
  // Stops and removes the network monitor.
  void StopNetworkMonitor();
  // Called when it receives updates from the network monitor.
  void OnNetworksChanged();

  // Updates the networks and reschedules the next update.
  void UpdateNetworksContinually();
  // Only updates the networks; does not reschedule the next update.
  void UpdateNetworksOnce();

  AdapterType GetAdapterTypeFromName(const char* network_name) const;

  Thread* thread_;
  bool sent_first_update_;
  int start_count_;
  std::vector<std::string> network_ignore_list_;
  bool ignore_non_default_routes_;
  std::unique_ptr<NetworkMonitorInterface> network_monitor_;
};

// Represents a Unix-type network interface, with a name and single address.
class Network {
 public:
  Network(const std::string& name,
          const std::string& description,
          const IPAddress& prefix,
          int prefix_length);

  Network(const std::string& name,
          const std::string& description,
          const IPAddress& prefix,
          int prefix_length,
          AdapterType type);
  ~Network();

  sigslot::signal1<const Network*> SignalInactive;
  sigslot::signal1<const Network*> SignalTypeChanged;

  const DefaultLocalAddressProvider* default_local_address_provider() {
    return default_local_address_provider_;
  }
  void set_default_local_address_provider(
      const DefaultLocalAddressProvider* provider) {
    default_local_address_provider_ = provider;
  }

  // Returns the name of the interface this network is associated wtih.
  const std::string& name() const { return name_; }

  // Returns the OS-assigned name for this network. This is useful for
  // debugging but should not be sent over the wire (for privacy reasons).
  const std::string& description() const { return description_; }

  // Returns the prefix for this network.
  const IPAddress& prefix() const { return prefix_; }
  // Returns the length, in bits, of this network's prefix.
  int prefix_length() const { return prefix_length_; }

  // |key_| has unique value per network interface. Used in sorting network
  // interfaces. Key is derived from interface name and it's prefix.
  std::string key() const { return key_; }

  // Returns the Network's current idea of the 'best' IP it has.
  // Or return an unset IP if this network has no active addresses.
  // Here is the rule on how we mark the IPv6 address as ignorable for WebRTC.
  // 1) return all global temporary dynamic and non-deprecrated ones.
  // 2) if #1 not available, return global ones.
  // 3) if #2 not available, use ULA ipv6 as last resort. (ULA stands
  // for unique local address, which is not route-able in open
  // internet but might be useful for a close WebRTC deployment.

  // TODO(guoweis): rule #3 actually won't happen at current
  // implementation. The reason being that ULA address starting with
  // 0xfc 0r 0xfd will be grouped into its own Network. The result of
  // that is WebRTC will have one extra Network to generate candidates
  // but the lack of rule #3 shouldn't prevent turning on IPv6 since
  // ULA should only be tried in a close deployment anyway.

  // Note that when not specifying any flag, it's treated as case global
  // IPv6 address
  IPAddress GetBestIP() const;

  // Keep the original function here for now.
  // TODO(guoweis): Remove this when all callers are migrated to GetBestIP().
  IPAddress ip() const { return GetBestIP(); }

  // Adds an active IP address to this network. Does not check for duplicates.
  void AddIP(const InterfaceAddress& ip) { ips_.push_back(ip); }

  // Sets the network's IP address list. Returns true if new IP addresses were
  // detected. Passing true to already_changed skips this check.
  bool SetIPs(const std::vector<InterfaceAddress>& ips, bool already_changed);
  // Get the list of IP Addresses associated with this network.
  const std::vector<InterfaceAddress>& GetIPs() const { return ips_;}
  // Clear the network's list of addresses.
  void ClearIPs() { ips_.clear(); }

  // Returns the scope-id of the network's address.
  // Should only be relevant for link-local IPv6 addresses.
  int scope_id() const { return scope_id_; }
  void set_scope_id(int id) { scope_id_ = id; }

  // Indicates whether this network should be ignored, perhaps because
  // the IP is 0, or the interface is one we know is invalid.
  bool ignored() const { return ignored_; }
  void set_ignored(bool ignored) { ignored_ = ignored; }

  AdapterType type() const { return type_; }
  void set_type(AdapterType type) {
    if (type_ == type) {
      return;
    }
    type_ = type;
    SignalTypeChanged(this);
  }

  uint16_t GetCost() const {
    switch (type_) {
      case rtc::ADAPTER_TYPE_ETHERNET:
      case rtc::ADAPTER_TYPE_LOOPBACK:
        return kNetworkCostMin;
      case rtc::ADAPTER_TYPE_WIFI:
      case rtc::ADAPTER_TYPE_VPN:
        return kNetworkCostLow;
      case rtc::ADAPTER_TYPE_CELLULAR:
        return kNetworkCostHigh;
      default:
        return kNetworkCostUnknown;
    }
  }
  // A unique id assigned by the network manager, which may be signaled
  // to the remote side in the candidate.
  uint16_t id() const { return id_; }
  void set_id(uint16_t id) { id_ = id; }

  int preference() const { return preference_; }
  void set_preference(int preference) { preference_ = preference; }

  // When we enumerate networks and find a previously-seen network is missing,
  // we do not remove it (because it may be used elsewhere). Instead, we mark
  // it inactive, so that we can detect network changes properly.
  bool active() const { return active_; }
  void set_active(bool active) {
    if (active_ == active) {
      return;
    }
    active_ = active;
    if (!active) {
      SignalInactive(this);
    }
  }

  // Debugging description of this network
  std::string ToString() const;

 private:
  const DefaultLocalAddressProvider* default_local_address_provider_ = nullptr;
  std::string name_;
  std::string description_;
  IPAddress prefix_;
  int prefix_length_;
  std::string key_;
  std::vector<InterfaceAddress> ips_;
  int scope_id_;
  bool ignored_;
  AdapterType type_;
  int preference_;
  bool active_ = true;
  uint16_t id_ = 0;

  friend class NetworkManager;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_NETWORK_H_
