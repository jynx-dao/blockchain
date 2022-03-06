package com.jynx.pro.repository;

import com.jynx.pro.entity.WithdrawalSignature;
import org.springframework.stereotype.Repository;

@Repository
public class WithdrawalSignatureRepository extends EntityRepository<WithdrawalSignature> {
    @Override
    public Class<WithdrawalSignature> getType() {
        return WithdrawalSignature.class;
    }
}