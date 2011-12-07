package com.dynamo.cr.sceneed.core.util;

import javax.vecmath.Quat4d;
import javax.vecmath.Vector4d;

import com.dynamo.proto.DdfMath.Point3;
import com.dynamo.proto.DdfMath.Quat;

public class LoaderUtil {

    public static Vector4d toVector4(Point3 p) {
        return new Vector4d(p.getX(), p.getY(), p.getZ(), 1);
    }

    public static Quat4d toQuat4(Quat q) {
        return new Quat4d(q.getX(), q.getY(), q.getZ(), q.getW());
    }

    public static Point3 toPoint3(Vector4d v) {
        return Point3.newBuilder()
            .setX((float) v.getX())
            .setY((float) v.getY())
            .setZ((float) v.getZ()).build();
    }

    public static Quat toQuat(Quat4d q) {
        return Quat.newBuilder()
            .setX((float) q.getX())
            .setY((float) q.getY())
            .setZ((float) q.getZ())
            .setW((float) q.getW()).build();
    }


}
