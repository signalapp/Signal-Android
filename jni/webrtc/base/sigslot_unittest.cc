/*
 *  Copyright 2012 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/sigslot.h"

#include "webrtc/base/gunit.h"

// This function, when passed a has_slots or signalx, will break the build if
// its threading requirement is not single threaded
static bool TemplateIsST(const sigslot::single_threaded* p) {
  return true;
}
// This function, when passed a has_slots or signalx, will break the build if
// its threading requirement is not multi threaded
static bool TemplateIsMT(const sigslot::multi_threaded_local* p) {
  return true;
}

class SigslotDefault : public testing::Test, public sigslot::has_slots<> {
 protected:
  sigslot::signal0<> signal_;
};

template<class slot_policy = sigslot::single_threaded,
         class signal_policy = sigslot::single_threaded>
class SigslotReceiver : public sigslot::has_slots<slot_policy> {
 public:
  SigslotReceiver() : signal_(NULL), signal_count_(0) {
  }
  ~SigslotReceiver() {
  }

  void Connect(sigslot::signal0<signal_policy>* signal) {
    if (!signal) return;
    Disconnect();
    signal_ = signal;
    signal->connect(this,
                    &SigslotReceiver<slot_policy, signal_policy>::OnSignal);
  }
  void Disconnect() {
    if (!signal_) return;
    signal_->disconnect(this);
    signal_ = NULL;
  }
  void OnSignal() {
    ++signal_count_;
  }
  int signal_count() { return signal_count_; }

 private:
  sigslot::signal0<signal_policy>* signal_;
  int signal_count_;
};

template<class slot_policy = sigslot::single_threaded,
         class mt_signal_policy = sigslot::multi_threaded_local>
class SigslotSlotTest : public testing::Test {
 protected:
  SigslotSlotTest() {
    mt_signal_policy mt_policy;
    TemplateIsMT(&mt_policy);
  }

  virtual void SetUp() {
    Connect();
  }
  virtual void TearDown() {
    Disconnect();
  }

  void Disconnect() {
    st_receiver_.Disconnect();
    mt_receiver_.Disconnect();
  }

  void Connect() {
    st_receiver_.Connect(&SignalSTLoopback);
    mt_receiver_.Connect(&SignalMTLoopback);
  }

  int st_loop_back_count() { return st_receiver_.signal_count(); }
  int mt_loop_back_count() { return mt_receiver_.signal_count(); }

  sigslot::signal0<> SignalSTLoopback;
  SigslotReceiver<slot_policy, sigslot::single_threaded> st_receiver_;
  sigslot::signal0<mt_signal_policy> SignalMTLoopback;
  SigslotReceiver<slot_policy, mt_signal_policy> mt_receiver_;
};

typedef SigslotSlotTest<> SigslotSTSlotTest;
typedef SigslotSlotTest<sigslot::multi_threaded_local,
                        sigslot::multi_threaded_local> SigslotMTSlotTest;

class multi_threaded_local_fake : public sigslot::multi_threaded_local {
 public:
  multi_threaded_local_fake() : lock_count_(0), unlock_count_(0) {
  }

  virtual void lock() {
    ++lock_count_;
  }
  virtual void unlock() {
    ++unlock_count_;
  }

  int lock_count() { return lock_count_; }

  bool InCriticalSection() { return lock_count_ != unlock_count_; }

 protected:
  int lock_count_;
  int unlock_count_;
};

typedef SigslotSlotTest<multi_threaded_local_fake,
                        multi_threaded_local_fake> SigslotMTLockBase;

class SigslotMTLockTest : public SigslotMTLockBase {
 protected:
  SigslotMTLockTest() {}

  virtual void SetUp() {
    EXPECT_EQ(0, SlotLockCount());
    SigslotMTLockBase::SetUp();
    // Connects to two signals (ST and MT). However,
    // SlotLockCount() only gets the count for the
    // MT signal (there are two separate SigslotReceiver which
    // keep track of their own count).
    EXPECT_EQ(1, SlotLockCount());
  }
  virtual void TearDown() {
    const int previous_lock_count = SlotLockCount();
    SigslotMTLockBase::TearDown();
    // Disconnects from two signals. Note analogous to SetUp().
    EXPECT_EQ(previous_lock_count + 1, SlotLockCount());
  }

  int SlotLockCount() { return mt_receiver_.lock_count(); }
  void Signal() { SignalMTLoopback(); }
  int SignalLockCount() { return SignalMTLoopback.lock_count(); }
  int signal_count() { return mt_loop_back_count(); }
  bool InCriticalSection() { return SignalMTLoopback.InCriticalSection(); }
};

// This test will always succeed. However, if the default template instantiation
// changes from single threaded to multi threaded it will break the build here.
TEST_F(SigslotDefault, DefaultIsST) {
  EXPECT_TRUE(TemplateIsST(this));
  EXPECT_TRUE(TemplateIsST(&signal_));
}

// ST slot, ST signal
TEST_F(SigslotSTSlotTest, STLoopbackTest) {
  SignalSTLoopback();
  EXPECT_EQ(1, st_loop_back_count());
  EXPECT_EQ(0, mt_loop_back_count());
}

// ST slot, MT signal
TEST_F(SigslotSTSlotTest, MTLoopbackTest) {
  SignalMTLoopback();
  EXPECT_EQ(1, mt_loop_back_count());
  EXPECT_EQ(0, st_loop_back_count());
}

// ST slot, both ST and MT (separate) signal
TEST_F(SigslotSTSlotTest, AllLoopbackTest) {
  SignalSTLoopback();
  SignalMTLoopback();
  EXPECT_EQ(1, mt_loop_back_count());
  EXPECT_EQ(1, st_loop_back_count());
}

TEST_F(SigslotSTSlotTest, Reconnect) {
  SignalSTLoopback();
  SignalMTLoopback();
  EXPECT_EQ(1, mt_loop_back_count());
  EXPECT_EQ(1, st_loop_back_count());
  Disconnect();
  SignalSTLoopback();
  SignalMTLoopback();
  EXPECT_EQ(1, mt_loop_back_count());
  EXPECT_EQ(1, st_loop_back_count());
  Connect();
  SignalSTLoopback();
  SignalMTLoopback();
  EXPECT_EQ(2, mt_loop_back_count());
  EXPECT_EQ(2, st_loop_back_count());
}

// MT slot, ST signal
TEST_F(SigslotMTSlotTest, STLoopbackTest) {
  SignalSTLoopback();
  EXPECT_EQ(1, st_loop_back_count());
  EXPECT_EQ(0, mt_loop_back_count());
}

// MT slot, MT signal
TEST_F(SigslotMTSlotTest, MTLoopbackTest) {
  SignalMTLoopback();
  EXPECT_EQ(1, mt_loop_back_count());
  EXPECT_EQ(0, st_loop_back_count());
}

// MT slot, both ST and MT (separate) signal
TEST_F(SigslotMTSlotTest, AllLoopbackTest) {
  SignalMTLoopback();
  SignalSTLoopback();
  EXPECT_EQ(1, st_loop_back_count());
  EXPECT_EQ(1, mt_loop_back_count());
}

// Test that locks are acquired and released correctly.
TEST_F(SigslotMTLockTest, LockSanity) {
  const int lock_count = SignalLockCount();
  Signal();
  EXPECT_FALSE(InCriticalSection());
  EXPECT_EQ(lock_count + 1, SignalLockCount());
  EXPECT_EQ(1, signal_count());
}

// Destroy signal and slot in different orders.
TEST(DestructionOrder, SignalFirst) {
  sigslot::signal0<>* signal = new sigslot::signal0<>;
  SigslotReceiver<>* receiver = new SigslotReceiver<>();
  receiver->Connect(signal);
  (*signal)();
  EXPECT_EQ(1, receiver->signal_count());
  delete signal;
  delete receiver;
}

TEST(DestructionOrder, SlotFirst) {
  sigslot::signal0<>* signal = new sigslot::signal0<>;
  SigslotReceiver<>* receiver = new SigslotReceiver<>();
  receiver->Connect(signal);
  (*signal)();
  EXPECT_EQ(1, receiver->signal_count());

  delete receiver;
  (*signal)();
  delete signal;
}
