package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderBookType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.model.Quote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderBookService {

    @Autowired
    private OrderService orderService;

    /**
     * Get asks for given market
     *
     * @param market {@link Market}
     * @param readOnly use static data
     *
     * @return {@link List<OrderBookItem>}
     */
    private List<OrderBookItem> getAsks(
            final Market market,
            final boolean readOnly
    ) {
        return orderService.getSideOfBook(market, MarketSide.SELL, readOnly)
                .stream()
                .map(o -> new OrderBookItem().setQuantity(o.getRemainingQuantity()).setPrice(o.getPrice()))
                .collect(Collectors.toList());
    }

    /**
     * Get bids for given market
     *
     * @param market {@link Market}
     * @param readOnly use static data
     *
     * @return {@link List<OrderBookItem>}
     */
    private List<OrderBookItem> getBids(
            final Market market,
            final boolean readOnly
    ) {
        return orderService.getSideOfBook(market, MarketSide.BUY, readOnly)
                .stream()
                .map(o -> new OrderBookItem().setQuantity(o.getRemainingQuantity()).setPrice(o.getPrice()))
                .collect(Collectors.toList());
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param market the {@link Market}
     * @param readOnly use static data
     *
     * @return the {@link OrderBook}
     */
    private OrderBook getOrderBookL1(
            final Market market,
            final boolean readOnly
    ) {
        OrderBook orderBook = new OrderBook();
        List<OrderBookItem> asks = getAsks(market, readOnly);
        List<OrderBookItem> bids = getBids(market, readOnly);
        if(asks.size() > 0) {
            orderBook.setAsks(asks.subList(0, 1));
        }
        if(bids.size() > 0) {
            orderBook.setBids(bids.subList(0, 1));
        }
        return orderBook;
    }

    /**
     * Aggregate order book items by price
     *
     * @param items {@link List<OrderBookItem>}
     *
     * @return {@link List<OrderBookItem>}
     */
    private List<OrderBookItem> aggregateByPrice(
            final List<OrderBookItem> items
    ) {
        return items.stream()
                .collect(Collectors.groupingBy(OrderBookItem::getPrice))
                .entrySet()
                .stream()
                .map(es -> new OrderBookItem()
                        .setPrice(es.getKey())
                        .setQuantity(es.getValue()
                                .stream()
                                .map(OrderBookItem::getQuantity)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .collect(Collectors.toList());
    }

    /**
     * Filter asks by cutoff price
     *
     * @param asks {@link List<OrderBookItem>}
     * @param cutoff cutoff price
     *
     * @return {@link List<OrderBookItem>}
     */
    private List<OrderBookItem> filterAsks(
            final List<OrderBookItem> asks,
            final BigDecimal cutoff
    ) {
        return asks.stream().filter(a -> a.getPrice().doubleValue() <= cutoff.doubleValue())
                .collect(Collectors.toList());
    }

    /**
     * Filter bids by cutoff price
     *
     * @param bids {@link List<OrderBookItem>}
     * @param cutoff cutoff price
     *
     * @return {@link List<OrderBookItem>}
     */
    private List<OrderBookItem> filterBids(
            final List<OrderBookItem> bids,
            final BigDecimal cutoff
    ) {
        return bids.stream().filter(a -> a.getPrice().doubleValue() >= cutoff.doubleValue())
                .collect(Collectors.toList());
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param market the {@link Market}
     * @param depth maximum order book depth
     * @param readOnly use static data
     *
     * @return the {@link OrderBook}
     */
    private OrderBook getOrderBookL2(
            final Market market,
            final BigDecimal depth,
            final boolean readOnly
    ) {
        OrderBook orderBook = getOrderBookL3(market, depth, readOnly);
        orderBook.setBids(aggregateByPrice(orderBook.getBids()));
        orderBook.setAsks(aggregateByPrice(orderBook.getAsks()));
        return orderBook;
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param market the {@link Market}
     * @param readOnly use static data
     *
     * @return the {@link OrderBook}
     */
    private OrderBook getOrderBookL2(
            final Market market,
            final boolean readOnly
    ) {
        return getOrderBookL2(market, BigDecimal.ONE, readOnly);
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param market the {@link Market}
     * @param readOnly use static data
     *
     * @return the {@link OrderBook}
     */
    private OrderBook getOrderBookL3(
            final Market market,
            final boolean readOnly
    ) {
        return getOrderBookL3(market, BigDecimal.ONE, readOnly);
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param market the {@link Market}
     * @param depth maximum order book depth
     * @param readOnly use static data
     *
     * @return the {@link OrderBook}
     */
    private OrderBook getOrderBookL3(
            final Market market,
            final BigDecimal depth,
            final boolean readOnly
    ) {
        OrderBook orderBook = new OrderBook();
        List<OrderBookItem> asks = getAsks(market, readOnly);
        List<OrderBookItem> bids = getBids(market, readOnly);
        if(asks.size() > 0) {
            BigDecimal cutoff = asks.get(0).getPrice().multiply(BigDecimal.ONE.add(depth.min(BigDecimal.ONE)));
            orderBook.setAsks(filterAsks(asks, cutoff));
        }
        if(bids.size() > 0) {
            BigDecimal cutoff = bids.get(0).getPrice().multiply(BigDecimal.ONE.subtract(depth.min(BigDecimal.ONE)));
            orderBook.setBids(filterBids(bids, cutoff));
        }
        return orderBook;
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param type {@link OrderBookType}
     * @param market {@link Market}
     *
     * @return the {@link OrderBook}
     */
    public OrderBook getOrderBook(
            final OrderBookType type,
            final Market market
    ) {
        return getOrderBook(type, market, false);
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param type {@link OrderBookType}
     * @param market {@link Market}
     * @param depth the order book depth
     *
     * @return the {@link OrderBook}
     */
    public OrderBook getOrderBook(
            final OrderBookType type,
            final Market market,
            final BigDecimal depth
    ) {
        return getOrderBook(type, market, depth, false);
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param type {@link OrderBookType}
     * @param market {@link Market}
     * @param readOnly use static data
     *
     * @return the {@link OrderBook}
     */
    public OrderBook getOrderBook(
            final OrderBookType type,
            final Market market,
            final boolean readOnly
    ) {
        if(OrderBookType.L3.equals(type)) {
            return getOrderBookL3(market, readOnly);
        } else if (OrderBookType.L2.equals(type)) {
            return getOrderBookL2(market, readOnly);
        }
        return getOrderBookL1(market, readOnly);
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param type {@link OrderBookType}
     * @param market {@link Market}
     * @param depth the order book depth
     * @param readOnly use static data
     *
     * @return the {@link OrderBook}
     */
    public OrderBook getOrderBook(
            final OrderBookType type,
            final Market market,
            final BigDecimal depth,
            final boolean readOnly
    ) {
        if(OrderBookType.L3.equals(type)) {
            return getOrderBookL3(market, Objects.isNull(depth) ? BigDecimal.ONE : depth, readOnly);
        } else if (OrderBookType.L2.equals(type)) {
            return getOrderBookL2(market, Objects.isNull(depth) ? BigDecimal.ONE : depth, readOnly);
        }
        return getOrderBookL1(market, readOnly);
    }

    /**
     * Get quote by market
     *
     * @param market {@link Market}
     * @param readOnly use static data
     *
     * @return {@link Quote}
     */
    public Quote getQuote(
            final Market market,
            final boolean readOnly
    ) {
        OrderBook orderBook = getOrderBookL1(market, readOnly);
        Quote quote = new Quote();
        if(orderBook.getBids().size() > 0) {
            quote.setBidPrice(orderBook.getBids().get(0).getPrice());
            quote.setBidSize(orderBook.getBids().get(0).getQuantity());
        }
        if(orderBook.getAsks().size() > 0) {
            quote.setAskPrice(orderBook.getAsks().get(0).getPrice());
            quote.setAskSize(orderBook.getAsks().get(0).getQuantity());
        }
        return quote;
    }
}