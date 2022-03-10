package com.jynx.pro.utils;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;

@Component
public class PriceUtils {

    /**
     * Convert large integer to decimal representation
     *
     * @param value {@link BigInteger}
     * @param decimals the number of decimals
     *
     * @return {@link BigDecimal}
     */
    public BigDecimal fromBigInteger(
            final BigInteger value,
            final int decimals
    ) {
        long modifier = Math.round(Math.pow(10, decimals));
        return new BigDecimal(value.divide(BigInteger.valueOf(modifier)));
    }

    /**
     * Convert long to decimal representation
     *
     * @param value {@link Long}
     * @param decimals the number of decimals
     *
     * @return {@link BigDecimal}
     */
    public BigDecimal fromLong(
            final long value,
            final int decimals
    ) {
        double modifier = Math.pow(10, decimals);
        return BigDecimal.valueOf(value / modifier);
    }

    /**
     * Convert decimal representation to long
     *
     * @param value {@link BigDecimal}
     * @param decimals the number of decimals
     *
     * @return {@link Long}
     */
    public long toLong(
            final BigDecimal value,
            final int decimals
    ) {
        double modifier = Math.pow(10, decimals);
        return value.multiply(BigDecimal.valueOf(modifier)).longValue();
    }

    /**
     * Convert decimal representation to large integer
     *
     * @param value {@link BigDecimal}
     * @param decimals the number of decimals
     *
     * @return {@link BigInteger}
     */
    public BigInteger toBigInteger(
            final BigDecimal value,
            final int decimals
    ) {
        double modifier = Math.pow(10, decimals);
        return value.multiply(BigDecimal.valueOf(modifier)).toBigInteger();
    }

    /**
     * Convert large integer to decimal representation using 18 decimal places
     *
     * @param value {@link BigInteger}
     *
     * @return {@link BigDecimal}
     */
    public BigDecimal fromBigInteger(
            final BigInteger value
    ) {
        return fromBigInteger(value, 18);
    }

    /**
     * Convert decimal representation to large integer using 18 decimal places
     *
     * @param value {@link BigDecimal}
     *
     * @return {@link BigInteger}
     */
    public BigInteger toBigInteger(
            final BigDecimal value
    ) {
        return toBigInteger(value, 18);
    }

}