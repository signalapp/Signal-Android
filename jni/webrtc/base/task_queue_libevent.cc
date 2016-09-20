/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/task_queue.h"

#include <fcntl.h>
#include <string.h>
#include <unistd.h>

#include "base/third_party/libevent/event.h"
#include "webrtc/base/checks.h"
#include "webrtc/base/logging.h"
#include "webrtc/base/task_queue_posix.h"
#include "webrtc/base/timeutils.h"

namespace rtc {
using internal::GetQueuePtrTls;
using internal::AutoSetCurrentQueuePtr;

namespace {
static const char kQuit = 1;
static const char kRunTask = 2;

struct TimerEvent {
  explicit TimerEvent(std::unique_ptr<QueuedTask> task)
      : task(std::move(task)) {}
  ~TimerEvent() { event_del(&ev); }
  event ev;
  std::unique_ptr<QueuedTask> task;
};

bool SetNonBlocking(int fd) {
  const int flags = fcntl(fd, F_GETFL);
  RTC_CHECK(flags != -1);
  return (flags & O_NONBLOCK) || fcntl(fd, F_SETFL, flags | O_NONBLOCK) != -1;
}
}  // namespace

struct TaskQueue::QueueContext {
  explicit QueueContext(TaskQueue* q) : queue(q), is_active(true) {}
  TaskQueue* queue;
  bool is_active;
  // Holds a list of events pending timers for cleanup when the loop exits.
  std::list<TimerEvent*> pending_timers_;
};

class TaskQueue::PostAndReplyTask : public QueuedTask {
 public:
  PostAndReplyTask(std::unique_ptr<QueuedTask> task,
                   std::unique_ptr<QueuedTask> reply,
                   TaskQueue* reply_queue)
      : task_(std::move(task)),
        reply_(std::move(reply)),
        reply_queue_(reply_queue) {
    reply_queue->PrepareReplyTask(this);
  }

  ~PostAndReplyTask() override {
    CritScope lock(&lock_);
    if (reply_queue_)
      reply_queue_->ReplyTaskDone(this);
  }

  void OnReplyQueueGone() {
    CritScope lock(&lock_);
    reply_queue_ = nullptr;
  }

 private:
  bool Run() override {
    if (!task_->Run())
      task_.release();

    CritScope lock(&lock_);
    if (reply_queue_)
      reply_queue_->PostTask(std::move(reply_));
    return true;
  }

  CriticalSection lock_;
  std::unique_ptr<QueuedTask> task_;
  std::unique_ptr<QueuedTask> reply_;
  TaskQueue* reply_queue_ GUARDED_BY(lock_);
};

class TaskQueue::SetTimerTask : public QueuedTask {
 public:
  SetTimerTask(std::unique_ptr<QueuedTask> task, uint32_t milliseconds)
      : task_(std::move(task)),
        milliseconds_(milliseconds),
        posted_(Time32()) {}

 private:
  bool Run() override {
    // Compensate for the time that has passed since construction
    // and until we got here.
    uint32_t post_time = Time32() - posted_;
    TaskQueue::Current()->PostDelayedTask(
        std::move(task_),
        post_time > milliseconds_ ? 0 : milliseconds_ - post_time);
    return true;
  }

  std::unique_ptr<QueuedTask> task_;
  const uint32_t milliseconds_;
  const uint32_t posted_;
};

TaskQueue::TaskQueue(const char* queue_name)
    : event_base_(event_base_new()),
      wakeup_event_(new event()),
      thread_(&TaskQueue::ThreadMain, this, queue_name) {
  RTC_DCHECK(queue_name);
  int fds[2];
  RTC_CHECK(pipe(fds) == 0);
  SetNonBlocking(fds[0]);
  SetNonBlocking(fds[1]);
  wakeup_pipe_out_ = fds[0];
  wakeup_pipe_in_ = fds[1];
  event_set(wakeup_event_.get(), wakeup_pipe_out_, EV_READ | EV_PERSIST,
            OnWakeup, this);
  event_base_set(event_base_, wakeup_event_.get());
  event_add(wakeup_event_.get(), 0);
  thread_.Start();
}

TaskQueue::~TaskQueue() {
  RTC_DCHECK(!IsCurrent());
  struct timespec ts;
  char message = kQuit;
  while (write(wakeup_pipe_in_, &message, sizeof(message)) != sizeof(message)) {
    // The queue is full, so we have no choice but to wait and retry.
    RTC_CHECK_EQ(EAGAIN, errno);
    ts.tv_sec = 0;
    ts.tv_nsec = 1000000;
    nanosleep(&ts, nullptr);
  }

  thread_.Stop();

  event_del(wakeup_event_.get());
  close(wakeup_pipe_in_);
  close(wakeup_pipe_out_);
  wakeup_pipe_in_ = -1;
  wakeup_pipe_out_ = -1;

  {
    // Synchronize against any pending reply tasks that might be running on
    // other queues.
    CritScope lock(&pending_lock_);
    for (auto* reply : pending_replies_)
      reply->OnReplyQueueGone();
    pending_replies_.clear();
  }

  event_base_free(event_base_);
}

// static
TaskQueue* TaskQueue::Current() {
  QueueContext* ctx =
      static_cast<QueueContext*>(pthread_getspecific(GetQueuePtrTls()));
  return ctx ? ctx->queue : nullptr;
}

// static
bool TaskQueue::IsCurrent(const char* queue_name) {
  TaskQueue* current = Current();
  return current && current->thread_.name().compare(queue_name) == 0;
}

bool TaskQueue::IsCurrent() const {
  return IsThreadRefEqual(thread_.GetThreadRef(), CurrentThreadRef());
}

void TaskQueue::PostTask(std::unique_ptr<QueuedTask> task) {
  RTC_DCHECK(task.get());
  // libevent isn't thread safe.  This means that we can't use methods such
  // as event_base_once to post tasks to the worker thread from a different
  // thread.  However, we can use it when posting from the worker thread itself.
  if (IsCurrent()) {
    if (event_base_once(event_base_, -1, EV_TIMEOUT, &TaskQueue::RunTask,
                        task.get(), nullptr) == 0) {
      task.release();
    }
  } else {
    QueuedTask* task_id = task.get();  // Only used for comparison.
    {
      CritScope lock(&pending_lock_);
      pending_.push_back(std::move(task));
    }
    char message = kRunTask;
    if (write(wakeup_pipe_in_, &message, sizeof(message)) != sizeof(message)) {
      LOG(WARNING) << "Failed to queue task.";
      CritScope lock(&pending_lock_);
      pending_.remove_if([task_id](std::unique_ptr<QueuedTask>& t) {
        return t.get() == task_id;
      });
    }
  }
}

void TaskQueue::PostDelayedTask(std::unique_ptr<QueuedTask> task,
                                uint32_t milliseconds) {
  if (IsCurrent()) {
    TimerEvent* timer = new TimerEvent(std::move(task));
    evtimer_set(&timer->ev, &TaskQueue::RunTimer, timer);
    event_base_set(event_base_, &timer->ev);
    QueueContext* ctx =
        static_cast<QueueContext*>(pthread_getspecific(GetQueuePtrTls()));
    ctx->pending_timers_.push_back(timer);
    timeval tv = {milliseconds / 1000, (milliseconds % 1000) * 1000};
    event_add(&timer->ev, &tv);
  } else {
    PostTask(std::unique_ptr<QueuedTask>(
        new SetTimerTask(std::move(task), milliseconds)));
  }
}

void TaskQueue::PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                                 std::unique_ptr<QueuedTask> reply,
                                 TaskQueue* reply_queue) {
  std::unique_ptr<QueuedTask> wrapper_task(
      new PostAndReplyTask(std::move(task), std::move(reply), reply_queue));
  PostTask(std::move(wrapper_task));
}

void TaskQueue::PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                                 std::unique_ptr<QueuedTask> reply) {
  return PostTaskAndReply(std::move(task), std::move(reply), Current());
}

// static
bool TaskQueue::ThreadMain(void* context) {
  TaskQueue* me = static_cast<TaskQueue*>(context);

  QueueContext queue_context(me);
  pthread_setspecific(GetQueuePtrTls(), &queue_context);

  while (queue_context.is_active)
    event_base_loop(me->event_base_, 0);

  pthread_setspecific(GetQueuePtrTls(), nullptr);

  for (TimerEvent* timer : queue_context.pending_timers_)
    delete timer;

  return false;
}

// static
void TaskQueue::OnWakeup(int socket, short flags, void* context) {  // NOLINT
  QueueContext* ctx =
      static_cast<QueueContext*>(pthread_getspecific(GetQueuePtrTls()));
  RTC_DCHECK(ctx->queue->wakeup_pipe_out_ == socket);
  char buf;
  RTC_CHECK(sizeof(buf) == read(socket, &buf, sizeof(buf)));
  switch (buf) {
    case kQuit:
      ctx->is_active = false;
      event_base_loopbreak(ctx->queue->event_base_);
      break;
    case kRunTask: {
      std::unique_ptr<QueuedTask> task;
      {
        CritScope lock(&ctx->queue->pending_lock_);
        RTC_DCHECK(!ctx->queue->pending_.empty());
        task = std::move(ctx->queue->pending_.front());
        ctx->queue->pending_.pop_front();
        RTC_DCHECK(task.get());
      }
      if (!task->Run())
        task.release();
      break;
    }
    default:
      RTC_NOTREACHED();
      break;
  }
}

// static
void TaskQueue::RunTask(int fd, short flags, void* context) {  // NOLINT
  auto* task = static_cast<QueuedTask*>(context);
  if (task->Run())
    delete task;
}

// static
void TaskQueue::RunTimer(int fd, short flags, void* context) {  // NOLINT
  TimerEvent* timer = static_cast<TimerEvent*>(context);
  if (!timer->task->Run())
    timer->task.release();
  QueueContext* ctx =
      static_cast<QueueContext*>(pthread_getspecific(GetQueuePtrTls()));
  ctx->pending_timers_.remove(timer);
  delete timer;
}

void TaskQueue::PrepareReplyTask(PostAndReplyTask* reply_task) {
  RTC_DCHECK(reply_task);
  CritScope lock(&pending_lock_);
  pending_replies_.push_back(reply_task);
}

void TaskQueue::ReplyTaskDone(PostAndReplyTask* reply_task) {
  CritScope lock(&pending_lock_);
  pending_replies_.remove(reply_task);
}

}  // namespace rtc
