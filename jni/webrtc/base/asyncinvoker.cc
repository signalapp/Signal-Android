/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/asyncinvoker.h"

#include "webrtc/base/checks.h"
#include "webrtc/base/logging.h"

namespace rtc {

AsyncInvoker::AsyncInvoker() : destroying_(false) {}

AsyncInvoker::~AsyncInvoker() {
  destroying_ = true;
  SignalInvokerDestroyed();
  // Messages for this need to be cleared *before* our destructor is complete.
  MessageQueueManager::Clear(this);
}

void AsyncInvoker::OnMessage(Message* msg) {
  // Get the AsyncClosure shared ptr from this message's data.
  ScopedRefMessageData<AsyncClosure>* data =
      static_cast<ScopedRefMessageData<AsyncClosure>*>(msg->pdata);
  scoped_refptr<AsyncClosure> closure = data->data();
  delete msg->pdata;
  msg->pdata = NULL;

  // Execute the closure and trigger the return message if needed.
  closure->Execute();
}

void AsyncInvoker::Flush(Thread* thread, uint32_t id /*= MQID_ANY*/) {
  if (destroying_) return;

  // Run this on |thread| to reduce the number of context switches.
  if (Thread::Current() != thread) {
    thread->Invoke<void>(RTC_FROM_HERE,
                         Bind(&AsyncInvoker::Flush, this, thread, id));
    return;
  }

  MessageList removed;
  thread->Clear(this, id, &removed);
  for (MessageList::iterator it = removed.begin(); it != removed.end(); ++it) {
    // This message was pending on this thread, so run it now.
    thread->Send(it->posted_from, it->phandler, it->message_id, it->pdata);
  }
}

void AsyncInvoker::DoInvoke(const Location& posted_from,
                            Thread* thread,
                            const scoped_refptr<AsyncClosure>& closure,
                            uint32_t id) {
  if (destroying_) {
    LOG(LS_WARNING) << "Tried to invoke while destroying the invoker.";
    return;
  }
  thread->Post(posted_from, this, id,
               new ScopedRefMessageData<AsyncClosure>(closure));
}

void AsyncInvoker::DoInvokeDelayed(const Location& posted_from,
                                   Thread* thread,
                                   const scoped_refptr<AsyncClosure>& closure,
                                   uint32_t delay_ms,
                                   uint32_t id) {
  if (destroying_) {
    LOG(LS_WARNING) << "Tried to invoke while destroying the invoker.";
    return;
  }
  thread->PostDelayed(posted_from, delay_ms, this, id,
                      new ScopedRefMessageData<AsyncClosure>(closure));
}

GuardedAsyncInvoker::GuardedAsyncInvoker() : thread_(Thread::Current()) {
  thread_->SignalQueueDestroyed.connect(this,
                                        &GuardedAsyncInvoker::ThreadDestroyed);
}

GuardedAsyncInvoker::~GuardedAsyncInvoker() {
}

bool GuardedAsyncInvoker::Flush(uint32_t id) {
  rtc::CritScope cs(&crit_);
  if (thread_ == nullptr)
    return false;
  invoker_.Flush(thread_, id);
  return true;
}

void GuardedAsyncInvoker::ThreadDestroyed() {
  rtc::CritScope cs(&crit_);
  // We should never get more than one notification about the thread dying.
  RTC_DCHECK(thread_ != nullptr);
  thread_ = nullptr;
}

NotifyingAsyncClosureBase::NotifyingAsyncClosureBase(
    AsyncInvoker* invoker,
    const Location& callback_posted_from,
    Thread* calling_thread)
    : invoker_(invoker),
      callback_posted_from_(callback_posted_from),
      calling_thread_(calling_thread) {
  calling_thread->SignalQueueDestroyed.connect(
      this, &NotifyingAsyncClosureBase::CancelCallback);
  invoker->SignalInvokerDestroyed.connect(
      this, &NotifyingAsyncClosureBase::CancelCallback);
}

NotifyingAsyncClosureBase::~NotifyingAsyncClosureBase() {
  disconnect_all();
}

void NotifyingAsyncClosureBase::TriggerCallback() {
  CritScope cs(&crit_);
  if (!CallbackCanceled() && !callback_.empty()) {
    invoker_->AsyncInvoke<void>(callback_posted_from_, calling_thread_,
                                callback_);
  }
}

void NotifyingAsyncClosureBase::CancelCallback() {
  // If the callback is triggering when this is called, block the
  // destructor of the dying object here by waiting until the callback
  // is done triggering.
  CritScope cs(&crit_);
  // calling_thread_ == NULL means do not trigger the callback.
  calling_thread_ = NULL;
}

}  // namespace rtc
