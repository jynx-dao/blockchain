package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.entity.Delegation;
import com.jynx.pro.entity.User;
import com.jynx.pro.entity.Validator;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.UpdateDelegationRequest;
import com.jynx.pro.request.ValidatorApplicationRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class ValidatorServiceTest extends IntegrationTest {

    @Autowired
    private ValidatorService validatorService;

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

    private void apply(
            final String tmKey,
            final User user
    ) {
        String tendermintKey = "";
        try {
            tendermintKey = Base64.encodeBase64String(Hex.decodeHex(tmKey));
        } catch(Exception e) {
            log.warn(e.getMessage());
        }
        ValidatorApplicationRequest request = new ValidatorApplicationRequest();
        request.setTendermintPublicKey(tendermintKey);
        request.setPublicKey(user.getPublicKey());
        request.setUser(user);
        Validator validator = validatorService.apply(request);
        Assertions.assertEquals(validator.getDelegation().doubleValue(), 0d);
        Assertions.assertTrue(validator.getEnabled());
    }


    @Test
    public void testApplyFailsWithWrongKeyFormat() {
        try {
            apply(Base64.encodeBase64String(Hex.decodeHex(takerUser.getPublicKey())), takerUser);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.TENDERMINT_SIGNATURE_INVALID);
        }
    }

    @Test
    public void testApplyFailsWithDifferentKey() {
        try {
            apply(makerUser.getPublicKey(), takerUser);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.TENDERMINT_SIGNATURE_INVALID);
        }
    }

    @Test
    public void testApplyFailsWithDuplicatedValidator() {
        apply(takerUser.getPublicKey(), takerUser);
        try {
            apply(takerUser.getPublicKey(), takerUser);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.VALIDATOR_ALREADY_EXISTS);
        }
    }

    @Test
    public void testApplyFailsWithInsufficientBond() {
        try {
            String key = "ef5043e769593458416fbb9f0bbde77726c97abca2b8d9397f34de57f5483fd7";
            User user = new User().setPublicKey(key);
            apply(key, user);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INSUFFICIENT_VALIDATOR_STAKE);
        }
    }

    @Test
    public void testApply() {
        apply(takerUser.getPublicKey(), takerUser);
    }

    @Test
    public void testAddDelegation() {
        apply(takerUser.getPublicKey(), takerUser);
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(validators.size(), 1);
        Validator validator = validators.get(0);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.ONE)
                .setValidatorId(validator.getId());
        request.setUser(takerUser);
        Delegation delegation = validatorService.addDelegation(request);
        Assertions.assertEquals(delegation.getAmount().doubleValue(), BigDecimal.ONE.doubleValue());
    }

    @Test
    public void testAddDelegationTwice() {
        apply(takerUser.getPublicKey(), takerUser);
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(validators.size(), 1);
        Validator validator = validators.get(0);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.ONE)
                .setValidatorId(validator.getId());
        request.setUser(takerUser);
        Delegation delegation = validatorService.addDelegation(request);
        Assertions.assertEquals(delegation.getAmount().doubleValue(), BigDecimal.ONE.doubleValue());
        delegation = validatorService.addDelegation(request);
        Assertions.assertEquals(delegation.getAmount().doubleValue(), BigDecimal.valueOf(2).doubleValue());
    }

    @Test
    public void testAddDelegationFailsWithInsufficientStake() {
        apply(takerUser.getPublicKey(), takerUser);
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(validators.size(), 1);
        Validator validator = validators.get(0);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.valueOf(1000000000).multiply(BigDecimal.valueOf(10000000)))
                .setValidatorId(validator.getId());
        request.setUser(takerUser);
        try {
            validatorService.addDelegation(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INSUFFICIENT_STAKE);
        }
    }

    @Test
    public void testAddDelegationFailsWithMissingValidator() {
        apply(takerUser.getPublicKey(), takerUser);
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(validators.size(), 1);
        Validator validator = validators.get(0);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.valueOf(1000000000).multiply(BigDecimal.valueOf(10000000)))
                .setValidatorId(UUID.randomUUID());
        request.setUser(takerUser);
        try {
            validatorService.addDelegation(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.VALIDATOR_NOT_FOUND);
        }
    }
}