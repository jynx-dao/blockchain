package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.*;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.request.AmendOrderRequest;
import com.jynx.pro.request.CancelOrderRequest;
import com.jynx.pro.request.CreateOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
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

    @BeforeAll
    public static void setupAll() {
        ganache.start();
    }

    @AfterAll
    public static void shutdownAll() {
        ganache.stop();
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
        request.setTag(OrderTag.USER_GENERATED);
        return request;
    }

    private Market createOrderBook(
            final int bids,
            final int asks,
            final int stepSize
    ) throws InterruptedException {
        Market market = createAndEnactMarket(true);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        for(int i=0; i<bids; i++) {
            Order buyOrder = orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45590-((long) i * stepSize)), BigDecimal.ONE, MarketSide.BUY, OrderType.LIMIT, makerUser));
            Assertions.assertEquals(buyOrder.getStatus(), OrderStatus.OPEN);
        }
        for(int i=0; i<asks; i++) {
            Order sellOrder = orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45610+((long) i * stepSize)), BigDecimal.ONE, MarketSide.SELL, OrderType.LIMIT, makerUser));
            Assertions.assertEquals(sellOrder.getStatus(), OrderStatus.OPEN);
        }
        OrderBook orderBook = orderService.getOrderBook(market);
        Assertions.assertEquals(orderBook.getAsks().size(), asks);
        Assertions.assertEquals(orderBook.getBids().size(), bids);
        BigDecimal marginBalance = BigDecimal.ZERO;
        for(int i=0; i<bids; i++) {
            OrderBookItem item = orderBook.getBids().get(i);
            Assertions.assertEquals(item.getPrice().setScale(dps, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(45590-((long) i * stepSize)).setScale(dps, RoundingMode.HALF_UP));
            marginBalance = marginBalance.add(item.getPrice().multiply(item.getSize())
                    .multiply(market.getInitialMargin()));
        }
        for(int i=0; i<asks; i++) {
            if(i == 0) {
                marginBalance = BigDecimal.ZERO;
            }
            OrderBookItem item = orderBook.getAsks().get(i);
            Assertions.assertEquals(item.getPrice().setScale(dps, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(45610+((long) i * stepSize)).setScale(dps, RoundingMode.HALF_UP));
            marginBalance = marginBalance.add(item.getPrice().multiply(item.getSize())
                    .multiply(market.getInitialMargin()));
        }
        BigDecimal startingBalance = BigDecimal.valueOf(INITIAL_BALANCE);
        BigDecimal availableBalance = startingBalance.subtract(marginBalance);
        Optional<Account> accountOptional = accountRepository
                .findByUserAndAsset(makerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                marginBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                availableBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                startingBalance.setScale(dps, RoundingMode.HALF_UP));
        return market;
    }

    private Market createOrderBook(
            final int bids,
            final int asks
    ) throws InterruptedException {
        return createOrderBook(bids, asks, 1);
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
                    BigDecimal.valueOf(45590), BigDecimal.valueOf(10000000), MarketSide.BUY, OrderType.LIMIT, makerUser));
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
                BigDecimal.valueOf(INITIAL_BALANCE).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(INITIAL_BALANCE).setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void createMarketOrderBuy() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(0.5));
        BigDecimal expectedTakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()));
        BigDecimal expectedMakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()))
                .add((positionNotionalSize).multiply(market.getInitialMargin()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.5),
                1,
                1,
                MarketSide.SELL,
                MarketSide.BUY,
                1,
                expectedTakerMargin,
                expectedMakerMargin,
                makerFee,
                takerFee,
                treasuryFee,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    @Test
    public void createCrossingLimitOrderBuy() throws InterruptedException {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45611), BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.LIMIT, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(0.5));
        BigDecimal makerSize = positionNotionalSize.add(BigDecimal.valueOf(45611).multiply(BigDecimal.valueOf(1))).add(BigDecimal.valueOf(45612).multiply(BigDecimal.valueOf(1)));
        BigDecimal expectedTakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()));
        BigDecimal expectedMakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()))
                .add(makerSize.multiply(market.getInitialMargin()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610), BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.5),
                3,
                3,
                MarketSide.SELL,
                MarketSide.BUY,
                1,
                expectedTakerMargin,
                expectedMakerMargin,
                makerFee,
                takerFee,
                treasuryFee,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getUser().getId().equals(takerUser.getId())).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 0);
    }

    @Test
    public void createCrossingLimitOrderBuyWithLeftover() throws InterruptedException {
        Market market = createOrderBook(3, 3);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45611), BigDecimal.valueOf(2.5), MarketSide.BUY, OrderType.LIMIT, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45610.5).multiply(BigDecimal.valueOf(2));
        BigDecimal makerSize = BigDecimal.valueOf(45612).multiply(BigDecimal.valueOf(1));
        BigDecimal takerSize = BigDecimal.valueOf(45611).multiply(BigDecimal.valueOf(0.5));
        BigDecimal expectedTakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()))
                .add(takerSize.multiply(market.getInitialMargin()));
        BigDecimal expectedMakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()))
                .add(makerSize.multiply(market.getInitialMargin()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(45610.5),
                BigDecimal.valueOf(45611),
                BigDecimal.valueOf(45611),
                BigDecimal.valueOf(45612),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(1),
                4,
                1,
                MarketSide.SELL,
                MarketSide.BUY,
                2,
                expectedTakerMargin,
                expectedMakerMargin,
                makerFee,
                takerFee,
                treasuryFee,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getUser().getId().equals(takerUser.getId())).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 1);
        Assertions.assertEquals(orders.get(0).getStatus(), OrderStatus.PARTIALLY_FILLED);
        Assertions.assertEquals(orders.get(0).getSize().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(2.5).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orders.get(0).getRemainingSize().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.5).setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testOpenAndClosePosition() throws InterruptedException {
        Market market = createOrderBook(5, 5);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(2.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(2.5), MarketSide.SELL, OrderType.MARKET, takerUser));
        BigDecimal expectedTakerMargin = BigDecimal.ZERO;
        BigDecimal expectedMakerMargin = BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45612))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45613)))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45614)))
                .multiply(market.getInitialMargin());
        BigDecimal makerFee = market.getMakerFee().multiply(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45610))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45611)))
                .add(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45612)))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45590)))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45589)))
                .add(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45588))));
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        BigDecimal entryPrice = (BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45610))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45611)))
                .add(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45612))))
                .divide(BigDecimal.valueOf(2.5), dps, RoundingMode.HALF_UP);
        BigDecimal exitPrice = (BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45590))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45589)))
                .add(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45588))))
                .divide(BigDecimal.valueOf(2.5), dps, RoundingMode.HALF_UP);;
        BigDecimal realisedProfit = ((exitPrice.subtract(entryPrice)).divide(entryPrice, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(2.5).multiply(exitPrice)).abs();
        validateMarketState(
                market.getId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(45588),
                BigDecimal.valueOf(45588),
                BigDecimal.valueOf(45612),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.5),
                3,
                3,
                null,
                null,
                6,
                expectedTakerMargin,
                expectedMakerMargin,
                makerFee,
                takerFee,
                treasuryFee,
                realisedProfit,
                realisedProfit.multiply(BigDecimal.valueOf(-1))
        );
    }

    @Test
    public void testOpenAndFlipPosition() throws InterruptedException {
        Market market = createOrderBook(5, 5);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(2.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(3.5), MarketSide.SELL, OrderType.MARKET, takerUser));
        BigDecimal gain = BigDecimal.valueOf(12).divide(BigDecimal.valueOf(45587.5), dps, RoundingMode.HALF_UP);
        BigDecimal expectedTakerMargin = (BigDecimal.ONE.multiply(BigDecimal.valueOf(45587.5))
                .multiply(market.getMaintenanceMargin()));
        BigDecimal expectedMakerMargin = (BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45613))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45614))))
                .multiply(market.getInitialMargin()).add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45587.5)
                        .multiply(market.getMaintenanceMargin())));
        BigDecimal makerFee = market.getMakerFee().multiply(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45610))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45611)))
                .add(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45612)))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45590)))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45589)))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45588)))
                .add(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45587))));
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        BigDecimal entryPrice = (BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45610))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45611)))
                .add(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45612))))
                .divide(BigDecimal.valueOf(2.5), dps, RoundingMode.HALF_UP);
        BigDecimal exitPrice = (BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45590))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45589)))
                .add(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45588))))
                .divide(BigDecimal.valueOf(2.5), dps, RoundingMode.HALF_UP);;
        BigDecimal realisedProfit = ((exitPrice.subtract(entryPrice)).divide(entryPrice, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(2.5).multiply(exitPrice)).abs();
        validateMarketState(
                market.getId(),
                BigDecimal.ONE,
                BigDecimal.valueOf(45587.5),
                BigDecimal.valueOf(45587),
                BigDecimal.valueOf(45587),
                BigDecimal.valueOf(45612),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.5),
                2,
                3,
                MarketSide.BUY,
                MarketSide.SELL,
                7,
                expectedTakerMargin,
                expectedMakerMargin,
                makerFee,
                takerFee,
                treasuryFee,
                realisedProfit,
                realisedProfit.multiply(BigDecimal.valueOf(-1))
        );
    }

    @Test
    public void createMarketOrderSell() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(0.5), MarketSide.SELL, OrderType.MARKET, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(0.5));
        BigDecimal makerSize = BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(0.5));
        BigDecimal expectedTakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()));
        BigDecimal expectedMakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()))
                .add((makerSize).multiply(market.getInitialMargin()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(1),
                1,
                1,
                MarketSide.BUY,
                MarketSide.SELL,
                1,
                expectedTakerMargin,
                expectedMakerMargin,
                makerFee,
                takerFee,
                treasuryFee,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    @Test
    public void createCrossingLimitOrderSell() throws InterruptedException {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45589), BigDecimal.valueOf(0.5), MarketSide.SELL, OrderType.LIMIT, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(0.5));
        BigDecimal makerSize = BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(0.5)).add(BigDecimal.valueOf(45611).multiply(BigDecimal.valueOf(1))).add(BigDecimal.valueOf(45612).multiply(BigDecimal.valueOf(1)));
        BigDecimal expectedTakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()));
        BigDecimal expectedMakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()))
                .add(makerSize.multiply(market.getInitialMargin()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(1),
                3,
                3,
                MarketSide.BUY,
                MarketSide.SELL,
                1,
                expectedTakerMargin,
                expectedMakerMargin,
                makerFee,
                takerFee,
                treasuryFee,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getUser().getId().equals(takerUser.getId())).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 0);
    }

    @Test
    public void createCrossingLimitOrderSellWithLeftover() throws InterruptedException {
        Market market = createOrderBook(3, 3);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45589), BigDecimal.valueOf(2.5), MarketSide.SELL, OrderType.LIMIT, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45589.5).multiply(BigDecimal.valueOf(2));
        BigDecimal makerSize = BigDecimal.valueOf(45612).multiply(BigDecimal.valueOf(1));
        BigDecimal takerSize = BigDecimal.valueOf(45589).multiply(BigDecimal.valueOf(0.5));
        BigDecimal expectedTakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()))
                .add(takerSize.multiply(market.getInitialMargin()));
        BigDecimal expectedMakerMargin = (positionNotionalSize.multiply(market.getMaintenanceMargin()))
                .add(makerSize.multiply(market.getInitialMargin()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(45589.5),
                BigDecimal.valueOf(45589),
                BigDecimal.valueOf(45588),
                BigDecimal.valueOf(45589),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.5),
                1,
                4,
                MarketSide.BUY,
                MarketSide.SELL,
                2,
                expectedTakerMargin,
                expectedMakerMargin,
                makerFee,
                takerFee,
                treasuryFee,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getUser().getId().equals(takerUser.getId())).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 1);
        Assertions.assertEquals(orders.get(0).getStatus(), OrderStatus.PARTIALLY_FILLED);
        Assertions.assertEquals(orders.get(0).getSize().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(2.5).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orders.get(0).getRemainingSize().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.5).setScale(dps, RoundingMode.HALF_UP));
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
    public void testBulkInstructions() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        CreateOrderRequest createOrderRequest = getCreateOrderRequest(market.getId(), BigDecimal.valueOf(5),
                BigDecimal.ONE, MarketSide.BUY, OrderType.LIMIT, makerUser);
        List<CreateOrderRequest> bulkCreateRequest = Arrays.asList(createOrderRequest, createOrderRequest);
        List<Order> orders = orderService.createMany(bulkCreateRequest);
        orders.forEach(o -> Assertions.assertEquals(o.getStatus(), OrderStatus.OPEN));
        List<AmendOrderRequest> bulkAmendRequest = Arrays.asList(new AmendOrderRequest(), new AmendOrderRequest());
        for(int i=0; i< orders.size(); i++) {
            bulkAmendRequest.get(i).setId(orders.get(i).getId());
            bulkAmendRequest.get(i).setSize(BigDecimal.valueOf(2));
            bulkAmendRequest.get(i).setUser(makerUser);
        }
        orders = orderService.amendMany(bulkAmendRequest);
        orders.forEach(o -> Assertions.assertEquals(o.getSize().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(2).setScale(dps, RoundingMode.HALF_UP)));
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
            List<AmendOrderRequest> request = new ArrayList<>();
            for(int i=0; i<30; i++) {
                request.add(new AmendOrderRequest());
            }
            orderService.amendMany(request);
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
    public void testCreateStopOrderFails() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.ONE, BigDecimal.valueOf(1),
                    MarketSide.SELL, OrderType.STOP_MARKET, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.STOP_ORDER_NOT_SUPPORTED);
        }
    }

    @Test
    public void testCreateOrderFailsWithMissingSize() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.ONE, null,
                    MarketSide.SELL, OrderType.LIMIT, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_SIZE_MANDATORY);
        }
    }

    @Test
    public void testCreateOrderFailsWithMissingType() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.ONE, BigDecimal.ONE,
                    MarketSide.SELL, null, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_TYPE_MANDATORY);
        }
    }

    @Test
    public void testCreateOrderFailsWithMissingSide() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.ONE,
                    MarketSide.BUY, OrderType.LIMIT, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_PRICE_MANDATORY);
        }
    }

    @Test
    public void testCreateOrderFailsWithMissingPrice() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.ONE, BigDecimal.ONE,
                    null, OrderType.LIMIT, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_SIDE_MANDATORY);
        }
    }

    @Test
    public void testCreateOrderFailsWithMissingMarket() throws InterruptedException {
        createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(null, BigDecimal.ONE, BigDecimal.ONE,
                    MarketSide.SELL, OrderType.LIMIT, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_MARKET_MANDATORY);
        }
    }

    private void amendOrder(
            final MarketSide side,
            final BigDecimal createPrice,
            final BigDecimal amendPrice,
            final BigDecimal createSize,
            final BigDecimal amendSize,
            final BigDecimal expectedMargin
    ) throws InterruptedException {
        Market market = createOrderBook(1, 1);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order order = orderService.create(getCreateOrderRequest(market.getId(), createPrice, createSize,
                side, OrderType.LIMIT, takerUser));
        Optional<Account> accountOptional = accountRepository.findByUserAndAsset(takerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        BigDecimal startingBalance = BigDecimal.valueOf(INITIAL_BALANCE);
        BigDecimal marginBalance = order.getPrice().multiply(order.getSize()).multiply(market.getInitialMargin());
        BigDecimal availableBalance = startingBalance.subtract(marginBalance);
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                marginBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                startingBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                availableBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(order.getStatus(), OrderStatus.OPEN);
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(amendPrice);
        amendOrderRequest.setSize(amendSize);
        orderService.amend(amendOrderRequest);
        order = orderRepository.getOne(order.getId());
        accountOptional = accountRepository.findByUserAndAsset(takerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        if(amendSize == null) {
            Assertions.assertEquals(order.getSize().setScale(dps, RoundingMode.HALF_UP),
                    createSize.setScale(dps, RoundingMode.HALF_UP));
        } else {
            Assertions.assertEquals(order.getSize().setScale(dps, RoundingMode.HALF_UP),
                    amendSize.setScale(dps, RoundingMode.HALF_UP));
        }
        if(amendPrice == null) {
            Assertions.assertEquals(order.getPrice().setScale(dps, RoundingMode.HALF_UP),
                    createPrice.setScale(dps, RoundingMode.HALF_UP));
        } else {
            Assertions.assertEquals(order.getPrice().setScale(dps, RoundingMode.HALF_UP),
                    amendPrice.setScale(dps, RoundingMode.HALF_UP));
        }
        startingBalance = BigDecimal.valueOf(INITIAL_BALANCE);
        availableBalance = startingBalance.subtract(expectedMargin);
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                expectedMargin.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                startingBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                availableBalance.setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendBuyOrderChangePrice() throws InterruptedException {
        BigDecimal expectedMargin = BigDecimal.valueOf(45100)
                .multiply(BigDecimal.ONE).multiply(BigDecimal.valueOf(0.01));
        amendOrder(MarketSide.BUY, BigDecimal.valueOf(45600), BigDecimal.valueOf(45100),
                BigDecimal.ONE, BigDecimal.ONE, expectedMargin);
    }

    @Test
    public void testAmendBuyOrderChangeSize() throws InterruptedException {
        BigDecimal expectedMargin = BigDecimal.valueOf(45600)
                .multiply(BigDecimal.valueOf(0.9)).multiply(BigDecimal.valueOf(0.01));
        amendOrder(MarketSide.BUY, BigDecimal.valueOf(45600), BigDecimal.valueOf(45600),
                BigDecimal.ONE, BigDecimal.valueOf(0.9), expectedMargin);
    }

    @Test
    public void testAmendSellOrderChangePrice() throws InterruptedException {
        BigDecimal expectedMargin = BigDecimal.valueOf(46500)
                .multiply(BigDecimal.ONE).multiply(BigDecimal.valueOf(0.01));
        amendOrder(MarketSide.SELL, BigDecimal.valueOf(45600), BigDecimal.valueOf(46500),
                BigDecimal.ONE, null, expectedMargin);
    }

    @Test
    public void testAmendSellOrderChangeSize() throws InterruptedException {
        BigDecimal expectedMargin = BigDecimal.valueOf(45600)
                .multiply(BigDecimal.valueOf(1.1)).multiply(BigDecimal.valueOf(0.01));
        amendOrder(MarketSide.SELL, BigDecimal.valueOf(45600), null,
                BigDecimal.ONE, BigDecimal.valueOf(1.1), expectedMargin);
    }

    @Test
    public void testAmendOrderFailsWithInsufficientMargin() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.BUY, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45600));
        amendOrderRequest.setSize(BigDecimal.valueOf(100000000));
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INSUFFICIENT_MARGIN);
        }
        Optional<Account> accountOptional = accountRepository.findByUserAndAsset(takerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        BigDecimal startingBalance = BigDecimal.valueOf(INITIAL_BALANCE);
        BigDecimal marginBalance = order.getPrice().multiply(order.getSize()).multiply(market.getInitialMargin());
        BigDecimal availableBalance = startingBalance.subtract(marginBalance);
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                marginBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                startingBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                availableBalance.setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendOrderFailsWithImmediateExecutionBuy() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.BUY, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45620));
        amendOrderRequest.setSize(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_AMEND_WOULD_EXECUTE);
        }
    }

    @Test
    public void testAmendOrderFreelyWhenAskEmpty() throws InterruptedException {
        Market market = createOrderBook(1, 0);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.BUY, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45620));
        amendOrderRequest.setSize(BigDecimal.ONE);
        order = orderService.amend(amendOrderRequest);
        order = orderRepository.getOne(order.getId());
        Assertions.assertEquals(order.getPrice().setScale(dps, RoundingMode.HALF_UP),
                amendOrderRequest.getPrice().setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendOrderFreelyWhenBidEmpty() throws InterruptedException {
        Market market = createOrderBook(0, 1);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45580));
        amendOrderRequest.setSize(BigDecimal.ONE);
        order = orderService.amend(amendOrderRequest);
        order = orderRepository.getOne(order.getId());
        Assertions.assertEquals(order.getPrice().setScale(dps, RoundingMode.HALF_UP),
                amendOrderRequest.getPrice().setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendOrderFailsWithImmediateExecutionSell() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45580));
        amendOrderRequest.setSize(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_AMEND_WOULD_EXECUTE);
        }
    }

    @Test
    public void testAmendOrderFailsWhenPermissionDenied() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(makerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45602));
        amendOrderRequest.setSize(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.PERMISSION_DENIED);
        }
    }

    @Test
    public void testAmendOrderFailsWhenOrderNotFound() {
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(UUID.randomUUID());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45602));
        amendOrderRequest.setSize(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Test
    public void testAmendOrderFailsWithInvalidType() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(0.1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45602));
        amendOrderRequest.setSize(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_TYPE);
        }
    }

    @Test
    public void testAmendOrderFailsWithInvalidStatus() throws InterruptedException {
        Market market = createOrderBook(1, 1);
        List<Order> openOrders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getSide().equals(MarketSide.BUY)).collect(Collectors.toList());
        Assertions.assertEquals(openOrders.size(), 1);
        Order buyOrder = openOrders.get(0);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(buyOrder.getId());
        amendOrderRequest.setUser(makerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45602));
        amendOrderRequest.setSize(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    private void testLiquidation(
            final MarketSide side,
            final int liquidationOffset,
            final boolean withInsuranceFund
    ) throws InterruptedException {
        Market market = createOrderBook(100, 100, 15);
        if(withInsuranceFund) {
            market = market.setInsuranceFund(BigDecimal.valueOf(1000000));
            market = marketRepository.save(market);
        }
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1.5),
                side, OrderType.MARKET, degenUser));
        Optional<Position> positionDegenOptional = positionRepository.findByUserAndMarket(degenUser, market);
        Assertions.assertTrue(positionDegenOptional.isPresent());
        Position positionDegen = positionDegenOptional.get();
        BigDecimal price = side.equals(MarketSide.SELL) ? positionDegen.getLiquidationPrice().add(BigDecimal.valueOf(liquidationOffset)) :
                positionDegen.getLiquidationPrice().subtract(BigDecimal.valueOf(liquidationOffset));
        orderService.create(getCreateOrderRequest(market.getId(), price, BigDecimal.valueOf(400),
                orderService.getOtherSide(side), OrderType.LIMIT, takerUser));
        positionDegenOptional = positionRepository.findByUserAndMarket(degenUser, market);
        Assertions.assertTrue(positionDegenOptional.isPresent());
        positionDegen = positionDegenOptional.get();
        Optional<Market> updatedMarketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(updatedMarketOptional.isPresent());
        Market updatedMarket = updatedMarketOptional.get();
        Optional<Account> degenAccountOptional = accountRepository.findByUserAndAsset(
                degenUser, market.getSettlementAsset());
        Assertions.assertTrue(degenAccountOptional.isPresent());
        Account degenAccount = degenAccountOptional.get();
        Assertions.assertEquals(degenAccount.getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(degenAccount.getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(degenAccount.getBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(dps, RoundingMode.HALF_UP));
        List<Transaction> transactions = transactionRepository.findByUserAndAsset(
                degenUser, market.getSettlementAsset());
        double txSum = transactions.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
        List<TransactionType> txTypes = Arrays.asList(TransactionType.SETTLEMENT, TransactionType.LOSS_SOCIALIZATION,
                TransactionType.LIQUIDATION_DEBIT, TransactionType.LIQUIDATION_CREDIT);
        double settlementSum = transactions.stream().filter(t -> txTypes.contains(t.getType()))
                .mapToDouble(t -> t.getAmount().doubleValue()).sum();
        // TODO - the realised PNL on the position is wrong during liquidation events
        // TODO - likely wrong for both sides of the trade
        // TODO - I think we should be deriving realised PNL by summing the transactions, it'll be much cleaner
        // TODO - realised PNL should also include fees, which will be achieved by doing [the above]
//        Assertions.assertEquals(settlementSum, positionDegen.getRealisedPnl().doubleValue());
        Assertions.assertEquals(Math.abs(txSum), 1000d, 0.001d);
        // TODO - assert the market state is all okay...
    }

    @Test
    public void testLiquidationShortPosition() throws InterruptedException {
        testLiquidation(MarketSide.SELL, 1, false);
    }

    @Test
    public void testLiquidationLongPosition() throws InterruptedException {
        testLiquidation(MarketSide.BUY, 1, false);
    }

    @Test
    public void testLiquidationShortPositionWithLossSocialization() throws InterruptedException {
        testLiquidation(MarketSide.SELL, 100, false);
    }

    @Test
    public void testLiquidationShortPositionWithInsuranceFund() throws InterruptedException {
        testLiquidation(MarketSide.SELL, 100, true);
    }

    private void validateMarketState(
            final UUID marketId,
            final BigDecimal size,
            final BigDecimal avgEntryPrice,
            final BigDecimal lastPrice,
            final BigDecimal bidPrice,
            final BigDecimal askPrice,
            final BigDecimal bidSize,
            final BigDecimal askSize,
            final int bidCount,
            final int askCount,
            final MarketSide makerSide,
            final MarketSide takerSide,
            final int tradeCount,
            final BigDecimal expectedTakerMargin,
            final BigDecimal expectedMakerMargin,
            final BigDecimal makerFee,
            final BigDecimal takerFee,
            final BigDecimal treasuryFee,
            final BigDecimal makerRealisedProfit,
            final BigDecimal takerRealisedProfit
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
                lastPrice.setScale(dps, RoundingMode.HALF_UP));
        List<Transaction> makerTxns = transactionRepository.findByUserAndAsset(makerUser, market.getSettlementAsset())
                .stream().filter(t -> t.getType().equals(TransactionType.FEE)).collect(Collectors.toList());
        List<Transaction> takerTxns = transactionRepository.findByUserAndAsset(takerUser, market.getSettlementAsset())
                .stream().filter(t -> t.getType().equals(TransactionType.FEE)).collect(Collectors.toList());
        Assertions.assertEquals(makerTxns.size(), tradeCount);
        Assertions.assertEquals(takerTxns.size(), tradeCount);
        BigDecimal makerFeeFromTxns = BigDecimal.ZERO;
        BigDecimal takerFeeFromTxns = BigDecimal.ZERO;
        for(int i=0; i<tradeCount; i++) {
            makerFeeFromTxns = makerFeeFromTxns.add(makerTxns.get(i).getAmount());
            takerFeeFromTxns = takerFeeFromTxns.add(takerTxns.get(i).getAmount());
            Assertions.assertEquals(makerTxns.get(i).getType(), TransactionType.FEE);
            Assertions.assertEquals(takerTxns.get(i).getType(), TransactionType.FEE);
        }
        Assertions.assertEquals(makerFeeFromTxns.setScale(dps, RoundingMode.HALF_UP),
                makerFee.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(takerFeeFromTxns.setScale(dps, RoundingMode.HALF_UP),
                takerFee.setScale(dps, RoundingMode.HALF_UP));
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
        BigDecimal takerStartingBalance = BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee);
        BigDecimal takerAvailableBalance = takerStartingBalance.subtract(expectedTakerMargin);
        BigDecimal makerStartingBalance = BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee);
        BigDecimal makerAvailableBalance = makerStartingBalance.subtract(expectedMakerMargin);
        Optional<Account> makerAccountOptional = accountRepository.findByUserAndAsset(
                makerUser, market.getSettlementAsset());
        Optional<Account> takerAccountOptional = accountRepository.findByUserAndAsset(
                takerUser, market.getSettlementAsset());
        Assertions.assertTrue(makerAccountOptional.isPresent());
        Assertions.assertTrue(takerAccountOptional.isPresent());
        BigDecimal gain = BigDecimal.ZERO;
        if(avgEntryPrice.doubleValue() > 0) {
            gain = market.getMarkPrice().subtract(avgEntryPrice).divide(avgEntryPrice, dps, RoundingMode.HALF_UP);
        }
        BigDecimal makerUnrealisedProfit = gain.multiply(size).multiply(avgEntryPrice).abs();
        BigDecimal takerUnrealisedProfit = makerUnrealisedProfit.multiply(BigDecimal.valueOf(-1));
        if(market.getMarkPrice().doubleValue() > positionMaker.getAverageEntryPrice().doubleValue() &&
                MarketSide.SELL.equals(positionMaker.getSide())) {
            makerUnrealisedProfit = makerUnrealisedProfit.multiply(BigDecimal.valueOf(-1));
            takerUnrealisedProfit = takerUnrealisedProfit.multiply(BigDecimal.valueOf(-1));
        }
        if(market.getMarkPrice().doubleValue() < positionMaker.getAverageEntryPrice().doubleValue() &&
                positionMaker.getSide().equals(MarketSide.BUY)) {
            makerUnrealisedProfit = makerUnrealisedProfit.multiply(BigDecimal.valueOf(-1));
            takerUnrealisedProfit = takerUnrealisedProfit.multiply(BigDecimal.valueOf(-1));
        }
        Assertions.assertEquals(makerUnrealisedProfit.setScale(dps, RoundingMode.HALF_UP),
                positionMaker.getUnrealisedPnl().setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(takerUnrealisedProfit.setScale(dps, RoundingMode.HALF_UP),
                positionTaker.getUnrealisedPnl().setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(makerRealisedProfit.doubleValue(),
                positionMaker.getRealisedPnl().doubleValue(), 0.01d);
        Assertions.assertEquals(takerRealisedProfit.doubleValue(),
                positionTaker.getRealisedPnl().doubleValue(), 0.01d);
        makerStartingBalance = makerStartingBalance.add(makerRealisedProfit);
        takerStartingBalance = takerStartingBalance.add(takerRealisedProfit);
        orderService.getMarginRequirement(market, makerUser);
        Assertions.assertEquals(makerAccountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                expectedMakerMargin.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(makerAccountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                makerAvailableBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(makerAccountOptional.get().getBalance().doubleValue(),
                makerStartingBalance.doubleValue(), 0.01d);
        Assertions.assertEquals(takerAccountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                expectedTakerMargin.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(takerAccountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_DOWN),
                takerAvailableBalance.setScale(dps, RoundingMode.HALF_DOWN));
        Assertions.assertEquals(takerAccountOptional.get().getBalance().doubleValue(),
                takerStartingBalance.doubleValue(), 0.01d);
        List<Transaction> makerSettlementTxns = transactionRepository
                .findByUserAndAsset(makerUser, market.getSettlementAsset())
                .stream().filter(t -> t.getType().equals(TransactionType.SETTLEMENT))
                .collect(Collectors.toList());
        List<Transaction> takerSettlementTxns = transactionRepository
                .findByUserAndAsset(takerUser, market.getSettlementAsset())
                .stream().filter(t -> t.getType().equals(TransactionType.SETTLEMENT))
                .collect(Collectors.toList());
        double sumTakerTxns = takerSettlementTxns.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
        double sumMakerTxns = makerSettlementTxns.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
        Assertions.assertEquals(sumTakerTxns, positionTaker.getRealisedPnl().doubleValue(), 0.0001d);
        Assertions.assertEquals(sumMakerTxns, positionMaker.getRealisedPnl().doubleValue(), 0.0001d);
    }
}