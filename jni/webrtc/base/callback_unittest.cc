/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/bind.h"
#include "webrtc/base/callback.h"
#include "webrtc/base/gunit.h"
#include "webrtc/base/keep_ref_until_done.h"
#include "webrtc/base/refcount.h"

namespace rtc {

namespace {

void f() {}
int g() { return 42; }
int h(int x) { return x * x; }
void i(int& x) { x *= x; }  // NOLINT: Testing refs

struct BindTester {
  int a() { return 24; }
  int b(int x) const { return x * x; }
};

class RefCountedBindTester : public RefCountInterface {
 public:
  RefCountedBindTester() : count_(0) {}
  int AddRef() const override {
    return ++count_;
  }
  int Release() const override {
    return --count_;
  }
  int RefCount() const { return count_; }

 private:
  mutable int count_;
};

}  // namespace

TEST(CallbackTest, VoidReturn) {
  Callback0<void> cb;
  EXPECT_TRUE(cb.empty());
  cb();  // Executing an empty callback should not crash.
  cb = Callback0<void>(&f);
  EXPECT_FALSE(cb.empty());
  cb();
}

TEST(CallbackTest, IntReturn) {
  Callback0<int> cb;
  EXPECT_TRUE(cb.empty());
  cb = Callback0<int>(&g);
  EXPECT_FALSE(cb.empty());
  EXPECT_EQ(42, cb());
  EXPECT_EQ(42, cb());
}

TEST(CallbackTest, OneParam) {
  Callback1<int, int> cb1(&h);
  EXPECT_FALSE(cb1.empty());
  EXPECT_EQ(9, cb1(-3));
  EXPECT_EQ(100, cb1(10));

  // Try clearing a callback.
  cb1 = Callback1<int, int>();
  EXPECT_TRUE(cb1.empty());

  // Try a callback with a ref parameter.
  Callback1<void, int&> cb2(&i);
  int x = 3;
  cb2(x);
  EXPECT_EQ(9, x);
  cb2(x);
  EXPECT_EQ(81, x);
}

TEST(CallbackTest, WithBind) {
  BindTester t;
  Callback0<int> cb1 = Bind(&BindTester::a, &t);
  EXPECT_EQ(24, cb1());
  EXPECT_EQ(24, cb1());
  cb1 = Bind(&BindTester::b, &t, 10);
  EXPECT_EQ(100, cb1());
  EXPECT_EQ(100, cb1());
  cb1 = Bind(&BindTester::b, &t, 5);
  EXPECT_EQ(25, cb1());
  EXPECT_EQ(25, cb1());
}

TEST(KeepRefUntilDoneTest, simple) {
  RefCountedBindTester t;
  EXPECT_EQ(0, t.RefCount());
  {
    Callback0<void> cb = KeepRefUntilDone(&t);
    EXPECT_EQ(1, t.RefCount());
    cb();
    EXPECT_EQ(1, t.RefCount());
    cb();
    EXPECT_EQ(1, t.RefCount());
  }
  EXPECT_EQ(0, t.RefCount());
}

TEST(KeepRefUntilDoneTest, copy) {
  RefCountedBindTester t;
  EXPECT_EQ(0, t.RefCount());
  Callback0<void> cb2;
  {
    Callback0<void> cb = KeepRefUntilDone(&t);
    EXPECT_EQ(1, t.RefCount());
    cb2 = cb;
  }
  EXPECT_EQ(1, t.RefCount());
  cb2 = Callback0<void>();
  EXPECT_EQ(0, t.RefCount());
}

TEST(KeepRefUntilDoneTest, scopedref) {
  RefCountedBindTester t;
  EXPECT_EQ(0, t.RefCount());
  {
    scoped_refptr<RefCountedBindTester> t_scoped_ref(&t);
    Callback0<void> cb = KeepRefUntilDone(t_scoped_ref);
    t_scoped_ref = nullptr;
    EXPECT_EQ(1, t.RefCount());
    cb();
    EXPECT_EQ(1, t.RefCount());
  }
  EXPECT_EQ(0, t.RefCount());
}

}  // namespace rtc
