package com.jynx.pro.repository;

import com.jynx.pro.entity.BridgeUpdateSignature;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BridgeUpdateSignatureRepository extends EntityRepository<BridgeUpdateSignature> {
    @Override
    public Class<BridgeUpdateSignature> getType() {
        return BridgeUpdateSignature.class;
    }

    /**
     * Get {@link BridgeUpdateSignature} by batch ID and validator ID
     *
     * @param updateId the update ID
     * @param validatorId the validator ID
     *
     * @return {@link Optional <BridgeUpdateSignature>}
     */
    public Optional<BridgeUpdateSignature> findByBridgeUpdateIdAndValidatorId(
            final UUID updateId,
            final UUID validatorId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<BridgeUpdateSignature> query = cb.createQuery(BridgeUpdateSignature.class);
        Root<BridgeUpdateSignature> rootType = query.from(BridgeUpdateSignature.class);
        Path<UUID> update_id = rootType.join("bridgeUpdate").get("id");
        Path<UUID> validator_id = rootType.join("validator").get("id");
        query = query.select(rootType).where(cb.equal(update_id, updateId), cb.equal(validator_id, validatorId));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }
}