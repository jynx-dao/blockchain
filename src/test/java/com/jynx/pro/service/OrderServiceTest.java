package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.model.OrderBook;
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
            final MarketSide side
    ) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPrice(price);
        request.setSide(side);
        request.setSize(BigDecimal.ONE);
        request.setUser(takerUser);
        request.setMarketId(marketId);
        request.setType(OrderType.LIMIT);
        return request;
    }

    @Test
    public void createOrder() throws InterruptedException {
        Market market = createAndEnactMarket();
        Order sellOrder = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.ONE, MarketSide.SELL));
        Order buyOrder = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(0.9), MarketSide.BUY));
        Assertions.assertEquals(sellOrder.getStatus(), OrderStatus.OPEN);
        Assertions.assertEquals(buyOrder.getStatus(), OrderStatus.OPEN);
        OrderBook orderBook = orderService.getOrderBook(market, 100);
        Assertions.assertEquals(orderBook.getAsks().size(), 1);
        Assertions.assertEquals(orderBook.getBids().size(), 1);
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice(), BigDecimal.valueOf(1.0));
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice(), BigDecimal.valueOf(0.9));
    }
}