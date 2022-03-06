package com.jynx.pro.service;

import com.jynx.pro.entity.Validator;
import com.jynx.pro.repository.ReadOnlyRepository;
import com.jynx.pro.repository.ValidatorRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class ValidatorService {

    @Autowired
    private ValidatorRepository validatorRepository;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private UUIDUtils uuidUtils;

    /**
     * Add a {@link Validator} to the database if it doesn't exist
     *
     * @param publicKey the validator's public key
     */
    public void add(
            final String publicKey
    ) {
        Optional<Validator> validatorOptional = validatorRepository.findByPublicKey(publicKey);
        if(validatorOptional.isEmpty()) {
            Validator validator = new Validator()
                    .setId(uuidUtils.next())
                    .setPublicKey(publicKey)
                    .setActive(true);
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
        Optional<Validator> validatorOptional = readOnlyRepository.getValidatorByPublicKey(publicKey);
        if(validatorOptional.isEmpty()) return false;
        return validatorOptional.get().getActive();
    }
}