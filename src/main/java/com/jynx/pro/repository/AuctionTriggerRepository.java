package com.jynx.pro.repository;

import com.jynx.pro.entity.AuctionTrigger;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

@Repository
public class AuctionTriggerRepository extends EntityRepository<AuctionTrigger> {

    /**
     * Get {@link AuctionTrigger}s by market ID
     *
     * @param marketId the market ID
     *
     * @return {@link List} of {@link AuctionTrigger}s
     */
    public List<AuctionTrigger> findByMarketId(
            final UUID marketId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<AuctionTrigger> query = cb.createQuery(getType());
        Root<AuctionTrigger> rootType = query.from(getType());
        Path<UUID> market_id = rootType.join("market").get("id");
        query = query.select(rootType).where(cb.equal(market_id, marketId));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<AuctionTrigger> getType() {
        return AuctionTrigger.class;
    }
}