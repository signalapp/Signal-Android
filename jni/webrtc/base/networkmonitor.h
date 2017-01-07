/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_NETWORKMONITOR_H_
#define WEBRTC_BASE_NETWORKMONITOR_H_

#include "webrtc/base/logging.h"
#include "webrtc/base/sigslot.h"
#include "webrtc/base/thread.h"

namespace rtc {

class IPAddress;

// Error values are negative.
enum NetworkBindingResults {
  NETWORK_BIND_SUCCESS = 0,   // No error
  NETWORK_BIND_FAILURE = -1,  // Generic error
  NETWORK_BIND_NOT_IMPLEMENTED = -2,
  NETWORK_BIND_ADDRESS_NOT_FOUND = -3,
  NETWORK_BIND_NETWORK_CHANGED = -4
};

enum AdapterType {
  // This enum resembles the one in Chromium net::ConnectionType.
  ADAPTER_TYPE_UNKNOWN = 0,
  ADAPTER_TYPE_ETHERNET = 1 << 0,
  ADAPTER_TYPE_WIFI = 1 << 1,
  ADAPTER_TYPE_CELLULAR = 1 << 2,
  ADAPTER_TYPE_VPN = 1 << 3,
  ADAPTER_TYPE_LOOPBACK = 1 << 4
};

class NetworkBinderInterface {
 public:
  // Binds a socket to the network that is attached to |address| so that all
  // packets on the socket |socket_fd| will be sent via that network.
  // This is needed because some operating systems (like Android) require a
  // special bind call to put packets on a non-default network interface.
  virtual int BindSocketToNetwork(int socket_fd, const IPAddress& address) = 0;
  virtual ~NetworkBinderInterface() {}
};

/*
 * Receives network-change events via |OnNetworksChanged| and signals the
 * networks changed event.
 *
 * Threading consideration:
 * It is expected that all upstream operations (from native to Java) are
 * performed from the worker thread. This includes creating, starting and
 * stopping the monitor. This avoids the potential race condition when creating
 * the singleton Java NetworkMonitor class. Downstream operations can be from
 * any thread, but this class will forward all the downstream operations onto
 * the worker thread.
 *
 * Memory consideration:
 * NetworkMonitor is owned by the caller (NetworkManager). The global network
 * monitor factory is owned by the factory itself but needs to be released from
 * the factory creator.
 */
// Generic network monitor interface. It starts and stops monitoring network
// changes, and fires the SignalNetworksChanged event when networks change.
class NetworkMonitorInterface {
 public:
  NetworkMonitorInterface();
  virtual ~NetworkMonitorInterface();

  sigslot::signal0<> SignalNetworksChanged;

  virtual void Start() = 0;
  virtual void Stop() = 0;

  // Implementations should call this method on the base when networks change,
  // and the base will fire SignalNetworksChanged on the right thread.
  virtual void OnNetworksChanged() = 0;

  virtual AdapterType GetAdapterType(const std::string& interface_name) = 0;
};

class NetworkMonitorBase : public NetworkMonitorInterface,
                           public MessageHandler,
                           public sigslot::has_slots<> {
 public:
  NetworkMonitorBase();
  ~NetworkMonitorBase() override;

  void OnNetworksChanged() override;

  void OnMessage(Message* msg) override;

 protected:
  Thread* worker_thread() { return worker_thread_; }

 private:
  Thread* worker_thread_;
};

/*
 * NetworkMonitorFactory creates NetworkMonitors.
 */
class NetworkMonitorFactory {
 public:
  // This is not thread-safe; it should be called once (or once per audio/video
  // call) during the call initialization.
  static void SetFactory(NetworkMonitorFactory* factory);

  static void ReleaseFactory(NetworkMonitorFactory* factory);
  static NetworkMonitorFactory* GetFactory();

  virtual NetworkMonitorInterface* CreateNetworkMonitor() = 0;

  virtual ~NetworkMonitorFactory();

 protected:
  NetworkMonitorFactory();
};

}  // namespace rtc

#endif  // WEBRTC_BASE_NETWORKMONITOR_H_
