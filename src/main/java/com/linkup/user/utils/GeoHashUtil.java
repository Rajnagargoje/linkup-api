package com.linkup.user.utils;

/**
 * Standard base-32 geohash encoder. Used to give User.geohash an
 * indexable prefix for "nearby" queries. This alone doesn't replace a
 * proper geospatial query (PostGIS / a geohash-prefix range query) —
 * it's a stepping stone so the column exists and is populated; wire up
 * an actual prefix-range query in UserRepository when you're ready to
 * stop doing a full-table haversine scan in LocationServiceImpl.
 */
public final class GeoHashUtil {

    private static final char[] BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();

    private GeoHashUtil() {
    }

    public static String encode(double latitude, double longitude, int precision) {
        double[] latRange = {-90.0, 90.0};
        double[] lonRange = {-180.0, 180.0};
        StringBuilder geohash = new StringBuilder();
        boolean isEven = true;
        int bit = 0;
        int ch = 0;

        while (geohash.length() < precision) {
            double mid;
            if (isEven) {
                mid = (lonRange[0] + lonRange[1]) / 2;
                if (longitude > mid) {
                    ch |= (1 << (4 - bit));
                    lonRange[0] = mid;
                } else {
                    lonRange[1] = mid;
                }
            } else {
                mid = (latRange[0] + latRange[1]) / 2;
                if (latitude > mid) {
                    ch |= (1 << (4 - bit));
                    latRange[0] = mid;
                } else {
                    latRange[1] = mid;
                }
            }
            isEven = !isEven;
            if (bit < 4) {
                bit++;
            } else {
                geohash.append(BASE32[ch]);
                bit = 0;
                ch = 0;
            }
        }
        return geohash.toString();
    }
}
