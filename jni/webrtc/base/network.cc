/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/network.h"

#if defined(WEBRTC_POSIX)
// linux/if.h can't be included at the same time as the posix sys/if.h, and
// it's transitively required by linux/route.h, so include that version on
// linux instead of the standard posix one.
#if defined(WEBRTC_LINUX)
#include <linux/if.h>
#include <linux/route.h>
#elif !defined(__native_client__)
#include <net/if.h>
#endif
#endif  // WEBRTC_POSIX

#if defined(WEBRTC_WIN)
#include "webrtc/base/win32.h"
#include <Iphlpapi.h>
#elif !defined(__native_client__)
#include "webrtc/base/ifaddrs_converter.h"
#endif

#include <stdio.h>

#include <algorithm>
#include <memory>

#include "webrtc/base/logging.h"
#include "webrtc/base/networkmonitor.h"
#include "webrtc/base/socket.h"  // includes something that makes windows happy
#include "webrtc/base/stream.h"
#include "webrtc/base/stringencode.h"
#include "webrtc/base/thread.h"

namespace rtc {
namespace {

// Turning on IPv6 could make many IPv6 interfaces available for connectivity
// check and delay the call setup time. kMaxIPv6Networks is the default upper
// limit of IPv6 networks but could be changed by set_max_ipv6_networks().
const int kMaxIPv6Networks = 5;

const uint32_t kUpdateNetworksMessage = 1;
const uint32_t kSignalNetworksMessage = 2;

// Fetch list of networks every two seconds.
const int kNetworksUpdateIntervalMs = 2000;

const int kHighestNetworkPreference = 127;

typedef struct {
  Network* net;
  std::vector<InterfaceAddress> ips;
} AddressList;

bool CompareNetworks(const Network* a, const Network* b) {
  if (a->prefix_length() == b->prefix_length()) {
    if (a->name() == b->name()) {
      return a->prefix() < b->prefix();
    }
  }
  return a->name() < b->name();
}

bool SortNetworks(const Network* a, const Network* b) {
  // Network types will be preferred above everything else while sorting
  // Networks.

  // Networks are sorted first by type.
  if (a->type() != b->type()) {
    return a->type() < b->type();
  }

  IPAddress ip_a = a->GetBestIP();
  IPAddress ip_b = b->GetBestIP();

  // After type, networks are sorted by IP address precedence values
  // from RFC 3484-bis
  if (IPAddressPrecedence(ip_a) != IPAddressPrecedence(ip_b)) {
    return IPAddressPrecedence(ip_a) > IPAddressPrecedence(ip_b);
  }

  // TODO(mallinath) - Add VPN and Link speed conditions while sorting.

  // Networks are sorted last by key.
  return a->key() > b->key();
}

std::string AdapterTypeToString(AdapterType type) {
  switch (type) {
    case ADAPTER_TYPE_UNKNOWN:
      return "Unknown";
    case ADAPTER_TYPE_ETHERNET:
      return "Ethernet";
    case ADAPTER_TYPE_WIFI:
      return "Wifi";
    case ADAPTER_TYPE_CELLULAR:
      return "Cellular";
    case ADAPTER_TYPE_VPN:
      return "VPN";
    case ADAPTER_TYPE_LOOPBACK:
      return "Loopback";
    default:
      RTC_DCHECK(false) << "Invalid type " << type;
      return std::string();
  }
}

#if !defined(__native_client__)
bool IsIgnoredIPv6(const InterfaceAddress& ip) {
  if (ip.family() != AF_INET6) {
    return false;
  }

  // Link-local addresses require scope id to be bound successfully.
  // However, our IPAddress structure doesn't carry that so the
  // information is lost and causes binding failure.
  if (IPIsLinkLocal(ip)) {
    return true;
  }

  // Any MAC based IPv6 should be avoided to prevent the MAC tracking.
  if (IPIsMacBased(ip)) {
    return true;
  }

  // Ignore deprecated IPv6.
  if (ip.ipv6_flags() & IPV6_ADDRESS_FLAG_DEPRECATED) {
    return true;
  }

  return false;
}
#endif  // !defined(__native_client__)

}  // namespace

// These addresses are used as the targets to find out the default local address
// on a multi-homed endpoint. They are actually DNS servers.
const char kPublicIPv4Host[] = "8.8.8.8";
const char kPublicIPv6Host[] = "2001:4860:4860::8888";
const int kPublicPort = 53;  // DNS port.

std::string MakeNetworkKey(const std::string& name, const IPAddress& prefix,
                           int prefix_length) {
  std::ostringstream ost;
  ost << name << "%" << prefix.ToString() << "/" << prefix_length;
  return ost.str();
}

NetworkManager::NetworkManager() {
}

NetworkManager::~NetworkManager() {
}

NetworkManager::EnumerationPermission NetworkManager::enumeration_permission()
    const {
  return ENUMERATION_ALLOWED;
}

bool NetworkManager::GetDefaultLocalAddress(int family, IPAddress* addr) const {
  return false;
}

NetworkManagerBase::NetworkManagerBase()
    : enumeration_permission_(NetworkManager::ENUMERATION_ALLOWED),
      max_ipv6_networks_(kMaxIPv6Networks),
      ipv6_enabled_(true) {
}

NetworkManagerBase::~NetworkManagerBase() {
  for (const auto& kv : networks_map_) {
    delete kv.second;
  }
}

NetworkManager::EnumerationPermission
NetworkManagerBase::enumeration_permission() const {
  return enumeration_permission_;
}

void NetworkManagerBase::GetAnyAddressNetworks(NetworkList* networks) {
  if (!ipv4_any_address_network_) {
    const rtc::IPAddress ipv4_any_address(INADDR_ANY);
    ipv4_any_address_network_.reset(
        new rtc::Network("any", "any", ipv4_any_address, 0));
    ipv4_any_address_network_->set_default_local_address_provider(this);
    ipv4_any_address_network_->AddIP(ipv4_any_address);
  }
  networks->push_back(ipv4_any_address_network_.get());

  if (ipv6_enabled()) {
    if (!ipv6_any_address_network_) {
      const rtc::IPAddress ipv6_any_address(in6addr_any);
      ipv6_any_address_network_.reset(
          new rtc::Network("any", "any", ipv6_any_address, 0));
      ipv6_any_address_network_->set_default_local_address_provider(this);
      ipv6_any_address_network_->AddIP(ipv6_any_address);
    }
    networks->push_back(ipv6_any_address_network_.get());
  }
}

void NetworkManagerBase::GetNetworks(NetworkList* result) const {
  int ipv6_networks = 0;
  result->clear();
  for (Network* network : networks_) {
    // Keep the number of IPv6 networks under |max_ipv6_networks_|.
    if (network->prefix().family() == AF_INET6) {
      if (ipv6_networks >= max_ipv6_networks_) {
        continue;
      }
      ++ipv6_networks;
    }
    result->push_back(network);
  }
}

void NetworkManagerBase::MergeNetworkList(const NetworkList& new_networks,
                                          bool* changed) {
  NetworkManager::Stats stats;
  MergeNetworkList(new_networks, changed, &stats);
}

void NetworkManagerBase::MergeNetworkList(const NetworkList& new_networks,
                                          bool* changed,
                                          NetworkManager::Stats* stats) {
  *changed = false;
  // AddressList in this map will track IP addresses for all Networks
  // with the same key.
  std::map<std::string, AddressList> consolidated_address_list;
  NetworkList list(new_networks);
  std::sort(list.begin(), list.end(), CompareNetworks);
  // First, build a set of network-keys to the ipaddresses.
  for (Network* network : list) {
    bool might_add_to_merged_list = false;
    std::string key = MakeNetworkKey(network->name(),
                                     network->prefix(),
                                     network->prefix_length());
    if (consolidated_address_list.find(key) ==
        consolidated_address_list.end()) {
      AddressList addrlist;
      addrlist.net = network;
      consolidated_address_list[key] = addrlist;
      might_add_to_merged_list = true;
    }
    const std::vector<InterfaceAddress>& addresses = network->GetIPs();
    AddressList& current_list = consolidated_address_list[key];
    for (const InterfaceAddress& address : addresses) {
      current_list.ips.push_back(address);
    }
    if (!might_add_to_merged_list) {
      delete network;
    } else {
      if (current_list.ips[0].family() == AF_INET) {
        stats->ipv4_network_count++;
      } else {
        ASSERT(current_list.ips[0].family() == AF_INET6);
        stats->ipv6_network_count++;
      }
    }
  }

  // Next, look for existing network objects to re-use.
  // Result of Network merge. Element in this list should have unique key.
  NetworkList merged_list;
  for (const auto& kv : consolidated_address_list) {
    const std::string& key = kv.first;
    Network* net = kv.second.net;
    auto existing = networks_map_.find(key);
    if (existing == networks_map_.end()) {
      // This network is new. Place it in the network map.
      merged_list.push_back(net);
      networks_map_[key] = net;
      net->set_id(next_available_network_id_++);
      // Also, we might have accumulated IPAddresses from the first
      // step, set it here.
      net->SetIPs(kv.second.ips, true);
      *changed = true;
    } else {
      // This network exists in the map already. Reset its IP addresses.
      Network* existing_net = existing->second;
      *changed = existing_net->SetIPs(kv.second.ips, *changed);
      merged_list.push_back(existing_net);
      if (net->type() != ADAPTER_TYPE_UNKNOWN &&
          net->type() != existing_net->type()) {
        existing_net->set_type(net->type());
        *changed = true;
      }
      // If the existing network was not active, networks have changed.
      if (!existing_net->active()) {
        *changed = true;
      }
      ASSERT(net->active());
      if (existing_net != net) {
        delete net;
      }
    }
  }
  // It may still happen that the merged list is a subset of |networks_|.
  // To detect this change, we compare their sizes.
  if (merged_list.size() != networks_.size()) {
    *changed = true;
  }

  // If the network list changes, we re-assign |networks_| to the merged list
  // and re-sort it.
  if (*changed) {
    networks_ = merged_list;
    // Reset the active states of all networks.
    for (const auto& kv : networks_map_) {
      Network* network = kv.second;
      // If |network| is in the newly generated |networks_|, it is active.
      bool found = std::find(networks_.begin(), networks_.end(), network) !=
                   networks_.end();
      network->set_active(found);
    }
    std::sort(networks_.begin(), networks_.end(), SortNetworks);
    // Now network interfaces are sorted, we should set the preference value
    // for each of the interfaces we are planning to use.
    // Preference order of network interfaces might have changed from previous
    // sorting due to addition of higher preference network interface.
    // Since we have already sorted the network interfaces based on our
    // requirements, we will just assign a preference value starting with 127,
    // in decreasing order.
    int pref = kHighestNetworkPreference;
    for (Network* network : networks_) {
      network->set_preference(pref);
      if (pref > 0) {
        --pref;
      } else {
        LOG(LS_ERROR) << "Too many network interfaces to handle!";
        break;
      }
    }
  }
}

void NetworkManagerBase::set_default_local_addresses(const IPAddress& ipv4,
                                                     const IPAddress& ipv6) {
  if (ipv4.family() == AF_INET) {
    default_local_ipv4_address_ = ipv4;
  }
  if (ipv6.family() == AF_INET6) {
    default_local_ipv6_address_ = ipv6;
  }
}

bool NetworkManagerBase::GetDefaultLocalAddress(int family,
                                                IPAddress* ipaddr) const {
  if (family == AF_INET && !default_local_ipv4_address_.IsNil()) {
    *ipaddr = default_local_ipv4_address_;
    return true;
  } else if (family == AF_INET6 && !default_local_ipv6_address_.IsNil()) {
    Network* ipv6_network = GetNetworkFromAddress(default_local_ipv6_address_);
    if (ipv6_network) {
      // If the default ipv6 network's BestIP is different than
      // default_local_ipv6_address_, use it instead.
      // This is to prevent potential IP address leakage. See WebRTC bug 5376.
      *ipaddr = ipv6_network->GetBestIP();
    } else {
      *ipaddr = default_local_ipv6_address_;
    }
    return true;
  }
  return false;
}

Network* NetworkManagerBase::GetNetworkFromAddress(
    const rtc::IPAddress& ip) const {
  for (Network* network : networks_) {
    const auto& ips = network->GetIPs();
    if (std::find_if(ips.begin(), ips.end(),
                     [ip](const InterfaceAddress& existing_ip) {
                       return ip == static_cast<rtc::IPAddress>(existing_ip);
                     }) != ips.end()) {
      return network;
    }
  }
  return nullptr;
}

BasicNetworkManager::BasicNetworkManager()
    : thread_(NULL), sent_first_update_(false), start_count_(0),
      ignore_non_default_routes_(false) {
}

BasicNetworkManager::~BasicNetworkManager() {
}

void BasicNetworkManager::OnNetworksChanged() {
  LOG(LS_INFO) << "Network change was observed";
  UpdateNetworksOnce();
}

#if defined(__native_client__)

bool BasicNetworkManager::CreateNetworks(bool include_ignored,
                                         NetworkList* networks) const {
  ASSERT(false);
  LOG(LS_WARNING) << "BasicNetworkManager doesn't work on NaCl yet";
  return false;
}

#elif defined(WEBRTC_POSIX)
void BasicNetworkManager::ConvertIfAddrs(struct ifaddrs* interfaces,
                                         IfAddrsConverter* ifaddrs_converter,
                                         bool include_ignored,
                                         NetworkList* networks) const {
  NetworkMap current_networks;

  for (struct ifaddrs* cursor = interfaces;
       cursor != NULL; cursor = cursor->ifa_next) {
    IPAddress prefix;
    IPAddress mask;
    InterfaceAddress ip;
    int scope_id = 0;

    // Some interfaces may not have address assigned.
    if (!cursor->ifa_addr || !cursor->ifa_netmask) {
      continue;
    }
    // Skip ones which are down.
    if (!(cursor->ifa_flags & IFF_RUNNING)) {
      continue;
    }
    // Skip unknown family.
    if (cursor->ifa_addr->sa_family != AF_INET &&
        cursor->ifa_addr->sa_family != AF_INET6) {
      continue;
    }
    // Skip IPv6 if not enabled.
    if (cursor->ifa_addr->sa_family == AF_INET6 && !ipv6_enabled()) {
      continue;
    }
    // Convert to InterfaceAddress.
    if (!ifaddrs_converter->ConvertIfAddrsToIPAddress(cursor, &ip, &mask)) {
      continue;
    }

    // Special case for IPv6 address.
    if (cursor->ifa_addr->sa_family == AF_INET6) {
      if (IsIgnoredIPv6(ip)) {
        continue;
      }
      scope_id =
          reinterpret_cast<sockaddr_in6*>(cursor->ifa_addr)->sin6_scope_id;
    }

    AdapterType adapter_type = ADAPTER_TYPE_UNKNOWN;
    if (cursor->ifa_flags & IFF_LOOPBACK) {
      adapter_type = ADAPTER_TYPE_LOOPBACK;
    } else {
      adapter_type = GetAdapterTypeFromName(cursor->ifa_name);
    }
    int prefix_length = CountIPMaskBits(mask);
    prefix = TruncateIP(ip, prefix_length);
    std::string key = MakeNetworkKey(std::string(cursor->ifa_name),
                                     prefix, prefix_length);
    auto iter = current_networks.find(key);
    if (iter == current_networks.end()) {
      // TODO(phoglund): Need to recognize other types as well.
      std::unique_ptr<Network> network(
          new Network(cursor->ifa_name, cursor->ifa_name, prefix, prefix_length,
                      adapter_type));
      network->set_default_local_address_provider(this);
      network->set_scope_id(scope_id);
      network->AddIP(ip);
      network->set_ignored(IsIgnoredNetwork(*network));
      if (include_ignored || !network->ignored()) {
        current_networks[key] = network.get();
        networks->push_back(network.release());
      }
    } else {
      Network* existing_network = iter->second;
      existing_network->AddIP(ip);
      if (adapter_type != ADAPTER_TYPE_UNKNOWN) {
        existing_network->set_type(adapter_type);
      }
    }
  }
}

bool BasicNetworkManager::CreateNetworks(bool include_ignored,
                                         NetworkList* networks) const {
  struct ifaddrs* interfaces;
  int error = getifaddrs(&interfaces);
  if (error != 0) {
    LOG_ERR(LERROR) << "getifaddrs failed to gather interface data: " << error;
    return false;
  }

  std::unique_ptr<IfAddrsConverter> ifaddrs_converter(CreateIfAddrsConverter());
  ConvertIfAddrs(interfaces, ifaddrs_converter.get(), include_ignored,
                 networks);

  freeifaddrs(interfaces);
  return true;
}

#elif defined(WEBRTC_WIN)

unsigned int GetPrefix(PIP_ADAPTER_PREFIX prefixlist,
              const IPAddress& ip, IPAddress* prefix) {
  IPAddress current_prefix;
  IPAddress best_prefix;
  unsigned int best_length = 0;
  while (prefixlist) {
    // Look for the longest matching prefix in the prefixlist.
    if (prefixlist->Address.lpSockaddr == NULL ||
        prefixlist->Address.lpSockaddr->sa_family != ip.family()) {
      prefixlist = prefixlist->Next;
      continue;
    }
    switch (prefixlist->Address.lpSockaddr->sa_family) {
      case AF_INET: {
        sockaddr_in* v4_addr =
            reinterpret_cast<sockaddr_in*>(prefixlist->Address.lpSockaddr);
        current_prefix = IPAddress(v4_addr->sin_addr);
        break;
      }
      case AF_INET6: {
          sockaddr_in6* v6_addr =
              reinterpret_cast<sockaddr_in6*>(prefixlist->Address.lpSockaddr);
          current_prefix = IPAddress(v6_addr->sin6_addr);
          break;
      }
      default: {
        prefixlist = prefixlist->Next;
        continue;
      }
    }
    if (TruncateIP(ip, prefixlist->PrefixLength) == current_prefix &&
        prefixlist->PrefixLength > best_length) {
      best_prefix = current_prefix;
      best_length = prefixlist->PrefixLength;
    }
    prefixlist = prefixlist->Next;
  }
  *prefix = best_prefix;
  return best_length;
}

bool BasicNetworkManager::CreateNetworks(bool include_ignored,
                                         NetworkList* networks) const {
  NetworkMap current_networks;
  // MSDN recommends a 15KB buffer for the first try at GetAdaptersAddresses.
  size_t buffer_size = 16384;
  std::unique_ptr<char[]> adapter_info(new char[buffer_size]);
  PIP_ADAPTER_ADDRESSES adapter_addrs =
      reinterpret_cast<PIP_ADAPTER_ADDRESSES>(adapter_info.get());
  int adapter_flags = (GAA_FLAG_SKIP_DNS_SERVER | GAA_FLAG_SKIP_ANYCAST |
                       GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_INCLUDE_PREFIX);
  int ret = 0;
  do {
    adapter_info.reset(new char[buffer_size]);
    adapter_addrs = reinterpret_cast<PIP_ADAPTER_ADDRESSES>(adapter_info.get());
    ret = GetAdaptersAddresses(AF_UNSPEC, adapter_flags,
                               0, adapter_addrs,
                               reinterpret_cast<PULONG>(&buffer_size));
  } while (ret == ERROR_BUFFER_OVERFLOW);
  if (ret != ERROR_SUCCESS) {
    return false;
  }
  int count = 0;
  while (adapter_addrs) {
    if (adapter_addrs->OperStatus == IfOperStatusUp) {
      PIP_ADAPTER_UNICAST_ADDRESS address = adapter_addrs->FirstUnicastAddress;
      PIP_ADAPTER_PREFIX prefixlist = adapter_addrs->FirstPrefix;
      std::string name;
      std::string description;
#if !defined(NDEBUG)
      name = ToUtf8(adapter_addrs->FriendlyName,
                    wcslen(adapter_addrs->FriendlyName));
#endif
      description = ToUtf8(adapter_addrs->Description,
                           wcslen(adapter_addrs->Description));
      for (; address; address = address->Next) {
#if defined(NDEBUG)
        name = rtc::ToString(count);
#endif

        IPAddress ip;
        int scope_id = 0;
        std::unique_ptr<Network> network;
        switch (address->Address.lpSockaddr->sa_family) {
          case AF_INET: {
            sockaddr_in* v4_addr =
                reinterpret_cast<sockaddr_in*>(address->Address.lpSockaddr);
            ip = IPAddress(v4_addr->sin_addr);
            break;
          }
          case AF_INET6: {
            if (ipv6_enabled()) {
              sockaddr_in6* v6_addr =
                  reinterpret_cast<sockaddr_in6*>(address->Address.lpSockaddr);
              scope_id = v6_addr->sin6_scope_id;
              ip = IPAddress(v6_addr->sin6_addr);

              if (IsIgnoredIPv6(ip)) {
                continue;
              }

              break;
            } else {
              continue;
            }
          }
          default: {
            continue;
          }
        }

        IPAddress prefix;
        int prefix_length = GetPrefix(prefixlist, ip, &prefix);
        std::string key = MakeNetworkKey(name, prefix, prefix_length);
        auto existing_network = current_networks.find(key);
        if (existing_network == current_networks.end()) {
          AdapterType adapter_type = ADAPTER_TYPE_UNKNOWN;
          if (adapter_addrs->IfType == IF_TYPE_SOFTWARE_LOOPBACK) {
            // TODO(phoglund): Need to recognize other types as well.
            adapter_type = ADAPTER_TYPE_LOOPBACK;
          }
          std::unique_ptr<Network> network(new Network(
              name, description, prefix, prefix_length, adapter_type));
          network->set_default_local_address_provider(this);
          network->set_scope_id(scope_id);
          network->AddIP(ip);
          bool ignored = IsIgnoredNetwork(*network);
          network->set_ignored(ignored);
          if (include_ignored || !network->ignored()) {
            current_networks[key] = network.get();
            networks->push_back(network.release());
          }
        } else {
          (*existing_network).second->AddIP(ip);
        }
      }
      // Count is per-adapter - all 'Networks' created from the same
      // adapter need to have the same name.
      ++count;
    }
    adapter_addrs = adapter_addrs->Next;
  }
  return true;
}
#endif  // WEBRTC_WIN

#if defined(WEBRTC_LINUX)
bool IsDefaultRoute(const std::string& network_name) {
  FileStream fs;
  if (!fs.Open("/proc/net/route", "r", NULL)) {
    LOG(LS_WARNING) << "Couldn't read /proc/net/route, skipping default "
                    << "route check (assuming everything is a default route).";
    return true;
  } else {
    std::string line;
    while (fs.ReadLine(&line) == SR_SUCCESS) {
      char iface_name[256];
      unsigned int iface_ip, iface_gw, iface_mask, iface_flags;
      if (sscanf(line.c_str(),
                 "%255s %8X %8X %4X %*d %*u %*d %8X",
                 iface_name, &iface_ip, &iface_gw,
                 &iface_flags, &iface_mask) == 5 &&
          network_name == iface_name &&
          iface_mask == 0 &&
          (iface_flags & (RTF_UP | RTF_HOST)) == RTF_UP) {
        return true;
      }
    }
  }
  return false;
}
#endif

bool BasicNetworkManager::IsIgnoredNetwork(const Network& network) const {
  // Ignore networks on the explicit ignore list.
  for (const std::string& ignored_name : network_ignore_list_) {
    if (network.name() == ignored_name) {
      return true;
    }
  }

#if defined(WEBRTC_POSIX)
  // Filter out VMware/VirtualBox interfaces, typically named vmnet1,
  // vmnet8, or vboxnet0.
  if (strncmp(network.name().c_str(), "vmnet", 5) == 0 ||
      strncmp(network.name().c_str(), "vnic", 4) == 0 ||
      strncmp(network.name().c_str(), "vboxnet", 7) == 0) {
    return true;
  }
#if defined(WEBRTC_LINUX)
  // Make sure this is a default route, if we're ignoring non-defaults.
  if (ignore_non_default_routes_ && !IsDefaultRoute(network.name())) {
    return true;
  }
#endif
#elif defined(WEBRTC_WIN)
  // Ignore any HOST side vmware adapters with a description like:
  // VMware Virtual Ethernet Adapter for VMnet1
  // but don't ignore any GUEST side adapters with a description like:
  // VMware Accelerated AMD PCNet Adapter #2
  if (strstr(network.description().c_str(), "VMnet") != NULL) {
    return true;
  }
#endif

  // Ignore any networks with a 0.x.y.z IP
  if (network.prefix().family() == AF_INET) {
    return (network.prefix().v4AddressAsHostOrderInteger() < 0x01000000);
  }

  return false;
}

void BasicNetworkManager::StartUpdating() {
  thread_ = Thread::Current();
  if (start_count_) {
    // If network interfaces are already discovered and signal is sent,
    // we should trigger network signal immediately for the new clients
    // to start allocating ports.
    if (sent_first_update_)
      thread_->Post(RTC_FROM_HERE, this, kSignalNetworksMessage);
  } else {
    thread_->Post(RTC_FROM_HERE, this, kUpdateNetworksMessage);
    StartNetworkMonitor();
  }
  ++start_count_;
}

void BasicNetworkManager::StopUpdating() {
  ASSERT(Thread::Current() == thread_);
  if (!start_count_)
    return;

  --start_count_;
  if (!start_count_) {
    thread_->Clear(this);
    sent_first_update_ = false;
    StopNetworkMonitor();
  }
}

void BasicNetworkManager::StartNetworkMonitor() {
  NetworkMonitorFactory* factory = NetworkMonitorFactory::GetFactory();
  if (factory == nullptr) {
    return;
  }
  if (!network_monitor_) {
    network_monitor_.reset(factory->CreateNetworkMonitor());
    if (!network_monitor_) {
      return;
    }
    network_monitor_->SignalNetworksChanged.connect(
        this, &BasicNetworkManager::OnNetworksChanged);
  }
  network_monitor_->Start();
}

void BasicNetworkManager::StopNetworkMonitor() {
  if (!network_monitor_) {
    return;
  }
  network_monitor_->Stop();
}

void BasicNetworkManager::OnMessage(Message* msg) {
  switch (msg->message_id) {
    case kUpdateNetworksMessage: {
      UpdateNetworksContinually();
      break;
    }
    case kSignalNetworksMessage:  {
      SignalNetworksChanged();
      break;
    }
    default:
      ASSERT(false);
  }
}

AdapterType BasicNetworkManager::GetAdapterTypeFromName(
    const char* network_name) const {
  // If there is a network_monitor, use it to get the adapter type.
  // Otherwise, get the adapter type based on a few name matching rules.
  if (network_monitor_) {
    AdapterType type = network_monitor_->GetAdapterType(network_name);
    if (type != ADAPTER_TYPE_UNKNOWN) {
      return type;
    }
  }
#if defined(WEBRTC_IOS)
  // Cell networks are pdp_ipN on iOS.
  if (strncmp(network_name, "pdp_ip", 6) == 0) {
    return ADAPTER_TYPE_CELLULAR;
  }
  if (strncmp(network_name, "en", 2) == 0) {
    // This may not be most accurate because sometimes Ethernet interface
    // name also starts with "en" but it is better than showing it as
    // "unknown" type.
    // TODO(honghaiz): Write a proper IOS network manager.
    return ADAPTER_TYPE_WIFI;
  }
#elif defined(WEBRTC_ANDROID)
  if (strncmp(network_name, "rmnet", 5) == 0 ||
      strncmp(network_name, "v4-rmnet", 8) == 0) {
    return ADAPTER_TYPE_CELLULAR;
  }
  if (strncmp(network_name, "wlan", 4) == 0) {
    return ADAPTER_TYPE_WIFI;
  }
#endif

  return ADAPTER_TYPE_UNKNOWN;
}

IPAddress BasicNetworkManager::QueryDefaultLocalAddress(int family) const {
  ASSERT(thread_ == Thread::Current());
  ASSERT(thread_->socketserver() != nullptr);
  ASSERT(family == AF_INET || family == AF_INET6);

  std::unique_ptr<AsyncSocket> socket(
      thread_->socketserver()->CreateAsyncSocket(family, SOCK_DGRAM));
  if (!socket) {
    LOG_ERR(LERROR) << "Socket creation failed";
    return IPAddress();
  }

  if (socket->Connect(SocketAddress(
          family == AF_INET ? kPublicIPv4Host : kPublicIPv6Host, kPublicPort)) <
      0) {
    LOG(LS_INFO) << "Connect failed with " << socket->GetError();
    return IPAddress();
  }
  return socket->GetLocalAddress().ipaddr();
}

void BasicNetworkManager::UpdateNetworksOnce() {
  if (!start_count_)
    return;

  ASSERT(Thread::Current() == thread_);

  NetworkList list;
  if (!CreateNetworks(false, &list)) {
    SignalError();
  } else {
    bool changed;
    NetworkManager::Stats stats;
    MergeNetworkList(list, &changed, &stats);
    set_default_local_addresses(QueryDefaultLocalAddress(AF_INET),
                                QueryDefaultLocalAddress(AF_INET6));
    if (changed || !sent_first_update_) {
      SignalNetworksChanged();
      sent_first_update_ = true;
    }
  }
}

void BasicNetworkManager::UpdateNetworksContinually() {
  UpdateNetworksOnce();
  thread_->PostDelayed(RTC_FROM_HERE, kNetworksUpdateIntervalMs, this,
                       kUpdateNetworksMessage);
}

void BasicNetworkManager::DumpNetworks() {
  NetworkList list;
  GetNetworks(&list);
  LOG(LS_INFO) << "NetworkManager detected " << list.size() << " networks:";
  for (const Network* network : list) {
    LOG(LS_INFO) << network->ToString() << ": " << network->description()
                 << ", active ? " << network->active()
                 << ((network->ignored()) ? ", Ignored" : "");
  }
}

Network::Network(const std::string& name,
                 const std::string& desc,
                 const IPAddress& prefix,
                 int prefix_length)
    : name_(name),
      description_(desc),
      prefix_(prefix),
      prefix_length_(prefix_length),
      key_(MakeNetworkKey(name, prefix, prefix_length)),
      scope_id_(0),
      ignored_(false),
      type_(ADAPTER_TYPE_UNKNOWN),
      preference_(0) {}

Network::Network(const std::string& name,
                 const std::string& desc,
                 const IPAddress& prefix,
                 int prefix_length,
                 AdapterType type)
    : name_(name),
      description_(desc),
      prefix_(prefix),
      prefix_length_(prefix_length),
      key_(MakeNetworkKey(name, prefix, prefix_length)),
      scope_id_(0),
      ignored_(false),
      type_(type),
      preference_(0) {}

Network::~Network() = default;

// Sets the addresses of this network. Returns true if the address set changed.
// Change detection is short circuited if the changed argument is true.
bool Network::SetIPs(const std::vector<InterfaceAddress>& ips, bool changed) {
  // Detect changes with a nested loop; n-squared but we expect on the order
  // of 2-3 addresses per network.
  changed = changed || ips.size() != ips_.size();
  if (!changed) {
    for (const InterfaceAddress& ip : ips) {
      if (std::find(ips_.begin(), ips_.end(), ip) == ips_.end()) {
        changed = true;
        break;
      }
    }
  }

  ips_ = ips;
  return changed;
}

// Select the best IP address to use from this Network.
IPAddress Network::GetBestIP() const {
  if (ips_.size() == 0) {
    return IPAddress();
  }

  if (prefix_.family() == AF_INET) {
    return static_cast<IPAddress>(ips_.at(0));
  }

  InterfaceAddress selected_ip, ula_ip;

  for (const InterfaceAddress& ip : ips_) {
    // Ignore any address which has been deprecated already.
    if (ip.ipv6_flags() & IPV6_ADDRESS_FLAG_DEPRECATED)
      continue;

    // ULA address should only be returned when we have no other
    // global IP.
    if (IPIsULA(static_cast<const IPAddress&>(ip))) {
      ula_ip = ip;
      continue;
    }
    selected_ip = ip;

    // Search could stop once a temporary non-deprecated one is found.
    if (ip.ipv6_flags() & IPV6_ADDRESS_FLAG_TEMPORARY)
      break;
  }

  // No proper global IPv6 address found, use ULA instead.
  if (IPIsUnspec(selected_ip) && !IPIsUnspec(ula_ip)) {
    selected_ip = ula_ip;
  }

  return static_cast<IPAddress>(selected_ip);
}

std::string Network::ToString() const {
  std::stringstream ss;
  // Print out the first space-terminated token of the network desc, plus
  // the IP address.
  ss << "Net[" << description_.substr(0, description_.find(' '))
     << ":" << prefix_.ToSensitiveString() << "/" << prefix_length_
     << ":" << AdapterTypeToString(type_) << "]";
  return ss.str();
}

}  // namespace rtc
