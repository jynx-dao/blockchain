package com.jynx.pro.repository;

import com.jynx.pro.constant.BridgeUpdateType;
import com.jynx.pro.entity.BridgeUpdate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Repository
public class BridgeUpdateRepository extends EntityRepository<BridgeUpdate> {
    @Override
    public Class<BridgeUpdate> getType() {
        return BridgeUpdate.class;
    }

    /**
     * Find {@link BridgeUpdate}s by asset ID and {@link BridgeUpdateType}
     *
     * @param assetId the asset ID
     * @param type {@link BridgeUpdateType}
     *
     * @return {@link List<BridgeUpdate>}
     */
    public List<BridgeUpdate> findByAssetIdAndType(
            final UUID assetId,
            final BridgeUpdateType type
    ) {
        return Collections.emptyList();
    }
}