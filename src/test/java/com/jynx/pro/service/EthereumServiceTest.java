package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.jupiter.api.*;
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
@Disabled
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class EthereumServiceTest extends IntegrationTest {

    private static final String JYNX_KEY = "02d47b3068c9ff8e25eec7c83b74eb2c61073a1862f925b644b4b234c21e83dd";

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

    private void stakeTokens(double expectedStake, boolean unstake) throws InterruptedException {
        long modifier = (long) Math.pow(10, 18);
        BigInteger amount = BigInteger.valueOf(100).multiply(BigInteger.valueOf(modifier));
        ethereumHelper.approveJynx(ethereumHelper.getJynxProBridge().getContractAddress(), amount);
        if(unstake) {
            ethereumHelper.removeTokens(JYNX_KEY, amount);
        } else {
            ethereumHelper.stakeTokens(JYNX_KEY, amount);
        }
        Thread.sleep(30000L);
        ethereumService.confirmEvents();
        List<Event> events = eventRepository.findByConfirmed(false);
        Assertions.assertEquals(events.size(), 0);
        Optional<User> user = userRepository.findByPublicKey(JYNX_KEY);
        Assertions.assertTrue(user.isPresent());
        Optional<Stake> stake = stakeRepository.findByUser(user.get());
        Assertions.assertTrue(stake.isPresent());
        Assertions.assertEquals(stake.get().getAmount().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(expectedStake).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    public void testStakeAndRemoveTokens() throws InterruptedException {
        stakeTokens(100, false);
        stakeTokens(0, true);
    }

    @Test
    public void testDepositAsset() throws Exception {
        Asset asset = createAndEnactAsset(true);
        boolean assetActive = ethereumHelper.getJynxProBridge().assets(asset.getAddress()).send();
        Assertions.assertTrue(assetActive);
        BigInteger amount = priceUtils.toBigInteger(BigDecimal.TEN);
        ethereumHelper.approveDai(ethereumHelper.getJynxProBridge().getContractAddress(), amount);
        ethereumHelper.depositAsset(asset.getAddress(), amount, JYNX_KEY);
        Thread.sleep(30000L);
        ethereumService.confirmEvents();
        List<Event> events = eventRepository.findByConfirmed(false);
        Assertions.assertEquals(events.size(), 0);
        Optional<User> user = userRepository.findByPublicKey(JYNX_KEY);
        Assertions.assertTrue(user.isPresent());
        Optional<Account> account = accountRepository.findByUserAndAsset(user.get(), asset);
        Assertions.assertTrue(account.isPresent());
        Assertions.assertEquals(account.get().getBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.TEN.setScale(2, RoundingMode.HALF_UP));
        Assertions.assertEquals(account.get().getAvailableBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.TEN.setScale(2, RoundingMode.HALF_UP));
        Assertions.assertEquals(account.get().getMarginBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
}
