package com.jynx.pro.repository;

import com.jynx.pro.entity.Delegation;
import org.springframework.stereotype.Repository;

@Repository
public class DelegationRepository extends EntityRepository<Delegation> {
    @Override
    public Class<Delegation> getType() {
        return Delegation.class;
    }
}