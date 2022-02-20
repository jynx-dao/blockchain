package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.constant.TransactionType;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.request.CancelOrderRequest;
import com.jynx.pro.request.CreateOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class OrderServiceTest extends IntegrationTest {

    @Autowired
    private OrderService orderService;

    @BeforeEach
    public void setup() {
        initializeState();
    }

    @AfterEach
    public void shutdown() {
        clearState();
    }

    private CreateOrderRequest getCreateOrderRequest(
            final UUID marketId,
            final BigDecimal price,
            final BigDecimal size,
            final MarketSide side,
            final OrderType type,
            final User user
    ) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPrice(price);
        request.setSide(side);
        request.setSize(size);
        request.setUser(user);
        request.setMarketId(marketId);
        request.setType(type);
        return request;
    }

    private Market createOrderBook(
            final int bids,
            final int asks
    ) throws InterruptedException {
        Market market = createAndEnactMarket(true);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        for(int i=0; i<bids; i++) {
            Order buyOrder = orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45590-i), BigDecimal.ONE, MarketSide.BUY, OrderType.LIMIT, makerUser));
            Assertions.assertEquals(buyOrder.getStatus(), OrderStatus.OPEN);
        }
        for(int i=0; i<asks; i++) {
            Order sellOrder = orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45610+i), BigDecimal.ONE, MarketSide.SELL, OrderType.LIMIT, makerUser));
            Assertions.assertEquals(sellOrder.getStatus(), OrderStatus.OPEN);
        }

        OrderBook orderBook = orderService.getOrderBook(market);
        Assertions.assertEquals(orderBook.getAsks().size(), asks);
        Assertions.assertEquals(orderBook.getBids().size(), bids);
        BigDecimal bidMargin = BigDecimal.ZERO;
        BigDecimal askMargin = BigDecimal.ZERO;
        for(int i=0; i<bids; i++) {
            OrderBookItem item = orderBook.getBids().get(i);
            Assertions.assertEquals(orderBook.getBids().get(i).getPrice().setScale(dps, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(45590-i).setScale(dps, RoundingMode.HALF_UP));
            bidMargin = bidMargin.add(orderService
                    .getInitialMarginRequirement(market, OrderType.LIMIT, item.getSize(), item.getPrice()));
        }
        for(int i=0; i<asks; i++) {
            OrderBookItem item = orderBook.getAsks().get(i);
            Assertions.assertEquals(orderBook.getAsks().get(i).getPrice().setScale(dps, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(45610+i).setScale(dps, RoundingMode.HALF_UP));
            askMargin = askMargin.add(orderService
                    .getInitialMarginRequirement(market, OrderType.LIMIT, item.getSize(), item.getPrice()));
        }
        BigDecimal marginBalance = bidMargin.add(askMargin);
        BigDecimal startingBalance = BigDecimal.valueOf(1000000);
        BigDecimal availableBalance = startingBalance.subtract(marginBalance);
        Optional<Account> accountOptional = accountRepository
                .findByUserAndAsset(makerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                availableBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                marginBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                startingBalance.setScale(dps, RoundingMode.HALF_UP));
        return market;
    }

    @Test
    public void cannotGetMidPriceWithEmptyAsk() throws InterruptedException {
        Market market = createAndEnactMarket(true);
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45590), BigDecimal.ONE, MarketSide.BUY, OrderType.LIMIT, makerUser));
        try {
            orderService.getMidPrice(market);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.EMPTY_ORDER_BOOK);
        }
    }

    @Test
    public void cannotGetMidPriceWithEmptyBid() throws InterruptedException {
        Market market = createAndEnactMarket(true);
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45610), BigDecimal.ONE, MarketSide.SELL, OrderType.LIMIT, makerUser));
        try {
            orderService.getMidPrice(market);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.EMPTY_ORDER_BOOK);
        }
    }

    @Test
    public void createLimitOrder() throws InterruptedException {
        createOrderBook(2, 2);
    }

    @Test
    public void createOrderFailedWithInsufficientMargin() throws InterruptedException {
        Market market = createAndEnactMarket(true);
        try {
            orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45590), BigDecimal.valueOf(1000), MarketSide.BUY, OrderType.LIMIT, makerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INSUFFICIENT_MARGIN);
        }
    }

    @Test
    public void createOrderFailedWhenMarketInactive() throws InterruptedException {
        Market market = createAndEnactMarket(false);
        try {
            orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45590), BigDecimal.valueOf(1), MarketSide.BUY, OrderType.LIMIT, makerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MARKET_NOT_ACTIVE);
        }
    }

    @Test
    public void createOrderFailedWithInvalidType() throws InterruptedException {
        Market market = createAndEnactMarket(true);
        try {
            orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45590), BigDecimal.valueOf(1), MarketSide.BUY, null, makerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_TYPE);
        }
    }

    @Test
    public void cancelOrder() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        List<Order> orders = orderService.getOpenLimitOrders(market);
        for(Order order : orders) {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(makerUser);
            request.setId(order.getId());
            orderService.cancel(request);
            Order cancelledOrder = orderRepository.findById(order.getId()).orElse(new Order());
            Assertions.assertEquals(cancelledOrder.getStatus(), OrderStatus.CANCELLED);
        }
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Optional<Account> accountOptional = accountRepository
                .findByUserAndAsset(makerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(1000000).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(1000000).setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void createMarketOrderBuy() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        BigDecimal midPrice = orderService.getMidPrice(market);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        validateMarketState(market.getId(), BigDecimal.valueOf(0.5), BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590), BigDecimal.valueOf(45610), BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.5), 1, 1, MarketSide.SELL, MarketSide.BUY, 1, List.of(midPrice));
    }

    @Test
    public void createCrossingLimitOrderBuy() throws InterruptedException {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45611), BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.LIMIT, takerUser));
        validateMarketState(market.getId(), BigDecimal.valueOf(0.5), BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590), BigDecimal.valueOf(45610), BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.5), 3, 3, MarketSide.SELL, MarketSide.BUY, 1, List.of(BigDecimal.valueOf(45611)));
    }

    @Test
    public void createMarketOrderSell() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        BigDecimal midPrice = orderService.getMidPrice(market);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(0.5), MarketSide.SELL, OrderType.MARKET, takerUser));
        validateMarketState(market.getId(), BigDecimal.valueOf(0.5), BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45590), BigDecimal.valueOf(45610), BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(1), 1, 1, MarketSide.BUY, MarketSide.SELL, 1, List.of(midPrice));
    }

    @Test
    public void createCrossingLimitOrderSell() throws InterruptedException {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45589), BigDecimal.valueOf(0.5), MarketSide.SELL, OrderType.LIMIT, takerUser));
        validateMarketState(market.getId(), BigDecimal.valueOf(0.5), BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45590), BigDecimal.valueOf(45610), BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(1), 3, 3, MarketSide.BUY, MarketSide.SELL, 1, List.of(BigDecimal.valueOf(45589)));
    }

    @Test
    public void createCrossingLimitOrderFailsWithPostOnlyTrue() throws InterruptedException {
        Market market = createOrderBook(3, 3);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45589), BigDecimal.valueOf(0.5),
                    MarketSide.SELL, OrderType.LIMIT, takerUser).setPostOnly(true));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.POST_ONLY_FAILED);
        }
    }

    @Test
    public void createMarketOrderFailsWithInsufficientPassiveVolume() throws InterruptedException {
        Market market = createOrderBook(3, 3);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(4),
                    MarketSide.SELL, OrderType.MARKET, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INSUFFICIENT_PASSIVE_VOLUME);
        }
    }

    @Test
    public void cancelOrderFailsWithUnsupportedType() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.ONE, BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        try {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(takerUser);
            request.setId(order.getId());
            orderService.cancel(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_TYPE);
        }
    }

    @Test
    public void cancelOrderFailsWithInvalidPermission() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        try {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(makerUser);
            request.setId(order.getId());
            orderService.cancel(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.PERMISSION_DENIED);
        }
    }

    @Test
    public void cancelOrderFailsWithInvalidID(){
        try {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(makerUser);
            request.setId(UUID.randomUUID());
            orderService.cancel(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Test
    public void cancelOrderFailsWithStatusFilled() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(order -> order.getSide().equals(MarketSide.SELL)).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 1);
        Order orderToCancel = orders.get(0);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(1), MarketSide.BUY, OrderType.MARKET, takerUser));
        try {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(makerUser);
            request.setId(orderToCancel.getId());
            orderService.cancel(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    @Test
    public void createOrderFailsWithInvalidType() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(),
                    null, BigDecimal.valueOf(0.5), MarketSide.BUY, null, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_TYPE);
        }
    }

    @Test
    public void testBulkInstructions() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        CreateOrderRequest createOrderRequest = getCreateOrderRequest(market.getId(), BigDecimal.valueOf(5),
                BigDecimal.ONE, MarketSide.BUY, OrderType.LIMIT, makerUser);
        List<CreateOrderRequest> bulkCreateRequest = Arrays.asList(createOrderRequest, createOrderRequest);
        List<Order> orders = orderService.createMany(bulkCreateRequest);
        orders.forEach(o -> Assertions.assertEquals(o.getStatus(), OrderStatus.OPEN));
        List<CancelOrderRequest> bulkCancelRequest = Arrays.asList(new CancelOrderRequest(), new CancelOrderRequest());
        for(int i=0; i< orders.size(); i++) {
            bulkCancelRequest.get(i).setId(orders.get(i).getId());
            bulkCancelRequest.get(i).setUser(makerUser);
        }
        orders = orderService.cancelMany(bulkCancelRequest);
        orders.forEach(o -> Assertions.assertEquals(o.getStatus(), OrderStatus.CANCELLED));
    }

    @Test
    public void testBulkFailsWhenExceedsLimit() {
        try {
            List<CreateOrderRequest> request = new ArrayList<>();
            for(int i=0; i<30; i++) {
                request.add(new CreateOrderRequest());
            }
            orderService.createMany(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MAX_BULK_EXCEEDED);
        }
        try {
            List<CancelOrderRequest> request = new ArrayList<>();
            for(int i=0; i<30; i++) {
                request.add(new CancelOrderRequest());
            }
            orderService.cancelMany(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MAX_BULK_EXCEEDED);
        }
    }

    @Test
    public void testCreateOrderFailsWithMissingSide() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(5),
                    BigDecimal.ONE, null, OrderType.LIMIT, makerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.UNKNOWN_MARKET_SIDE);
        }
    }

    private void validateMarketState(
            final UUID marketId,
            final BigDecimal size,
            final BigDecimal avgEntryPrice,
            final BigDecimal bidPrice,
            final BigDecimal askPrice,
            final BigDecimal bidSize,
            final BigDecimal askSize,
            final int bidCount,
            final int askCount,
            final MarketSide makerSide,
            final MarketSide takerSide,
            final int tradeCount,
            final List<BigDecimal> midPriceAtTrade
    ) {
        Market market = marketRepository.getOne(marketId);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Optional<Position> positionOptionalMaker = positionRepository.findByUserAndMarket(makerUser, market);
        Optional<Position> positionOptionalTaker = positionRepository.findByUserAndMarket(takerUser, market);
        Assertions.assertTrue(positionOptionalMaker.isPresent());
        Assertions.assertTrue(positionOptionalTaker.isPresent());
        Position positionMaker = positionOptionalMaker.get();
        Position positionTaker = positionOptionalTaker.get();
        Assertions.assertEquals(positionMaker.getSize().setScale(dps, RoundingMode.HALF_UP),
                size.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(positionTaker.getSize().setScale(dps, RoundingMode.HALF_UP),
                size.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(positionMaker.getAverageEntryPrice().setScale(dps, RoundingMode.HALF_UP),
                avgEntryPrice.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(positionTaker.getAverageEntryPrice().setScale(dps, RoundingMode.HALF_UP),
                avgEntryPrice.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(positionMaker.getSide(), makerSide);
        Assertions.assertEquals(positionTaker.getSide(), takerSide);
        Assertions.assertEquals(market.getOpenVolume().setScale(dps, RoundingMode.HALF_UP),
                size.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(market.getLastPrice().setScale(dps, RoundingMode.HALF_UP),
                avgEntryPrice.setScale(dps, RoundingMode.HALF_UP));
        List<Transaction> makerTxns = transactionRepository.findByUserAndAsset(makerUser, market.getSettlementAsset())
                .stream().filter(t -> t.getType().equals(TransactionType.FEE)).collect(Collectors.toList());
        List<Transaction> takerTxns = transactionRepository.findByUserAndAsset(takerUser, market.getSettlementAsset())
                .stream().filter(t -> t.getType().equals(TransactionType.FEE)).collect(Collectors.toList());
        Assertions.assertEquals(makerTxns.size(), 1);
        Assertions.assertEquals(takerTxns.size(), 1);
        BigDecimal makerFee = avgEntryPrice.multiply(size).multiply(market.getMakerFee());
        BigDecimal takerFee = avgEntryPrice.multiply(size).multiply(market.getTakerFee()).multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = (market.getTakerFee().subtract(market.getMakerFee())).multiply(avgEntryPrice).multiply(size);
        Assertions.assertEquals(makerTxns.get(0).getAmount().setScale(dps, RoundingMode.HALF_UP),
                makerFee.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(makerTxns.get(0).getType(), TransactionType.FEE);
        Assertions.assertEquals(takerTxns.get(0).getAmount().setScale(dps, RoundingMode.HALF_UP),
                takerFee.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(takerTxns.get(0).getType(), TransactionType.FEE);
        Optional<Asset> assetOptional = assetRepository.findById(market.getSettlementAsset().getId());
        Assertions.assertTrue(assetOptional.isPresent());
        Assertions.assertEquals(assetOptional.get().getTreasuryBalance().setScale(dps, RoundingMode.HALF_UP),
                treasuryFee.setScale(dps, RoundingMode.HALF_UP));
        OrderBook orderBook = orderService.getOrderBook(market);
        Assertions.assertEquals(orderBook.getBids().size(), bidCount);
        Assertions.assertEquals(orderBook.getAsks().size(), askCount);
        Assertions.assertEquals(orderBook.getBids().get(0).getSize().setScale(dps, RoundingMode.HALF_UP),
                bidSize.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orderBook.getAsks().get(0).getSize().setScale(dps, RoundingMode.HALF_UP),
                askSize.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice().setScale(dps, RoundingMode.HALF_UP),
                bidPrice.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().setScale(dps, RoundingMode.HALF_UP),
                askPrice.setScale(dps, RoundingMode.HALF_UP));
        List<Trade> makerTrades = tradeRepository.findByMakerOrderUserAndMarket(makerUser, market)
                .stream().sorted(Comparator.comparing(Trade::getExecuted).reversed()).collect(Collectors.toList());
        List<Trade> takerTrades = tradeRepository.findByTakerOrderUserAndMarket(takerUser, market)
                .stream().sorted(Comparator.comparing(Trade::getExecuted).reversed()).collect(Collectors.toList());
        Assertions.assertEquals(makerTrades.size(), takerTrades.size());
        Assertions.assertEquals(makerTrades.size(), tradeCount);
        Assertions.assertEquals(makerTrades.get(0).getId(), takerTrades.get(0).getId());
        BigDecimal takerMarginBalance = BigDecimal.ZERO;
        BigDecimal makerMarginBalance = BigDecimal.ZERO;
        for(int i=0; i<tradeCount; i++) {
            Trade trade = makerTrades.get(i);
            takerMarginBalance = takerMarginBalance.add(orderService
                    .getInitialMarginRequirement(market, OrderType.LIMIT, trade.getSize(), midPriceAtTrade.get(i)));
        }
        for(int i=0; i<tradeCount; i++) {
            Trade trade = makerTrades.get(i);
            makerMarginBalance = makerMarginBalance.add(orderService
                    .getInitialMarginRequirement(market, OrderType.LIMIT, trade.getSize(), trade.getPrice()));
        }
        for(OrderBookItem item : orderBook.getBids()) {
            makerMarginBalance = makerMarginBalance.add(orderService.getInitialMarginRequirement(
                    market, OrderType.LIMIT, item.getSize(), item.getPrice()));
        }
        for(OrderBookItem item : orderBook.getAsks()) {
            makerMarginBalance = makerMarginBalance.add(orderService.getInitialMarginRequirement(
                    market, OrderType.LIMIT, item.getSize(), item.getPrice()));
        }
        BigDecimal takerStartingBalance = BigDecimal.valueOf(1000000).add(takerFee);
        BigDecimal takerAvailableBalance = takerStartingBalance.subtract(takerMarginBalance);
        BigDecimal makerStartingBalance = BigDecimal.valueOf(1000000).add(makerFee);
        BigDecimal makerAvailableBalance = makerStartingBalance.subtract(makerMarginBalance);
        Optional<Account> makerAccountOptional = accountRepository.findByUserAndAsset(
                makerUser, market.getSettlementAsset());
        Optional<Account> takerAccountOptional = accountRepository.findByUserAndAsset(
                takerUser, market.getSettlementAsset());
        Assertions.assertTrue(makerAccountOptional.isPresent());
        Assertions.assertTrue(takerAccountOptional.isPresent());
        BigDecimal gain = market.getMarkPrice().subtract(avgEntryPrice).divide(avgEntryPrice, dps, RoundingMode.HALF_UP);
        BigDecimal takerUnrealisedProfit = gain.multiply(size).multiply(avgEntryPrice).abs();
        BigDecimal makerUnrealisedProfit = takerUnrealisedProfit.multiply(BigDecimal.valueOf(-1));
        if(takerSide.equals(MarketSide.BUY)) {
            takerUnrealisedProfit = takerUnrealisedProfit.multiply(BigDecimal.valueOf(-1));
            makerUnrealisedProfit = makerUnrealisedProfit.multiply(BigDecimal.valueOf(-1));
        }
        Assertions.assertEquals(makerUnrealisedProfit.setScale(dps, RoundingMode.HALF_UP),
                positionMaker.getUnrealisedPnl().setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(takerUnrealisedProfit.setScale(dps, RoundingMode.HALF_UP),
                positionTaker.getUnrealisedPnl().setScale(dps, RoundingMode.HALF_UP));
        // TODO - balance and availableBalance should be adjusted to account for unrealised PNL
        Assertions.assertEquals(makerAccountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                makerAvailableBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(makerAccountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                makerMarginBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(makerAccountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                makerStartingBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(takerAccountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                takerAvailableBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(takerAccountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                takerMarginBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(takerAccountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                takerStartingBalance.setScale(dps, RoundingMode.HALF_UP));
    }
}