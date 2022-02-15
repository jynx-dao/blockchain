package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.repository.OrderRepository;
import com.jynx.pro.request.CancelOrderRequest;
import com.jynx.pro.request.CreateOrderRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private MarketService marketService;
    @Autowired
    private UUIDUtils uuidUtils;

    private static final int MAX_BULK = 25;
    private static final int ORDER_BOOK_LIMIT = 100;

    private BigDecimal convertFromLong(Long longValue, int decimals) {
        double modifier = Math.pow(10, decimals);
        return BigDecimal.valueOf(longValue.doubleValue() / modifier);
    }

    public Long convertFromDecimal(BigDecimal decimalValue, int decimals) {
        double modifier = Math.pow(10, decimals);
        return decimalValue.multiply(BigDecimal.valueOf(modifier)).longValueExact();
    }

    private OrderBookItem toOrderBookItem(
            final Order order,
            final int decimals
    ) {
        return new OrderBookItem()
                .setPrice(convertFromLong(order.getPrice(), decimals))
                .setSize(convertFromLong(order.getSize(), decimals));
    }

    public OrderBook getOrderBook(
            final Market market,
            final Integer limit
    ) {
        int bookLimit = limit == null ? ORDER_BOOK_LIMIT : limit;
        OrderBook orderBook = new OrderBook();
        List<Order> openOrders = getOpenLimitOrders(market);
        List<OrderBookItem> bids = openOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.BUY))
                .sorted(Comparator.comparing(Order::getPrice).reversed())
                .map(o -> toOrderBookItem(o, market.getDecimalPlaces()))
                .limit(bookLimit)
                .collect(Collectors.toList());
        List<OrderBookItem> asks = openOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.SELL))
                .sorted(Comparator.comparing(Order::getPrice))
                .map(o -> toOrderBookItem(o, market.getDecimalPlaces()))
                .limit(bookLimit)
                .collect(Collectors.toList());
        orderBook.setAsks(asks);
        orderBook.setBids(bids);
        return orderBook;
    }

    public List<Order> getOpenLimitOrders(
            final Market market
    ) {
        List<OrderStatus> statusList = Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
        return orderRepository.findByStatusInAndTypeAndMarket(statusList, OrderType.LIMIT, market);
    }

    /**
     * Cancels an order
     *
     * @param request {@link CancelOrderRequest}
     *
     * @return {@link Order}
     */
    private Order cancel(
            final CancelOrderRequest request
    ) {
        Order order = orderRepository.findById(request.getId())
                .orElseThrow(() -> new JynxProException(ErrorCode.ORDER_NOT_FOUND));
        if(!order.getUser().getId().equals(request.getUser().getId())) {
            throw new JynxProException(ErrorCode.PERMISSION_DENIED);
        }
        List<OrderStatus> statusList = Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
        if(!statusList.contains(order.getStatus())) {
            throw new JynxProException(ErrorCode.INVALID_ORDER_STATUS);
        }
        if(order.getType().equals(OrderType.MARKET)) {
            throw new JynxProException(ErrorCode.INVALID_ORDER_TYPE);
        }
        order.setStatus(OrderStatus.CANCELLED);
        // TODO - free margin
        return orderRepository.save(order);
    }

    private Order createLimitOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        Order order = new Order()
                .setId(uuidUtils.next())
                .setMarket(market)
                .setUser(request.getUser())
                .setPrice(convertFromDecimal(request.getPrice(), market.getDecimalPlaces()))
                .setSide(request.getSide())
                .setSize(convertFromDecimal(request.getSize(), market.getDecimalPlaces()))
                .setStatus(OrderStatus.OPEN)
                .setType(request.getType());
        // TODO - allocate margin
        return orderRepository.save(order);
    }

    private Order createMarketOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        // TODO - eat up anything that's on the book
        return null;
    }

    private Order handleLimitOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        double modifier = Math.pow(10, market.getDecimalPlaces());
        long price = request.getPrice().multiply(BigDecimal.valueOf(modifier)).longValueExact();
        List<Order> openLimitOrders = getOpenLimitOrders(market);
        if(request.getSide().equals(MarketSide.BUY)) {
            Optional<Order> bestOffer = openLimitOrders.stream()
                    .filter(o -> o.getSide().equals(MarketSide.SELL)).min(Comparator.comparing(Order::getPrice));
            if(bestOffer.isEmpty() || price < bestOffer.get().getPrice()) {
                return createLimitOrder(request, market);
            } else {
                throw new JynxProException("Cannot place limit order that crosses");
                // TODO - handle crossing limit order
            }
        } else if(request.getSide().equals(MarketSide.SELL)) {
            Optional<Order> bestBid = openLimitOrders.stream()
                    .filter(o -> o.getSide().equals(MarketSide.BUY)).max(Comparator.comparing(Order::getPrice));
            if(bestBid.isEmpty() || price > bestBid.get().getPrice()) {
                return createLimitOrder(request, market);
            } else {
                throw new JynxProException("Cannot place limit order that crosses");
                // TODO - handle crossing limit order
            }
        }
        throw new JynxProException(ErrorCode.UNKNOWN_MARKET_SIDE);
    }

    public Order create(
            final CreateOrderRequest request
    ) {
        performMarginCheck(request);
        Market market = marketService.get(request.getMarketId());
        if(!market.getStatus().equals(MarketStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_ACTIVE);
        }
        if(request.getType().equals(OrderType.LIMIT)) {
            return handleLimitOrder(request, market);
        } else if(request.getType().equals(OrderType.STOP_MARKET)) {
            // TODO - handle stop market
        } else if(request.getType().equals(OrderType.MARKET)) {
            return createMarketOrder(request, market);
        }
        throw new JynxProException(ErrorCode.INVALID_ORDER_TYPE);
    }

    public Order cancel(
            final UUID id
    ) {
        // TODO - cancel order
        return null;
    }

    public Order amend(
            final Order order
    ) {
        // TODO - amend order
        return null;
    }

    public List<Order> createMany(
            final List<CreateOrderRequest> requests
    ) {
        if(requests.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return requests.stream().map(this::create).collect(Collectors.toList());
    }

    public List<Order> amendMany(
            final List<Order> orders
    ) {
        if(orders.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return orders.stream().map(this::amend).collect(Collectors.toList());
    }

    public List<Order> cancelMany(
            final List<UUID> ids
    ) {
        if(ids.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return ids.stream().map(this::cancel).collect(Collectors.toList());
    }

    private void performMarginCheck(
            final CreateOrderRequest request
    ) {
        // TODO - check if the user has sufficient margin for their requested order
    }
}