package dev.queryengine.exec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.temporal.Temporal;
import java.util.Base64;

final class JdbcValues {

    private JdbcValues() {
    }

    static Object normalize(Object value) {
        if (value == null || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof BigInteger big) {
            if (big.bitLength() < 63) {
                return big.longValue();
            }
            return big;
        }
        if (value instanceof BigDecimal dec) {
            BigDecimal stripped = dec.stripTrailingZeros();
            if (stripped.scale() <= 0 && stripped.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
                return stripped.longValue();
            }
            return dec;
        }
        if (value instanceof Double || value instanceof Float) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (value instanceof Temporal temporal) {
            return temporal.toString();
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value.toString();
    }
}
