package com.jynx.pro.service;

import com.jynx.pro.constant.BlockValidatorStatus;
import com.jynx.pro.entity.BlockValidator;
import com.jynx.pro.entity.Validator;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.BlockValidatorRepository;
import com.jynx.pro.repository.ReadOnlyRepository;
import com.jynx.pro.repository.ValidatorRepository;
import com.jynx.pro.request.ValidatorApplicationRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.java_websocket.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ValidatorService {

    @Autowired
    private ValidatorRepository validatorRepository;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private BlockValidatorRepository blockValidatorRepository;
    @Autowired
    private ConfigService configService;
    @Autowired
    private UUIDUtils uuidUtils;

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
        Validator validator = new Validator()
                .setPublicKey(request.getTendermintPublicKey())
                .setActive(false)
                .setId(uuidUtils.next());
        Optional<Validator> validatorOptional = validatorRepository.findByPublicKey(request.getTendermintPublicKey());
        if(validatorOptional.isPresent()) {
            throw new JynxProException(ErrorCode.VALIDATOR_ALREADY_EXISTS);
        }
        return validatorRepository.save(validator);
    }

    /**
     * Get the backup set of validators
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getAll() {
        return validatorRepository.findAll();
    }

    /**
     * Get the backup set of validators
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getBackupSet() {
        return validatorRepository.findAll().stream()
                .sorted(Comparator.comparing(Validator::getDelegation).reversed())
                .skip(configService.get().getActiveValidatorCount())
                .limit(configService.get().getBackupValidatorCount())
                .collect(Collectors.toList());
    }

    /**
     * Get the active set of validators
     *
     * @return {@link List<Validator>}
     */
    public List<Validator> getActiveSet() {
        return validatorRepository.findAll().stream()
                .sorted(Comparator.comparing(Validator::getDelegation).reversed())
                .limit(configService.get().getActiveValidatorCount())
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
     * Activate a {@link Validator}
     *
     * @param publicKey the validator's public key
     */
    public void activate(
            final String publicKey
    ) {
        Optional<Validator> validatorOptional = validatorRepository.findByPublicKey(publicKey);
        if(validatorOptional.isEmpty()) {
            Validator validator = new Validator()
                    .setId(uuidUtils.next())
                    .setPublicKey(publicKey)
                    .setDelegation(BigDecimal.ONE)
                    .setActive(true);
            validatorRepository.save(validator);
        } else {
            validatorOptional.get().setActive(true);
            validatorRepository.save(validatorOptional.get());
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
        Optional<Validator> validatorOptional = readOnlyRepository.getValidatorByPublicKey(publicKey);
        if(validatorOptional.isEmpty()) return false;
        return validatorOptional.get().getActive();
    }
}