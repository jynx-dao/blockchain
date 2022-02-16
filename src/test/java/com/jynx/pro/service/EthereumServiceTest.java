package com.jynx.pro.service;

import com.jynx.pro.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class EthereumServiceTest extends IntegrationTest {

    @BeforeEach
    public void setup() {
        initializeState();
    }

    @AfterEach
    public void shutdown() {
        clearState();
    }

    @Test
    public void testGetTokenSupply() {
        BigDecimal jynxTotalSupply = ethereumService.totalSupply(
                ethereumHelper.getJynxToken().getContractAddress());
        Assertions.assertEquals(Convert.toWei("1000000000", Convert.Unit.WEI)
                .setScale(1, RoundingMode.HALF_UP), jynxTotalSupply
                .setScale(1, RoundingMode.HALF_UP));
    }

    @Test
    public void testStakeTokens() throws InterruptedException {
        String jynxKey = "02d47b3068c9ff8e25eec7c83b74eb2c61073a1862f925b644b4b234c21e83dd";
        ethereumHelper.approveJynx(ethereumHelper.getJynxProBridge().getContractAddress(), BigInteger.ONE);
        ethereumHelper.stakeTokens(jynxKey, BigInteger.ONE);
        Thread.sleep(60000L);
    }
}