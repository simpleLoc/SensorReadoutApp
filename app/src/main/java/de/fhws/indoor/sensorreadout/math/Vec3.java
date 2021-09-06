package de.fhws.indoor.sensorreadout.math;

public class Vec3 {
    public double x;
    public double y;
    public double z;

    public Vec3(double x, double y, double z) {
        this.set(x, y, z);
    }
    public Vec3() {
        this(0.0, 0.0, 0.0);
    }

    public void set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 add(Vec3 o) {
        this.x += o.x;
        this.y += o.y;
        this.z += o.z;
        return this;
    }
    public Vec3 sub(Vec3 o) {
        this.x -= o.x;
        this.y -= o.y;
        this.z -= o.z;
        return this;
    }
    public Vec3 scaled(double scalar) {
        return new Vec3(x * scalar, y * scalar, z * scalar);
    }

    public static final Vec3 sub(Vec3 a, Vec3 b) {
        return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z);
    }
    public static final Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    public Vec3 normalize() {
        double len = Math.sqrt(x*x + y*y + z*z);
        this.x /= len;
        this.y /= len;
        this.z /= len;
        return this;
    }
    public double dot(Vec3 o) {
        return (x*o.x + y*o.y + z*o.z);
    }
    public Vec3 cross(Vec3 o) {
        return new Vec3(
                y*o.z - z*o.y,
                z*o.x - x*o.z,
                x*o.y - y*o.x
        );
    }
}