package com.derrick.dronelocation.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class GeoUtils {

    private static final double EARTH_RADIUS = 6371000.0;

    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    public static double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double y = Math.sin(dLng) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    public static double[] calculateDestinationPoint(double lat, double lng, double bearing, double distance) {
        double lat1Rad = Math.toRadians(lat);
        double lng1Rad = Math.toRadians(lng);
        double bearingRad = Math.toRadians(bearing);
        double angularDistance = distance / EARTH_RADIUS;

        double lat2Rad = Math.asin(Math.sin(lat1Rad) * Math.cos(angularDistance) +
                Math.cos(lat1Rad) * Math.sin(angularDistance) * Math.cos(bearingRad));

        double lng2Rad = lng1Rad + Math.atan2(
                Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(lat1Rad),
                Math.cos(angularDistance) - Math.sin(lat1Rad) * Math.sin(lat2Rad)
        );

        return new double[]{
                Math.toDegrees(lat2Rad),
                Math.toDegrees(lng2Rad)
        };
    }

    public static double calculateTotalDistance(double[][] waypoints) {
        if (waypoints == null || waypoints.length < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 0; i < waypoints.length - 1; i++) {
            totalDistance += calculateDistance(
                    waypoints[i][0], waypoints[i][1],
                    waypoints[i + 1][0], waypoints[i + 1][1]
            );
        }

        return totalDistance;
    }

    public static double calculateEstimatedTime(double distance, double speed) {
        if (speed <= 0) {
            return 0.0;
        }
        return distance / speed;
    }

    public static BigDecimal formatCoordinate(double coordinate) {
        return BigDecimal.valueOf(coordinate).setScale(8, RoundingMode.HALF_UP);
    }

    public static boolean isValidLatitude(double lat) {
        return lat >= -90 && lat <= 90;
    }

    public static boolean isValidLongitude(double lng) {
        return lng >= -180 && lng <= 180;
    }

    /**
     * 将 GCJ-02 坐标系转换为 WGS84 坐标系
     * @param gcjLat GCJ-02 纬度
     * @param gcjLng GCJ-02 经度
     * @return WGS84 坐标 [lat, lng]
     */
    public static double[] gcj02ToWgs84(double gcjLat, double gcjLng) {
        if (!isOutOfChina(gcjLat, gcjLng)) {
            return new double[]{gcjLat, gcjLng};
        }

        double dLat = transformLat(gcjLng - 105.0, gcjLat - 35.0);
        double dLng = transformLng(gcjLng - 105.0, gcjLat - 35.0);
        double radLat = gcjLat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - 0.00669342162296594323 * magic * magic;
        double sqrtMagic = Math.sqrt(magic);

        dLat = (dLat * 180.0) / ((6378245.0 * (1 - 0.00669342162296594323)) / (magic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (6378245.0 / sqrtMagic * Math.cos(radLat) * Math.PI);

        double wgsLat = gcjLat - dLat;
        double wgsLng = gcjLng - dLng;

        return new double[]{wgsLat, wgsLng};
    }

    /**
     * 判断坐标是否在中国境外
     */
    private static boolean isOutOfChina(double lat, double lng) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    /**
     * 转换纬度
     */
    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * 转换经度
     */
    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }
}
