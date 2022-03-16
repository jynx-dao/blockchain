package com.jynx.pro.repository;

import com.jynx.pro.entity.PendingAuctionTrigger;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

@Repository
public class PendingAuctionTriggerRepository extends EntityRepository<PendingAuctionTrigger> {

    /**
     * Get {@link PendingAuctionTrigger}s by market ID
     *
     * @param marketId the market ID
     *
     * @return {@link List} of {@link PendingAuctionTrigger}s
     */
    public List<PendingAuctionTrigger> findByMarketId(
            final UUID marketId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<PendingAuctionTrigger> query = cb.createQuery(getType());
        Root<PendingAuctionTrigger> rootType = query.from(getType());
        Path<UUID> market_id = rootType.join("market").get("id");
        query = query.select(rootType).where(cb.equal(market_id, marketId));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<PendingAuctionTrigger> getType() {
        return PendingAuctionTrigger.class;
    }
}