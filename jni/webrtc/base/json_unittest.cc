/*
 *  Copyright 2009 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "webrtc/base/json.h"

#include <vector>

#include "webrtc/base/gunit.h"

namespace rtc {

static Json::Value in_s("foo");
static Json::Value in_sn("99");
static Json::Value in_si("-99");
static Json::Value in_sb("true");
static Json::Value in_sd("1.2");
static Json::Value in_n(12);
static Json::Value in_i(-12);
static Json::Value in_u(34U);
static Json::Value in_b(true);
static Json::Value in_d(1.2);
static Json::Value big_sn("12345678901234567890");
static Json::Value big_si("-12345678901234567890");
static Json::Value big_u(0xFFFFFFFF);
static Json::Value bad_a(Json::arrayValue);
static Json::Value bad_o(Json::objectValue);

TEST(JsonTest, GetString) {
  std::string out;
  EXPECT_TRUE(GetStringFromJson(in_s, &out));
  EXPECT_EQ("foo", out);
  EXPECT_TRUE(GetStringFromJson(in_sn, &out));
  EXPECT_EQ("99", out);
  EXPECT_TRUE(GetStringFromJson(in_si, &out));
  EXPECT_EQ("-99", out);
  EXPECT_TRUE(GetStringFromJson(in_i, &out));
  EXPECT_EQ("-12", out);
  EXPECT_TRUE(GetStringFromJson(in_n, &out));
  EXPECT_EQ("12", out);
  EXPECT_TRUE(GetStringFromJson(in_u, &out));
  EXPECT_EQ("34", out);
  EXPECT_TRUE(GetStringFromJson(in_b, &out));
  EXPECT_EQ("true", out);
  // Not supported here yet.
  EXPECT_FALSE(GetStringFromJson(bad_a, &out));
  EXPECT_FALSE(GetStringFromJson(bad_o, &out));
}

TEST(JsonTest, GetInt) {
  int out;
  EXPECT_TRUE(GetIntFromJson(in_sn, &out));
  EXPECT_EQ(99, out);
  EXPECT_TRUE(GetIntFromJson(in_si, &out));
  EXPECT_EQ(-99, out);
  EXPECT_TRUE(GetIntFromJson(in_n, &out));
  EXPECT_EQ(12, out);
  EXPECT_TRUE(GetIntFromJson(in_i, &out));
  EXPECT_EQ(-12, out);
  EXPECT_TRUE(GetIntFromJson(in_u, &out));
  EXPECT_EQ(34, out);
  EXPECT_TRUE(GetIntFromJson(in_b, &out));
  EXPECT_EQ(1, out);
  EXPECT_FALSE(GetIntFromJson(in_s, &out));
  EXPECT_FALSE(GetIntFromJson(big_sn, &out));
  EXPECT_FALSE(GetIntFromJson(big_si, &out));
  EXPECT_FALSE(GetIntFromJson(big_u, &out));
  EXPECT_FALSE(GetIntFromJson(bad_a, &out));
  EXPECT_FALSE(GetIntFromJson(bad_o, &out));
}

TEST(JsonTest, GetUInt) {
  unsigned int out;
  EXPECT_TRUE(GetUIntFromJson(in_sn, &out));
  EXPECT_EQ(99U, out);
  EXPECT_TRUE(GetUIntFromJson(in_n, &out));
  EXPECT_EQ(12U, out);
  EXPECT_TRUE(GetUIntFromJson(in_u, &out));
  EXPECT_EQ(34U, out);
  EXPECT_TRUE(GetUIntFromJson(in_b, &out));
  EXPECT_EQ(1U, out);
  EXPECT_TRUE(GetUIntFromJson(big_u, &out));
  EXPECT_EQ(0xFFFFFFFFU, out);
  EXPECT_FALSE(GetUIntFromJson(in_s, &out));
  // TODO: Fail reading negative strings.
  // EXPECT_FALSE(GetUIntFromJson(in_si, &out));
  EXPECT_FALSE(GetUIntFromJson(in_i, &out));
  EXPECT_FALSE(GetUIntFromJson(big_sn, &out));
  EXPECT_FALSE(GetUIntFromJson(big_si, &out));
  EXPECT_FALSE(GetUIntFromJson(bad_a, &out));
  EXPECT_FALSE(GetUIntFromJson(bad_o, &out));
}

TEST(JsonTest, GetBool) {
  bool out;
  EXPECT_TRUE(GetBoolFromJson(in_sb, &out));
  EXPECT_EQ(true, out);
  EXPECT_TRUE(GetBoolFromJson(in_n, &out));
  EXPECT_EQ(true, out);
  EXPECT_TRUE(GetBoolFromJson(in_i, &out));
  EXPECT_EQ(true, out);
  EXPECT_TRUE(GetBoolFromJson(in_u, &out));
  EXPECT_EQ(true, out);
  EXPECT_TRUE(GetBoolFromJson(in_b, &out));
  EXPECT_EQ(true, out);
  EXPECT_TRUE(GetBoolFromJson(big_u, &out));
  EXPECT_EQ(true, out);
  EXPECT_FALSE(GetBoolFromJson(in_s, &out));
  EXPECT_FALSE(GetBoolFromJson(in_sn, &out));
  EXPECT_FALSE(GetBoolFromJson(in_si, &out));
  EXPECT_FALSE(GetBoolFromJson(big_sn, &out));
  EXPECT_FALSE(GetBoolFromJson(big_si, &out));
  EXPECT_FALSE(GetBoolFromJson(bad_a, &out));
  EXPECT_FALSE(GetBoolFromJson(bad_o, &out));
}

TEST(JsonTest, GetDouble) {
  double out;
  EXPECT_TRUE(GetDoubleFromJson(in_sn, &out));
  EXPECT_EQ(99, out);
  EXPECT_TRUE(GetDoubleFromJson(in_si, &out));
  EXPECT_EQ(-99, out);
  EXPECT_TRUE(GetDoubleFromJson(in_sd, &out));
  EXPECT_EQ(1.2, out);
  EXPECT_TRUE(GetDoubleFromJson(in_n, &out));
  EXPECT_EQ(12, out);
  EXPECT_TRUE(GetDoubleFromJson(in_i, &out));
  EXPECT_EQ(-12, out);
  EXPECT_TRUE(GetDoubleFromJson(in_u, &out));
  EXPECT_EQ(34, out);
  EXPECT_TRUE(GetDoubleFromJson(in_b, &out));
  EXPECT_EQ(1, out);
  EXPECT_TRUE(GetDoubleFromJson(in_d, &out));
  EXPECT_EQ(1.2, out);
  EXPECT_FALSE(GetDoubleFromJson(in_s, &out));
}

TEST(JsonTest, GetFromArray) {
  Json::Value a, out;
  a.append(in_s);
  a.append(in_i);
  a.append(in_u);
  a.append(in_b);
  EXPECT_TRUE(GetValueFromJsonArray(a, 0, &out));
  EXPECT_TRUE(GetValueFromJsonArray(a, 3, &out));
  EXPECT_FALSE(GetValueFromJsonArray(a, 99, &out));
  EXPECT_FALSE(GetValueFromJsonArray(a, 0xFFFFFFFF, &out));
}

TEST(JsonTest, GetFromObject) {
  Json::Value o, out;
  o["string"] = in_s;
  o["int"] = in_i;
  o["uint"] = in_u;
  o["bool"] = in_b;
  EXPECT_TRUE(GetValueFromJsonObject(o, "int", &out));
  EXPECT_TRUE(GetValueFromJsonObject(o, "bool", &out));
  EXPECT_FALSE(GetValueFromJsonObject(o, "foo", &out));
  EXPECT_FALSE(GetValueFromJsonObject(o, "", &out));
}

namespace {
template <typename T>
std::vector<T> VecOf3(const T& a, const T& b, const T& c) {
  std::vector<T> in;
  in.push_back(a);
  in.push_back(b);
  in.push_back(c);
  return in;
}
template <typename T>
Json::Value JsonVecOf3(const T& a, const T& b, const T& c) {
  Json::Value in(Json::arrayValue);
  in.append(a);
  in.append(b);
  in.append(c);
  return in;
}
}  // unnamed namespace

TEST(JsonTest, ValueVectorToFromArray) {
  std::vector<Json::Value> in = VecOf3<Json::Value>("a", "b", "c");
  Json::Value out = ValueVectorToJsonArray(in);
  EXPECT_EQ(in.size(), out.size());
  for (Json::Value::ArrayIndex i = 0; i < in.size(); ++i) {
    EXPECT_EQ(in[i].asString(), out[i].asString());
  }
  Json::Value inj = JsonVecOf3<Json::Value>("a", "b", "c");
  EXPECT_EQ(inj, out);
  std::vector<Json::Value> outj;
  EXPECT_TRUE(JsonArrayToValueVector(inj, &outj));
  for (Json::Value::ArrayIndex i = 0; i < in.size(); i++) {
    EXPECT_EQ(in[i], outj[i]);
  }
}

TEST(JsonTest, IntVectorToFromArray) {
  std::vector<int> in = VecOf3<int>(1, 2, 3);
  Json::Value out = IntVectorToJsonArray(in);
  EXPECT_EQ(in.size(), out.size());
  for (Json::Value::ArrayIndex i = 0; i < in.size(); ++i) {
    EXPECT_EQ(in[i], out[i].asInt());
  }
  Json::Value inj = JsonVecOf3<int>(1, 2, 3);
  EXPECT_EQ(inj, out);
  std::vector<int> outj;
  EXPECT_TRUE(JsonArrayToIntVector(inj, &outj));
  for (Json::Value::ArrayIndex i = 0; i < in.size(); i++) {
    EXPECT_EQ(in[i], outj[i]);
  }
}

TEST(JsonTest, UIntVectorToFromArray) {
  std::vector<unsigned int> in = VecOf3<unsigned int>(1, 2, 3);
  Json::Value out = UIntVectorToJsonArray(in);
  EXPECT_EQ(in.size(), out.size());
  for (Json::Value::ArrayIndex i = 0; i < in.size(); ++i) {
    EXPECT_EQ(in[i], out[i].asUInt());
  }
  Json::Value inj = JsonVecOf3<unsigned int>(1, 2, 3);
  EXPECT_EQ(inj, out);
  std::vector<unsigned int> outj;
  EXPECT_TRUE(JsonArrayToUIntVector(inj, &outj));
  for (Json::Value::ArrayIndex i = 0; i < in.size(); i++) {
    EXPECT_EQ(in[i], outj[i]);
  }
}

TEST(JsonTest, StringVectorToFromArray) {
  std::vector<std::string> in = VecOf3<std::string>("a", "b", "c");
  Json::Value out = StringVectorToJsonArray(in);
  EXPECT_EQ(in.size(), out.size());
  for (Json::Value::ArrayIndex i = 0; i < in.size(); ++i) {
    EXPECT_EQ(in[i], out[i].asString());
  }
  Json::Value inj = JsonVecOf3<std::string>("a", "b", "c");
  EXPECT_EQ(inj, out);
  std::vector<std::string> outj;
  EXPECT_TRUE(JsonArrayToStringVector(inj, &outj));
  for (Json::Value::ArrayIndex i = 0; i < in.size(); i++) {
    EXPECT_EQ(in[i], outj[i]);
  }
}

TEST(JsonTest, BoolVectorToFromArray) {
  std::vector<bool> in = VecOf3<bool>(false, true, false);
  Json::Value out = BoolVectorToJsonArray(in);
  EXPECT_EQ(in.size(), out.size());
  for (Json::Value::ArrayIndex i = 0; i < in.size(); ++i) {
    EXPECT_EQ(in[i], out[i].asBool());
  }
  Json::Value inj = JsonVecOf3<bool>(false, true, false);
  EXPECT_EQ(inj, out);
  std::vector<bool> outj;
  EXPECT_TRUE(JsonArrayToBoolVector(inj, &outj));
  for (Json::Value::ArrayIndex i = 0; i < in.size(); i++) {
    EXPECT_EQ(in[i], outj[i]);
  }
}

TEST(JsonTest, DoubleVectorToFromArray) {
  std::vector<double> in = VecOf3<double>(1.0, 2.0, 3.0);
  Json::Value out = DoubleVectorToJsonArray(in);
  EXPECT_EQ(in.size(), out.size());
  for (Json::Value::ArrayIndex i = 0; i < in.size(); ++i) {
    EXPECT_EQ(in[i], out[i].asDouble());
  }
  Json::Value inj = JsonVecOf3<double>(1.0, 2.0, 3.0);
  EXPECT_EQ(inj, out);
  std::vector<double> outj;
  EXPECT_TRUE(JsonArrayToDoubleVector(inj, &outj));
  for (Json::Value::ArrayIndex i = 0; i < in.size(); i++) {
    EXPECT_EQ(in[i], outj[i]);
  }
}

}  // namespace rtc
