package de.fhws.indoor.sensorreadout.math;

/**
 * MadgwickFilter in the relative variant (without magnetometer).
 * This is basically an alternative implementation of GameRotationVector.
 */
public class MadgwickFilter {
    private double beta = 0;
    private Quaternion q = new Quaternion();

    public MadgwickFilter(double beta) {
        this.beta = beta;
    }
    public Quaternion getQuaternion() { return q; }

    public void calculcate(long timestampDeltaNs, Vec3 accel, Vec3 gyro) {
        double recipNorm;
        double s0, s1, s2, s3;
        double _2q0, _2q1, _2q2, _2q3, _4q0, _4q1, _4q2 ,_8q1, _8q2, q0q0, q1q1, q2q2, q3q3;

        double ax = accel.x;
        double ay = accel.y;
        double az = accel.z;
        double gx = gyro.x;
        double gy = gyro.y;
        double gz = gyro.z;

        // Rate of change of quaternion from gyroscope
        double qDot1 = 0.5f * (-q.q[1] * gx - q.q[2] * gy - q.q[3] * gz);
        double qDot2 = 0.5f * (q.q[0] * gx + q.q[2] * gz - q.q[3] * gy);
        double qDot3 = 0.5f * (q.q[0] * gy - q.q[1] * gz + q.q[3] * gx);
        double qDot4 = 0.5f * (q.q[0] * gz + q.q[1] * gy - q.q[2] * gx);

        // Compute feedback only if accelerometer measurement valid (avoids NaN in accelerometer normalisation)
        if(!((ax == 0.0f) && (ay == 0.0f) && (az == 0.0f))) {

            // Normalise accelerometer measurement
            recipNorm = 1.0 / Math.sqrt(ax * ax + ay * ay + az * az);
            ax *= recipNorm;
            ay *= recipNorm;
            az *= recipNorm;

            // Auxiliary variables to avoid repeated arithmetic
            _2q0 = 2.0f * q.q[0];
            _2q1 = 2.0f * q.q[1];
            _2q2 = 2.0f * q.q[2];
            _2q3 = 2.0f * q.q[3];
            _4q0 = 4.0f * q.q[0];
            _4q1 = 4.0f * q.q[1];
            _4q2 = 4.0f * q.q[2];
            _8q1 = 8.0f * q.q[1];
            _8q2 = 8.0f * q.q[2];
            q0q0 = q.q[0] * q.q[0];
            q1q1 = q.q[1] * q.q[1];
            q2q2 = q.q[2] * q.q[2];
            q3q3 = q.q[3] * q.q[3];

            // Gradient decent algorithm corrective step
            s0 = _4q0 * q2q2 + _2q2 * ax + _4q0 * q1q1 - _2q1 * ay;
            s1 = _4q1 * q3q3 - _2q3 * ax + 4.0f * q0q0 * q.q[1] - _2q0 * ay - _4q1 + _8q1 * q1q1 + _8q1 * q2q2 + _4q1 * az;
            s2 = 4.0f * q0q0 * q.q[2] + _2q0 * ax + _4q2 * q3q3 - _2q3 * ay - _4q2 + _8q2 * q1q1 + _8q2 * q2q2 + _4q2 * az;
            s3 = 4.0f * q1q1 * q.q[3] - _2q1 * ax + 4.0f * q2q2 * q.q[3] - _2q2 * ay;
            recipNorm = 1.0 / Math.sqrt(s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3); // normalise step magnitude
            s0 *= recipNorm;
            s1 *= recipNorm;
            s2 *= recipNorm;
            s3 *= recipNorm;

            // Apply feedback step
            qDot1 -= beta * s0;
            qDot2 -= beta * s1;
            qDot3 -= beta * s2;
            qDot4 -= beta * s3;
        }

        double timeStep = timestampDeltaNs / 1000000000.0;
        // Integrate rate of change of quaternion to yield quaternion
        q.q[0] += qDot1 * timeStep;
        q.q[1] += qDot2 * timeStep;
        q.q[2] += qDot3 * timeStep;
        q.q[3] += qDot4 * timeStep;

        // Normalise quaternion
        recipNorm = 1.0 / Math.sqrt(q.q[0] * q.q[0] + q.q[1] * q.q[1] + q.q[2] * q.q[2] + q.q[3] * q.q[3]);
        q.q[0] *= recipNorm;
        q.q[1] *= recipNorm;
        q.q[2] *= recipNorm;
        q.q[3] *= recipNorm;
    }
}