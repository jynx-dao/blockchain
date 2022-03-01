package com.jynx.pro.repository;

import com.jynx.pro.entity.Trade;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.util.List;
import java.util.UUID;

@Repository
public class TradeRepository extends EntityRepository<Trade> {

    /**
     * Get {@link Trade}s by market ID and executed between 'to' and 'from' timestamps
     *
     * @param marketId the market ID
     * @param from the 'from' timestamp
     * @param to the 'to' timestamp
     *
     * @return {@link List<Trade>}
     */
    public List<Trade> findByMarketIdAndExecutedGreaterThanAndExecutedLessThan(
            final UUID marketId,
            final Long from,
            final Long to
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Trade> query = cb.createQuery(getType());
        Root<Trade> rootType = query.from(getType());
        Path<UUID> market_id = rootType.join("market").get("id");
        Path<Long> tradeExecuted = rootType.get("executed");
        query = query.select(rootType).where(cb.equal(market_id, marketId), cb.greaterThan(tradeExecuted, from),
                cb.lessThan(tradeExecuted, to));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Trade> getType() {
        return Trade.class;
    }
}