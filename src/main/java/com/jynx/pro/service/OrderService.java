package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.User;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TradeService tradeService;
    @Autowired
    private MarketService marketService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private UUIDUtils uuidUtils;

    private static final int MAX_BULK = 25;
    private static final int ORDER_BOOK_LIMIT = 100;

    public MarketSide getOtherSide(
            final MarketSide side
    ) {
        return side.equals(MarketSide.SELL) ? MarketSide.BUY : MarketSide.SELL;
    }

    public OrderBook getOrderBook(
            final Market market
    ) {
        return getOrderBook(market, ORDER_BOOK_LIMIT);
    }

    public BigDecimal getMidPrice(
            final Market market
    ) {
        OrderBook orderBook = getOrderBook(market, 1);
        if(orderBook.getAsks().size() == 0 || orderBook.getBids().size() == 0) {
            throw new JynxProException(ErrorCode.EMPTY_ORDER_BOOK);
        }
        return (orderBook.getBids().get(0).getPrice().add(orderBook.getAsks().get(0).getPrice()))
                .multiply(BigDecimal.valueOf(0.5));
    }

    private List<Order> getSideOfBook(
            final Market market,
            final MarketSide side
    ) {
        List<Order> orders = getOpenLimitOrders(market).stream()
                .filter(o -> o.getSide().equals(side))
                .sorted(Comparator.comparing(Order::getPrice))
                .collect(Collectors.toList());
        if(side.equals(MarketSide.BUY)) {
            orders.sort(Comparator.comparing(Order::getPrice).reversed());
        }
        return orders;
    }

    public OrderBook getOrderBook(
            final Market market,
            final Integer limit
    ) {
        OrderBook orderBook = new OrderBook();
        List<OrderBookItem> bids = getSideOfBook(market, MarketSide.BUY)
                .stream()
                .map(o -> new OrderBookItem().setSize(o.getRemainingSize()).setPrice(o.getPrice()))
                .limit(limit)
                .collect(Collectors.toList());
        List<OrderBookItem> asks = getSideOfBook(market, MarketSide.SELL)
                .stream()
                .map(o -> new OrderBookItem().setSize(o.getRemainingSize()).setPrice(o.getPrice()))
                .limit(limit)
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
    public Order cancel(
            final CancelOrderRequest request
    ) {
        Order order = orderRepository.findById(request.getId())
                .orElseThrow(() -> new JynxProException(ErrorCode.ORDER_NOT_FOUND));
        if(!order.getUser().getId().equals(request.getUser().getId())) {
            throw new JynxProException(ErrorCode.PERMISSION_DENIED);
        }
        if(order.getType().equals(OrderType.MARKET)) {
            throw new JynxProException(ErrorCode.INVALID_ORDER_TYPE);
        }
        List<OrderStatus> statusList = Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
        if(!statusList.contains(order.getStatus())) {
            throw new JynxProException(ErrorCode.INVALID_ORDER_STATUS);
        }
        order.setStatus(OrderStatus.CANCELLED);
        BigDecimal margin = getInitialMarginRequirement(order.getMarket(), order.getType(),
                order.getRemainingSize(), order.getPrice());
        accountService.releaseMargin(margin, request.getUser(), order.getMarket().getSettlementAsset());
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
                .setPrice(request.getPrice())
                .setSide(request.getSide())
                .setSize(request.getSize())
                .setRemainingSize(request.getSize())
                .setStatus(OrderStatus.OPEN)
                .setType(request.getType());
        BigDecimal margin = getInitialMarginRequirement(market, order.getType(), request.getSize(), request.getPrice());
        accountService.allocateMargin(margin, request.getUser(), market.getSettlementAsset());
        return orderRepository.save(order);
    }

    private Order matchOrders(
            final List<Order> passiveOrders,
            final Order order,
            final Market market,
            final MarketSide side
    ) {
        for(Order passiveOrder : passiveOrders) {
            BigDecimal price = passiveOrder.getPrice();
            User taker = order.getUser();
            User maker = passiveOrder.getUser();
            Order partialOrder = passiveOrder;
            Order fullOrder = order;
            if(passiveOrder.getRemainingSize().doubleValue() <= order.getRemainingSize().doubleValue()) {
                partialOrder = order;
                fullOrder = passiveOrder;
            }
            BigDecimal size = fullOrder.getRemainingSize();
            accountService.processFees(size, price, maker, taker, market);
            partialOrder.setRemainingSize(partialOrder.getRemainingSize().subtract(size));
            partialOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
            fullOrder.setRemainingSize(BigDecimal.ZERO);
            fullOrder.setStatus(OrderStatus.FILLED);
            tradeService.save(market, passiveOrder, order, price, size, side);
            positionService.update(market, price, size, maker, getOtherSide(side));
            positionService.update(market, price, size, taker, side);
            marketService.updateLastPrice(price, market);
            if(order.getRemainingSize().equals(BigDecimal.ZERO)) {
                order.setStatus(OrderStatus.FILLED);
                break;
            }
        }
        orderRepository.saveAll(passiveOrders);
        // TODO - closeout distressed positions
        // TODO - update LP orders
        return orderRepository.save(order);
    }

    private Order createMarketOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        List<Order> passiveOrders = getSideOfBook(market, getOtherSide(request.getSide()));
        double passiveVolume = passiveOrders.stream().mapToDouble(o -> o.getRemainingSize().doubleValue()).sum();
        Order order = new Order()
                .setType(OrderType.MARKET)
                .setSide(request.getSide())
                .setMarket(market)
                .setStatus(OrderStatus.FILLED)
                .setSize(request.getSize())
                .setRemainingSize(request.getSize())
                .setId(uuidUtils.next())
                .setUser(request.getUser());
        if(request.getSize().doubleValue() > passiveVolume) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectedReason(ErrorCode.INSUFFICIENT_PASSIVE_VOLUME);
            orderRepository.save(order);
            throw new JynxProException(ErrorCode.INSUFFICIENT_PASSIVE_VOLUME);
        }
        order = orderRepository.save(order);
        // TODO - allocate margin
        return matchOrders(passiveOrders, order, market, request.getSide());
    }

    private Order handleCrossingLimitOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        if(request.getPostOnly()) {
            throw new JynxProException(ErrorCode.POST_ONLY_FAILED);
        }
        List<Order> passiveOrders = getSideOfBook(market, getOtherSide(request.getSide())).stream().filter(o -> {
            if(request.getSide().equals(MarketSide.BUY)) {
                return o.getPrice().doubleValue() <= request.getPrice().doubleValue();
            } else {
                return o.getPrice().doubleValue() >= request.getPrice().doubleValue();
            }
        }).collect(Collectors.toList());
        Order order = new Order()
                .setUser(request.getUser())
                .setId(uuidUtils.next())
                .setStatus(OrderStatus.OPEN)
                .setType(OrderType.LIMIT)
                .setSide(request.getSide())
                .setMarket(market)
                .setSize(request.getSize())
                .setRemainingSize(request.getSize())
                .setPrice(request.getPrice());
        // TODO - allocate margin
        return matchOrders(passiveOrders, order, market, request.getSide());
    }

    private Order handleLimitOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        List<Order> openLimitOrders = getOpenLimitOrders(market);
        if(request.getSide().equals(MarketSide.BUY)) {
            Optional<Order> bestOffer = openLimitOrders.stream()
                    .filter(o -> o.getSide().equals(MarketSide.SELL)).min(Comparator.comparing(Order::getPrice));
            if(bestOffer.isEmpty() || request.getPrice().doubleValue() < bestOffer.get().getPrice().doubleValue()) {
                return createLimitOrder(request, market);
            } else {
                return handleCrossingLimitOrder(request, market);
            }
        } else if(request.getSide().equals(MarketSide.SELL)) {
            Optional<Order> bestBid = openLimitOrders.stream()
                    .filter(o -> o.getSide().equals(MarketSide.BUY)).max(Comparator.comparing(Order::getPrice));
            if(bestBid.isEmpty() || request.getPrice().doubleValue() > bestBid.get().getPrice().doubleValue()) {
                return createLimitOrder(request, market);
            } else {
                return handleCrossingLimitOrder(request, market);
            }
        }
        throw new JynxProException(ErrorCode.UNKNOWN_MARKET_SIDE);
    }

    public Order create(
            final CreateOrderRequest request
    ) {
        Market market = marketService.get(request.getMarketId());
        performMarginCheck(market, request.getType(), request.getSize(), request.getPrice(), request.getUser());
        if(!market.getStatus().equals(MarketStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_ACTIVE);
        }
        validateRequest(request);
        if(OrderType.LIMIT.equals(request.getType())) {
            return handleLimitOrder(request, market);
        } else if(OrderType.STOP_MARKET.equals(request.getType())) {
            // TODO - handle stop market
            return null;
        }
        return createMarketOrder(request, market);
    }

    private void validateRequest(
            final CreateOrderRequest request
    ) {
        // TODO - check mandatory fields
        if(request.getType().equals(OrderType.MARKET)) {
            request.setPrice(null);
            request.setPostOnly(null);
            request.setReduceOnly(null);
        }
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
            final List<CancelOrderRequest> ids
    ) {
        if(ids.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return ids.stream().map(this::cancel).collect(Collectors.toList());
    }

    public BigDecimal getInitialMarginRequirement(
            final Market market,
            final OrderType type,
            final BigDecimal size,
            final BigDecimal price
    ) {
        // TODO - the margin requirement will be different if it doesn't increase exposure and trader has open position
        if(OrderType.LIMIT.equals(type)) {
            BigDecimal notionalSize = price.multiply(size);
            return notionalSize.multiply(market.getInitialMargin());
        } else if(OrderType.MARKET.equals(type)) {
            BigDecimal midPrice = getMidPrice(market);
            BigDecimal notionalSize = midPrice.multiply(size);
            return notionalSize.multiply(market.getInitialMargin());
        } else if(OrderType.STOP_MARKET.equals(type)) {
            throw new JynxProException("Cannot place stop market order");
        }
        throw new JynxProException(ErrorCode.INVALID_ORDER_TYPE);
    }

    private void performMarginCheck(
            final Market market,
            final OrderType type,
            final BigDecimal size,
            final BigDecimal price,
            final User user
    ) {
        Account account = accountService.getAndCreate(user, market.getSettlementAsset());
        BigDecimal margin = getInitialMarginRequirement(market, type, size, price);
        if(account.getAvailableBalance().doubleValue() < margin.doubleValue()) {
            throw new JynxProException(ErrorCode.INSUFFICIENT_MARGIN);
        }
    }
}