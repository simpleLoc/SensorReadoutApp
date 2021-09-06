package de.fhws.indoor.sensorreadout.math;

public class Quaternion {
    public double q[] = new double[]{1, 0, 0, 0};

    public Quaternion() {
        this(1, 0, 0, 0);
    }
    public Quaternion(double w, double x, double y, double z) {
        q[0] = w;
        q[1] = x;
        q[2] = y;
        q[3] = z;
    }

    // see: Eigen - QuaternionBase<Derived>::_transformVector(const Vector3& v)
    public Vec3 transformVector(Vec3 vec) {
        Vec3 qVec = new Vec3(q[1], q[2], q[3]);
        Vec3 uv = qVec.cross(vec);
        uv.add(uv);
        return Vec3.add(vec, uv.scaled(q[0])).add(qVec.cross(uv));
    }
}
