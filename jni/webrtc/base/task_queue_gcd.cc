/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains the implementation of TaskQueue for Mac and iOS.
// The implementation uses Grand Central Dispatch queues (GCD) to
// do the actual task queuing.

#include "webrtc/base/task_queue.h"

#include <string.h>

#include "webrtc/base/checks.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/task_queue_posix.h"

namespace rtc {
using internal::GetQueuePtrTls;
using internal::AutoSetCurrentQueuePtr;

struct TaskQueue::QueueContext {
  explicit QueueContext(TaskQueue* q) : queue(q), is_active(true) {}

  static void SetNotActive(void* context) {
    QueueContext* qc = static_cast<QueueContext*>(context);
    qc->is_active = false;
  }

  static void DeleteContext(void* context) {
    QueueContext* qc = static_cast<QueueContext*>(context);
    delete qc;
  }

  TaskQueue* const queue;
  bool is_active;
};

struct TaskQueue::TaskContext {
  TaskContext(QueueContext* queue_ctx, std::unique_ptr<QueuedTask> task)
      : queue_ctx(queue_ctx), task(std::move(task)) {}
  virtual ~TaskContext() {}

  static void RunTask(void* context) {
    std::unique_ptr<TaskContext> tc(static_cast<TaskContext*>(context));
    if (tc->queue_ctx->is_active) {
      AutoSetCurrentQueuePtr set_current(tc->queue_ctx->queue);
      if (!tc->task->Run())
        tc->task.release();
    }
  }

  QueueContext* const queue_ctx;
  std::unique_ptr<QueuedTask> task;
};

// Special case context for holding two tasks, a |first_task| + the task
// that's owned by the parent struct, TaskContext, that then becomes the
// second (i.e. 'reply') task.
struct TaskQueue::PostTaskAndReplyContext : public TaskQueue::TaskContext {
  explicit PostTaskAndReplyContext(QueueContext* first_queue_ctx,
                                   std::unique_ptr<QueuedTask> first_task,
                                   QueueContext* second_queue_ctx,
                                   std::unique_ptr<QueuedTask> second_task)
      : TaskContext(second_queue_ctx, std::move(second_task)),
        first_queue_ctx(first_queue_ctx),
        first_task(std::move(first_task)) {
    // Retain the reply queue for as long as this object lives.
    // If we don't, we may have memory leaks and/or failures.
    dispatch_retain(first_queue_ctx->queue->queue_);
  }
  ~PostTaskAndReplyContext() override {
    dispatch_release(first_queue_ctx->queue->queue_);
  }

  static void RunTask(void* context) {
    auto* rc = static_cast<PostTaskAndReplyContext*>(context);
    if (rc->first_queue_ctx->is_active) {
      AutoSetCurrentQueuePtr set_current(rc->first_queue_ctx->queue);
      if (!rc->first_task->Run())
        rc->first_task.release();
    }
    // Post the reply task.  This hands the work over to the parent struct.
    // This task will eventually delete |this|.
    dispatch_async_f(rc->queue_ctx->queue->queue_, rc, &TaskContext::RunTask);
  }

  QueueContext* const first_queue_ctx;
  std::unique_ptr<QueuedTask> first_task;
};

TaskQueue::TaskQueue(const char* queue_name)
    : queue_(dispatch_queue_create(queue_name, DISPATCH_QUEUE_SERIAL)),
      context_(new QueueContext(this)) {
  RTC_DCHECK(queue_name);
  RTC_CHECK(queue_);
  dispatch_set_context(queue_, context_);
  // Assign a finalizer that will delete the context when the last reference
  // to the queue is released.  This may run after the TaskQueue object has
  // been deleted.
  dispatch_set_finalizer_f(queue_, &QueueContext::DeleteContext);
}

TaskQueue::~TaskQueue() {
  RTC_DCHECK(!IsCurrent());
  // Implementation/behavioral note:
  // Dispatch queues are reference counted via calls to dispatch_retain and
  // dispatch_release. Pending blocks submitted to a queue also hold a
  // reference to the queue until they have finished. Once all references to a
  // queue have been released, the queue will be deallocated by the system.
  // This is why we check the context before running tasks.

  // Use dispatch_sync to set the context to null to guarantee that there's not
  // a race between checking the context and using it from a task.
  dispatch_sync_f(queue_, context_, &QueueContext::SetNotActive);
  dispatch_release(queue_);
}

// static
TaskQueue* TaskQueue::Current() {
  return static_cast<TaskQueue*>(pthread_getspecific(GetQueuePtrTls()));
}

// static
bool TaskQueue::IsCurrent(const char* queue_name) {
  TaskQueue* current = Current();
  return current &&
         strcmp(queue_name, dispatch_queue_get_label(current->queue_)) == 0;
}

bool TaskQueue::IsCurrent() const {
  RTC_DCHECK(queue_);
  return this == Current();
}

void TaskQueue::PostTask(std::unique_ptr<QueuedTask> task) {
  auto* context = new TaskContext(context_, std::move(task));
  dispatch_async_f(queue_, context, &TaskContext::RunTask);
}

void TaskQueue::PostDelayedTask(std::unique_ptr<QueuedTask> task,
                                uint32_t milliseconds) {
  auto* context = new TaskContext(context_, std::move(task));
  dispatch_after_f(
      dispatch_time(DISPATCH_TIME_NOW, milliseconds * NSEC_PER_MSEC), queue_,
      context, &TaskContext::RunTask);
}

void TaskQueue::PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                                 std::unique_ptr<QueuedTask> reply,
                                 TaskQueue* reply_queue) {
  auto* context = new PostTaskAndReplyContext(
      context_, std::move(task), reply_queue->context_, std::move(reply));
  dispatch_async_f(queue_, context, &PostTaskAndReplyContext::RunTask);
}

void TaskQueue::PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                                 std::unique_ptr<QueuedTask> reply) {
  return PostTaskAndReply(std::move(task), std::move(reply), Current());
}

}  // namespace rtc
