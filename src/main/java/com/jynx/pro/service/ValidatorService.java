package com.jynx.pro.service;

import com.jynx.pro.entity.Validator;
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
    private UUIDUtils uuidUtils;

    public void add(
            final String publicKey
    ) {
        Validator validator = new Validator()
                .setId(uuidUtils.next())
                .setPublicKey(publicKey)
                .setActive(true);
        validatorRepository.save(validator);
    }

    public boolean isValidator(
            final String publicKey
    ) {
        Optional<Validator> validatorOptional = validatorRepository.findByPublicKey(publicKey);
        if(validatorOptional.isEmpty()) return false;
        return validatorOptional.get().getActive();
    }
}