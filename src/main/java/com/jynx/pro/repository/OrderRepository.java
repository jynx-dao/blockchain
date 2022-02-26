package com.jynx.pro.repository;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.User;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public class OrderRepository extends EntityRepository<Order> {

    /**
     * Get {@link Order}s by status, type and market
     *
     * @param statusList {@link List<OrderStatus>}
     * @param type {@link OrderType}
     * @param market {@link Market}
     *
     * @return {@link List<Order>}
     */
    public List<Order> findByStatusInAndTypeAndMarket(
            final List<OrderStatus> statusList,
            final OrderType type,
            final Market market
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(getType());
        Root<Order> rootType = query.from(getType());
        Path<UUID> marketId = rootType.join("market").get("id");
        Path<OrderType> orderType = rootType.get("type");
        query = query.select(rootType).where(cb.equal(marketId, market.getId()),
                cb.equal(orderType, type), rootType.get("status").in(statusList));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Order}s by status, type, market and user
     *
     * @param statusList {@link List<OrderStatus>}
     * @param type {@link OrderType}
     * @param market {@link Market}
     * @param user {@link User}
     *
     * @return {@link List<Order>}
     */
    public List<Order> findByStatusInAndTypeAndMarketAndUser(
            final List<OrderStatus> statusList,
            final OrderType type,
            final Market market,
            final User user
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(getType());
        Root<Order> rootType = query.from(getType());
        Path<UUID> marketId = rootType.join("market").get("id");
        Path<UUID> userId = rootType.join("user").get("id");
        Path<OrderType> orderType = rootType.get("type");
        query = query.select(rootType).where(cb.equal(marketId, market.getId()), cb.equal(userId, user.getId()),
                cb.equal(orderType, type), rootType.get("status").in(statusList));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Count {@link Order}s by status, type, market, price and side
     *
     * @param statusList {@link List<OrderStatus>}
     * @param type {@link OrderType}
     * @param market {@link Market}
     * @param price the price
     * @param side {@link MarketSide}
     *
     * @return count of {@link Order}s
     */
    public long countByMarketAndPriceAndSideAndStatusInAndType(
            final Market market,
            final BigDecimal price,
            final MarketSide side,
            final List<OrderStatus> statusList,
            final OrderType type
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(getType());
        Root<Order> rootType = query.from(getType());
        Path<UUID> marketId = rootType.join("market").get("id");
        Path<BigDecimal> orderPrice = rootType.get("price");
        Path<MarketSide> orderSide = rootType.get("side");
        Path<OrderType> orderType = rootType.get("type");
        query = query.select(rootType).where(cb.equal(marketId, market.getId()), cb.equal(orderPrice, price),
                cb.equal(orderType, type), rootType.get("status").in(statusList), cb.equal(orderSide, side));
        return getEntityManager().createQuery(query).getResultList().size();
    }

    @Override
    public Class<Order> getType() {
        return Order.class;
    }
}