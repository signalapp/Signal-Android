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

#include <string.h>
#include <unordered_map>

#include "webrtc/base/checks.h"
#include "webrtc/base/logging.h"

namespace rtc {
namespace {
#define WM_RUN_TASK WM_USER + 1
#define WM_QUEUE_DELAYED_TASK WM_USER + 2

DWORD g_queue_ptr_tls = 0;

BOOL CALLBACK InitializeTls(PINIT_ONCE init_once, void* param, void** context) {
  g_queue_ptr_tls = TlsAlloc();
  return TRUE;
}

DWORD GetQueuePtrTls() {
  static INIT_ONCE init_once = INIT_ONCE_STATIC_INIT;
  InitOnceExecuteOnce(&init_once, InitializeTls, nullptr, nullptr);
  return g_queue_ptr_tls;
}

struct ThreadStartupData {
  Event* started;
  void* thread_context;
};

void CALLBACK InitializeQueueThread(ULONG_PTR param) {
  MSG msg;
  PeekMessage(&msg, NULL, WM_USER, WM_USER, PM_NOREMOVE);
  ThreadStartupData* data = reinterpret_cast<ThreadStartupData*>(param);
  TlsSetValue(GetQueuePtrTls(), data->thread_context);
  data->started->Set();
}
}  // namespace

TaskQueue::TaskQueue(const char* queue_name)
    : thread_(&TaskQueue::ThreadMain, this, queue_name) {
  RTC_DCHECK(queue_name);
  thread_.Start();
  Event event(false, false);
  ThreadStartupData startup = {&event, this};
  RTC_CHECK(thread_.QueueAPC(&InitializeQueueThread,
                             reinterpret_cast<ULONG_PTR>(&startup)));
  event.Wait(Event::kForever);
}

TaskQueue::~TaskQueue() {
  RTC_DCHECK(!IsCurrent());
  while (!PostThreadMessage(thread_.GetThreadRef(), WM_QUIT, 0, 0)) {
    RTC_CHECK_EQ(static_cast<DWORD>(ERROR_NOT_ENOUGH_QUOTA), ::GetLastError());
    Sleep(1);
  }
  thread_.Stop();
}

// static
TaskQueue* TaskQueue::Current() {
  return static_cast<TaskQueue*>(TlsGetValue(GetQueuePtrTls()));
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
  if (PostThreadMessage(thread_.GetThreadRef(), WM_RUN_TASK, 0,
                        reinterpret_cast<LPARAM>(task.get()))) {
    task.release();
  }
}

void TaskQueue::PostDelayedTask(std::unique_ptr<QueuedTask> task,
                                uint32_t milliseconds) {
  WPARAM wparam;
#if defined(_WIN64)
  // GetTickCount() returns a fairly coarse tick count (resolution or about 8ms)
  // so this compensation isn't that accurate, but since we have unused 32 bits
  // on Win64, we might as well use them.
  wparam = (static_cast<WPARAM>(::GetTickCount()) << 32) | milliseconds;
#else
  wparam = milliseconds;
#endif
  if (PostThreadMessage(thread_.GetThreadRef(), WM_QUEUE_DELAYED_TASK, wparam,
                        reinterpret_cast<LPARAM>(task.get()))) {
    task.release();
  }
}

void TaskQueue::PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                                 std::unique_ptr<QueuedTask> reply,
                                 TaskQueue* reply_queue) {
  QueuedTask* task_ptr = task.release();
  QueuedTask* reply_task_ptr = reply.release();
  DWORD reply_thread_id = reply_queue->thread_.GetThreadRef();
  PostTask([task_ptr, reply_task_ptr, reply_thread_id]() {
    if (task_ptr->Run())
      delete task_ptr;
    // If the thread's message queue is full, we can't queue the task and will
    // have to drop it (i.e. delete).
    if (!PostThreadMessage(reply_thread_id, WM_RUN_TASK, 0,
                           reinterpret_cast<LPARAM>(reply_task_ptr))) {
      delete reply_task_ptr;
    }
  });
}

void TaskQueue::PostTaskAndReply(std::unique_ptr<QueuedTask> task,
                                 std::unique_ptr<QueuedTask> reply) {
  return PostTaskAndReply(std::move(task), std::move(reply), Current());
}

// static
bool TaskQueue::ThreadMain(void* context) {
  std::unordered_map<UINT_PTR, std::unique_ptr<QueuedTask>> delayed_tasks;

  BOOL ret;
  MSG msg;

  while ((ret = GetMessage(&msg, nullptr, 0, 0)) != 0 && ret != -1) {
    if (!msg.hwnd) {
      switch (msg.message) {
        case WM_RUN_TASK: {
          QueuedTask* task = reinterpret_cast<QueuedTask*>(msg.lParam);
          if (task->Run())
            delete task;
          break;
        }
        case WM_QUEUE_DELAYED_TASK: {
          QueuedTask* task = reinterpret_cast<QueuedTask*>(msg.lParam);
          uint32_t milliseconds = msg.wParam & 0xFFFFFFFF;
#if defined(_WIN64)
          // Subtract the time it took to queue the timer.
          const DWORD now = GetTickCount();
          DWORD post_time = now - (msg.wParam >> 32);
          milliseconds =
              post_time > milliseconds ? 0 : milliseconds - post_time;
#endif
          UINT_PTR timer_id = SetTimer(nullptr, 0, milliseconds, nullptr);
          delayed_tasks.insert(std::make_pair(timer_id, task));
          break;
        }
        case WM_TIMER: {
          KillTimer(nullptr, msg.wParam);
          auto found = delayed_tasks.find(msg.wParam);
          RTC_DCHECK(found != delayed_tasks.end());
          if (!found->second->Run())
            found->second.release();
          delayed_tasks.erase(found);
          break;
        }
        default:
          RTC_NOTREACHED();
          break;
      }
    } else {
      TranslateMessage(&msg);
      DispatchMessage(&msg);
    }
  }

  return false;
}
}  // namespace rtc
