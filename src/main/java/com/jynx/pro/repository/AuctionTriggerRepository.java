package com.jynx.pro.repository;

import com.jynx.pro.entity.AuctionTrigger;
import org.springframework.stereotype.Repository;

@Repository
public class AuctionTriggerRepository extends EntityRepository<AuctionTrigger> {
    @Override
    public Class<AuctionTrigger> getType() {
        return AuctionTrigger.class;
    }
}