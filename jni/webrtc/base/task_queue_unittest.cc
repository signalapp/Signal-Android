/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <vector>

#include "webrtc/base/bind.h"
#include "webrtc/base/event.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/task_queue.h"
#include "webrtc/base/timeutils.h"

namespace rtc {

namespace {
void CheckCurrent(const char* expected_queue, Event* signal, TaskQueue* queue) {
  EXPECT_TRUE(TaskQueue::IsCurrent(expected_queue));
  EXPECT_TRUE(queue->IsCurrent());
  if (signal)
    signal->Set();
}

}  // namespace

TEST(TaskQueueTest, Construct) {
  static const char kQueueName[] = "Construct";
  TaskQueue queue(kQueueName);
  EXPECT_FALSE(queue.IsCurrent());
}

TEST(TaskQueueTest, PostAndCheckCurrent) {
  static const char kQueueName[] = "PostAndCheckCurrent";
  TaskQueue queue(kQueueName);

  // We're not running a task, so there shouldn't be a current queue.
  EXPECT_FALSE(queue.IsCurrent());
  EXPECT_FALSE(TaskQueue::Current());

  Event event(false, false);
  queue.PostTask(Bind(&CheckCurrent, kQueueName, &event, &queue));
  EXPECT_TRUE(event.Wait(1000));
}

TEST(TaskQueueTest, PostCustomTask) {
  static const char kQueueName[] = "PostCustomImplementation";
  TaskQueue queue(kQueueName);

  Event event(false, false);

  class CustomTask : public QueuedTask {
   public:
    explicit CustomTask(Event* event) : event_(event) {}

   private:
    bool Run() override {
      event_->Set();
      return false;  // Never allows the task to be deleted by the queue.
    }

    Event* const event_;
  } my_task(&event);

  // Please don't do this in production code! :)
  queue.PostTask(std::unique_ptr<QueuedTask>(&my_task));
  EXPECT_TRUE(event.Wait(1000));
}

TEST(TaskQueueTest, PostLambda) {
  static const char kQueueName[] = "PostLambda";
  TaskQueue queue(kQueueName);

  Event event(false, false);
  queue.PostTask([&event]() { event.Set(); });
  EXPECT_TRUE(event.Wait(1000));
}

TEST(TaskQueueTest, PostFromQueue) {
  static const char kQueueName[] = "PostFromQueue";
  TaskQueue queue(kQueueName);

  Event event(false, false);
  queue.PostTask(
      [&event, &queue]() { queue.PostTask([&event]() { event.Set(); }); });
  EXPECT_TRUE(event.Wait(1000));
}

TEST(TaskQueueTest, PostDelayed) {
  static const char kQueueName[] = "PostDelayed";
  TaskQueue queue(kQueueName);

  Event event(false, false);
  uint32_t start = Time();
  queue.PostDelayedTask(Bind(&CheckCurrent, kQueueName, &event, &queue), 100);
  EXPECT_TRUE(event.Wait(1000));
  uint32_t end = Time();
  EXPECT_GE(end - start, 100u);
  EXPECT_NEAR(end - start, 200u, 100u);  // Accept 100-300.
}

TEST(TaskQueueTest, PostMultipleDelayed) {
  static const char kQueueName[] = "PostMultipleDelayed";
  TaskQueue queue(kQueueName);

  std::vector<std::unique_ptr<Event>> events;
  for (int i = 0; i < 10; ++i) {
    events.push_back(std::unique_ptr<Event>(new Event(false, false)));
    queue.PostDelayedTask(
        Bind(&CheckCurrent, kQueueName, events.back().get(), &queue), 10);
  }

  for (const auto& e : events)
    EXPECT_TRUE(e->Wait(100));
}

TEST(TaskQueueTest, PostDelayedAfterDestruct) {
  static const char kQueueName[] = "PostDelayedAfterDestruct";
  Event event(false, false);
  {
    TaskQueue queue(kQueueName);
    queue.PostDelayedTask(Bind(&CheckCurrent, kQueueName, &event, &queue), 100);
  }
  EXPECT_FALSE(event.Wait(200));  // Task should not run.
}

TEST(TaskQueueTest, PostAndReply) {
  static const char kPostQueue[] = "PostQueue";
  static const char kReplyQueue[] = "ReplyQueue";
  TaskQueue post_queue(kPostQueue);
  TaskQueue reply_queue(kReplyQueue);

  Event event(false, false);
  post_queue.PostTaskAndReply(
      Bind(&CheckCurrent, kPostQueue, nullptr, &post_queue),
      Bind(&CheckCurrent, kReplyQueue, &event, &reply_queue), &reply_queue);
  EXPECT_TRUE(event.Wait(1000));
}

TEST(TaskQueueTest, PostAndReuse) {
  static const char kPostQueue[] = "PostQueue";
  static const char kReplyQueue[] = "ReplyQueue";
  TaskQueue post_queue(kPostQueue);
  TaskQueue reply_queue(kReplyQueue);

  int call_count = 0;

  class ReusedTask : public QueuedTask {
   public:
    ReusedTask(int* counter, TaskQueue* reply_queue, Event* event)
        : counter_(counter), reply_queue_(reply_queue), event_(event) {
      EXPECT_EQ(0, *counter_);
    }

   private:
    bool Run() override {
      if (++(*counter_) == 1) {
        std::unique_ptr<QueuedTask> myself(this);
        reply_queue_->PostTask(std::move(myself));
        // At this point, the object is owned by reply_queue_ and it's
        // theoratically possible that the object has been deleted (e.g. if
        // posting wasn't possible).  So, don't touch any member variables here.

        // Indicate to the current queue that ownership has been transferred.
        return false;
      } else {
        EXPECT_EQ(2, *counter_);
        EXPECT_TRUE(reply_queue_->IsCurrent());
        event_->Set();
        return true;  // Indicate that the object should be deleted.
      }
    }

    int* const counter_;
    TaskQueue* const reply_queue_;
    Event* const event_;
  };

  Event event(false, false);
  std::unique_ptr<QueuedTask> task(
      new ReusedTask(&call_count, &reply_queue, &event));

  post_queue.PostTask(std::move(task));
  EXPECT_TRUE(event.Wait(1000));
}

TEST(TaskQueueTest, PostAndReplyLambda) {
  static const char kPostQueue[] = "PostQueue";
  static const char kReplyQueue[] = "ReplyQueue";
  TaskQueue post_queue(kPostQueue);
  TaskQueue reply_queue(kReplyQueue);

  Event event(false, false);
  bool my_flag = false;
  post_queue.PostTaskAndReply([&my_flag]() { my_flag = true; },
                              [&event]() { event.Set(); }, &reply_queue);
  EXPECT_TRUE(event.Wait(1000));
  EXPECT_TRUE(my_flag);
}

void TestPostTaskAndReply(TaskQueue* work_queue,
                          const char* work_queue_name,
                          Event* event) {
  ASSERT_FALSE(work_queue->IsCurrent());
  work_queue->PostTaskAndReply(
      Bind(&CheckCurrent, work_queue_name, nullptr, work_queue),
      NewClosure([event]() { event->Set(); }));
}

// Does a PostTaskAndReply from within a task to post and reply to the current
// queue.  All in all there will be 3 tasks posted and run.
TEST(TaskQueueTest, PostAndReply2) {
  static const char kQueueName[] = "PostAndReply2";
  static const char kWorkQueueName[] = "PostAndReply2_Worker";
  TaskQueue queue(kQueueName);
  TaskQueue work_queue(kWorkQueueName);

  Event event(false, false);
  queue.PostTask(
      Bind(&TestPostTaskAndReply, &work_queue, kWorkQueueName, &event));
  EXPECT_TRUE(event.Wait(1000));
}

// Tests posting more messages than a queue can queue up.
// In situations like that, tasks will get dropped.
TEST(TaskQueueTest, PostALot) {
  // To destruct the event after the queue has gone out of scope.
  Event event(false, false);

  int tasks_executed = 0;
  int tasks_cleaned_up = 0;
  static const int kTaskCount = 0xffff;

  {
    static const char kQueueName[] = "PostALot";
    TaskQueue queue(kQueueName);

    // On linux, the limit of pending bytes in the pipe buffer is 0xffff.
    // So here we post a total of 0xffff+1 messages, which triggers a failure
    // case inside of the libevent queue implementation.

    queue.PostTask([&event]() { event.Wait(Event::kForever); });
    for (int i = 0; i < kTaskCount; ++i)
      queue.PostTask(NewClosure([&tasks_executed]() { ++tasks_executed; },
                                [&tasks_cleaned_up]() { ++tasks_cleaned_up; }));
    event.Set();  // Unblock the first task.
  }

  EXPECT_GE(tasks_cleaned_up, tasks_executed);
  EXPECT_EQ(kTaskCount, tasks_cleaned_up);

  LOG(INFO) << "tasks executed: " << tasks_executed
            << ", tasks cleaned up: " << tasks_cleaned_up;
}

}  // namespace rtc
