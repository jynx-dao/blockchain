package com.jynx.pro.repository;

import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Transaction;
import com.jynx.pro.entity.User;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

@Repository
public class TransactionRepository extends EntityRepository<Transaction> {

    /**
     * Get {@link Transaction}s by user and asset
     *
     * @param user {@link User}
     * @param asset {@link Asset}
     *
     * @return {@link List<Transaction>}
     */
    public List<Transaction> findByUserAndAsset(
            final User user,
            final Asset asset
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Transaction> query = cb.createQuery(getType());
        Root<Transaction> rootType = query.from(getType());
        Path<UUID> userId = rootType.join("user").get("id");
        Path<UUID> assetId = rootType.join("asset").get("id");
        query = query.select(rootType).where(cb.equal(userId, user.getId()), cb.equal(assetId, asset.getId()));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Transaction> getType() {
        return Transaction.class;
    }
}