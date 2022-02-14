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
                ethereumHelper.getJynxTokenContract().getContractAddress());
        Assertions.assertEquals(Convert.toWei("1000000000", Convert.Unit.WEI), jynxTotalSupply);
    }
}