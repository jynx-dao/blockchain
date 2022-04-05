package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.OrderBookType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.Quote;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ORDER_BOOK_SERVICE_TEST", matches = "true")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class OrderBookServiceTest extends IntegrationTest {

    @BeforeEach
    public void setup() {
        initializeState();
        databaseTransactionManager.createTransaction();
    }

    @AfterEach
    public void shutdown() {
        databaseTransactionManager.commit();
        clearState();
    }

    @Test
    public void testGetL1() {
        Market market  = createOrderBook(10, 10, 100);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L1, market);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(1, orderBook.getAsks().size());
        Assertions.assertEquals(1, orderBook.getBids().size());
    }

    @Test
    public void testGetL2() {
        Market market  = createOrderBook(10, 10, 0);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L2, market);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(1, orderBook.getAsks().size());
        Assertions.assertEquals(1, orderBook.getBids().size());
    }

    @Test
    public void testGetL3() {
        Market market  = createOrderBook(10, 10, 0);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L3, market);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(10, orderBook.getAsks().size());
        Assertions.assertEquals(10, orderBook.getBids().size());
    }

    @Test
    public void testGetL2WithStep() {
        Market market  = createOrderBook(10, 10, 10);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L2, market);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(10, orderBook.getAsks().size());
        Assertions.assertEquals(10, orderBook.getBids().size());
    }

    @Test
    public void testGetL1WithDepth() {
        Market market  = createOrderBook(10, 10, 100);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L1, market, BigDecimal.valueOf(0.002));
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(1, orderBook.getAsks().size());
        Assertions.assertEquals(1, orderBook.getBids().size());
    }

    @Test
    public void testGetL2WithDepth() {
        Market market  = createOrderBook(10, 10, 0);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L2, market, BigDecimal.valueOf(0.002));
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(1, orderBook.getAsks().size());
        Assertions.assertEquals(1, orderBook.getBids().size());
    }

    @Test
    public void testGetL2WithDepthNegative() {
        Market market  = createOrderBook(10, 10, 10);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L2, market, BigDecimal.valueOf(-0.01));
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(0, orderBook.getBids().size());
    }

    @Test
    public void testGetL3WithDepthZeroNegative() {
        Market market  = createOrderBook(10, 10, 10);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L3, market, BigDecimal.valueOf(-0.01));
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(0, orderBook.getBids().size());
    }

    @Test
    public void testGetL3WithDepth() {
        Market market  = createOrderBook(10, 10, 0);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L3, market, BigDecimal.valueOf(0.002));
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(10, orderBook.getAsks().size());
        Assertions.assertEquals(10, orderBook.getBids().size());
    }

    @Test
    public void testGetL2WithNullDepth() {
        Market market  = createOrderBook(10, 10, 100);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L2, market, null);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(10, orderBook.getAsks().size());
        Assertions.assertEquals(10, orderBook.getBids().size());
    }

    @Test
    public void testGetL3WithNullDepth() {
        Market market  = createOrderBook(10, 10, 100);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L3, market, null);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(10, orderBook.getAsks().size());
        Assertions.assertEquals(10, orderBook.getBids().size());
    }

    @Test
    public void testGetL2WithStepAndDepth() {
        Market market  = createOrderBook(10, 10, 100);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L2, market, BigDecimal.valueOf(0.002));
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(1, orderBook.getAsks().size());
        Assertions.assertEquals(1, orderBook.getBids().size());
    }

    @Test
    public void testGetQuote() {
        Market market  = createOrderBook(10, 10, 100);
        Quote quote = orderBookService.getQuote(market, false);
        Assertions.assertNotNull(quote);
        Assertions.assertEquals(1, quote.getAskSize().doubleValue());
        Assertions.assertEquals(1, quote.getBidSize().doubleValue());
    }

    @Test
    public void testGetQuoteWithEmptyOrderBook() {
        Market market  = createOrderBook(0, 0, 0);
        Quote quote = orderBookService.getQuote(market, false);
        Assertions.assertNotNull(quote);
        Assertions.assertNull(quote.getAskSize());
        Assertions.assertNull(quote.getBidSize());
    }

    @Test
    public void testGetL1WithEmptyOrderBook() {
        Market market  = createOrderBook(0, 0, 0);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L1, market, null);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(0, orderBook.getBids().size());
    }

    @Test
    public void testGetL2WithEmptyOrderBook() {
        Market market  = createOrderBook(0, 0, 0);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L2, market, null);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(0, orderBook.getBids().size());
    }

    @Test
    public void testGetL3WithEmptyOrderBook() {
        Market market  = createOrderBook(0, 0, 0);
        OrderBook orderBook = orderBookService.getOrderBook(OrderBookType.L3, market, null);
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(0, orderBook.getBids().size());
    }
}