package com.jynx.pro.repository;

import com.jynx.pro.entity.WithdrawalBatch;
import org.springframework.stereotype.Repository;

@Repository
public class WithdrawalBatchRepository extends EntityRepository<WithdrawalBatch> {
    @Override
    public Class<WithdrawalBatch> getType() {
        return WithdrawalBatch.class;
    }
}