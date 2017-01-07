/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/messagequeue.h"

#include "webrtc/base/bind.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/thread.h"
#include "webrtc/base/timeutils.h"
#include "webrtc/base/nullsocketserver.h"

using namespace rtc;

class MessageQueueTest: public testing::Test, public MessageQueue {
 public:
  MessageQueueTest() : MessageQueue(SocketServer::CreateDefault(), true) {}
  bool IsLocked_Worker() {
    if (!crit_.TryEnter()) {
      return true;
    }
    crit_.Leave();
    return false;
  }
  bool IsLocked() {
    // We have to do this on a worker thread, or else the TryEnter will
    // succeed, since our critical sections are reentrant.
    Thread worker;
    worker.Start();
    return worker.Invoke<bool>(
        RTC_FROM_HERE, rtc::Bind(&MessageQueueTest::IsLocked_Worker, this));
  }
};

struct DeletedLockChecker {
  DeletedLockChecker(MessageQueueTest* test, bool* was_locked, bool* deleted)
      : test(test), was_locked(was_locked), deleted(deleted) { }
  ~DeletedLockChecker() {
    *deleted = true;
    *was_locked = test->IsLocked();
  }
  MessageQueueTest* test;
  bool* was_locked;
  bool* deleted;
};

static void DelayedPostsWithIdenticalTimesAreProcessedInFifoOrder(
    MessageQueue* q) {
  EXPECT_TRUE(q != NULL);
  int64_t now = TimeMillis();
  q->PostAt(RTC_FROM_HERE, now, NULL, 3);
  q->PostAt(RTC_FROM_HERE, now - 2, NULL, 0);
  q->PostAt(RTC_FROM_HERE, now - 1, NULL, 1);
  q->PostAt(RTC_FROM_HERE, now, NULL, 4);
  q->PostAt(RTC_FROM_HERE, now - 1, NULL, 2);

  Message msg;
  for (size_t i=0; i<5; ++i) {
    memset(&msg, 0, sizeof(msg));
    EXPECT_TRUE(q->Get(&msg, 0));
    EXPECT_EQ(i, msg.message_id);
  }

  EXPECT_FALSE(q->Get(&msg, 0));  // No more messages
}

TEST_F(MessageQueueTest,
       DelayedPostsWithIdenticalTimesAreProcessedInFifoOrder) {
  MessageQueue q(SocketServer::CreateDefault(), true);
  DelayedPostsWithIdenticalTimesAreProcessedInFifoOrder(&q);

  NullSocketServer nullss;
  MessageQueue q_nullss(&nullss, true);
  DelayedPostsWithIdenticalTimesAreProcessedInFifoOrder(&q_nullss);
}

TEST_F(MessageQueueTest, DisposeNotLocked) {
  bool was_locked = true;
  bool deleted = false;
  DeletedLockChecker* d = new DeletedLockChecker(this, &was_locked, &deleted);
  Dispose(d);
  Message msg;
  EXPECT_FALSE(Get(&msg, 0));
  EXPECT_TRUE(deleted);
  EXPECT_FALSE(was_locked);
}

class DeletedMessageHandler : public MessageHandler {
 public:
  explicit DeletedMessageHandler(bool* deleted) : deleted_(deleted) { }
  ~DeletedMessageHandler() {
    *deleted_ = true;
  }
  void OnMessage(Message* msg) { }
 private:
  bool* deleted_;
};

TEST_F(MessageQueueTest, DiposeHandlerWithPostedMessagePending) {
  bool deleted = false;
  DeletedMessageHandler *handler = new DeletedMessageHandler(&deleted);
  // First, post a dispose.
  Dispose(handler);
  // Now, post a message, which should *not* be returned by Get().
  Post(RTC_FROM_HERE, handler, 1);
  Message msg;
  EXPECT_FALSE(Get(&msg, 0));
  EXPECT_TRUE(deleted);
}

struct UnwrapMainThreadScope {
  UnwrapMainThreadScope() : rewrap_(Thread::Current() != NULL) {
    if (rewrap_) ThreadManager::Instance()->UnwrapCurrentThread();
  }
  ~UnwrapMainThreadScope() {
    if (rewrap_) ThreadManager::Instance()->WrapCurrentThread();
  }
 private:
  bool rewrap_;
};

TEST(MessageQueueManager, Clear) {
  UnwrapMainThreadScope s;
  if (MessageQueueManager::IsInitialized()) {
    LOG(LS_INFO) << "Unable to run MessageQueueManager::Clear test, since the "
                 << "MessageQueueManager was already initialized by some "
                 << "other test in this run.";
    return;
  }
  bool deleted = false;
  DeletedMessageHandler* handler = new DeletedMessageHandler(&deleted);
  delete handler;
  EXPECT_TRUE(deleted);
  EXPECT_FALSE(MessageQueueManager::IsInitialized());
}
