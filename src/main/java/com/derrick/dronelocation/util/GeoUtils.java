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
}
