package com.jynx.pro.repository;

import com.jynx.pro.entity.BridgeUpdateSignature;
import org.springframework.stereotype.Repository;

@Repository
public class BridgeUpdateSignatureRepository extends EntityRepository<BridgeUpdateSignature> {
    @Override
    public Class<BridgeUpdateSignature> getType() {
        return BridgeUpdateSignature.class;
    }
}