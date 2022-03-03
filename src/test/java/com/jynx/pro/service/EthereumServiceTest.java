package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.WithdrawalStatus;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.CreateWithdrawalRequest;
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

    private static final String JYNX_KEY = "02d47b3068c9ff8e25eec7c83b74eb2c61073a1862f925b644b4b234c21e83dd";

    @BeforeEach
    public void setup() {
        initializeState();
        databaseTransactionManager.createTransaction();
    }

    @AfterEach
    public void shutdown() {
        databaseTransactionManager.commit();
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
    public void testGetTokenSupplyWithError() {
        try {
            ethereumService.totalSupply("12345");
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_GET_SUPPLY);
        }
    }

    @Test
    public void testDecimalPlacesWithError() {
        try {
            ethereumService.decimalPlaces("12345");
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_GET_DECIMAL_PLACES);
        }
    }

    @Test
    public void testWithdrawAssetsWithError() {
        try {
            ethereumService.withdrawAssets(List.of("12345"), List.of(BigInteger.ONE), List.of("12345"));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_WITHDRAW_ASSETS);
        }
    }

    @Test
    public void testRemoveAssetWithError() {
        try {
            ethereumService.removeAsset(null);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_REMOVE_ASSET);
        }
    }

    @Test
    public void testAddAssetWithError() {
        try {
            ethereumService.addAsset(null);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_ADD_ASSET);
        }
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

    private Asset depositAsset() throws Exception {
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
        return asset;
    }

    private void withdrawAsset(
            final Asset asset
    ) {
        Optional<User> user = userRepository.findByPublicKey(JYNX_KEY);
        Assertions.assertTrue(user.isPresent());
        CreateWithdrawalRequest request = new CreateWithdrawalRequest();
        request.setUser(user.get());
        request.setAssetId(asset.getId());
        request.setAmount(BigDecimal.TEN);
        request.setDestination(ethereumHelper.getJynxToken().getContractAddress());
        Withdrawal withdrawal = accountService.createWithdrawal(request);
        accountService.processWithdrawals();
        Optional<Withdrawal> withdrawalOptional = withdrawalRepository.findById(withdrawal.getId());
        Assertions.assertTrue(withdrawalOptional.isPresent());
        Assertions.assertEquals(withdrawalOptional.get().getStatus(), WithdrawalStatus.DEBITED);
        Optional<Account> account = accountRepository.findByUserAndAsset(user.get(), asset);
        Assertions.assertTrue(account.isPresent());
        Assertions.assertEquals(account.get().getBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        Assertions.assertEquals(account.get().getAvailableBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        Assertions.assertEquals(account.get().getMarginBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    public void testStakeAndRemoveTokens() throws InterruptedException {
        stakeTokens(100, false);
        stakeTokens(0, true);
    }

    @Test
    public void testDepositAndWithdrawAsset() throws Exception {
        Asset asset = depositAsset();
        withdrawAsset(asset);
    }

    @Test
    public void testConfirmEventsFailed() {
        ganache.stop();
        setupComplete = false;
        List<Event> events = ethereumService.confirmEvents();
        Assertions.assertEquals(events.size(), 0);
    }
}
