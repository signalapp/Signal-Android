/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_WORKER_H_
#define WEBRTC_BASE_WORKER_H_

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/messagehandler.h"

namespace rtc {

class Thread;

// A worker is an object that performs some specific long-lived task in an
// event-driven manner.
// The only method that should be considered thread-safe is HaveWork(), which
// allows you to signal the availability of work from any thread. All other
// methods are thread-hostile. Specifically:
// StartWork()/StopWork() should not be called concurrently with themselves or
// each other, and it is an error to call them while the worker is running on
// a different thread.
// The destructor may not be called if the worker is currently running
// (regardless of the thread), but you can call StopWork() in a subclass's
// destructor.
class Worker : private MessageHandler {
 public:
  Worker();

  // Destroys this Worker, but it must have already been stopped via StopWork().
  ~Worker() override;

  // Attaches the worker to the current thread and begins processing work if not
  // already doing so.
  bool StartWork();
  // Stops processing work if currently doing so and detaches from the current
  // thread.
  bool StopWork();

 protected:
  // Signal that work is available to be done. May only be called within the
  // lifetime of a OnStart()/OnStop() pair.
  void HaveWork();

  // These must be implemented by a subclass.
  // Called on the worker thread to start working.
  virtual void OnStart() = 0;
  // Called on the worker thread when work has been signalled via HaveWork().
  virtual void OnHaveWork() = 0;
  // Called on the worker thread to stop working. Upon return, any pending
  // OnHaveWork() calls are cancelled.
  virtual void OnStop() = 0;

 private:
  // Inherited from MessageHandler.
  void OnMessage(Message* msg) override;

  // The thread that is currently doing the work.
  Thread *worker_thread_;

  RTC_DISALLOW_COPY_AND_ASSIGN(Worker);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_WORKER_H_
