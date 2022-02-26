package com.jynx.pro.repository;

import com.jynx.pro.entity.Settlement;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementRepository extends EntityRepository<Settlement> {
    @Override
    public Class<Settlement> getType() {
        return Settlement.class;
    }
}