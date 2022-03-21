package com.jynx.pro.repository;

import com.jynx.pro.entity.BlockValidator;
import org.springframework.stereotype.Repository;

@Repository
public class BlockValidatorRepository extends EntityRepository<BlockValidator> {
    @Override
    public Class<BlockValidator> getType() {
        return BlockValidator.class;
    }
}