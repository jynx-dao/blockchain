package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.entity.Event;
import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
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
import java.util.List;
import java.util.Optional;

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
        long modifier = (long) Math.pow(10, 18);
        BigInteger amount = BigInteger.valueOf(100).multiply(BigInteger.valueOf(modifier));
        ethereumHelper.approveJynx(ethereumHelper.getJynxProBridge().getContractAddress(), amount);
        ethereumHelper.stakeTokens(jynxKey, amount);
        Thread.sleep(30000L);
        ethereumService.confirmEvents();
        List<Event> events = eventRepository.findByConfirmed(false);
        Assertions.assertEquals(events.size(), 0);
        Optional<User> user = userRepository.findByPublicKey(jynxKey);
        Assertions.assertTrue(user.isPresent());
        Optional<Stake> stake = stakeRepository.findByUser(user.get());
        Assertions.assertTrue(stake.isPresent());
        Assertions.assertEquals(stake.get().getAmount().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP));
    }
}