package com.jynx.pro.service;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.mashape.unirest.http.Unirest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mockStatic;

public class BinanceServiceTest {

    private final BinanceService binanceService = new BinanceService();

    @Test
    public void testGetPrice() {
        long time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        BigDecimal price = binanceService.getPriceAt("BTCUSDT", time);
        Assertions.assertTrue(price.doubleValue() > 0);
    }

    @Test
    public void testGetPriceFailsWhenOutOfRange() {
        long time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        try {
            binanceService.getPriceAt("BTCUSDT", time / 1000);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_GET_BINANCE_PRICE);
        }
    }

    @Test
    public void testGetPriceFailsWhenError() {
        try (MockedStatic mocked = mockStatic(Unirest.class)) {
            mocked.when(() -> Unirest.get(Mockito.anyString())).thenReturn(null);
            try {
                long time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
                binanceService.getPriceAt("BTCUSDT", time);
                Assertions.fail();
            } catch(JynxProException e) {
                Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_GET_BINANCE_PRICE);
            }
        }
    }
}