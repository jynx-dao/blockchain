package com.jynx.pro.repository;

import com.jynx.pro.constant.KlineInterval;
import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
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
        String query = "select Account from Account where user = ?1";
        try {
            Account account = getEntityManager().createQuery(query, Account.class)
                    .setParameter(1, id)
                    .getSingleResult();
            return Optional.of(account);
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
        Account account = getAccountById(accountId)
                .orElseThrow(() -> new JynxProException(ErrorCode.ACCOUNT_NOT_FOUND));
        String query = "select Deposit from Deposit where user.id = ?1 and asset.id = ?2";
        return getEntityManager().createQuery(query, Deposit.class)
                .setParameter(1, userId)
                .setParameter(2, account.getAsset().getId())
                .getResultList();
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
        Account account = getAccountById(accountId)
                .orElseThrow(() -> new JynxProException(ErrorCode.ACCOUNT_NOT_FOUND));
        String query = "select Withdrawal from Withdrawal where user.id = ?1 and asset.id = ?2";
        return getEntityManager().createQuery(query, Withdrawal.class)
                .setParameter(1, userId)
                .setParameter(2, account.getAsset().getId())
                .getResultList();
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
        Account account = getAccountById(accountId)
                .orElseThrow(() -> new JynxProException(ErrorCode.ACCOUNT_NOT_FOUND));
        String query = "select Transaction from Transaction where user.id = ?1 and asset.id = ?2";
        return getEntityManager().createQuery(query, Transaction.class)
                .setParameter(1, userId)
                .setParameter(2, account.getAsset().getId())
                .getResultList();
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
        String query = "select Market from Market where id = ?1";
        try {
            Market market = getEntityManager().createQuery(query, Market.class)
                    .setParameter(1, id)
                    .getSingleResult();
            return Optional.of(market);
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
        String query = "select Trade from Trade where market.id = ?1";
        return getEntityManager().createQuery(query, Trade.class)
                .setParameter(1, marketId)
                .getResultList();
    }

    public List<Kline> getKline(
            final UUID marketID,
            final Long from,
            final Long to,
            final KlineInterval interval
    ) {
        return Collections.emptyList(); // TODO - generate aggregated price data
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
        String query = "select User from User where id = ?1";
        try {
            User user = getEntityManager().createQuery(query, User.class)
                    .setParameter(1, id)
                    .getSingleResult();
            return Optional.of(user);
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
        String query = "select User from User where publicKey = ?1";
        try {
            User user = getEntityManager().createQuery(query, User.class)
                    .setParameter(1, publicKey)
                    .getSingleResult();
            return Optional.of(user);
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
        String query = "select Account from Account where user.id = ?1";
        return getEntityManager().createQuery(query, Account.class)
                .setParameter(1, userId)
                .getResultList();
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
        String query = "select Order from Order where market.id = ?1 and status in (?2, ?3) and type = ?4";
        return getEntityManager().createQuery(query, Order.class)
                .setParameter(1, marketId)
                .setParameter(2, OrderStatus.OPEN)
                .setParameter(3, OrderStatus.PARTIALLY_FILLED)
                .setParameter(4, OrderType.LIMIT)
                .getResultList();
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
        String query = "select Order from Order where user.id = ?1";
        return getEntityManager().createQuery(query, Order.class)
                .setParameter(1, userId)
                .getResultList();
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
        String query = "select Trade from Trade where takerOrder.user.id = ?1 and market.id = ?2";
        return getEntityManager().createQuery(query, Trade.class)
                .setParameter(1, userId)
                .setParameter(2, marketId)
                .getResultList();
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
        String query = "select Position from Position where user.id = ?1";
        return getEntityManager().createQuery(query, Position.class)
                .setParameter(1, userId)
                .getResultList();
    }
}