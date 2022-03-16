package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.constant.OrderType;
import com.jynx.pro.entity.AuctionTrigger;
import com.jynx.pro.entity.Market;
import com.jynx.pro.model.OrderBook;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class AuctionServiceTest extends IntegrationTest {

    @Autowired
    private AuctionService auctionService;

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

    private boolean getIsTriggered(
            final UUID marketId,
            final BigDecimal depth,
            final BigDecimal ratio
    ) {
        Optional<Market> marketOptional = marketRepository.findById(marketId);
        Assertions.assertTrue(marketOptional.isPresent());
        Market market = marketOptional.get();
        List<AuctionTrigger> triggers = List.of(new AuctionTrigger()
                .setId(UUID.randomUUID())
                .setMarket(market)
                .setDepth(depth)
                .setOpenVolumeRatio(ratio));
        OrderBook orderBook = orderService.getOrderBook(market);
        return auctionService.isAuctionTriggered(market.getOpenVolume(), orderBook, triggers);
    }

    @Test
    public void testAuctionTriggeredWithBuyTrue() {
        Market market = createOrderBook(2, 2, 2000);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(0.9),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        boolean isTriggered = getIsTriggered(market.getId(), BigDecimal.valueOf(0.00001), BigDecimal.ONE);
        Assertions.assertTrue(isTriggered);
    }

    @Test
    public void testAuctionTriggeredWithSellTrue() {
        Market market = createOrderBook(2, 2, 2000);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(0.9),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        boolean isTriggered = getIsTriggered(market.getId(), BigDecimal.valueOf(0.00001), BigDecimal.ONE);
        Assertions.assertTrue(isTriggered);
    }

    @Test
    public void testAuctionTriggeredFalse() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(0.1),
                MarketSide.BUY, OrderType.MARKET, degenUser));
        boolean isTriggered = getIsTriggered(market.getId(), BigDecimal.valueOf(0.00001), BigDecimal.ONE);
        Assertions.assertFalse(isTriggered);
    }

    @Test
    public void testAuctionTriggeredTrueEmptyBook() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        boolean isTriggered = getIsTriggered(market.getId(), BigDecimal.valueOf(0.00001), BigDecimal.ONE);
        Assertions.assertTrue(isTriggered);
    }

    @Test
    public void testAuctionTriggeredTrueEmptyBid() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        boolean isTriggered = getIsTriggered(market.getId(), BigDecimal.valueOf(0.00001), BigDecimal.ONE);
        Assertions.assertTrue(isTriggered);
    }

    @Test
    public void testAuctionTriggeredTrueEmptyAsk() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        boolean isTriggered = getIsTriggered(market.getId(), BigDecimal.valueOf(0.00001), BigDecimal.ONE);
        Assertions.assertTrue(isTriggered);
    }

    @Test
    public void testEnterAuctions() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        auctionService.enterAuctions();
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        Assertions.assertEquals(market.getStatus(), MarketStatus.AUCTION);
    }

    private void createAuctionOrders(
            final Market market
    ) {
        createAuctionOrders(market, 0d);
    }

    private void createAuctionOrders(
            final Market market,
            double skew
    ) {
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47500), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47400), BigDecimal.valueOf(3),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47300), BigDecimal.valueOf(5),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47200), BigDecimal.valueOf(2),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47102), BigDecimal.valueOf(5),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47101), BigDecimal.valueOf(2.5),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47100), BigDecimal.valueOf(2.5),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47001), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47000), BigDecimal.valueOf(13),
                MarketSide.SELL, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47400), BigDecimal.valueOf(3 + skew),
                MarketSide.BUY, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47300), BigDecimal.valueOf(9),
                MarketSide.BUY, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47200), BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47100), BigDecimal.valueOf(3),
                MarketSide.BUY, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(47000), BigDecimal.valueOf(2),
                MarketSide.BUY, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(46900), BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.LIMIT, makerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(46800), BigDecimal.valueOf(11),
                MarketSide.BUY, OrderType.LIMIT, makerUser));
    }

    @Test
    public void testGetUncrossingPriceDuringAuction() {
        Market market = createOrderBook(1, 1);
        auctionService.enterAuctions();
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        auctionService.enterAuctions();
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        Assertions.assertEquals(market.getStatus(), MarketStatus.AUCTION);
        createAuctionOrders(market);
        BigDecimal price = auctionService.getUncrossingPrice(market);
        Assertions.assertEquals(47183, Math.round(price.doubleValue()));
    }

    @Test
    public void testGetUncrossingPriceWithEmptyBid() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        BigDecimal price = auctionService.getUncrossingPrice(market);
        Assertions.assertEquals(0d, price.doubleValue());
    }

    @Test
    public void testGetUncrossingPriceWithEmptyAsk() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        BigDecimal price = auctionService.getUncrossingPrice(market);
        Assertions.assertEquals(0d, price.doubleValue());
    }

    @Test
    public void testGetUncrossingPriceWithEmptyBook() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        BigDecimal price = auctionService.getUncrossingPrice(market);
        Assertions.assertEquals(0d, price.doubleValue());
    }

    @Test
    public void testGetUncrossingPriceOutsideOfAuction() {
        Market market = createOrderBook(1, 1);
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        BigDecimal price = auctionService.getUncrossingPrice(market);
        Assertions.assertEquals(45600d, price.doubleValue());
    }

    @Test
    public void testGetOrderBookAfterUncrossing() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        createAuctionOrders(market);
        OrderBook orderBook = auctionService.getOrderBookAfterUncrossing(market);
        Assertions.assertEquals(orderBook.getBids().size(), 3);
        Assertions.assertEquals(orderBook.getAsks().size(), 6);
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice().doubleValue(), 46900d);
        Assertions.assertEquals(orderBook.getBids().get(0).getQuantity().doubleValue(), 1d);
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(), 47101d);
        Assertions.assertEquals(orderBook.getAsks().get(0).getQuantity().doubleValue(), 1d);
    }

    @Test
    public void testGetOrderBookAfterUncrossingOtherSide() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        createAuctionOrders(market, 20d);
        OrderBook orderBook = auctionService.getOrderBookAfterUncrossing(market);
        Assertions.assertEquals(orderBook.getBids().size(), 5);
        Assertions.assertEquals(orderBook.getAsks().size(), 1);
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice().doubleValue(), 47100d);
        Assertions.assertEquals(orderBook.getBids().get(0).getQuantity().doubleValue(), 2d);
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(), 47500d);
        Assertions.assertEquals(orderBook.getAsks().get(0).getQuantity().doubleValue(), 1d);
    }

    @Test
    public void testGetOrderBookAfterUncrossingWithoutCrossingOrders() {
        Market market = createOrderBook(1, 1);
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        OrderBook orderBook = auctionService.getOrderBookAfterUncrossing(market);
        Assertions.assertEquals(orderBook.getBids().size(), 1);
        Assertions.assertEquals(orderBook.getAsks().size(), 1);
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice().doubleValue(), 45590d);
        Assertions.assertEquals(orderBook.getBids().get(0).getQuantity().doubleValue(), 1d);
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(), 45610d);
        Assertions.assertEquals(orderBook.getAsks().get(0).getQuantity().doubleValue(), 1d);
    }

    @Test
    public void testGetOrderBookAfterUncrossingWithMissingBid() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        OrderBook orderBook = auctionService.getOrderBookAfterUncrossing(market);
        Assertions.assertEquals(orderBook.getBids().size(), 0);
        Assertions.assertEquals(orderBook.getAsks().size(), 1);
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(), 45610d);
        Assertions.assertEquals(orderBook.getAsks().get(0).getQuantity().doubleValue(), 1d);
    }

    @Test
    public void testGetOrderBookAfterUncrossingWithMissingAsk() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        OrderBook orderBook = auctionService.getOrderBookAfterUncrossing(market);
        Assertions.assertEquals(orderBook.getBids().size(), 1);
        Assertions.assertEquals(orderBook.getAsks().size(), 0);
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice().doubleValue(), 45590d);
        Assertions.assertEquals(orderBook.getBids().get(0).getQuantity().doubleValue(), 1d);
    }

    @Test
    public void testGetUncrossingVolumeWithMissingBid() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        BigDecimal volume = auctionService.getUncrossingVolume(market);
        Assertions.assertEquals(volume.doubleValue(), 0d);
    }

    @Test
    public void testGetUncrossingVolumeWithMissingAsk() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        BigDecimal volume = auctionService.getUncrossingVolume(market);
        Assertions.assertEquals(volume.doubleValue(), 0d);
    }

    @Test
    public void testGetUncrossingVolume() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Optional<Market> marketOptional = marketRepository.findById(market.getId());
        Assertions.assertTrue(marketOptional.isPresent());
        market = marketOptional.get();
        createAuctionOrders(market);
        BigDecimal volume = auctionService.getUncrossingVolume(market);
        Assertions.assertEquals(volume.doubleValue(), 18d);
    }
}