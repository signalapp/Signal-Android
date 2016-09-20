/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include "webrtc/base/gunit.h"
#include "webrtc/base/optional.h"

namespace rtc {

namespace {

// Class whose instances logs various method calls (constructor, destructor,
// etc.). Each instance has a unique ID (a simple global sequence number) and
// an origin ID. When a copy is made, the new object gets a fresh ID but copies
// the origin ID from the original. When a new Logger is created from scratch,
// it gets a fresh ID, and the origin ID is the same as the ID (default
// constructor) or given as an argument (explicit constructor).
class Logger {
 public:
  Logger() : id_(g_next_id++), origin_(id_) { Log("default constructor"); }
  explicit Logger(int origin) : id_(g_next_id++), origin_(origin) {
    Log("explicit constructor");
  }
  Logger(const Logger& other) : id_(g_next_id++), origin_(other.origin_) {
    LogFrom("copy constructor", other);
  }
  Logger(Logger&& other) : id_(g_next_id++), origin_(other.origin_) {
    LogFrom("move constructor", other);
  }
  ~Logger() { Log("destructor"); }
  Logger& operator=(const Logger& other) {
    origin_ = other.origin_;
    LogFrom("operator= copy", other);
    return *this;
  }
  Logger& operator=(Logger&& other) {
    origin_ = other.origin_;
    LogFrom("operator= move", other);
    return *this;
  }
  friend void swap(Logger& a, Logger& b) {
    using std::swap;
    swap(a.origin_, b.origin_);
    Log2("swap", a, b);
  }
  friend bool operator==(const Logger& a, const Logger& b) {
    Log2("operator==", a, b);
    return a.origin_ == b.origin_;
  }
  friend bool operator!=(const Logger& a, const Logger& b) {
    Log2("operator!=", a, b);
    return a.origin_ != b.origin_;
  }
  void Foo() { Log("Foo()"); }
  void Foo() const { Log("Foo() const"); }
  static std::unique_ptr<std::vector<std::string>> Setup() {
    std::unique_ptr<std::vector<std::string>> s(new std::vector<std::string>);
    g_log = s.get();
    g_next_id = 0;
    return s;
  }

 private:
  int id_;
  int origin_;
  static std::vector<std::string>* g_log;
  static int g_next_id;
  void Log(const char* msg) const {
    std::ostringstream oss;
    oss << id_ << ':' << origin_ << ". " << msg;
    g_log->push_back(oss.str());
  }
  void LogFrom(const char* msg, const Logger& other) const {
    std::ostringstream oss;
    oss << id_ << ':' << origin_ << ". " << msg << " (from " << other.id_ << ':'
        << other.origin_ << ")";
    g_log->push_back(oss.str());
  }
  static void Log2(const char* msg, const Logger& a, const Logger& b) {
    std::ostringstream oss;
    oss << msg << ' ' << a.id_ << ':' << a.origin_ << ", " << b.id_ << ':'
        << b.origin_;
    g_log->push_back(oss.str());
  }
};

std::vector<std::string>* Logger::g_log = nullptr;
int Logger::g_next_id = 0;

// Append all the other args to the vector pointed to by the first arg.
template <typename T>
void VectorAppend(std::vector<T>* v) {}
template <typename T, typename... Ts>
void VectorAppend(std::vector<T>* v, const T& e, Ts... es) {
  v->push_back(e);
  VectorAppend(v, es...);
}

// Create a vector of strings. Because we're not allowed to use
// std::initializer_list.
template <typename... Ts>
std::vector<std::string> V(Ts... es) {
  std::vector<std::string> strings;
  VectorAppend(&strings, static_cast<std::string>(es)...);
  return strings;
}

}  // namespace

TEST(OptionalTest, TestConstructDefault) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x;
    EXPECT_FALSE(x);
  }
  EXPECT_EQ(V(), *log);
}

TEST(OptionalTest, TestConstructCopyEmpty) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x;
    EXPECT_FALSE(x);
    auto y = x;
    EXPECT_FALSE(y);
  }
  EXPECT_EQ(V(), *log);
}

TEST(OptionalTest, TestConstructCopyFull) {
  auto log = Logger::Setup();
  {
    Logger a;
    Optional<Logger> x(a);
    EXPECT_TRUE(x);
    log->push_back("---");
    auto y = x;
    EXPECT_TRUE(y);
    log->push_back("---");
  }
  EXPECT_EQ(V("0:0. default constructor", "1:0. copy constructor (from 0:0)",
              "---", "2:0. copy constructor (from 1:0)", "---",
              "2:0. destructor", "1:0. destructor", "0:0. destructor"),
            *log);
}

TEST(OptionalTest, TestConstructMoveEmpty) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x;
    EXPECT_FALSE(x);
    auto y = std::move(x);
    EXPECT_FALSE(y);
  }
  EXPECT_EQ(V(), *log);
}

TEST(OptionalTest, TestConstructMoveFull) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x(Logger(17));
    EXPECT_TRUE(x);
    log->push_back("---");
    auto y = std::move(x);
    EXPECT_TRUE(x);
    EXPECT_TRUE(y);
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "---", "2:17. move constructor (from 1:17)", "---",
        "2:17. destructor", "1:17. destructor"),
      *log);
}

TEST(OptionalTest, TestCopyAssignToEmptyFromEmpty) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x, y;
    x = y;
  }
  EXPECT_EQ(V(), *log);
}

TEST(OptionalTest, TestCopyAssignToFullFromEmpty) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x(Logger(17));
    Optional<Logger> y;
    log->push_back("---");
    x = y;
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "---", "1:17. destructor", "---"),
      *log);
}

TEST(OptionalTest, TestCopyAssignToEmptyFromFull) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x;
    Optional<Logger> y(Logger(17));
    log->push_back("---");
    x = y;
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "---", "2:17. copy constructor (from 1:17)", "---",
        "1:17. destructor", "2:17. destructor"),
      *log);
}

TEST(OptionalTest, TestCopyAssignToFullFromFull) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x(Logger(17));
    Optional<Logger> y(Logger(42));
    log->push_back("---");
    x = y;
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "2:42. explicit constructor",
        "3:42. move constructor (from 2:42)", "2:42. destructor", "---",
        "1:42. operator= copy (from 3:42)", "---", "3:42. destructor",
        "1:42. destructor"),
      *log);
}

TEST(OptionalTest, TestCopyAssignToEmptyFromT) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x;
    Logger y(17);
    log->push_back("---");
    x = Optional<Logger>(y);
    log->push_back("---");
  }
  EXPECT_EQ(V("0:17. explicit constructor", "---",
              "1:17. copy constructor (from 0:17)",
              "2:17. move constructor (from 1:17)", "1:17. destructor", "---",
              "0:17. destructor", "2:17. destructor"),
            *log);
}

TEST(OptionalTest, TestCopyAssignToFullFromT) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x(Logger(17));
    Logger y(42);
    log->push_back("---");
    x = Optional<Logger>(y);
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "2:42. explicit constructor", "---",
        "3:42. copy constructor (from 2:42)",
        "1:42. operator= move (from 3:42)", "3:42. destructor", "---",
        "2:42. destructor", "1:42. destructor"),
      *log);
}

TEST(OptionalTest, TestMoveAssignToEmptyFromEmpty) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x, y;
    x = std::move(y);
  }
  EXPECT_EQ(V(), *log);
}

TEST(OptionalTest, TestMoveAssignToFullFromEmpty) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x(Logger(17));
    Optional<Logger> y;
    log->push_back("---");
    x = std::move(y);
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "---", "1:17. destructor", "---"),
      *log);
}

TEST(OptionalTest, TestMoveAssignToEmptyFromFull) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x;
    Optional<Logger> y(Logger(17));
    log->push_back("---");
    x = std::move(y);
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "---", "2:17. move constructor (from 1:17)", "---",
        "1:17. destructor", "2:17. destructor"),
      *log);
}

TEST(OptionalTest, TestMoveAssignToFullFromFull) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x(Logger(17));
    Optional<Logger> y(Logger(42));
    log->push_back("---");
    x = std::move(y);
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "2:42. explicit constructor",
        "3:42. move constructor (from 2:42)", "2:42. destructor", "---",
        "1:42. operator= move (from 3:42)", "---", "3:42. destructor",
        "1:42. destructor"),
      *log);
}

TEST(OptionalTest, TestMoveAssignToEmptyFromT) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x;
    Logger y(17);
    log->push_back("---");
    x = Optional<Logger>(std::move(y));
    log->push_back("---");
  }
  EXPECT_EQ(V("0:17. explicit constructor", "---",
              "1:17. move constructor (from 0:17)",
              "2:17. move constructor (from 1:17)", "1:17. destructor", "---",
              "0:17. destructor", "2:17. destructor"),
            *log);
}

TEST(OptionalTest, TestMoveAssignToFullFromT) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x(Logger(17));
    Logger y(42);
    log->push_back("---");
    x = Optional<Logger>(std::move(y));
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:17. move constructor (from 0:17)",
        "0:17. destructor", "2:42. explicit constructor", "---",
        "3:42. move constructor (from 2:42)",
        "1:42. operator= move (from 3:42)", "3:42. destructor", "---",
        "2:42. destructor", "1:42. destructor"),
      *log);
}

TEST(OptionalTest, TestDereference) {
  auto log = Logger::Setup();
  {
    Optional<Logger> x(Logger(42));
    const auto& y = x;
    log->push_back("---");
    x->Foo();
    y->Foo();
    std::move(x)->Foo();
    std::move(y)->Foo();
    log->push_back("---");
    (*x).Foo();
    (*y).Foo();
    (*std::move(x)).Foo();
    (*std::move(y)).Foo();
    log->push_back("---");
  }
  EXPECT_EQ(V("0:42. explicit constructor",
              "1:42. move constructor (from 0:42)", "0:42. destructor", "---",
              "1:42. Foo()", "1:42. Foo() const", "1:42. Foo()",
              "1:42. Foo() const", "---", "1:42. Foo()", "1:42. Foo() const",
              "1:42. Foo()", "1:42. Foo() const", "---", "1:42. destructor"),
            *log);
}

TEST(OptionalTest, TestDereferenceWithDefault) {
  auto log = Logger::Setup();
  {
    const Logger a(17), b(42);
    Optional<Logger> x(a);
    Optional<Logger> y;
    log->push_back("-1-");
    EXPECT_EQ(a, x.value_or(Logger(42)));
    log->push_back("-2-");
    EXPECT_EQ(b, y.value_or(Logger(42)));
    log->push_back("-3-");
    EXPECT_EQ(a, Optional<Logger>(Logger(17)).value_or(b));
    log->push_back("-4-");
    EXPECT_EQ(b, Optional<Logger>().value_or(b));
    log->push_back("-5-");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:42. explicit constructor",
        "2:17. copy constructor (from 0:17)", "-1-",
        "3:42. explicit constructor", "operator== 0:17, 2:17",
        "3:42. destructor", "-2-", "4:42. explicit constructor",
        "operator== 1:42, 4:42", "4:42. destructor", "-3-",
        "5:17. explicit constructor", "6:17. move constructor (from 5:17)",
        "operator== 0:17, 6:17", "6:17. destructor", "5:17. destructor", "-4-",
        "operator== 1:42, 1:42", "-5-", "2:17. destructor", "1:42. destructor",
        "0:17. destructor"),
      *log);
}

TEST(OptionalTest, TestEquality) {
  auto log = Logger::Setup();
  {
    Logger a(17), b(42);
    Optional<Logger> ma1(a), ma2(a), mb(b), me1, me2;
    log->push_back("---");
    EXPECT_EQ(ma1, ma1);
    EXPECT_EQ(ma1, ma2);
    EXPECT_NE(ma1, mb);
    EXPECT_NE(ma1, me1);
    EXPECT_EQ(me1, me1);
    EXPECT_EQ(me1, me2);
    log->push_back("---");
  }
  EXPECT_EQ(
      V("0:17. explicit constructor", "1:42. explicit constructor",
        "2:17. copy constructor (from 0:17)",
        "3:17. copy constructor (from 0:17)",
        "4:42. copy constructor (from 1:42)", "---", "operator== 2:17, 2:17",
        "operator== 2:17, 3:17", "operator!= 2:17, 4:42", "---",
        "4:42. destructor", "3:17. destructor", "2:17. destructor",
        "1:42. destructor", "0:17. destructor"),
      *log);
}

TEST(OptionalTest, TestSwap) {
  auto log = Logger::Setup();
  {
    Logger a(17), b(42);
    Optional<Logger> x1(a), x2(b), y1(a), y2, z1, z2;
    log->push_back("---");
    swap(x1, x2);  // Swap full <-> full.
    swap(y1, y2);  // Swap full <-> empty.
    swap(z1, z2);  // Swap empty <-> empty.
    log->push_back("---");
  }
  EXPECT_EQ(V("0:17. explicit constructor", "1:42. explicit constructor",
              "2:17. copy constructor (from 0:17)",
              "3:42. copy constructor (from 1:42)",
              "4:17. copy constructor (from 0:17)", "---", "swap 2:42, 3:17",
              "5:17. move constructor (from 4:17)", "4:17. destructor", "---",
              "5:17. destructor", "3:17. destructor", "2:42. destructor",
              "1:42. destructor", "0:17. destructor"),
            *log);
}

}  // namespace rtc
