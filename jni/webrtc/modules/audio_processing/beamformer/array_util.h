/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_ARRAY_UTIL_H_
#define WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_ARRAY_UTIL_H_

#include <cmath>
#include <vector>

#include "webrtc/base/optional.h"

namespace webrtc {

// Coordinates in meters. The convention used is:
// x: the horizontal dimension, with positive to the right from the camera's
//    perspective.
// y: the depth dimension, with positive forward from the camera's
//    perspective.
// z: the vertical dimension, with positive upwards.
template<typename T>
struct CartesianPoint {
  CartesianPoint() {
    c[0] = 0;
    c[1] = 0;
    c[2] = 0;
  }
  CartesianPoint(T x, T y, T z) {
    c[0] = x;
    c[1] = y;
    c[2] = z;
  }
  T x() const { return c[0]; }
  T y() const { return c[1]; }
  T z() const { return c[2]; }
  T c[3];
};

using Point = CartesianPoint<float>;

// Calculates the direction from a to b.
Point PairDirection(const Point& a, const Point& b);

float DotProduct(const Point& a, const Point& b);
Point CrossProduct(const Point& a, const Point& b);

bool AreParallel(const Point& a, const Point& b);
bool ArePerpendicular(const Point& a, const Point& b);

// Returns the minimum distance between any two Points in the given
// |array_geometry|.
float GetMinimumSpacing(const std::vector<Point>& array_geometry);

// If the given array geometry is linear it returns the direction without
// normalizing.
rtc::Optional<Point> GetDirectionIfLinear(
    const std::vector<Point>& array_geometry);

// If the given array geometry is planar it returns the normal without
// normalizing.
rtc::Optional<Point> GetNormalIfPlanar(
    const std::vector<Point>& array_geometry);

// Returns the normal of an array if it has one and it is in the xy-plane.
rtc::Optional<Point> GetArrayNormalIfExists(
    const std::vector<Point>& array_geometry);

// The resulting Point will be in the xy-plane.
Point AzimuthToPoint(float azimuth);

template<typename T>
float Distance(CartesianPoint<T> a, CartesianPoint<T> b) {
  return std::sqrt((a.x() - b.x()) * (a.x() - b.x()) +
                   (a.y() - b.y()) * (a.y() - b.y()) +
                   (a.z() - b.z()) * (a.z() - b.z()));
}

// The convention used:
// azimuth: zero is to the right from the camera's perspective, with positive
//          angles in radians counter-clockwise.
// elevation: zero is horizontal, with positive angles in radians upwards.
// radius: distance from the camera in meters.
template <typename T>
struct SphericalPoint {
  SphericalPoint(T azimuth, T elevation, T radius) {
    s[0] = azimuth;
    s[1] = elevation;
    s[2] = radius;
  }
  T azimuth() const { return s[0]; }
  T elevation() const { return s[1]; }
  T distance() const { return s[2]; }
  T s[3];
};

using SphericalPointf = SphericalPoint<float>;

// Helper functions to transform degrees to radians and the inverse.
template <typename T>
T DegreesToRadians(T angle_degrees) {
  return M_PI * angle_degrees / 180;
}

template <typename T>
T RadiansToDegrees(T angle_radians) {
  return 180 * angle_radians / M_PI;
}

}  // namespace webrtc

#endif  // WEBRTC_MODULES_AUDIO_PROCESSING_BEAMFORMER_ARRAY_UTIL_H_
