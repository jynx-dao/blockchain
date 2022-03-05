package com.jynx.pro.repository;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.model.MarketStatistics;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.model.Quote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class ReadOnlyRepository {

    @Autowired
    private DatabaseTransactionManager databaseTransactionManager;

    private EntityManager getEntityManager() {
        return databaseTransactionManager.getReadEntityManager();
    }

    /**
     * Get {@link Account} by ID
     *
     * @param id the ID
     *
     * @return {@link Optional} {@link Account}
     */
    public Optional<Account> getAccountById(
            final UUID id
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Account> query = cb.createQuery(Account.class);
        Root<Account> rootType = query.from(Account.class);
        Path<UUID> account_id = rootType.get("id");
        query = query.select(rootType).where(cb.equal(account_id, id));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get {@link Deposit}s by account ID and user ID
     *
     * @param accountId the account ID
     * @param userId the user ID
     *
     * @return {@link List} of {@link Deposit}s
     */
    public List<Deposit> getDepositsByAccountIdAndUserId(
            final UUID accountId,
            final UUID userId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Deposit> query = cb.createQuery(Deposit.class);
        Root<Deposit> rootType = query.from(Deposit.class);
        Path<UUID> account_id = rootType.join("account").get("id");
        Path<UUID> user_id = rootType.join("user").get("id");
        query = query.select(rootType).where(cb.equal(account_id, accountId), cb.equal(user_id, userId));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Withdrawal}s by account ID and user ID
     *
     * @param accountId the account ID
     * @param userId the user ID
     *
     * @return {@link List} of {@link Withdrawal}s
     */
    public List<Withdrawal> getWithdrawalsByAccountIdAndUserId(
            final UUID accountId,
            final UUID userId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Withdrawal> query = cb.createQuery(Withdrawal.class);
        Root<Withdrawal> rootType = query.from(Withdrawal.class);
        Path<UUID> account_id = rootType.join("account").get("id");
        Path<UUID> user_id = rootType.join("user").get("id");
        query = query.select(rootType).where(cb.equal(account_id, accountId), cb.equal(user_id, userId));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Transaction}s by account ID and user ID
     *
     * @param accountId the account ID
     * @param userId the user ID
     *
     * @return {@link List} of {@link Transaction}s
     */
    public List<Transaction> getTransactionsByAccountIdAndUserId(
            final UUID accountId,
            final UUID userId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Transaction> query = cb.createQuery(Transaction.class);
        Root<Transaction> rootType = query.from(Transaction.class);
        Path<UUID> account_id = rootType.join("account").get("id");
        Path<UUID> user_id = rootType.join("user").get("id");
        query = query.select(rootType).where(cb.equal(account_id, accountId), cb.equal(user_id, userId));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get the latest {@link OrderBook} by market ID
     *
     * @param marketId the market ID
     *
     * @return {@link OrderBook}
     */
    public OrderBook getOrderBookByMarketId(
            final UUID marketId
    ) {
        List<Order> openLimitOrders = getOpenLimitOrdersByMarketId(marketId);
        // TODO - should aggregate by price
        List<OrderBookItem> bids = openLimitOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.BUY))
                .sorted(Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getPriority))
                .map(o -> new OrderBookItem().setQuantity(o.getRemainingQuantity()).setPrice(o.getPrice()))
                .collect(Collectors.toList());
        List<OrderBookItem> asks = openLimitOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.SELL))
                .sorted(Comparator.comparing(Order::getPrice).thenComparing(Order::getPriority))
                .map(o -> new OrderBookItem().setQuantity(o.getRemainingQuantity()).setPrice(o.getPrice()))
                .collect(Collectors.toList());
        return new OrderBook().setBids(bids).setAsks(asks);
    }

    /**
     * Get {@link Market} by ID
     *
     * @param id the ID
     *
     * @return {@link Optional} {@link Market}
     */
    public Optional<Market> getMarketById(
            final UUID id
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Market> query = cb.createQuery(Market.class);
        Root<Market> rootType = query.from(Market.class);
        Path<UUID> market_id = rootType.get("id");
        query = query.select(rootType).where(cb.equal(market_id, id));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get {@link Trade}s by market ID
     *
     * @param marketId the market ID
     *
     * @return {@link List} of {@link Trade}s
     */
    public List<Trade> getTradesByMarketId(
            final UUID marketId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Trade> query = cb.createQuery(Trade.class);
        Root<Trade> rootType = query.from(Trade.class);
        Path<UUID> market_id = rootType.join("market").get("id");
        query = query.select(rootType).where(cb.equal(market_id, marketId));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Quote} by market ID
     *
     * @param marketId the market ID
     *
     * @return {@link Quote}
     */
    public Quote getQuoteByMarketId(
            final UUID marketId
    ) {
        OrderBook orderBook = getOrderBookByMarketId(marketId);
        return new Quote()
                .setAskPrice(orderBook.getAsks().size() > 0 ? orderBook.getAsks().get(0).getPrice() : BigDecimal.ZERO)
                .setAskSize(orderBook.getAsks().size() > 0 ? orderBook.getAsks().get(0).getQuantity() : BigDecimal.ZERO)
                .setBidPrice(orderBook.getBids().size() > 0 ? orderBook.getBids().get(0).getPrice() : BigDecimal.ZERO)
                .setBidSize(orderBook.getBids().size() > 0 ? orderBook.getBids().get(0).getQuantity() : BigDecimal.ZERO);
    }

    /**
     * Get {@link MarketStatistics} by market ID
     *
     * @param marketId the market ID
     *
     * @return {@link MarketStatistics}
     */
    public MarketStatistics getStatisticsByMarketId(
            final UUID marketId
    ) {
        Market market = getMarketById(marketId).orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
        // TODO - generate all statistics
        return new MarketStatistics()
                .setOpenVolume(market.getOpenVolume());
    }

    /**
     * Get {@link User} by ID
     *
     * @param id the ID
     *
     * @return {@link Optional} {@link User}
     */
    public Optional<User> getUserById(
            final UUID id
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> rootType = query.from(User.class);
        Path<UUID> user_id = rootType.get("id");
        query = query.select(rootType).where(cb.equal(user_id, id));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get {@link User} by public key
     *
     * @param publicKey the public key
     *
     * @return {@link Optional} {@link User}
     */
    public Optional<User> getUserByPublicKey(
            final String publicKey
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> rootType = query.from(User.class);
        Path<String> userPublicKey = rootType.get("publicKey");
        query = query.select(rootType).where(cb.equal(userPublicKey, publicKey));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Get {@link Account}s by user ID
     *
     * @param userId the user ID
     *
     * @return {@link List} of {@link Account}s
     */
    public List<Account> getAccountsByUserId(
            final UUID userId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Account> query = cb.createQuery(Account.class);
        Root<Account> rootType = query.from(Account.class);
        Path<UUID> user_id = rootType.join("user").get("id");
        query = query.select(rootType).where(cb.equal(user_id, userId));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get open limit {@link Order}s by market ID
     *
     * @param marketId the market ID
     *
     * @return {@link List} of {@link Order}s
     */
    public List<Order> getOpenLimitOrdersByMarketId(
            final UUID marketId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> rootType = query.from(Order.class);
        Path<UUID> market_id = rootType.join("market").get("id");
        Path<OrderStatus> orderStatus = rootType.get("status");
        Path<OrderType> orderType = rootType.get("type");
        query = query.select(rootType).where(cb.equal(market_id, marketId), cb.equal(orderType, OrderType.LIMIT),
                orderStatus.in(List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED)));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Order}s by user ID
     *
     * @param userId the user ID
     *
     * @return {@link List} of {@link Order}s
     */
    public List<Order> getOrdersByUserId(
            final UUID userId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Order> query = cb.createQuery(Order.class);
        Root<Order> rootType = query.from(Order.class);
        Path<UUID> user_id = rootType.join("user").get("id");
        query = query.select(rootType).where(cb.equal(user_id, userId));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Trade}s by user ID and market ID
     *
     * @param userId the user ID
     * @param marketId the market ID
     *
     * @return {@link List} of {@link Trade}s
     */
    public List<Trade> getTradesByUserIdAndMarketId(
            final UUID userId,
            final UUID marketId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Trade> query = cb.createQuery(Trade.class);
        Root<Trade> rootType = query.from(Trade.class);
        Path<UUID> user_id = rootType.join("user").get("id");
        Path<UUID> market_id = rootType.join("market").get("id");
        query = query.select(rootType).where(cb.equal(user_id, userId), cb.equal(market_id, marketId));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Position}s by user ID
     *
     * @param userId the user ID
     *
     * @return {@link List} of {@link Position}s
     */
    public List<Position> getPositionsByUserId(
            final UUID userId
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Position> query = cb.createQuery(Position.class);
        Root<Position> rootType = query.from(Position.class);
        Path<UUID> user_id = rootType.join("user").get("id");
        query = query.select(rootType).where(cb.equal(user_id, userId));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get all {@link Asset}s
     *
     * @return {@link List<Asset>}
     */
    public List<Asset> getAssets() {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Asset> query = cb.createQuery(Asset.class);
        Root<Asset> rootType = query.from(Asset.class);
        query = query.select(rootType);
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Asset} by ID
     *
     * @param id the asset ID
     *
     * @return {@link Optional<Asset>}
     */
    public Optional<Asset> getAssetById(
            final UUID id
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Asset> query = cb.createQuery(Asset.class);
        Root<Asset> rootType = query.from(Asset.class);
        Path<UUID> assetId = rootType.get("id");
        query = query.select(rootType).where(cb.equal(assetId, id));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }

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
        CriteriaQuery<Trade> query = cb.createQuery(Trade.class);
        Root<Trade> rootType = query.from(Trade.class);
        Path<UUID> market_id = rootType.join("market").get("id");
        Path<Long> tradeExecuted = rootType.get("executed");
        query = query.select(rootType).where(cb.equal(market_id, marketId), cb.greaterThan(tradeExecuted, from),
                cb.lessThan(tradeExecuted, to));
        return getEntityManager().createQuery(query).getResultList();
    }

    /**
     * Get {@link Validator} by public key
     *
     * @param publicKey the validator's public key
     *
     * @return {@link Optional<Validator>}
     */
    public Optional<Validator> findByPublicKey(
            final String publicKey
    ) {
        CriteriaBuilder cb = getEntityManager().getCriteriaBuilder();
        CriteriaQuery<Validator> query = cb.createQuery(Validator.class);
        Root<Validator> rootType = query.from(Validator.class);
        Path<String> userPublicKey = rootType.get("publicKey");
        query = query.select(rootType).where(cb.equal(userPublicKey, publicKey));
        try {
            return Optional.of(getEntityManager().createQuery(query).getSingleResult());
        } catch(Exception e) {
            return Optional.empty();
        }
    }
}