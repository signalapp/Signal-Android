/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TASK_QUEUE_H_
#define WEBRTC_BASE_TASK_QUEUE_H_

#include <list>
#include <memory>

#if defined(WEBRTC_MAC) && !defined(WEBRTC_BUILD_LIBEVENT)
#include <dispatch/dispatch.h>
#endif

#include "webrtc/base/constructormagic.h"
#include "webrtc/base/criticalsection.h"

#if defined(WEBRTC_WIN) || defined(WEBRTC_BUILD_LIBEVENT)
#include "webrtc/base/platform_thread.h"
#endif

#if defined(WEBRTC_BUILD_LIBEVENT)
struct event_base;
struct event;
#endif

namespace rtc {

// Base interface for asynchronously executed tasks.
// The interface basically consists of a single function, Run(), that executes
// on the target queue.  For more details see the Run() method and TaskQueue.
class QueuedTask {
 public:
  QueuedTask() {}
  virtual ~QueuedTask() {}

  // Main routine that will run when the task is executed on the desired queue.
  // The task should return |true| to indicate that it should be deleted or
  // |false| to indicate that the queue should consider ownership of the task
  // having been transferred.  Returning |false| can be useful if a task has
  // re-posted itself to a different queue or is otherwise being re-used.
  virtual bool Run() = 0;

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(QueuedTask);
};

// Simple implementation of QueuedTask for use with rtc::Bind and lambdas.
template <class Closure>
class ClosureTask : public QueuedTask {
 public:
  explicit ClosureTask(const Closure& closure) : closure_(closure) {}

 private:
  bool Run() override {
    closure_();
    return true;
  }

  Closure closure_;
};

// Extends ClosureTask to also allow specifying cleanup code.
// This is useful when using lambdas if guaranteeing cleanup, even if a task
// was dropped (queue is too full), is required.
template <class Closure, class Cleanup>
class ClosureTaskWithCleanup : public ClosureTask<Closure> {
 public:
  ClosureTaskWithCleanup(const Closure& closure, Cleanup cleanup)
      : ClosureTask<Closure>(closure), cleanup_(cleanup) {}
  ~ClosureTaskWithCleanup() { cleanup_(); }

 private:
  Cleanup cleanup_;
};

// Convenience function to construct closures that can be passed directly
// to methods that support std::unique_ptr<QueuedTask> but not template
// based parameters.
template <class Closure>
static std::unique_ptr<QueuedTask> NewClosure(const Closure& closure) {
  return std::unique_ptr<QueuedTask>(new ClosureTask<Closure>(closure));
}

template <class Closure, class Cleanup>
static std::unique_ptr<QueuedTask> NewClosure(const Closure& closure,
                                              const Cleanup& cleanup) {
  return std::unique_ptr<QueuedTask>(
      new ClosureTaskWithCleanup<Closure, Cleanup>(closure, cleanup));
}

// Implements a task queue that asynchronously executes tasks in a way that
// guarantees that they're executed in FIFO order and that tasks never overlap.
// Tasks may always execute on the same worker thread and they may not.
// To DCHECK that tasks are executing on a known task queue, use IsCurrent().
//
// Here are some usage examples:
//
//   1) Asynchronously running a lambda:
//
//     class MyClass {
//       ...
//       TaskQueue queue_("MyQueue");
//     };
//
//     void MyClass::StartWork() {
//       queue_.PostTask([]() { Work(); });
//     ...
//
//   2) Doing work asynchronously on a worker queue and providing a notification
//      callback on the current queue, when the work has been done:
//
//     void MyClass::StartWorkAndLetMeKnowWhenDone(
//         std::unique_ptr<QueuedTask> callback) {
//       DCHECK(TaskQueue::Current()) << "Need to be running on a queue";
//       queue_.PostTaskAndReply([]() { Work(); }, std::move(callback));
//     }
//     ...
//     my_class->StartWorkAndLetMeKnowWhenDone(
//         NewClosure([]() { LOG(INFO) << "The work is done!";}));
//
//   3) Posting a custom task on a timer.  The task posts itself again after
//      every running:
//
//     class TimerTask : public QueuedTask {
//      public:
//       TimerTask() {}
//      private:
//       bool Run() override {
//         ++count_;
//         TaskQueue::Current()->PostDelayedTask(
//             std::unique_ptr<QueuedTask>(this), 1000);
//         // Ownership has been transferred to the next occurance,
//         // so return false to prevent from being deleted now.
//         return false;
//       }
//       int count_ = 0;
//     };
//     ...
//     queue_.PostDelayedTask(
//         std::unique_ptr<QueuedTask>(new TimerTask()), 1000);
//
// For more examples, see task_queue_unittests.cc.
//
// A note on destruction:
//
// When a TaskQueue is deleted, pending tasks will not be executed but they will
// be deleted.  The deletion of tasks may happen asynchronously after the
// TaskQueue itself has been deleted or it may happen synchronously while the
// TaskQueue instance is being deleted.  This may vary from one OS to the next
// so assumptions about lifetimes of pending tasks should not be made.
class LOCKABLE TaskQueue {
 public:
  explicit TaskQueue(const char* queue_name);
  // TODO(tommi): Implement move semantics?
  ~TaskQueue();

  static TaskQueue* Current();

  // Used for DCHECKing the current queue.
  static bool IsCurrent(const char* queue_name);
  bool IsCurrent() const;

  // TODO(tommi): For better debuggability, implement RTC_FROM_HERE.

  // Ownership of the task is passed to PostTask.
  void PostTask(std::unique_ptr<QueuedTask> task);
  void PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                        std::unique_ptr<QueuedTask> reply,
                        TaskQueue* reply_queue);
  void PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                        std::unique_ptr<QueuedTask> reply);

  void PostDelayedTask(std::unique_ptr<QueuedTask> task, uint32_t milliseconds);

  template <class Closure>
  void PostTask(const Closure& closure) {
    PostTask(std::unique_ptr<QueuedTask>(new ClosureTask<Closure>(closure)));
  }

  template <class Closure>
  void PostDelayedTask(const Closure& closure, uint32_t milliseconds) {
    PostDelayedTask(
        std::unique_ptr<QueuedTask>(new ClosureTask<Closure>(closure)),
        milliseconds);
  }

  template <class Closure1, class Closure2>
  void PostTaskAndReply(const Closure1& task,
                        const Closure2& reply,
                        TaskQueue* reply_queue) {
    PostTaskAndReply(
        std::unique_ptr<QueuedTask>(new ClosureTask<Closure1>(task)),
        std::unique_ptr<QueuedTask>(new ClosureTask<Closure2>(reply)),
        reply_queue);
  }

  template <class Closure>
  void PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                        const Closure& reply) {
    PostTaskAndReply(std::move(task), std::unique_ptr<QueuedTask>(
                                          new ClosureTask<Closure>(reply)));
  }

  template <class Closure>
  void PostTaskAndReply(const Closure& task,
                        std::unique_ptr<QueuedTask> reply) {
    PostTaskAndReply(
        std::unique_ptr<QueuedTask>(new ClosureTask<Closure>(task)),
        std::move(reply));
  }

  template <class Closure1, class Closure2>
  void PostTaskAndReply(const Closure1& task, const Closure2& reply) {
    PostTaskAndReply(
        std::unique_ptr<QueuedTask>(new ClosureTask<Closure1>(task)),
        std::unique_ptr<QueuedTask>(new ClosureTask<Closure2>(reply)));
  }

 private:
#if defined(WEBRTC_BUILD_LIBEVENT)
  static bool ThreadMain(void* context);
  static void OnWakeup(int socket, short flags, void* context);  // NOLINT
  static void RunTask(int fd, short flags, void* context);       // NOLINT
  static void RunTimer(int fd, short flags, void* context);      // NOLINT

  class PostAndReplyTask;
  class SetTimerTask;

  void PrepareReplyTask(PostAndReplyTask* reply_task);
  void ReplyTaskDone(PostAndReplyTask* reply_task);

  struct QueueContext;

  int wakeup_pipe_in_ = -1;
  int wakeup_pipe_out_ = -1;
  event_base* event_base_;
  std::unique_ptr<event> wakeup_event_;
  PlatformThread thread_;
  rtc::CriticalSection pending_lock_;
  std::list<std::unique_ptr<QueuedTask>> pending_ GUARDED_BY(pending_lock_);
  std::list<PostAndReplyTask*> pending_replies_ GUARDED_BY(pending_lock_);
#elif defined(WEBRTC_MAC)
  struct QueueContext;
  struct TaskContext;
  struct PostTaskAndReplyContext;
  dispatch_queue_t queue_;
  QueueContext* const context_;
#elif defined(WEBRTC_WIN)
  static bool ThreadMain(void* context);

  class WorkerThread : public PlatformThread {
   public:
    WorkerThread(ThreadRunFunction func, void* obj, const char* thread_name)
        : PlatformThread(func, obj, thread_name) {}

    bool QueueAPC(PAPCFUNC apc_function, ULONG_PTR data) {
      return PlatformThread::QueueAPC(apc_function, data);
    }
  };
  WorkerThread thread_;
#else
#error not supported.
#endif

  RTC_DISALLOW_COPY_AND_ASSIGN(TaskQueue);
};

}  // namespace rtc

#endif  // WEBRTC_BASE_TASK_QUEUE_H_
