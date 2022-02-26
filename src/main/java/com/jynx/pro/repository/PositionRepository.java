package com.jynx.pro.repository;

import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Position;
import com.jynx.pro.entity.User;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PositionRepository extends EntityRepository<Position> {

    /**
     * Get {@link Position} by user and market
     *
     * @param user {@link User}
     * @param market {@link Market}
     *
     * @return {@link Optional<Position>}
     */
    public Optional<Position> findByUserAndMarket(
            final User user,
            final Market market
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Position> query = cb.createQuery(getType());
        Root<Position> rootType = query.from(getType());
        Path<UUID> userId = rootType.join("user").get("id");
        Path<UUID> marketId = rootType.join("market").get("id");
        query = query.select(rootType).where(cb.equal(userId, user.getId()), cb.equal(marketId, market.getId()));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get {@link Position}s by market
     *
     * @param market {@link Market}
     *
     * @return {@link List<Position>}
     */
    public List<Position> findByMarket(
            final Market market
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Position> query = cb.createQuery(getType());
        Root<Position> rootType = query.from(getType());
        Path<UUID> marketId = rootType.join("market").get("id");
        query = query.select(rootType).where(cb.equal(marketId, market.getId()));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Position}s by market and quantity greater than
     *
     * @param market {@link Market}
     * @param quantity the minimum quantity
     *
     * @return {@link List<Position>}
     */
    public List<Position> findByMarketAndQuantityGreaterThan(
            final Market market,
            final BigDecimal quantity
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Position> query = cb.createQuery(getType());
        Root<Position> rootType = query.from(getType());
        Path<UUID> marketId = rootType.join("market").get("id");
        Path<BigDecimal> positionQuantity = rootType.get("quantity");
        query = query.select(rootType).where(cb.equal(marketId, market.getId()),
                cb.greaterThan(positionQuantity, quantity));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Position}s by ids
     *
     * @param ids the position IDs
     *
     * @return {@link List<Position>}
     */
    public List<Position> findByIdIn(
            final List<UUID> ids
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Position> query = cb.createQuery(getType());
        Root<Position> rootType = query.from(getType());
        query = query.select(rootType).where(rootType.get("id").in(ids));
        return getEntityManager().createQuery(query).getResultList();
    }

    @Override
    public Class<Position> getType() {
        return Position.class;
    }
}