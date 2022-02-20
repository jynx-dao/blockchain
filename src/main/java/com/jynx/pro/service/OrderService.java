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
import com.jynx.pro.request.AmendOrderRequest;
import com.jynx.pro.request.CancelOrderRequest;
import com.jynx.pro.request.CreateOrderRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
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
    private ConfigService configService;
    @Autowired
    private UUIDUtils uuidUtils;

    private static final int MAX_BULK = 25;

    /**
     * Gets the opposite {@link MarketSide}
     *
     * @param side the {@link MarketSide}
     *
     * @return the opposite side
     */
    public MarketSide getOtherSide(
            final MarketSide side
    ) {
        return side.equals(MarketSide.SELL) ? MarketSide.BUY : MarketSide.SELL;
    }

    /**
     * Get the mid-price from the current order book of given {@link Market}
     *
     * @param market the {@link Market}
     *
     * @return the mid-price
     */
    public BigDecimal getMidPrice(
            final Market market
    ) {
        OrderBook orderBook = getOrderBook(market);
        if(orderBook.getAsks().size() == 0 || orderBook.getBids().size() == 0) {
            throw new JynxProException(ErrorCode.EMPTY_ORDER_BOOK);
        }
        return (orderBook.getBids().get(0).getPrice().add(orderBook.getAsks().get(0).getPrice()))
                .multiply(BigDecimal.valueOf(0.5));
    }

    /**
     * Get the orders from one side of the order book of a given {@link Market}
     *
     * @param market the {@link Market}
     * @param side the {@link MarketSide}
     *
     * @return a list of {@link Order}s
     */
    private List<Order> getSideOfBook(
            final Market market,
            final MarketSide side
    ) {
        List<Order> orders = getOpenLimitOrders(market).stream()
                .filter(o -> o.getSide().equals(side))
                .sorted(Comparator.comparing(Order::getPrice).thenComparing(Order::getUpdated))
                .collect(Collectors.toList());
        if(side.equals(MarketSide.BUY)) {
            orders.sort(Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getUpdated));
        }
        return orders;
    }

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param market the {@link Market}
     *
     * @return the {@link OrderBook}
     */
    public OrderBook getOrderBook(
            final Market market
    ) {
        OrderBook orderBook = new OrderBook();
        List<OrderBookItem> bids = getSideOfBook(market, MarketSide.BUY)
                .stream()
                .map(o -> new OrderBookItem().setSize(o.getRemainingSize()).setPrice(o.getPrice()))
                .collect(Collectors.toList());
        List<OrderBookItem> asks = getSideOfBook(market, MarketSide.SELL)
                .stream()
                .map(o -> new OrderBookItem().setSize(o.getRemainingSize()).setPrice(o.getPrice()))
                .collect(Collectors.toList());
        orderBook.setAsks(asks);
        orderBook.setBids(bids);
        return orderBook;
    }

    /**
     * Get all open limit orders for given {@link Market}
     *
     * @param market the {@link Market}
     *
     * @return a list of {@link Order}s
     */
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

    /**
     * Create a passive limit order
     *
     * @param request {@link CreateOrderRequest}
     * @param market {@link Market}
     *
     * @return the new {@link Order}
     */
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
                .setType(request.getType())
                .setUpdated(configService.getTimestamp());
        BigDecimal margin = getInitialMarginRequirement(market, order.getType(), request.getSize(), request.getPrice());
        accountService.allocateMargin(margin, request.getUser(), market.getSettlementAsset());
        return orderRepository.save(order);
    }

    /**
     * Execute trades when orders match with each other
     *
     * @param passiveOrders a list of passive {@link Order}s
     * @param order the aggressive {@link Order}
     * @param market the {@link Market}
     *
     * @return the executed {@link Order}
     */
    private Order matchOrders(
            final List<Order> passiveOrders,
            final Order order,
            final Market market
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
            tradeService.save(market, passiveOrder, order, price, size, order.getSide());
            positionService.update(market, price, size, maker, getOtherSide(order.getSide()));
            positionService.update(market, price, size, taker, order.getSide());
            marketService.updateLastPrice(price, market);
            if(order.getRemainingSize().equals(BigDecimal.ZERO)) {
                order.setStatus(OrderStatus.FILLED);
                break;
            }
        }
        orderRepository.saveAll(passiveOrders);
        // TODO - closeout distressed positions (incl. executing stop losses)
        // TODO - update LP orders
        return orderRepository.save(order);
    }

    /**
     * Create a passive market order
     *
     * @param request {@link CreateOrderRequest}
     * @param market {@link Market}
     *
     * @return the executed {@link Order}
     */
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
                .setUser(request.getUser())
                .setUpdated(configService.getTimestamp());
        if(request.getSize().doubleValue() > passiveVolume) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectedReason(ErrorCode.INSUFFICIENT_PASSIVE_VOLUME);
            orderRepository.save(order);
            throw new JynxProException(ErrorCode.INSUFFICIENT_PASSIVE_VOLUME);
        }
        order = orderRepository.save(order);
        BigDecimal margin = getInitialMarginRequirement(market, order.getType(), request.getSize(), request.getPrice());
        accountService.allocateMargin(margin, request.getUser(), market.getSettlementAsset());
        return matchOrders(passiveOrders, order, market);
    }

    private Order handleCrossingLimitOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        if(request.getPostOnly() == null) {
            request.setPostOnly(false);
        }
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
                .setPrice(request.getPrice())
                .setUpdated(configService.getTimestamp());
        order = orderRepository.save(order);
        // TODO - can we calculate the effective execution price for the purpose of the margin calculation??
        BigDecimal margin = getInitialMarginRequirement(market, order.getType(), request.getSize(), request.getPrice());
        accountService.allocateMargin(margin, request.getUser(), market.getSettlementAsset());
        return matchOrders(passiveOrders, order, market);
    }

    /**
     * Handle a new limit order (it can cross or be passive)
     *
     * @param request {@link CreateOrderRequest}
     * @param market {@link Market}
     *
     * @return the new {@link Order}
     */
    private Order handleLimitOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        List<Order> openLimitOrders = getOpenLimitOrders(market);
        if(MarketSide.BUY.equals(request.getSide())) {
            Optional<Order> bestOffer = openLimitOrders.stream()
                    .filter(o -> o.getSide().equals(MarketSide.SELL)).min(Comparator.comparing(Order::getPrice));
            if(bestOffer.isEmpty() || request.getPrice().doubleValue() < bestOffer.get().getPrice().doubleValue()) {
                return createLimitOrder(request, market);
            } else {
                return handleCrossingLimitOrder(request, market);
            }
        } else {
            Optional<Order> bestBid = openLimitOrders.stream()
                    .filter(o -> o.getSide().equals(MarketSide.BUY)).max(Comparator.comparing(Order::getPrice));
            if(bestBid.isEmpty() || request.getPrice().doubleValue() > bestBid.get().getPrice().doubleValue()) {
                return createLimitOrder(request, market);
            } else {
                return handleCrossingLimitOrder(request, market);
            }
        }
    }

    /**
     * Create a new {@link Order}
     *
     * @param request {@link CreateOrderRequest}
     *
     * @return the new {@link Order}
     */
    public Order create(
            final CreateOrderRequest request
    ) {
        validateRequest(request);
        Market market = marketService.get(request.getMarketId());
        performMarginCheck(market, request.getType(), request.getSize(), request.getPrice(), request.getUser());
        if(!market.getStatus().equals(MarketStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_ACTIVE);
        }
        if(OrderType.LIMIT.equals(request.getType())) {
            return handleLimitOrder(request, market);
        } else if(OrderType.STOP_MARKET.equals(request.getType())) {
            throw new JynxProException(ErrorCode.STOP_ORDER_NOT_SUPPORTED);
        }
        return createMarketOrder(request, market);
    }

    /**
     * Validate {@link CreateOrderRequest}
     *
     * @param request {@link CreateOrderRequest}
     */
    private void validateRequest(
            final CreateOrderRequest request
    ) {
        if(OrderType.MARKET.equals(request.getType())) {
            request.setPrice(null);
            request.setPostOnly(null);
            request.setReduceOnly(null);
        }
        if(request.getSize() == null) {
            throw new JynxProException(ErrorCode.ORDER_SIZE_MANDATORY);
        }
        if(request.getType() == null) {
            throw new JynxProException(ErrorCode.ORDER_TYPE_MANDATORY);
        }
        if(request.getMarketId() == null) {
            throw new JynxProException(ErrorCode.ORDER_MARKET_MANDATORY);
        }
        if(request.getSide() == null) {
            throw new JynxProException(ErrorCode.ORDER_SIDE_MANDATORY);
        }
        if(request.getPrice() == null && request.getType().equals(OrderType.LIMIT)) {
            throw new JynxProException(ErrorCode.ORDER_PRICE_MANDATORY);
        }
    }

    /**
     * Amend an existing {@link Order}
     *
     * @param request {@link AmendOrderRequest}
     *
     * @return the amended {@link Order}
     */
    public Order amend(
            final AmendOrderRequest request
    ) {
        Order order = orderRepository.findById(request.getId())
                .orElseThrow(() -> new JynxProException(ErrorCode.ORDER_NOT_FOUND));
        int dps = order.getMarket().getSettlementAsset().getDecimalPlaces();
        BigDecimal originalSize = order.getSize();
        BigDecimal originalRemainingSize = order.getRemainingSize();
        BigDecimal originalPrice = order.getPrice();
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
        // TODO - order amend history should be saved
        if(!Objects.isNull(request.getSize()) && !request.getSize().setScale(dps, RoundingMode.HALF_UP)
                .equals(order.getSize().setScale(dps, RoundingMode.HALF_UP))) {
            order.setSize(request.getSize());
            order.setRemainingSize(order.getSize());
            if(request.getSize().doubleValue() > originalSize.doubleValue()) {
                order.setUpdated(configService.getTimestamp());
            }
        }
        if(!Objects.isNull(request.getPrice())) {
            OrderBook orderBook = getOrderBook(order.getMarket());
            if(order.getSide().equals(MarketSide.BUY) && orderBook.getAsks().size() > 0 &&
                    orderBook.getAsks().get(0).getPrice().doubleValue() < request.getPrice().doubleValue()) {
                throw new JynxProException(ErrorCode.CANNOT_AMEND_WOULD_EXECUTE);
            }
            if(order.getSide().equals(MarketSide.SELL) && orderBook.getBids().size() > 0 &&
                    orderBook.getBids().get(0).getPrice().doubleValue() > request.getPrice().doubleValue()) {
                throw new JynxProException(ErrorCode.CANNOT_AMEND_WOULD_EXECUTE);
            }
            order.setPrice(request.getPrice());
        }
        BigDecimal marginToRelease = getInitialMarginRequirement(order.getMarket(), order.getType(),
                originalRemainingSize, originalPrice);
        BigDecimal marginToAllocate = getInitialMarginRequirement(order.getMarket(), order.getType(),
                order.getRemainingSize(), order.getPrice());
        accountService.releaseMargin(marginToRelease, order.getUser(), order.getMarket().getSettlementAsset());
        try {
            performMarginCheck(order.getMarket(), order.getType(), order.getSize(), order.getPrice(), order.getUser());
            accountService.allocateMargin(marginToAllocate, order.getUser(), order.getMarket().getSettlementAsset());
        } catch(JynxProException e) {
            accountService.allocateMargin(marginToRelease, order.getUser(), order.getMarket().getSettlementAsset());
            throw e;
        }
        return orderRepository.save(order);
    }

    /**
     * Create many {@link Order}s in a single request
     *
     * @param requests list of {@link CreateOrderRequest}
     *
     * @return list of {@link Order}
     */
    public List<Order> createMany(
            final List<CreateOrderRequest> requests
    ) {
        if(requests.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return requests.stream().map(this::create).collect(Collectors.toList());
    }

    /**
     * Amend many {@link Order}s in a single request
     *
     * @param requests list of {@link AmendOrderRequest}
     *
     * @return list of {@link Order}
     */
    public List<Order> amendMany(
            final List<AmendOrderRequest> requests
    ) {
        if(requests.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return requests.stream().map(this::amend).collect(Collectors.toList());
    }

    /**
     * Cancel many {@link Order}s in a single request
     *
     * @param requests list of {@link CancelOrderRequest}
     *
     * @return list of {@link Order}
     */
    public List<Order> cancelMany(
            final List<CancelOrderRequest> requests
    ) {
        if(requests.size() > MAX_BULK) {
            throw new JynxProException(ErrorCode.MAX_BULK_EXCEEDED);
        }
        return requests.stream().map(this::cancel).collect(Collectors.toList());
    }

    /**
     * Get the initial margin requirement for a new {@link Order}
     *
     * @param market the {@link Market}
     * @param type the {@link OrderType}
     * @param size the size of the order
     * @param price the price of the order
     *
     * @return the margin requirement
     */
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
        } else {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Check if a {@link User} has sufficient balance to create a new {@link Order}
     *
     * @param market the {@link Market}
     * @param type the {@link OrderType}
     * @param size the size of the order
     * @param price the price of the order
     * @param user the {@link User}
     */
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