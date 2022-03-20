package com.jynx.pro.service;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.mashape.unirest.http.Unirest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mockStatic;

@EnabledIfEnvironmentVariable(named = "POLYGON_SERVICE_TEST", matches = "true")
public class PolygonServiceTest {

    private final PolygonService polygonService = new PolygonService();

    @BeforeEach
    public void setup() {
        polygonService.setApiKey("p_D7kUuuF1h6Kkx4Cun_SnRSiV2bWT76");
    }

    @Test
    public void testGetPrice() {
        long time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        BigDecimal price = polygonService.getPriceAt("C:EURUSD", time);
        Assertions.assertTrue(price.doubleValue() > 0);
    }

    @Test
    public void testGetPriceFailsWhenOutOfRange() {
        long time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        try {
            polygonService.getPriceAt("AAPL", time / 1000);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_GET_POLYGON_PRICE);
        }
    }

    @Test
    public void testGetPriceFailsWhenError() {
        try (MockedStatic<Unirest> mocked = mockStatic(Unirest.class)) {
            mocked.when(() -> Unirest.get(Mockito.anyString())).thenReturn(null);
            try {
                long time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
                polygonService.getPriceAt("AAPL", time);
                Assertions.fail();
            } catch(JynxProException e) {
                Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_GET_POLYGON_PRICE);
            }
        }
    }
}