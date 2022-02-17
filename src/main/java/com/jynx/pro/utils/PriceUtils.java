package com.jynx.pro.utils;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;

@Component
public class PriceUtils {

    public BigDecimal fromBigInteger(
            final BigInteger value,
            final int decimals
    ) {
        long modifier = Math.round(Math.pow(10, decimals));
        return new BigDecimal(value.divide(BigInteger.valueOf(modifier)));
    }

    public BigDecimal fromLong(
            final long value,
            final int decimals
    ) {
        double modifier = Math.pow(10, decimals);
        return BigDecimal.valueOf(value / modifier);
    }

    public long toLong(
            final BigDecimal value,
            final int decimals
    ) {
        double modifier = Math.pow(10, decimals);
        return value.multiply(BigDecimal.valueOf(modifier)).longValue();
    }

    public BigInteger toBigInteger(
            final BigDecimal value,
            final int decimals
    ) {
        double modifier = Math.pow(10, decimals);
        return value.multiply(BigDecimal.valueOf(modifier)).toBigInteger();
    }

    public BigDecimal fromBigInteger(
            final BigInteger value
    ) {
        return fromBigInteger(value, 18);
    }

    public BigDecimal fromLong(
            final long value
    ) {
        return fromLong(value, 18);
    }

    public long toLong(
            final BigDecimal value
    ) {
        return toLong(value, 18);
    }

    public BigInteger toBigInteger(
            final BigDecimal value
    ) {
        return toBigInteger(value, 18);
    }

}