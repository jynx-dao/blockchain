package com.jynx.pro.repository;

import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.entity.Market;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.List;

@Repository
public class MarketRepository extends EntityRepository<Market> {

    /**
     * Get {@link Market}s with matching status
     *
     * @param statusList {@link List<MarketStatus>}
     *
     * @return {@link List<Market>}
     */
    public List<Market> findByStatusIn(
            final List<MarketStatus> statusList
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Market> query = cb.createQuery(getType());
        Root<Market> rootType = query.from(getType());
        query = query.select(rootType).where(rootType.get("status").in(statusList));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Market> getType() {
        return Market.class;
    }
}