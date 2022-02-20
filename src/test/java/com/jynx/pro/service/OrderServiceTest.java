package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.Account;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.OrderBook;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
            final OrderType type
    ) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPrice(price);
        request.setSide(side);
        request.setSize(size);
        request.setUser(takerUser);
        request.setMarketId(marketId);
        request.setType(type);
        return request;
    }

    private Market createOrderBook() throws InterruptedException {
        Market market = createAndEnactMarket(true);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order sellOrder = orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45590), BigDecimal.ONE, MarketSide.BUY, OrderType.LIMIT));
        long before = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        Order buyOrder = orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45610), BigDecimal.ONE, MarketSide.SELL, OrderType.LIMIT));
        long after = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
        long duration = after - before;
        log.info("Created order in {}ms", duration);
        Assertions.assertEquals(sellOrder.getStatus(), OrderStatus.OPEN);
        Assertions.assertEquals(buyOrder.getStatus(), OrderStatus.OPEN);
        OrderBook orderBook = orderService.getOrderBook(market);
        Assertions.assertEquals(orderBook.getAsks().size(), 1);
        Assertions.assertEquals(orderBook.getBids().size(), 1);
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(45610).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(45590).setScale(dps, RoundingMode.HALF_UP));
        Optional<Account> accountOptional = accountRepository
                .findByUserAndAsset(takerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(990880).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(9120).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(1000000).setScale(dps, RoundingMode.HALF_UP));
        return market;
    }

    @Test
    public void createLimitOrder() throws InterruptedException {
        createOrderBook();
    }

    @Test
    public void createOrderFailedWithInsufficientMargin() throws InterruptedException {
        Market market = createAndEnactMarket(true);
        try {
            orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45590), BigDecimal.valueOf(1000), MarketSide.BUY, OrderType.LIMIT));
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
                    BigDecimal.valueOf(45590), BigDecimal.valueOf(1), MarketSide.BUY, OrderType.LIMIT));
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
                    BigDecimal.valueOf(45590), BigDecimal.valueOf(1), MarketSide.BUY, null));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_TYPE);
        }
    }

    @Test
    public void cancelOrder() throws InterruptedException {
        Market market = createOrderBook();
        List<Order> orders = orderService.getOpenLimitOrders(market);
        for(Order order : orders) {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(takerUser);
            request.setId(order.getId());
            orderService.cancel(request);
            Order cancelledOrder = orderRepository.findById(order.getId()).orElse(new Order());
            Assertions.assertEquals(cancelledOrder.getStatus(), OrderStatus.CANCELLED);
        }
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Optional<Account> accountOptional = accountRepository
                .findByUserAndAsset(takerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(1000000).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(1000000).setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void createMarketOrder() throws InterruptedException {
        // TODO - test market order
        Market market = createOrderBook();
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.MARKET));
    }
}