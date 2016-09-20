/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_BASE_TASK_H__
#define WEBRTC_BASE_TASK_H__

#include <string>
#include "webrtc/base/basictypes.h"
#include "webrtc/base/sigslot.h"
#include "webrtc/base/taskparent.h"

/////////////////////////////////////////////////////////////////////
//
// TASK
//
/////////////////////////////////////////////////////////////////////
//
// Task is a state machine infrastructure.  States are pushed forward by
// pushing forwards a TaskRunner that holds on to all Tasks.  The purpose
// of Task is threefold:
//
// (1) It manages ongoing work on the UI thread.  Multitasking without
// threads, keeping it easy, keeping it real. :-)  It does this by
// organizing a set of states for each task.  When you return from your
// Process*() function, you return an integer for the next state.  You do
// not go onto the next state yourself.  Every time you enter a state,
// you check to see if you can do anything yet.  If not, you return
// STATE_BLOCKED.  If you _could_ do anything, do not return
// STATE_BLOCKED - even if you end up in the same state, return
// STATE_mysamestate.  When you are done, return STATE_DONE and then the
// task will self-delete sometime afterwards.
//
// (2) It helps you avoid all those reentrancy problems when you chain
// too many triggers on one thread.  Basically if you want to tell a task
// to process something for you, you feed your task some information and
// then you Wake() it.  Don't tell it to process it right away.  If it
// might be working on something as you send it information, you may want
// to have a queue in the task.
//
// (3) Finally it helps manage parent tasks and children.  If a parent
// task gets aborted, all the children tasks are too.  The nice thing
// about this, for example, is if you have one parent task that
// represents, say, and Xmpp connection, then you can spawn a whole bunch
// of infinite lifetime child tasks and now worry about cleaning them up.
//  When the parent task goes to STATE_DONE, the task engine will make
// sure all those children are aborted and get deleted.
//
// Notice that Task has a few built-in states, e.g.,
//
// STATE_INIT - the task isn't running yet
// STATE_START - the task is in its first state
// STATE_RESPONSE - the task is in its second state
// STATE_DONE - the task is done
//
// STATE_ERROR - indicates an error - we should audit the error code in
// light of any usage of it to see if it should be improved.  When I
// first put down the task stuff I didn't have a good sense of what was
// needed for Abort and Error, and now the subclasses of Task will ground
// the design in a stronger way.
//
// STATE_NEXT - the first undefined state number.  (like WM_USER) - you
// can start defining more task states there.
//
// When you define more task states, just override Process(int state) and
// add your own switch statement.  If you want to delegate to
// Task::Process, you can effectively delegate to its switch statement.
// No fancy method pointers or such - this is all just pretty low tech,
// easy to debug, and fast.
//
// Also notice that Task has some primitive built-in timeout functionality.
//
// A timeout is defined as "the task stays in STATE_BLOCKED longer than
// timeout_seconds_."
//
// Descendant classes can override this behavior by calling the
// various protected methods to change the timeout behavior.  For
// instance, a descendand might call SuspendTimeout() when it knows
// that it isn't waiting for anything that might timeout, but isn't
// yet in the STATE_DONE state.
//

namespace rtc {

// Executes a sequence of steps
class Task : public TaskParent {
 public:
  Task(TaskParent *parent);
  ~Task() override;

  int32_t unique_id() { return unique_id_; }

  void Start();
  void Step();
  int GetState() const { return state_; }
  bool HasError() const { return (GetState() == STATE_ERROR); }
  bool Blocked() const { return blocked_; }
  bool IsDone() const { return done_; }
  int64_t ElapsedTime();

  // Called from outside to stop task without any more callbacks
  void Abort(bool nowake = false);

  bool TimedOut();

  int64_t timeout_time() const { return timeout_time_; }
  int timeout_seconds() const { return timeout_seconds_; }
  void set_timeout_seconds(int timeout_seconds);

  sigslot::signal0<> SignalTimeout;

  // Called inside the task to signal that the task may be unblocked
  void Wake();

 protected:

  enum {
    STATE_BLOCKED = -1,
    STATE_INIT = 0,
    STATE_START = 1,
    STATE_DONE = 2,
    STATE_ERROR = 3,
    STATE_RESPONSE = 4,
    STATE_NEXT = 5,  // Subclasses which need more states start here and higher
  };

  // Called inside to advise that the task should wake and signal an error
  void Error();

  int64_t CurrentTime();

  virtual std::string GetStateName(int state) const;
  virtual int Process(int state);
  virtual void Stop();
  virtual int ProcessStart() = 0;
  virtual int ProcessResponse();

  void ResetTimeout();
  void ClearTimeout();

  void SuspendTimeout();
  void ResumeTimeout();

 protected:
  virtual int OnTimeout();

 private:
  void Done();

  int state_;
  bool blocked_;
  bool done_;
  bool aborted_;
  bool busy_;
  bool error_;
  int64_t start_time_;
  int64_t timeout_time_;
  int timeout_seconds_;
  bool timeout_suspended_;
  int32_t unique_id_;

  static int32_t unique_id_seed_;
};

}  // namespace rtc

#endif  // WEBRTC_BASE_TASK_H__
