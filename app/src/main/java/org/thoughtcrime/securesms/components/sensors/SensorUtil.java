package org.thoughtcrime.securesms.components.sensors;

public final class SensorUtil {

  private SensorUtil() { }

  public static void getRotationMatrixWithoutMagneticSensorData(float[] rotationMatrix, float[] accelerometerReading) {
    double gx, gy, gz;
    gx = accelerometerReading[0] / 9.81f;
    gy = accelerometerReading[1] / 9.81f;
    gz = accelerometerReading[2] / 9.81f;

    float pitch   = (float) -Math.atan(gy / Math.sqrt(gx * gx + gz * gz));
    float roll    = (float) -Math.atan(gx / Math.sqrt(gy * gy + gz * gz));
    float azimuth = 0;

    float[] fakeMagnetometerReading = { azimuth, pitch, roll };

    System.arraycopy(getRotationMatrixForOrientation(fakeMagnetometerReading), 0, rotationMatrix, 0, rotationMatrix.length);
  }

  private static float[] getRotationMatrixForOrientation(float[] o) {
    float[] xM = new float[9];
    float[] yM = new float[9];
    float[] zM = new float[9];

    float sinX = (float) Math.sin(o[1]);
    float cosX = (float) Math.cos(o[1]);
    float sinY = (float) Math.sin(o[2]);
    float cosY = (float) Math.cos(o[2]);
    float sinZ = (float) Math.sin(o[0]);
    float cosZ = (float) Math.cos(o[0]);

    xM[0] = 1.0f;
    xM[1] = 0.0f;
    xM[2] = 0.0f;

    xM[3] = 0.0f;
    xM[4] = cosX;
    xM[5] = sinX;

    xM[6] = 0.0f;
    xM[7] = -sinX;
    xM[8] = cosX;

    yM[0] = cosY;
    yM[1] = 0.0f;
    yM[2] = sinY;

    yM[3] = 0.0f;
    yM[4] = 1.0f;
    yM[5] = 0.0f;

    yM[6] = -sinY;
    yM[7] = 0.0f;
    yM[8] = cosY;

    zM[0] = cosZ;
    zM[1] = sinZ;
    zM[2] = 0.0f;

    zM[3] = -sinZ;
    zM[4] = cosZ;
    zM[5] = 0.0f;

    zM[6] = 0.0f;
    zM[7] = 0.0f;
    zM[8] = 1.0f;

    float[] resultMatrix = matrixMultiplication(xM, yM);
    resultMatrix = matrixMultiplication(zM, resultMatrix);
    return resultMatrix;
  }

  private static float[] matrixMultiplication(float[] A, float[] B) {
    float[] result = new float[9];

    result[0] = A[0] * B[0] + A[1] * B[3] + A[2] * B[6];
    result[1] = A[0] * B[1] + A[1] * B[4] + A[2] * B[7];
    result[2] = A[0] * B[2] + A[1] * B[5] + A[2] * B[8];

    result[3] = A[3] * B[0] + A[4] * B[3] + A[5] * B[6];
    result[4] = A[3] * B[1] + A[4] * B[4] + A[5] * B[7];
    result[5] = A[3] * B[2] + A[4] * B[5] + A[5] * B[8];

    result[6] = A[6] * B[0] + A[7] * B[3] + A[8] * B[6];
    result[7] = A[6] * B[1] + A[7] * B[4] + A[8] * B[7];
    result[8] = A[6] * B[2] + A[7] * B[5] + A[8] * B[8];

    return result;
  }
}
