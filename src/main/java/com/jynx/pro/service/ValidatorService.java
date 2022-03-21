package com.jynx.pro.service;

import com.jynx.pro.constant.BlockValidatorStatus;
import com.jynx.pro.entity.BlockValidator;
import com.jynx.pro.entity.Delegation;
import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.Validator;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.BlockValidatorRepository;
import com.jynx.pro.repository.DelegationRepository;
import com.jynx.pro.repository.ReadOnlyRepository;
import com.jynx.pro.repository.ValidatorRepository;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.request.UpdateDelegationRequest;
import com.jynx.pro.request.ValidatorApplicationRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.java_websocket.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ValidatorService {

    @Autowired
    private ValidatorRepository validatorRepository;
    @Autowired
    private BlockValidatorRepository blockValidatorRepository;
    @Autowired
    private DelegationRepository delegationRepository;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private ConfigService configService;
    @Autowired
    private StakeService stakeService;
    @Autowired
    private UUIDUtils uuidUtils;

    /**
     * Get validator by ID
     *
     * @param id the validator ID
     *
     * @return {@link Validator}
     */
    private Validator get(
            final UUID id
    ) {
        return validatorRepository.findById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.VALIDATOR_NOT_FOUND));
    }

    /**
     * Add delegation to a validator
     *
     * @param request {@link UpdateDelegationRequest}
     *
     * @return {@link Delegation}
     */
    public Delegation addDelegation(
            final UpdateDelegationRequest request
    ) {
        Validator validator = get(request.getValidatorId());
        Stake stake = stakeService.getAndCreate(request.getUser());
        Optional<Delegation> delegationOptional = delegationRepository.findByValidatorIdAndStakeId(
                validator.getId(), stake.getId());
        List<Delegation> currentDelegation = delegationRepository.findByStakeId(stake.getId());
        BigDecimal totalDelegation = currentDelegation.stream().map(Delegation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal availableStake = stake.getAmount().subtract(totalDelegation);
        Delegation delegation = new Delegation();
        if(request.getAmount().doubleValue() > availableStake.doubleValue()) {
            throw new JynxProException(ErrorCode.INSUFFICIENT_STAKE);
        }
        if(delegationOptional.isPresent()) {
            delegation = delegationOptional.get();
            delegation.setAmount(delegation.getAmount().add(request.getAmount()));
            validator.setDelegation(validator.getDelegation().add(request.getAmount()));
        } else {
            delegation.setValidator(validator);
            delegation.setStake(stake);
            delegation.setId(uuidUtils.next());
            delegation.setAmount(request.getAmount());
            validator.setDelegation(validator.getDelegation().add(request.getAmount()));
        }
        validatorRepository.save(validator);
        return delegationRepository.save(delegation);
    }

    /**
     * Remove delegation from a validator
     *
     * @param request {@link UpdateDelegationRequest}
     *
     * @return {@link Delegation}
     */
    public Delegation removeDelegation(
            final UpdateDelegationRequest request
    ) {
        Validator validator = get(request.getValidatorId());
        Stake stake = stakeService.getAndCreate(request.getUser());
        Optional<Delegation> delegationOptional = delegationRepository.findByValidatorIdAndStakeId(
                validator.getId(), stake.getId());
        if(delegationOptional.isPresent()) {
            Delegation delegation = delegationOptional.get();
            if(request.getAmount().doubleValue() > delegation.getAmount().doubleValue()) {
                validator.setDelegation(validator.getDelegation().subtract(delegation.getAmount()));
                delegation.setAmount(BigDecimal.ZERO);
            } else {
                validator.setDelegation(validator.getDelegation().subtract(request.getAmount()));
                delegation.setAmount(delegation.getAmount().subtract(request.getAmount()));
            }
            validatorRepository.save(validator);
            return delegationRepository.save(delegation);
        } else {
            throw new JynxProException(ErrorCode.NO_DELEGATION);
        }
    }

    /**
     * Apply to become a validator
     *
     * @param request {@link ValidatorApplicationRequest}
     *
     * @return {@link Validator}
     */
    public Validator apply(
            final ValidatorApplicationRequest request
    ) {
        try {
            String tendermintKeyAsHex = Hex.encodeHexString(Base64.decode(request.getTendermintPublicKey()));
            if(!tendermintKeyAsHex.equals(request.getPublicKey())) {
                throw new JynxProException(ErrorCode.TENDERMINT_SIGNATURE_INVALID);
            }
        } catch(Exception e) {
            throw new JynxProException(ErrorCode.TENDERMINT_SIGNATURE_INVALID);
        }
        Optional<Validator> validatorOptional = validatorRepository.findByPublicKey(request.getTendermintPublicKey());
        if(validatorOptional.isPresent()) {
            throw new JynxProException(ErrorCode.VALIDATOR_ALREADY_EXISTS);
        }
        BigDecimal totalStake = stakeService.getStakeForUser(request.getUser());
        if(totalStake.doubleValue() < configService.get().getValidatorBond().doubleValue()) {
            throw new JynxProException(ErrorCode.INSUFFICIENT_VALIDATOR_STAKE);
        }
        List<Validator> validators = getAll();
        Validator validator = new Validator()
                .setPublicKey(request.getTendermintPublicKey())
                .setEnabled(true)
                .setPriority(validators.size())
                .setId(uuidUtils.next());
        return validatorRepository.save(validator);
    }

    /**
     * Resign as a validator
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link Validator}
     */
    public Validator resign(
            final SingleItemRequest request
    ) {
        Validator validator = get(request.getId());
        try {
            String tendermintKey = Base64.encodeBytes(Hex.decodeHex(request.getPublicKey()));
            if(!tendermintKey.equals(validator.getPublicKey())) {
                throw new JynxProException(ErrorCode.TENDERMINT_SIGNATURE_INVALID);
            }
        } catch(Exception e) {
            throw new JynxProException(ErrorCode.TENDERMINT_SIGNATURE_INVALID);
        }
        validator.setEnabled(false);
        return validatorRepository.save(validator);
    }

    /**
     * Get the backup set of validators
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getAll() {
        return getAll(false);
    }

    /**
     * Get the backup set of validators
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getBackupSet() {
        return getBackupSet(false);
    }

    /**
     * Get the active set of validators
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getActiveSet() {
        return getActiveSet(false);
    }

    /**
     * Get the backup set of validators
     *
     * @param readOnly use read-only repository
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getAll(
            boolean readOnly
    ) {
        if(readOnly) {
            return readOnlyRepository.getAllByEntity(Validator.class);
        }
        return validatorRepository.findAll();
    }

    /**
     * Get the backup set of validators
     *
     * @param readOnly use read-only repository
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getBackupSet(
            boolean readOnly
    ) {
        return getAll(readOnly).stream()
                .filter(v -> v.getDelegation().doubleValue() >= configService.getStatic()
                        .getValidatorMinDelegation().doubleValue())
                .filter(Validator::getEnabled)
                .sorted(Comparator.comparing(Validator::getDelegation).reversed().thenComparing(Validator::getPriority))
                .skip(configService.getStatic().getActiveValidatorCount())
                .limit(configService.getStatic().getBackupValidatorCount())
                .collect(Collectors.toList());
    }

    /**
     * Get the active set of validators
     *
     * @param readOnly use read-only repository
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getActiveSet(
            boolean readOnly
    ) {
        return getAll(readOnly).stream()
                .filter(v -> v.getDelegation().doubleValue() >= configService.getStatic()
                        .getValidatorMinDelegation().doubleValue())
                .filter(Validator::getEnabled)
                .sorted(Comparator.comparing(Validator::getDelegation).reversed().thenComparing(Validator::getPriority))
                .limit(configService.getStatic().getActiveValidatorCount())
                .collect(Collectors.toList());
    }

    /**
     * Save the validator set for the current block
     *
     * @param blockHeight the current block height
     */
    public void saveBlockValidators(
            final long blockHeight
    ) {
        List<BlockValidator> blockValidators = new ArrayList<>();
        for(Validator validator : getActiveSet()) {
            blockValidators.add(new BlockValidator()
                    .setValidator(validator)
                    .setBlockHeight(blockHeight)
                    .setDelegation(validator.getDelegation())
                    .setId(uuidUtils.next())
                    .setStatus(BlockValidatorStatus.ACTIVE));
        }
        for(Validator validator : getBackupSet()) {
            blockValidators.add(new BlockValidator()
                    .setValidator(validator)
                    .setBlockHeight(blockHeight)
                    .setDelegation(validator.getDelegation())
                    .setId(uuidUtils.next())
                    .setStatus(BlockValidatorStatus.BACKUP));
        }
        blockValidatorRepository.saveAll(blockValidators);
    }

    /**
     * Add a genesis {@link Validator}
     *
     * @param publicKey the validator's public key
     */
    public void addFromGenesis(
            final String publicKey
    ) {
        Optional<Validator> validatorOptional = validatorRepository.findByPublicKey(publicKey);
        List<Validator> validators = getAll();
        if(validatorOptional.isEmpty()) {
            Validator validator = new Validator()
                    .setId(uuidUtils.next())
                    .setPublicKey(publicKey)
                    .setEnabled(true)
                    .setPriority(validators.size())
                    .setDelegation(BigDecimal.ONE);
            validatorRepository.save(validator);
        }
    }

    /**
     * Check if a public key belongs to an active {@link Validator}
     *
     * @param publicKey the public key
     *
     * @return true / false
     */
    public boolean isValidator(
            final String publicKey
    ) {
        boolean activeValidator = getActiveSet(true).stream().anyMatch(v -> v.getPublicKey().equals(publicKey));
        boolean backupValidator = getBackupSet(true).stream().anyMatch(v -> v.getPublicKey().equals(publicKey));
        return activeValidator || backupValidator;
    }

    /**
     * Disable a validator when they do not have sufficient stake
     *
     * @param publicKey the validator's Tendermint key
     */
    public void disable(
            final String publicKey
    ) {
        Optional<Validator> validatorOptional = validatorRepository.findByPublicKey(publicKey);
        if(validatorOptional.isPresent()) {
            validatorOptional.get().setEnabled(false);
            validatorRepository.save(validatorOptional.get());
        }
    }

    /**
     * Disable a validator when they have sufficient stake
     *
     * @param publicKey the validator's Tendermint key
     */
    public void enable(
            final String publicKey
    ) {
        Optional<Validator> validatorOptional = validatorRepository.findByPublicKey(publicKey);
        if(validatorOptional.isPresent()) {
            validatorOptional.get().setEnabled(true);
            validatorRepository.save(validatorOptional.get());
        }
    }
}