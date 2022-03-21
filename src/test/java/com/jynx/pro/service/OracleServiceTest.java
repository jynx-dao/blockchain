package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.OracleType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Oracle;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ORACLE_SERVICE_TEST", matches = "true")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class OracleServiceTest {

    @Autowired
    private OracleService oracleService;

    @Test
    public void testGetSettlementValueFromPolygon() {
        BigDecimal value = oracleService.getSettlementValue(new Market().setOracle(new Oracle()
                .setKey("AAPL")
                .setType(OracleType.POLYGON))
                .setLastSettlement(LocalDateTime.of(2021, Month.DECEMBER,
                        22, 16, 0).toEpochSecond(ZoneOffset.UTC)));
        Assertions.assertTrue(value.doubleValue() > 0);
    }

    @Test
    public void testGetSettlementValueFromCoinbase() {
        BigDecimal value = oracleService.getSettlementValue(new Market().setOracle(new Oracle()
                        .setKey("BTC-USDT")
                        .setType(OracleType.COINBASE))
                .setLastSettlement(LocalDateTime.of(2021, Month.DECEMBER,
                        22, 16, 0).toEpochSecond(ZoneOffset.UTC)));
        Assertions.assertTrue(value.doubleValue() > 0);
    }

    @Test
    public void testGetSettlementValueFromSignedData() {
        BigDecimal value = oracleService.getSettlementValue(new Market().setOracle(new Oracle()
                        .setKey("BTC-USDT")
                        .setType(OracleType.SIGNED_DATA))
                .setLastSettlement(LocalDateTime.of(2021, Month.DECEMBER,
                        22, 16, 0).toEpochSecond(ZoneOffset.UTC)));
        Assertions.assertEquals(0, value.doubleValue());
    }
}