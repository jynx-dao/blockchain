package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.BlockValidatorStatus;
import com.jynx.pro.entity.BlockValidator;
import com.jynx.pro.entity.Delegation;
import com.jynx.pro.entity.User;
import com.jynx.pro.entity.Validator;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.SingleItemRequest;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "VALIDATOR_SERVICE_TEST", matches = "true")
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

    private Validator apply(
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
        return validator;
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

    private void addDelegation() {
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
    public void testApply() {
        apply(takerUser.getPublicKey(), takerUser);
    }

    @Test
    public void testAddDelegation() {
        addDelegation();
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
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.valueOf(1))
                .setValidatorId(UUID.randomUUID());
        request.setUser(takerUser);
        try {
            validatorService.addDelegation(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.VALIDATOR_NOT_FOUND);
        }
    }

    @Test
    public void testRemoveDelegation() {
        addDelegation();
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(validators.size(), 1);
        Validator validator = validators.get(0);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.ONE)
                .setValidatorId(validator.getId());
        request.setUser(takerUser);
        Delegation delegation = validatorService.removeDelegation(request);
        Assertions.assertEquals(delegation.getAmount().doubleValue(), BigDecimal.ZERO.doubleValue());
    }

    @Test
    public void testRemoveDelegationFailsWhenDelegationMissing() {
        apply(takerUser.getPublicKey(), takerUser);
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(validators.size(), 1);
        Validator validator = validators.get(0);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.valueOf(0.5))
                .setValidatorId(validator.getId());
        request.setUser(takerUser);
        try {
            validatorService.removeDelegation(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.NO_DELEGATION);
        }
    }

    @Test
    public void testRemoveDelegationExceedingAmount() {
        addDelegation();
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(validators.size(), 1);
        Validator validator = validators.get(0);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.TEN)
                .setValidatorId(validator.getId());
        request.setUser(takerUser);
        Delegation delegation = validatorService.removeDelegation(request);
        Assertions.assertEquals(delegation.getAmount().doubleValue(), BigDecimal.ZERO.doubleValue());
    }

    @Test
    public void testRemoveDelegationFailsWithMissingValidator() {
        apply(takerUser.getPublicKey(), takerUser);
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(validators.size(), 1);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.valueOf(1))
                .setValidatorId(UUID.randomUUID());
        request.setUser(takerUser);
        try {
            validatorService.removeDelegation(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.VALIDATOR_NOT_FOUND);
        }
    }

    @Test
    public void testGetBackupSet() {
        Validator validator1 = apply(takerUser.getPublicKey(), takerUser);
        Validator validator2 = apply(makerUser.getPublicKey(), makerUser);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.TEN)
                .setValidatorId(validator1.getId());
        request.setUser(takerUser);
        validatorService.addDelegation(request);
        request.setValidatorId(validator2.getId());
        validatorService.addDelegation(request);
        List<Validator> backupSet = validatorService.getBackupSet();
        Assertions.assertEquals(1, backupSet.size());
        Assertions.assertEquals(backupSet.get(0).getId(), validator2.getId());
        backupSet = validatorService.getBackupSet(true);
        Assertions.assertEquals(0, backupSet.size());
    }

    @Test
    public void testGetBackupSetInsufficientDelegation() {
        Validator validator1 = apply(takerUser.getPublicKey(), takerUser);
        apply(makerUser.getPublicKey(), makerUser);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.TEN)
                .setValidatorId(validator1.getId());
        request.setUser(takerUser);
        validatorService.addDelegation(request);
        List<Validator> backupSet = validatorService.getBackupSet();
        Assertions.assertEquals(0, backupSet.size());
    }

    @Test
    public void testGetActiveSet() {
        Validator validator1 = apply(takerUser.getPublicKey(), takerUser);
        Validator validator2 = apply(makerUser.getPublicKey(), makerUser);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.TEN)
                .setValidatorId(validator1.getId());
        request.setUser(takerUser);
        validatorService.addDelegation(request);
        request.setValidatorId(validator2.getId());
        validatorService.addDelegation(request);
        List<Validator> activeSet = validatorService.getActiveSet();
        Assertions.assertEquals(1, activeSet.size());
        Assertions.assertEquals(activeSet.get(0).getId(), validator1.getId());
        activeSet = validatorService.getActiveSet(true);
        Assertions.assertEquals(0, activeSet.size());
    }

    @Test
    public void testGetActiveSetInsufficientDelegation() {
        apply(takerUser.getPublicKey(), takerUser);
        List<Validator> activeSet = validatorService.getActiveSet();
        Assertions.assertEquals(0, activeSet.size());
    }

    @Test
    public void testSaveBlockValidators() {
        Validator validator1 = apply(takerUser.getPublicKey(), takerUser);
        Validator validator2 = apply(makerUser.getPublicKey(), makerUser);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.TEN)
                .setValidatorId(validator1.getId());
        request.setUser(takerUser);
        validatorService.addDelegation(request);
        request.setValidatorId(validator2.getId());
        validatorService.addDelegation(request);
        validatorService.saveBlockValidators(1L);
        List<BlockValidator> blockValidators = blockValidatorRepository.findAll();
        Assertions.assertEquals(2, blockValidators.size());
        Optional<BlockValidator> activeValidator = blockValidators.stream()
                .filter(v -> v.getStatus().equals(BlockValidatorStatus.ACTIVE)).findFirst();
        Optional<BlockValidator> backupValidator = blockValidators.stream()
                .filter(v -> v.getStatus().equals(BlockValidatorStatus.BACKUP)).findFirst();
        Assertions.assertTrue(activeValidator.isPresent());
        Assertions.assertTrue(backupValidator.isPresent());
        Assertions.assertEquals(activeValidator.get().getValidator().getId(), validator1.getId());
        Assertions.assertEquals(backupValidator.get().getValidator().getId(), validator2.getId());
    }

    @Test
    public void testAddFromGenesisOnlyOnce() {
        validatorService.addFromGenesis(takerUser.getPublicKey(), takerUser.getPublicKey(), ethereumService.getAddress());
        validatorService.addFromGenesis(takerUser.getPublicKey(), takerUser.getPublicKey(), ethereumService.getAddress());
        List<Validator> validators = validatorService.getActiveSet();
        Assertions.assertEquals(1, validators.size());
    }

    @Test
    public void testIsValidator() throws DecoderException {
        Validator validator1 = apply(takerUser.getPublicKey(), takerUser);
        Validator validator2 = apply(makerUser.getPublicKey(), makerUser);
        UpdateDelegationRequest request = new UpdateDelegationRequest()
                .setAmount(BigDecimal.TEN)
                .setValidatorId(validator1.getId());
        request.setUser(takerUser);
        validatorService.addDelegation(request);
        request.setValidatorId(validator2.getId());
        validatorService.addDelegation(request);
        databaseTransactionManager.commit();
        databaseTransactionManager.createTransaction();
        boolean isValidator = validatorService.isValidator(
                Base64.encodeBase64String(Hex.decodeHex(takerUser.getPublicKey())));
        Assertions.assertTrue(isValidator);
        isValidator = validatorService.isValidator(Base64.encodeBase64String(Hex.decodeHex(makerUser.getPublicKey())));
        Assertions.assertTrue(isValidator);
        isValidator = validatorService.isValidator(Base64.encodeBase64String(Hex.decodeHex(degenUser.getPublicKey())));
        Assertions.assertFalse(isValidator);
    }

    private Validator disableValidator() throws DecoderException {
        String base64key = Base64.encodeBase64String(Hex.decodeHex(takerUser.getPublicKey()));
        validatorService.disable(base64key);
        Validator validator = apply(takerUser.getPublicKey(), takerUser);
        validatorService.disable(base64key);
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(1, validators.size());
        validators = validators.stream().filter(v -> !v.getEnabled()).collect(Collectors.toList());
        Assertions.assertEquals(1, validators.size());
        Assertions.assertEquals(validator.getId(), validators.get(0).getId());
        return validator;
    }

    @Test
    public void testDisableValidator() throws DecoderException {
        disableValidator();
    }

    @Test
    public void testEnableValidator() throws DecoderException {
        Validator validator = disableValidator();
        validatorService.enable(validator.getPublicKey());
        List<Validator> validators = validatorService.getAll();
        Assertions.assertEquals(1, validators.size());
        validators = validators.stream().filter(Validator::getEnabled).collect(Collectors.toList());
        Assertions.assertEquals(1, validators.size());
        Assertions.assertEquals(validator.getId(), validators.get(0).getId());
    }

    @Test
    public void testResignValidator() {
        Validator validator = apply(takerUser.getPublicKey(), takerUser);
        SingleItemRequest request = new SingleItemRequest().setId(validator.getId());
        request.setPublicKey(takerUser.getPublicKey());
        validator = validatorService.resign(request);
        Assertions.assertFalse(validator.getEnabled());
    }

    @Test
    public void testResignValidatorWithInvalidKeyFormat() throws DecoderException {
        Validator validator = apply(takerUser.getPublicKey(), takerUser);
        SingleItemRequest request = new SingleItemRequest().setId(validator.getId());
        request.setPublicKey(Base64.encodeBase64String(Hex.decodeHex(takerUser.getPublicKey())));
        try {
            validatorService.resign(request);
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.TENDERMINT_SIGNATURE_INVALID);
        }
        Assertions.assertTrue(validator.getEnabled());
    }

    @Test
    public void testResignValidatorWithIncorrectKey() {
        Validator validator = apply(takerUser.getPublicKey(), takerUser);
        SingleItemRequest request = new SingleItemRequest().setId(validator.getId());
        request.setPublicKey(makerUser.getPublicKey());
        try {
            validatorService.resign(request);
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.TENDERMINT_SIGNATURE_INVALID);
        }
        Assertions.assertTrue(validator.getEnabled());
    }
}