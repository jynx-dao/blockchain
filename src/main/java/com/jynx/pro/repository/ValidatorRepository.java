package com.jynx.pro.repository;

import com.jynx.pro.entity.Validator;
import org.springframework.stereotype.Repository;

@Repository
public class ValidatorRepository extends EntityRepository<Validator> {
    @Override
    public Class<Validator> getType() {
        return Validator.class;
    }
}