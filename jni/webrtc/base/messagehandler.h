/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_MESSAGEHANDLER_H_
#define WEBRTC_BASE_MESSAGEHANDLER_H_

#include <memory>
#include <utility>

#include "webrtc/base/constructormagic.h"

namespace rtc {

struct Message;

// Messages get dispatched to a MessageHandler

class MessageHandler {
 public:
  virtual ~MessageHandler();
  virtual void OnMessage(Message* msg) = 0;

 protected:
  MessageHandler() {}

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(MessageHandler);
};

// Helper class to facilitate executing a functor on a thread.
template <class ReturnT, class FunctorT>
class FunctorMessageHandler : public MessageHandler {
 public:
  explicit FunctorMessageHandler(const FunctorT& functor)
      : functor_(functor) {}
  virtual void OnMessage(Message* msg) {
    result_ = functor_();
  }
  const ReturnT& result() const { return result_; }

 private:
  FunctorT functor_;
  ReturnT result_;
};

// Specialization for std::unique_ptr<ReturnT>.
template <class ReturnT, class FunctorT>
class FunctorMessageHandler<class std::unique_ptr<ReturnT>, FunctorT>
    : public MessageHandler {
 public:
  explicit FunctorMessageHandler(const FunctorT& functor) : functor_(functor) {}
  virtual void OnMessage(Message* msg) { result_ = std::move(functor_()); }
  std::unique_ptr<ReturnT> result() { return std::move(result_); }

 private:
  FunctorT functor_;
  std::unique_ptr<ReturnT> result_;
};

// Specialization for ReturnT of void.
template <class FunctorT>
class FunctorMessageHandler<void, FunctorT> : public MessageHandler {
 public:
  explicit FunctorMessageHandler(const FunctorT& functor)
      : functor_(functor) {}
  virtual void OnMessage(Message* msg) {
    functor_();
  }
  void result() const {}

 private:
  FunctorT functor_;
};

} // namespace rtc

#endif // WEBRTC_BASE_MESSAGEHANDLER_H_
