package com.jynx.pro.repository;

import com.jynx.pro.entity.Vote;
import com.jynx.pro.entity.WithdrawalBatchSignature;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WithdrawalBatchSignatureRepository extends EntityRepository<WithdrawalBatchSignature> {

    @Override
    public Class<WithdrawalBatchSignature> getType() {
        return WithdrawalBatchSignature.class;
    }

    /**
     * Find {@link WithdrawalBatchSignature} by withdrawal ID and validator ID
     *
     * @param withdrawalBatchId the withdrawal batch ID
     * @param validatorId the validator ID
     *
     * @return {@link Optional< WithdrawalBatchSignature >}
     */
    public Optional<WithdrawalBatchSignature> findByWithdrawalBatchIdAndValidatorId(
            final UUID withdrawalBatchId,
            final UUID validatorId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<WithdrawalBatchSignature> query = cb.createQuery(getType());
        Root<WithdrawalBatchSignature> rootType = query.from(getType());
        Path<UUID> withdrawal_batch_id = rootType.join("withdrawalBatch").get("id");
        Path<UUID> validator_id = rootType.join("validator").get("id");
        query = query.select(rootType).where(cb.equal(withdrawal_batch_id, withdrawalBatchId),
                cb.equal(validator_id, validatorId));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    public List<WithdrawalBatchSignature> findByWithdrawalBatchId(
            final UUID batchId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<WithdrawalBatchSignature> query = cb.createQuery(getType());
        Root<WithdrawalBatchSignature> rootType = query.from(getType());
        Path<UUID> batch_id = rootType.join("withdrawalBatch").get("id");
        query = query.select(rootType).where(cb.equal(batch_id, batchId));
        return getEntityManager().createQuery(query).getResultList();
    }
}