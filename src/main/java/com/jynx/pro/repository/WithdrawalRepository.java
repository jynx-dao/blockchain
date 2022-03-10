package com.jynx.pro.repository;

import com.jynx.pro.constant.WithdrawalStatus;
import com.jynx.pro.entity.Withdrawal;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

@Repository
public class WithdrawalRepository extends EntityRepository<Withdrawal> {

    /**
     * Get {@link Withdrawal}s by status
     *
     * @param status {@link WithdrawalStatus}
     *
     * @return {@link List<Withdrawal>}
     */
    public List<Withdrawal> findByStatus(
            final WithdrawalStatus status
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Withdrawal> query = cb.createQuery(getType());
        Root<Withdrawal> rootType = query.from(getType());
        Path<WithdrawalStatus> withdrawalStatus = rootType.get("status");
        query = query.select(rootType).where(cb.equal(withdrawalStatus, status));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Withdrawal}s by batch ID
     *
     * @param withdrawalBatchId the batch ID
     *
     * @return {@link List<Withdrawal>}
     */
    public List<Withdrawal> findByWithdrawalBatchId(
            final UUID withdrawalBatchId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Withdrawal> query = cb.createQuery(getType());
        Root<Withdrawal> rootType = query.from(getType());
        Path<UUID> withdrawal_batch_id = rootType.join("withdrawalBatch").get("id");
        query = query.select(rootType).where(cb.equal(withdrawal_batch_id, withdrawalBatchId));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Withdrawal> getType() {
        return Withdrawal.class;
    }
}