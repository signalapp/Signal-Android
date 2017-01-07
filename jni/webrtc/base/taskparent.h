/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TASKPARENT_H__
#define WEBRTC_BASE_TASKPARENT_H__

#include <memory>
#include <set>

#include "webrtc/base/basictypes.h"
#include "webrtc/base/constructormagic.h"

namespace rtc {

class Task;
class TaskRunner;

class TaskParent {
 public:
  TaskParent(Task *derived_instance, TaskParent *parent);
  explicit TaskParent(TaskRunner *derived_instance);
  virtual ~TaskParent();

  TaskParent *GetParent() { return parent_; }
  TaskRunner *GetRunner() { return runner_; }

  bool AllChildrenDone();
  bool AnyChildError();
#if !defined(NDEBUG)
  bool IsChildTask(Task *task);
#endif

 protected:
  void OnStopped(Task *task);
  void AbortAllChildren();
  TaskParent *parent() {
    return parent_;
  }

 private:
  void Initialize();
  void OnChildStopped(Task *child);
  void AddChild(Task *child);

  TaskParent *parent_;
  TaskRunner *runner_;
  bool child_error_;
  typedef std::set<Task *> ChildSet;
  std::unique_ptr<ChildSet> children_;
  RTC_DISALLOW_COPY_AND_ASSIGN(TaskParent);
};


} // namespace rtc

#endif  // WEBRTC_BASE_TASKPARENT_H__
