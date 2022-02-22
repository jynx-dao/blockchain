package com.jynx.pro.service;

import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.repository.OrderHistoryRepository;
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
    private UUIDUtils uuidUtils;
    @Autowired
    private ConfigService configService;
    @Autowired
    private OrderHistoryRepository orderHistoryRepository;

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
            return market.getLastPrice();
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
                .sorted(Comparator.comparing(Order::getPrice).thenComparing(Order::getPriority))
                .collect(Collectors.toList());
        if(side.equals(MarketSide.BUY)) {
            orders.sort(Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getPriority));
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
        order.setStatus(OrderStatus.CANCELED);
        order = orderRepository.save(order);
        BigDecimal margin = getMarginRequirement(order.getMarket(), order.getUser());
        accountService.allocateMargin(margin, order.getUser(), order.getMarket().getSettlementAsset());
        OrderHistory orderHistory = new OrderHistory()
                .setOrder(order)
                .setId(uuidUtils.next())
                .setAction(OrderAction.CANCEL)
                .setUpdated(configService.getTimestamp());
        orderHistoryRepository.save(orderHistory);
        return order;
    }

    /**
     * Calculate the priority for new limit orders
     *
     * @param market {@link Market}
     * @param side {@link MarketSide}
     * @param price the limit order price
     *
     * @return the priority of the order
     */
    private int getLimitOrderPriority(
            final Market market,
            final MarketSide side,
            final BigDecimal price
    ) {
        List<OrderStatus> statusList = Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
        return orderRepository.countByMarketAndPriceAndSideAndStatusInAndType(market, price,
                side, statusList, OrderType.LIMIT);
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
                .setTag(request.getTag())
                .setId(uuidUtils.next())
                .setMarket(market)
                .setUser(request.getUser())
                .setPrice(request.getPrice())
                .setSide(request.getSide())
                .setSize(request.getSize())
                .setRemainingSize(request.getSize())
                .setStatus(OrderStatus.OPEN)
                .setType(request.getType())
                .setPriority(getLimitOrderPriority(market, request.getSide(), request.getPrice()));
        BigDecimal margin = getMarginRequirementWithNewOrder(market, order.getSide(), request.getSize(),
                request.getPrice(), request.getUser());
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
        int dps = market.getSettlementAsset().getDecimalPlaces();
        BigDecimal price = BigDecimal.ZERO;
        for(Order passiveOrder : passiveOrders) {
            price = passiveOrder.getPrice();
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
            Trade trade = tradeService.save(market, passiveOrder, order, price, size, order.getSide());
            positionService.update(market, price, size, maker, getOtherSide(order.getSide()));
            positionService.update(market, price, size, taker, order.getSide());
            OrderHistory passiveOrderHistory = new OrderHistory()
                    .setOrder(passiveOrder)
                    .setId(uuidUtils.next())
                    .setTrade(trade)
                    .setAction(OrderAction.FILL)
                    .setUpdated(configService.getTimestamp());
            OrderHistory aggressiveOrderHistory = new OrderHistory()
                    .setOrder(order)
                    .setTrade(trade)
                    .setId(uuidUtils.next())
                    .setAction(OrderAction.FILL)
                    .setUpdated(configService.getTimestamp());
            orderHistoryRepository.save(passiveOrderHistory);
            orderHistoryRepository.save(aggressiveOrderHistory);
            if(order.getRemainingSize().equals(BigDecimal.ZERO)) {
                order.setStatus(OrderStatus.FILLED);
                break;
            }
        }
        orderRepository.saveAll(passiveOrders);
        Order takerOrder = orderRepository.save(order);
        BigDecimal markPrice = getMidPrice(market);
        if(!markPrice.setScale(dps, RoundingMode.HALF_UP)
                .equals(market.getMarkPrice().setScale(dps, RoundingMode.HALF_UP))) {
            // TODO - how does the mark price get updated when the book moves without any trades happening??
            marketService.updateLastPrice(price, market);
            executeStopOrders(market);
            positionService.executeLiquidations(markPrice, market);
            positionService.updatePassiveLiquidity(market);
        }
        return takerOrder;
    }

    /**
     * Check if a stop-loss order is triggered
     *
     * @param order stop-loss {@link Order}
     * @param price the price used to trigger the stop execution
     *
     * @return true / false
     */
    private boolean isStopTriggered(
            final Order order,
            final BigDecimal price
    ) {
        return (order.getSide().equals(MarketSide.SELL) &&
                order.getPrice().doubleValue() < price.doubleValue()) ||
                (order.getSide().equals(MarketSide.BUY) &&
                    order.getPrice().doubleValue() > price.doubleValue());
    }

    /**
     * Execute all stop-loss orders
     *
     * @param market {@link Market}
     */
    private void executeStopOrders(
            final Market market
    ) {
        List<Order> stopOrders = orderRepository.findByStatusInAndTypeAndMarket(
                List.of(OrderStatus.OPEN), OrderType.STOP_MARKET, market);
        for(Order order : stopOrders) {
            BigDecimal triggerPrice = order.getStopTrigger().equals(StopTrigger.LAST_PRICE) ?
                    market.getLastPrice() : market.getMarkPrice();
            if(isStopTriggered(order, triggerPrice)) {
                order = createMarketOrder(null, market, order);
                reconcileBalanceAfterStopOrder(order, market);
            }
        }
    }

    /**
     * Reconcile the user balance after a stop-loss order executes
     *
     * @param order {@link Order}
     * @param market {@link Market}
     */
    private void reconcileBalanceAfterStopOrder(
            final Order order,
            final Market market
    ) {
        Account account = accountService.getAndCreate(order.getUser(), market.getSettlementAsset());
        if(account.getBalance().doubleValue() < 0) {
            if(market.getInsuranceFund().doubleValue() < account.getBalance().abs().doubleValue()) {
                positionService.claimLossBySocialization(market, account);
            } else {
                positionService.claimLossFromInsuranceFund(market, account);
            }
        }
    }

    /**
     * Create a market order
     *
     * @param request {@link CreateOrderRequest}
     * @param market {@link Market}
     * @param orderOverride {@link Order} to override when executing a stop-loss
     *
     * @return the executed {@link Order}
     */
    private Order createMarketOrder(
            final CreateOrderRequest request,
            final Market market,
            final Order orderOverride
    ) {
        MarketSide side = orderOverride != null ? orderOverride.getSide() : request.getSide();
        User user = orderOverride != null ? orderOverride.getUser() : request.getUser();
        BigDecimal price = orderOverride != null ? orderOverride.getPrice() : request.getPrice();
        BigDecimal size = orderOverride != null ? orderOverride.getSize() : request.getSize();
        List<Order> passiveOrders = getSideOfBook(market, getOtherSide(side));
        double passiveVolume = passiveOrders.stream().mapToDouble(o -> o.getRemainingSize().doubleValue()).sum();
        Order order;
        if(orderOverride != null) {
            order = orderOverride;
            order.setStatus(OrderStatus.FILLED);
        } else {
            order = new Order()
                    .setTag(request.getTag())
                    .setType(OrderType.MARKET)
                    .setSide(side)
                    .setMarket(market)
                    .setStatus(OrderStatus.FILLED)
                    .setSize(size)
                    .setRemainingSize(size)
                    .setId(uuidUtils.next())
                    .setUser(user)
                    .setPriority(0);
        }
        if(size.doubleValue() > passiveVolume) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectedReason(ErrorCode.INSUFFICIENT_PASSIVE_VOLUME);
            orderRepository.save(order);
            throw new JynxProException(ErrorCode.INSUFFICIENT_PASSIVE_VOLUME);
        }
        order = orderRepository.save(order);
        BigDecimal margin = getMarginRequirementWithNewOrder(market, side, size, price, user);
        accountService.allocateMargin(margin, user, market.getSettlementAsset());
        return matchOrders(passiveOrders, order, market);
    }

    /**
     * Create a market order
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
        return createMarketOrder(request, market, null);
    }

    /**
     * Create a new stop-loss order
     *
     * @param request {@link CreateOrderRequest}
     * @param market {@link Market}
     *
     * @return {@link Order}
     */
    private Order handleStopOrder(
            final CreateOrderRequest request,
            final Market market
    ) {
        Order order = new Order()
                .setUser(request.getUser())
                .setId(uuidUtils.next())
                .setStatus(OrderStatus.OPEN)
                .setType(OrderType.STOP_MARKET)
                .setSide(request.getSide())
                .setMarket(market)
                .setSize(request.getSize())
                .setRemainingSize(request.getSize())
                .setPrice(request.getPrice())
                .setPriority(1)
                .setTag(OrderTag.USER_GENERATED)
                .setStopTrigger(request.getStopTrigger());
        order = orderRepository.save(order);
        BigDecimal triggerPrice = request.getStopTrigger().equals(StopTrigger.LAST_PRICE) ?
                market.getLastPrice() : market.getMarkPrice();
        if(isStopTriggered(order, triggerPrice)) {
            order = createMarketOrder(null, market, order);
            reconcileBalanceAfterStopOrder(order, market);
        }
        return order;
    }

    /**
     * Handles limit orders that cross with the other side of the book
     *
     * @param request {@link CreateOrderRequest}
     * @param market {@link Market}
     *
     * @return the new {@link Order}
     */
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
                .setTag(request.getTag())
                .setUser(request.getUser())
                .setId(uuidUtils.next())
                .setStatus(OrderStatus.OPEN)
                .setType(OrderType.LIMIT)
                .setSide(request.getSide())
                .setMarket(market)
                .setSize(request.getSize())
                .setRemainingSize(request.getSize())
                .setPrice(request.getPrice())
                .setPriority(getLimitOrderPriority(market, request.getSide(), request.getPrice()));
        order = orderRepository.save(order);
        BigDecimal margin = getMarginRequirementWithNewOrder(market, order.getSide(), request.getSize(),
                request.getPrice(), request.getUser());
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
     * @param skipMarginCheck this must ONLY be used when liquidating distressed positions
     *
     * @return the new {@link Order}
     */
    public Order create(
            final CreateOrderRequest request,
            final boolean skipMarginCheck
    ) {
        validate(request);
        Market market = marketService.get(request.getMarketId());
        if(!skipMarginCheck) {
            performMarginCheck(market, request.getSide(), request.getSize(), request.getPrice(), request.getUser());
        } else {
            if(!request.getType().equals(OrderType.MARKET)) {
                performMarginCheck(market, request.getSide(), request.getSize(), request.getPrice(), request.getUser());
            }
        }
        if(!market.getStatus().equals(MarketStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_ACTIVE);
        }
        Order order;
        if(OrderType.LIMIT.equals(request.getType())) {
            order = handleLimitOrder(request, market);
        } else if(OrderType.STOP_MARKET.equals(request.getType())) {
            order = handleStopOrder(request, market);
        } else {
            order = createMarketOrder(request, market);
        }
        OrderHistory orderHistory = new OrderHistory()
                .setOrder(order)
                .setId(uuidUtils.next())
                .setAction(OrderAction.CREATE)
                .setUpdated(configService.getTimestamp());
        orderHistoryRepository.save(orderHistory);
        return order;
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
        return create(request, false);
    }

    /**
     * Validate {@link CreateOrderRequest}
     *
     * @param request {@link CreateOrderRequest}
     */
    private void validate(
            final CreateOrderRequest request
    ) {
        // TODO - we need to check more things here for stop-loss orders
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
        if(request.getSize().doubleValue() < 0) {
            throw new JynxProException(ErrorCode.NEGATIVE_SIZE);
        }
        if(request.getPrice() != null && request.getPrice().doubleValue() < 0) {
            throw new JynxProException(ErrorCode.NEGATIVE_PRICE);
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
        if(!Objects.isNull(request.getSize()) && !request.getSize().setScale(dps, RoundingMode.HALF_UP)
                .equals(order.getSize().setScale(dps, RoundingMode.HALF_UP))) {
            order.setSize(request.getSize());
            order.setRemainingSize(order.getSize());
            if(request.getSize().doubleValue() > originalSize.doubleValue()) {
                order.setPriority(getLimitOrderPriority(order.getMarket(), order.getSide(), order.getPrice()));
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
        if(order.getPrice().doubleValue() > originalPrice.doubleValue() ||
                order.getRemainingSize().doubleValue() > originalSize.doubleValue()) {
            BigDecimal deltaSize = order.getRemainingSize().subtract(originalSize).max(BigDecimal.ZERO);
            performMarginCheck(order.getMarket(), order.getSide(), deltaSize,
                    order.getPrice(), order.getUser());
        }
        BigDecimal originalMargin = getMarginRequirement(order.getMarket(), request.getUser());
        BigDecimal combinedMargin = getMarginRequirementWithNewOrder(order.getMarket(), order.getSide(),
                order.getRemainingSize(), order.getPrice(), request.getUser());
        BigDecimal newMargin = combinedMargin.subtract(originalMargin);
        order = orderRepository.save(order);
        accountService.allocateMargin(newMargin, order.getUser(), order.getMarket().getSettlementAsset());
        OrderHistory orderHistory = new OrderHistory()
                .setOrder(order)
                .setId(uuidUtils.next())
                .setAction(OrderAction.AMEND)
                .setFromPrice(originalPrice)
                .setFromSize(originalSize)
                .setToPrice(order.getPrice())
                .setToSize(order.getSize())
                .setUpdated(configService.getTimestamp());
        orderHistoryRepository.save(orderHistory);
        return order;
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
     * Get the margin requirement for a {@link User}
     *
     * @param market the {@link Market}
     * @param user the {@link User}
     *
     * @return the margin requirement
     */
    public BigDecimal getMarginRequirement(
            final Market market,
            final User user
    ) {
        return getMarginRequirementWithNewOrder(market, MarketSide.BUY, BigDecimal.ONE, BigDecimal.ZERO, user);
    }

    /**
     * Get the margin requirement for a {@link User} when executing a new {@link Order}
     *
     * @param market the {@link Market}
     * @param side the {@link MarketSide}
     * @param size the size of the order
     * @param price the price of the order
     * @param user the {@link User}
     *
     * @return the margin requirement
     */
    public BigDecimal getMarginRequirementWithNewOrder(
            final Market market,
            final MarketSide side,
            final BigDecimal size,
            final BigDecimal price,
            final User user
    ) {
        Position position = positionService.getAndCreate(user, market);
        BigDecimal maintenanceMargin = position.getSize().multiply(position.getAverageEntryPrice())
                .multiply(market.getMarginRequirement());
        List<OrderStatus> statusList = Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
        List<Order> openLimitOrders = orderRepository.findByStatusInAndTypeAndMarketAndUser(
                statusList, OrderType.LIMIT, market, user);
        List<Order> openStopOrders = orderRepository.findByStatusInAndTypeAndMarketAndUser(
                statusList, OrderType.STOP_MARKET, market, user);
        List<Order> buyLimitOrders = openLimitOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.BUY))
                .sorted(Comparator.comparing(Order::getPrice).reversed())
                .collect(Collectors.toList());
        List<Order> sellLimitOrders = openLimitOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.SELL))
                .sorted(Comparator.comparing(Order::getPrice))
                .collect(Collectors.toList());
        List<Order> buyStopOrders = openStopOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.BUY))
                .sorted(Comparator.comparing(Order::getPrice).reversed())
                .collect(Collectors.toList());
        List<Order> sellStopOrders = openStopOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.SELL))
                .sorted(Comparator.comparing(Order::getPrice))
                .collect(Collectors.toList());
        List<Order> buyOrders = new ArrayList<>();
        List<Order> sellOrders = new ArrayList<>();
        buyOrders.addAll(buyLimitOrders);
        buyOrders.addAll(buyStopOrders);
        sellOrders.addAll(sellLimitOrders);
        sellOrders.addAll(sellStopOrders);
        BigDecimal marginPrice = Objects.isNull(price) ? getMidPrice(market) : price;
        BigDecimal newSize = getEffectiveNewSize(position, side, size);
        BigDecimal newInitialMargin = marginPrice.multiply(newSize).multiply(market.getMarginRequirement());
        BigDecimal buyInitialMargin = getMarginFromOpenOrders(buyOrders, position, market, MarketSide.SELL);
        BigDecimal sellInitialMargin = getMarginFromOpenOrders(sellOrders, position, market, MarketSide.BUY);
        if(side.equals(MarketSide.BUY)) {
            buyInitialMargin = buyInitialMargin.add(newInitialMargin);
        } else {
            sellInitialMargin = sellInitialMargin.add(newInitialMargin);
        }
        BigDecimal initialMargin = buyInitialMargin.max(sellInitialMargin);
        BigDecimal unrealisedProfitMargin = position.getUnrealisedPnl().min(BigDecimal.ZERO).abs();
        unrealisedProfitMargin = unrealisedProfitMargin.subtract(maintenanceMargin).max(BigDecimal.ZERO);
        return initialMargin.add(maintenanceMargin).add(unrealisedProfitMargin);
    }

    /**
     * Calculates the size to use for margin calculations on new orders after considering open volume
     *
     * @param position {@link Position}
     * @param side {@link MarketSide}
     * @param size order size
     *
     * @return the effective size
     */
    private BigDecimal getEffectiveNewSize(
            final Position position,
            final MarketSide side,
            final BigDecimal size
    ) {
        BigDecimal newSize = size;
        if(!Objects.isNull(position.getSide()) && position.getSide().equals(getOtherSide(side))) {
            BigDecimal sizeDelta = position.getSize().subtract(size);
            if(sizeDelta.doubleValue() < 0) {
                newSize = sizeDelta.abs();
            } else {
                newSize = BigDecimal.ZERO;
            }
        }
        return newSize;
    }

    /**
     * Calculates the margin to hold on open orders after considering open volume
     *
     * @param openOrders a list of open {@link Order}s
     * @param position {@link Position}
     * @param market {@link Market}
     * @param side {@link MarketSide}
     *
     * @return the margin to hold
     */
    private BigDecimal getMarginFromOpenOrders(
            final List<Order> openOrders,
            final Position position,
            final Market market,
            final MarketSide side
    ) {
        BigDecimal positionAllocation = position.getSize();
        BigDecimal initialMargin = BigDecimal.ZERO;
        for(Order order : openOrders) {
            if(position.getSide() != null && position.getSide().equals(side) &&
                    positionAllocation.doubleValue() > BigDecimal.ZERO.doubleValue()) {
                if(order.getRemainingSize().doubleValue() < positionAllocation.doubleValue()) {
                    positionAllocation = positionAllocation.subtract(order.getRemainingSize());
                } else {
                    BigDecimal diff = order.getRemainingSize().subtract(positionAllocation);
                    positionAllocation = BigDecimal.ZERO;
                    initialMargin = initialMargin.add(order.getPrice()
                            .multiply(diff.multiply(market.getMarginRequirement())));
                }
            } else {
                initialMargin = initialMargin.add(order.getPrice()
                        .multiply(order.getRemainingSize().multiply(market.getMarginRequirement())));
            }
        }
        return initialMargin;
    }

    /**
     * Check if a {@link User} has sufficient balance to create a new {@link Order}
     *
     * @param market the {@link Market}
     * @param side the {@link MarketSide}
     * @param size the size of the order
     * @param price the price of the order
     * @param user the {@link User}
     */
    private void performMarginCheck(
            final Market market,
            final MarketSide side,
            final BigDecimal size,
            final BigDecimal price,
            final User user
    ) {
        positionService.getAndCreate(user, market);
        Account account = accountService.getAndCreate(user, market.getSettlementAsset());
        BigDecimal margin = getMarginRequirementWithNewOrder(market, side, size, price, user);
        if(account.getBalance().doubleValue() < margin.doubleValue()) {
            throw new JynxProException(ErrorCode.INSUFFICIENT_MARGIN);
        }
    }
}