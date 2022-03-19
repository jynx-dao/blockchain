package com.jynx.pro.repository;

import com.jynx.pro.entity.Delegation;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DelegationRepository extends EntityRepository<Delegation> {
    @Override
    public Class<Delegation> getType() {
        return Delegation.class;
    }

    /**
     * Find {@link Delegation} by validator ID and stake ID
     *
     * @param validatorId the validator ID
     * @param stakeId     the stake ID
     * @return {@link Optional<Delegation>}
     */
    public Optional<Delegation> findByValidatorIdAndStakeId(
            final UUID validatorId,
            final UUID stakeId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Delegation> query = cb.createQuery(getType());
        Root<Delegation> rootType = query.from(getType());
        Path<UUID> validator_id = rootType.join("validator").get("id");
        Path<UUID> stake_id = rootType.join("stake").get("id");
        query = query.select(rootType).where(cb.equal(validator_id, validatorId), cb.equal(stake_id, stakeId));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Find {@link Delegation}s by stake ID
     *
     * @param stakeId the stake ID
     *
     * @return {@link List<Delegation>}
     */
    public List<Delegation> findByStakeId(
            final UUID stakeId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Delegation> query = cb.createQuery(getType());
        Root<Delegation> rootType = query.from(getType());
        Path<UUID> stake_id = rootType.join("stake").get("id");
        query = query.select(rootType).where(cb.equal(stake_id, stakeId));
        return getEntityManager().createQuery(query).getResultList();
    }
}