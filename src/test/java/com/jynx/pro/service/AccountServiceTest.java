package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.WithdrawalStatus;
import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Withdrawal;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.request.CreateWithdrawalRequest;
import com.jynx.pro.request.SingleItemRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class AccountServiceTest extends IntegrationTest {

    @Autowired
    private AccountService accountService;

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
    public void testWithdrawFailsWithInsufficientBalance() throws InterruptedException {
        Asset asset = createAndEnactAsset(false);
        try {
            CreateWithdrawalRequest request = new CreateWithdrawalRequest();
            request.setUser(makerUser);
            request.setDestination(ETH_ADDRESS);
            request.setAssetId(asset.getId());
            request.setAmount(BigDecimal.valueOf(1000000000).multiply(BigDecimal.TEN));
            accountService.createWithdrawal(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    @Test
    public void testWithdrawFailsWithMissingAmount() throws InterruptedException {
        Asset asset = createAndEnactAsset(false);
        try {
            CreateWithdrawalRequest request = new CreateWithdrawalRequest();
            request.setUser(makerUser);
            request.setDestination(ETH_ADDRESS);
            request.setAssetId(asset.getId());
            accountService.createWithdrawal(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.AMOUNT_MANDATORY);
        }
    }

    @Test
    public void testWithdrawFailsWithNegativeAmount() throws InterruptedException {
        Asset asset = createAndEnactAsset(false);
        try {
            CreateWithdrawalRequest request = new CreateWithdrawalRequest();
            request.setUser(makerUser);
            request.setDestination(ETH_ADDRESS);
            request.setAssetId(asset.getId());
            request.setAmount(BigDecimal.valueOf(100000).multiply(BigDecimal.valueOf(-1)));
            accountService.createWithdrawal(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.AMOUNT_NEGATIVE);
        }
    }

    @Test
    public void testWithdrawFailsWithMissingDestination() throws InterruptedException {
        Asset asset = createAndEnactAsset(false);
        try {
            CreateWithdrawalRequest request = new CreateWithdrawalRequest();
            request.setUser(makerUser);
            request.setAssetId(asset.getId());
            request.setAmount(BigDecimal.valueOf(100000).multiply(BigDecimal.TEN));
            accountService.createWithdrawal(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.DESTINATION_MANDATORY);
        }
    }

    @Test
    public void testWithdrawFailsWithMissingAssetID() {
        try {
            CreateWithdrawalRequest request = new CreateWithdrawalRequest();
            request.setUser(makerUser);
            request.setDestination(ETH_ADDRESS);
            request.setAmount(BigDecimal.valueOf(100000).multiply(BigDecimal.TEN));
            accountService.createWithdrawal(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_ID_MANDATORY);
        }
    }

    private Withdrawal createWithdrawalRequest() throws InterruptedException {
        Asset asset = createAndEnactAsset(false);
        CreateWithdrawalRequest request = new CreateWithdrawalRequest();
        request.setUser(makerUser);
        request.setDestination(ETH_ADDRESS);
        request.setAssetId(asset.getId());
        request.setAmount(BigDecimal.valueOf(500000000));
        Withdrawal withdrawal = accountService.createWithdrawal(request);
        Assertions.assertEquals(withdrawal.getStatus(), WithdrawalStatus.PENDING);
        Optional<Account> accountOptional = accountService.get(makerUser, asset);
        Assertions.assertTrue(accountOptional.isPresent());
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().doubleValue(),
                request.getAmount().doubleValue());
        return withdrawal;
    }

    private Withdrawal cancelWithdrawal() throws InterruptedException {
        Withdrawal withdrawal = createWithdrawalRequest();
        SingleItemRequest request = new SingleItemRequest();
        request.setId(withdrawal.getId());
        request.setUser(makerUser);
        accountService.cancelWithdrawal(request);
        Optional<Account> accountOptional = accountService.get(makerUser, withdrawal.getAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().doubleValue(),
                BigDecimal.valueOf(1000000000).doubleValue());
        return withdrawal;
    }

    @Test
    public void testWithdrawalRequest() throws InterruptedException {
        createWithdrawalRequest();
    }

    @Test
    public void testCancelWithdrawal() throws InterruptedException {
        cancelWithdrawal();
    }

    @Test
    public void testCancelWithdrawalFailsWithInvalidID() {
        try {
            SingleItemRequest request = new SingleItemRequest();
            request.setId(UUID.randomUUID());
            request.setUser(makerUser);
            accountService.cancelWithdrawal(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.WITHDRAWAL_NOT_FOUND);
        }
    }

    @Test
    public void testCancelWithdrawalFailsWithMissingID() {
        try {
            SingleItemRequest request = new SingleItemRequest();
            request.setUser(makerUser);
            accountService.cancelWithdrawal(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ID_MANDATORY);
        }
    }

    @Test
    public void testCancelWithdrawalFailsWhenNotPending() throws InterruptedException {
        Withdrawal withdrawal = cancelWithdrawal();
        try {
            SingleItemRequest request = new SingleItemRequest();
            request.setId(withdrawal.getId());
            request.setUser(makerUser);
            accountService.cancelWithdrawal(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.WITHDRAWAL_NOT_PENDING);
        }
    }
}