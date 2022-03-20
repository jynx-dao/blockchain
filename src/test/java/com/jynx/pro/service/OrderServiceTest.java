package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.*;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.request.*;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
@EnabledIfEnvironmentVariable(named = "ORDER_SERVICE_TEST", matches = "true")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class OrderServiceTest extends IntegrationTest {

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

    @BeforeAll
    public static void setupAll() {
        ganache.start();
    }

    @AfterAll
    public static void shutdownAll() {
        ganache.stop();
    }

    @Test
    public void testCreateLimitOrder() {
        createOrderBook(2, 2);
    }

    @Test
    public void testCreateOrderFailedWithInsufficientMargin() throws DecoderException {
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
    public void testCreateOrderFailedWhenMarketInactive() throws DecoderException {
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
    public void testCancelOrder() {
        Market market = createOrderBook(1, 1);
        List<Order> orders = orderService.getOpenLimitOrders(market);
        for(Order order : orders) {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(makerUser);
            request.setId(order.getId());
            orderService.cancel(request);
            Order cancelledOrder = orderRepository.findById(order.getId()).orElse(new Order());
            Assertions.assertEquals(cancelledOrder.getStatus(), OrderStatus.CANCELED);
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
    public void testCreateMarketOrderBuy() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(0.5));
        BigDecimal takerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()));
        BigDecimal makerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()))
                .add((positionNotionalSize).multiply(market.getMarginRequirement()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(0.5))
                .setRealisedProfit(makerFee)
                .setTradeCount(1)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(0.5))
                .setRealisedProfit(takerFee)
                .setTradeCount(1)
                .setFee(takerFee);
        taker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee));
        maker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.5),
                1,
                1,
                treasuryFee,
                List.of(maker, taker)
        );
    }

    @Test
    public void testCreateCrossingLimitOrderBuy() {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45611), BigDecimal.valueOf(0.5), MarketSide.BUY, OrderType.LIMIT, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(0.5));
        BigDecimal makerSize = positionNotionalSize.add(BigDecimal.valueOf(45611).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45612).multiply(BigDecimal.valueOf(1)));
        BigDecimal takerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()));
        BigDecimal makerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()))
                .add(makerSize.multiply(market.getMarginRequirement()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(0.5))
                .setRealisedProfit(makerFee)
                .setTradeCount(1)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(0.5))
                .setRealisedProfit(takerFee)
                .setTradeCount(1)
                .setFee(takerFee);
        taker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee));
        maker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.5),
                3,
                3,
                treasuryFee,
                List.of(maker, taker)
        );
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getUser().getId().equals(takerUser.getId())).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 0);
    }

    @Test
    public void testCreateCrossingLimitOrderBuyWithLeftover() {
        Market market = createOrderBook(3, 3);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45611), BigDecimal.valueOf(2.5), MarketSide.BUY, OrderType.LIMIT, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45610.5).multiply(BigDecimal.valueOf(2));
        BigDecimal makerSize = BigDecimal.valueOf(45612).multiply(BigDecimal.valueOf(1));
        BigDecimal takerSize = BigDecimal.valueOf(45611).multiply(BigDecimal.valueOf(0.5));
        BigDecimal takerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()))
                .add(takerSize.multiply(market.getMarginRequirement()));
        BigDecimal makerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()))
                .add(makerSize.multiply(market.getMarginRequirement()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45610.5))
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(2))
                .setRealisedProfit(makerFee)
                .setTradeCount(2)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45610.5))
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(2))
                .setRealisedProfit(takerFee)
                .setTradeCount(2)
                .setFee(takerFee);
        taker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee));
        maker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(45611),
                BigDecimal.valueOf(45611),
                BigDecimal.valueOf(45612),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(1),
                4,
                1,
                treasuryFee,
                List.of(maker, taker)
        );
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getUser().getId().equals(takerUser.getId())).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 1);
        Assertions.assertEquals(orders.get(0).getStatus(), OrderStatus.PARTIALLY_FILLED);
        Assertions.assertEquals(orders.get(0).getQuantity().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(2.5).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orders.get(0).getRemainingQuantity().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.5).setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testOpenAndClosePosition() {
        Market market = createOrderBook(5, 5);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(2.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(2.5), MarketSide.SELL, OrderType.MARKET, takerUser));
        BigDecimal takerMargin = BigDecimal.ZERO;
        BigDecimal makerMargin = BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45612))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45613)))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45614)))
                .multiply(market.getMarginRequirement());
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
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(0))
                .setUser(makerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.valueOf(0))
                .setRealisedProfit(makerFee.add(realisedProfit))
                .setTradeCount(6)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(0))
                .setUser(takerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.valueOf(0))
                .setRealisedProfit(takerFee.subtract(realisedProfit))
                .setTradeCount(6)
                .setFee(takerFee);
        taker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee).subtract(realisedProfit));
        maker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee).add(realisedProfit));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0),
                BigDecimal.valueOf(45588),
                BigDecimal.valueOf(45588),
                BigDecimal.valueOf(45612),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.5),
                3,
                3,
                treasuryFee,
                List.of(maker, taker)
        );
    }

    @Test
    public void testOpenAndFlipPosition() {
        Market market = createOrderBook(5, 5);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(2.5), MarketSide.BUY, OrderType.MARKET, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(3.5), MarketSide.SELL, OrderType.MARKET, takerUser));
        BigDecimal takerMargin = (BigDecimal.ONE.multiply(BigDecimal.valueOf(45587.5))
                .multiply(market.getMarginRequirement()));
        BigDecimal makerMargin = (BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45613))
                .add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45614))))
                .multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45587.5)
                        .multiply(market.getMarginRequirement())));
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
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45587.5))
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(1))
                .setRealisedProfit(makerFee.add(realisedProfit))
                .setTradeCount(7)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45587.5))
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(1))
                .setRealisedProfit(takerFee.subtract(realisedProfit))
                .setTradeCount(7)
                .setFee(takerFee);
        taker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee).subtract(realisedProfit));
        maker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee).add(realisedProfit));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(45587),
                BigDecimal.valueOf(45587),
                BigDecimal.valueOf(45612),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.5),
                2,
                3,
                treasuryFee,
                List.of(maker, taker)
        );
    }

    @Test
    public void testCreateMarketOrderSell() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.valueOf(0.5), MarketSide.SELL, OrderType.MARKET, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(0.5));
        BigDecimal makerSize = BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(0.5));
        BigDecimal takerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()));
        BigDecimal makerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()))
                .add((makerSize).multiply(market.getMarginRequirement()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45590))
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(0.5))
                .setRealisedProfit(makerFee)
                .setTradeCount(1)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45590))
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(0.5))
                .setRealisedProfit(takerFee)
                .setTradeCount(1)
                .setFee(takerFee);
        taker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee));
        maker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(1),
                1,
                1,
                treasuryFee,
                List.of(maker, taker)
        );
    }

    @Test
    public void testCreateCrossingLimitOrderSell() {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45589), BigDecimal.valueOf(0.5), MarketSide.SELL, OrderType.LIMIT, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(0.5));
        BigDecimal makerSize = BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(0.5)).add(BigDecimal.valueOf(45611).multiply(BigDecimal.valueOf(1))).add(BigDecimal.valueOf(45612).multiply(BigDecimal.valueOf(1)));
        BigDecimal takerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()));
        BigDecimal makerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()))
                .add(makerSize.multiply(market.getMarginRequirement()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45590))
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(0.5))
                .setRealisedProfit(makerFee)
                .setTradeCount(1)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45590))
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(0.5))
                .setRealisedProfit(takerFee)
                .setTradeCount(1)
                .setFee(takerFee);
        taker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee));
        maker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(1),
                3,
                3,
                treasuryFee,
                List.of(maker, taker)
        );
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getUser().getId().equals(takerUser.getId())).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 0);
    }

    @Test
    public void testCreateCrossingLimitOrderSellWithLeftover() {
        Market market = createOrderBook(3, 3);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(),
                BigDecimal.valueOf(45589), BigDecimal.valueOf(2.5), MarketSide.SELL, OrderType.LIMIT, takerUser));
        BigDecimal positionNotionalSize = BigDecimal.valueOf(45589.5).multiply(BigDecimal.valueOf(2));
        BigDecimal makerSize = BigDecimal.valueOf(45612).multiply(BigDecimal.valueOf(1));
        BigDecimal takerSize = BigDecimal.valueOf(45589).multiply(BigDecimal.valueOf(0.5));
        BigDecimal takerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()))
                .add(takerSize.multiply(market.getMarginRequirement()));
        BigDecimal makerMargin = (positionNotionalSize.multiply(market.getMarginRequirement()))
                .add(makerSize.multiply(market.getMarginRequirement()));
        BigDecimal makerFee = positionNotionalSize.multiply(market.getMakerFee());
        BigDecimal takerFee = makerFee.multiply(BigDecimal.valueOf(-1));
        BigDecimal treasuryFee = BigDecimal.ZERO;
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45589.5))
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(2))
                .setRealisedProfit(makerFee)
                .setTradeCount(2)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setAverageEntryPrice(BigDecimal.valueOf(45589.5))
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(2))
                .setRealisedProfit(takerFee)
                .setTradeCount(2)
                .setFee(takerFee);
        taker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee));
        maker.setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(45589),
                BigDecimal.valueOf(45588),
                BigDecimal.valueOf(45589),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(0.5),
                1,
                4,
                treasuryFee,
                List.of(maker, taker)
        );
        List<Order> orders = orderService.getOpenLimitOrders(market).stream()
                .filter(o -> o.getUser().getId().equals(takerUser.getId())).collect(Collectors.toList());
        Assertions.assertEquals(orders.size(), 1);
        Assertions.assertEquals(orders.get(0).getStatus(), OrderStatus.PARTIALLY_FILLED);
        Assertions.assertEquals(orders.get(0).getQuantity().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(2.5).setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orders.get(0).getRemainingQuantity().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.5).setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testCreateCrossingLimitOrderFailsWithPostOnlyTrue() {
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
    public void testCreateMarketOrderFailsWithInsufficientPassiveVolume() {
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
    public void testCancelOrderFailsWithUnsupportedType() {
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
    public void testCancelOrderFailsWithInvalidPermission() {
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
    public void testCancelOrderFailsWithInvalidID() {
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
    public void testCancelOrderFailsWithStatusFilled() {
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
    public void testBulkInstructions() {
        Market market = createOrderBook(1, 1);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        CreateOrderRequest createOrderRequest = getCreateOrderRequest(market.getId(), BigDecimal.valueOf(5),
                BigDecimal.ONE, MarketSide.BUY, OrderType.LIMIT, makerUser);
        List<CreateOrderRequest> bulkCreateRequest = Arrays.asList(createOrderRequest, createOrderRequest);
        BulkCreateOrderRequest bulkCreate = new BulkCreateOrderRequest().setOrders(bulkCreateRequest);
        bulkCreate.setUser(makerUser);
        List<Order> orders = orderService.createMany(bulkCreate);
        orders.forEach(o -> Assertions.assertEquals(o.getStatus(), OrderStatus.OPEN));
        List<AmendOrderRequest> bulkAmendRequest = Arrays.asList(new AmendOrderRequest(), new AmendOrderRequest());
        for(int i=0; i< orders.size(); i++) {
            bulkAmendRequest.get(i).setId(orders.get(i).getId());
            bulkAmendRequest.get(i).setQuantity(BigDecimal.valueOf(2));
            bulkAmendRequest.get(i).setUser(makerUser);
        }
        BulkAmendOrderRequest bulkAmend = new BulkAmendOrderRequest().setOrders(bulkAmendRequest);
        bulkAmend.setUser(makerUser);
        orders = orderService.amendMany(bulkAmend);
        orders.forEach(o -> Assertions.assertEquals(o.getQuantity().setScale(dps, RoundingMode.HALF_UP),
                BigDecimal.valueOf(2).setScale(dps, RoundingMode.HALF_UP)));
        List<CancelOrderRequest> bulkCancelRequest = Arrays.asList(new CancelOrderRequest(), new CancelOrderRequest());
        for(int i=0; i< orders.size(); i++) {
            bulkCancelRequest.get(i).setId(orders.get(i).getId());
            bulkCancelRequest.get(i).setUser(makerUser);
        }
        BulkCancelOrderRequest bulkCancel = new BulkCancelOrderRequest().setOrders(bulkCancelRequest);
        bulkCancel.setUser(makerUser);
        orders = orderService.cancelMany(bulkCancel);
        orders.forEach(o -> Assertions.assertEquals(o.getStatus(), OrderStatus.CANCELED));
    }

    @Test
    public void testBulkFailsWhenExceedsLimit() {
        try {
            List<CreateOrderRequest> request = new ArrayList<>();
            for(int i=0; i<30; i++) {
                request.add(new CreateOrderRequest());
            }
            orderService.createMany(new BulkCreateOrderRequest().setOrders(request));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MAX_BULK_EXCEEDED);
        }
        try {
            List<AmendOrderRequest> request = new ArrayList<>();
            for(int i=0; i<30; i++) {
                request.add(new AmendOrderRequest());
            }
            orderService.amendMany(new BulkAmendOrderRequest().setOrders(request));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MAX_BULK_EXCEEDED);
        }
        try {
            List<CancelOrderRequest> request = new ArrayList<>();
            for(int i=0; i<30; i++) {
                request.add(new CancelOrderRequest());
            }
            orderService.cancelMany(new BulkCancelOrderRequest().setOrders(request));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MAX_BULK_EXCEEDED);
        }
    }

    @Test
    public void testCreateStopOrderSellUsingLastPrice() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(44000), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.STOP_MARKET, takerUser).setStopTrigger(StopTrigger.LAST_PRICE));
        BigDecimal takerMargin = BigDecimal.valueOf(44000).multiply(market.getMarginRequirement());
        BigDecimal makerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement());
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(takerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(BigDecimal.ZERO)
                .setTradeCount(0)
                .setFee(BigDecimal.ZERO);
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(makerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(BigDecimal.ZERO)
                .setTradeCount(0)
                .setFee(BigDecimal.ZERO);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                1,
                1,
                BigDecimal.ZERO,
                List.of(taker, maker)
        );
    }

    @Test
    public void testCreateStopOrderSell() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(44000), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.STOP_MARKET, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(44000).multiply(market.getMarginRequirement());
        BigDecimal makerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement());
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(takerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(BigDecimal.ZERO)
                .setTradeCount(0)
                .setFee(BigDecimal.ZERO);
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(makerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(BigDecimal.ZERO)
                .setTradeCount(0)
                .setFee(BigDecimal.ZERO);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                1,
                1,
                BigDecimal.ZERO,
                List.of(taker, maker)
        );
    }

    @Test
    public void testCreateStopOrderBuy() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(48000), BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.STOP_MARKET, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(48000).multiply(market.getMarginRequirement());
        BigDecimal makerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement());
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(takerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(BigDecimal.ZERO)
                .setTradeCount(0)
                .setFee(BigDecimal.ZERO);
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(makerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(BigDecimal.ZERO)
                .setTradeCount(0)
                .setFee(BigDecimal.ZERO);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                1,
                1,
                BigDecimal.ZERO,
                List.of(taker, maker)
        );
    }

    @Test
    public void testCreateStopOrderBuyWithImmediateExecution() {
        Market market = createOrderBook(2, 2);
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(44000), BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.STOP_MARKET, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement());
        BigDecimal makerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(45611).multiply(market.getMarginRequirement()));
        BigDecimal takerFee = BigDecimal.valueOf(45610).multiply(market.getTakerFee());
        BigDecimal makerFee = BigDecimal.valueOf(45610).multiply(market.getMakerFee());
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.ONE)
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)))
                .setTradeCount(1)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.ONE)
                .setRealisedProfit(makerFee)
                .setTradeCount(1)
                .setFee(makerFee);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45611),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                2,
                1,
                BigDecimal.ZERO,
                List.of(maker, taker)
        );
    }

    @Test
    public void testCreateStopOrderSellWithImmediateExecution() {
        Market market = createOrderBook(2, 2);
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(48000), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.STOP_MARKET, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(45590).multiply(market.getMarginRequirement());
        BigDecimal makerMargin = BigDecimal.valueOf(45590).multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(45611).multiply(market.getMarginRequirement()));
        BigDecimal takerFee = BigDecimal.valueOf(45590).multiply(market.getTakerFee());
        BigDecimal makerFee = BigDecimal.valueOf(45590).multiply(market.getMakerFee());
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45590))
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.ONE)
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)))
                .setTradeCount(1)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45590))
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.ONE)
                .setRealisedProfit(makerFee)
                .setTradeCount(1)
                .setFee(makerFee);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45589),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                1,
                2,
                BigDecimal.ZERO,
                List.of(maker, taker)
        );
    }

    @Test
    public void testTriggerStopLoss() {
        Market market = createOrderBook(5, 5, 100);
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45550), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.STOP_MARKET, degenUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45490), BigDecimal.valueOf(10),
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(45540).multiply(BigDecimal.valueOf(2))
                .multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(45490)
                        .multiply(BigDecimal.valueOf(8).multiply(market.getMarginRequirement())));
        BigDecimal degenMargin = BigDecimal.valueOf(45390).multiply(market.getMarginRequirement());
        BigDecimal makerMargin = BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(3)).multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(45910).multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(46010).multiply(market.getMarginRequirement())));
        BigDecimal takerFee = BigDecimal.valueOf(45590).multiply(market.getTakerFee()).add(BigDecimal.valueOf(45490).multiply(market.getTakerFee()));
        BigDecimal degenFee = BigDecimal.valueOf(45390).multiply(market.getMakerFee());
        BigDecimal makerFee = BigDecimal.valueOf(45590).multiply(market.getMakerFee()).add(BigDecimal.valueOf(45490).multiply(market.getMakerFee())).add(BigDecimal.valueOf(45390).multiply(market.getMakerFee()));
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45540))
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(2))
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)))
                .setTradeCount(2)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45490))
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(3))
                .setRealisedProfit(makerFee)
                .setTradeCount(3)
                .setFee(makerFee);
        Trader degen = new Trader()
                .setMargin(degenMargin)
                .setBalance(BigDecimal.valueOf(1000).subtract(degenFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45390))
                .setUser(degenUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(1))
                .setRealisedProfit(degenFee.multiply(BigDecimal.valueOf(-1)))
                .setTradeCount(1)
                .setFee(degenFee.multiply(BigDecimal.valueOf(-1)));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        degen.setAvailableBalance(degen.getBalance().subtract(degen.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(45390),
                BigDecimal.valueOf(45290),
                BigDecimal.valueOf(45490),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(8),
                2,
                6,
                BigDecimal.ZERO,
                List.of(taker, maker, degen)
        );
    }

    @Test
    public void testTriggerStopLossForBankruptTraderUsingInsuranceFund() {
        Market market = createOrderBook(20, 20, 100);
        market = market.setInsuranceFund(BigDecimal.valueOf(1000000));
        market = marketRepository.save(market);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, degenUser));
        Optional<Position> degenPositionOptional = positionRepository.findByUserAndMarket(degenUser, market);
        Assertions.assertTrue(degenPositionOptional.isPresent());
        orderService.create(getCreateOrderRequest(market.getId(), degenPositionOptional.get().getLiquidationPrice().add(BigDecimal.ONE),
                BigDecimal.valueOf(1), MarketSide.SELL, OrderType.STOP_MARKET, degenUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(44590), BigDecimal.valueOf(20),
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(45090).multiply(BigDecimal.valueOf(11))
                .multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(44590)
                        .multiply(BigDecimal.valueOf(9).multiply(market.getMarginRequirement())));
        BigDecimal degenMargin = BigDecimal.ZERO;
        BigDecimal makerInitialMargin = BigDecimal.valueOf(47160).multiply(BigDecimal.valueOf(8).multiply(market.getMarginRequirement()));
        BigDecimal unrealisedProfit = (BigDecimal.valueOf(-500).divide(BigDecimal.valueOf(44990), dps, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(44990)).multiply(BigDecimal.valueOf(11));
        BigDecimal makerMaintenanceMargin = BigDecimal.valueOf(44990).multiply(BigDecimal.valueOf(11)).multiply(market.getMarginRequirement());
        BigDecimal unrealisedProfitMargin = (unrealisedProfit.abs().subtract(makerMaintenanceMargin)).max(BigDecimal.ZERO);
        BigDecimal makerMargin = makerInitialMargin.add(makerMaintenanceMargin).add(unrealisedProfitMargin);
        BigDecimal takerFee = BigDecimal.valueOf(45090).multiply(BigDecimal.valueOf(11)).multiply(market.getTakerFee());
        BigDecimal degenFee = BigDecimal.valueOf(44490).multiply(market.getMakerFee()).add(BigDecimal.valueOf(45610).multiply(market.getMakerFee()));
        BigDecimal makerFee = degenFee.add(takerFee);
        BigDecimal realisedProfit = (BigDecimal.valueOf(20).divide(BigDecimal.valueOf(45610), dps, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(45590));
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45090))
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(11))
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)))
                .setTradeCount(11)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee).add(realisedProfit))
                .setAverageEntryPrice(BigDecimal.valueOf(44990))
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(11))
                .setRealisedProfit(makerFee.add(realisedProfit))
                .setTradeCount(13)
                .setFee(makerFee);
        Trader degen = new Trader()
                .setMargin(degenMargin)
                .setBalance(BigDecimal.valueOf(0))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(degenUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(BigDecimal.valueOf(-1000))
                .setTradeCount(2)
                .setFee(degenFee.multiply(BigDecimal.valueOf(-1)));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        degen.setAvailableBalance(BigDecimal.ZERO);
        degen.setBalance(BigDecimal.ZERO);
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(11),
                BigDecimal.valueOf(44490),
                BigDecimal.valueOf(44390),
                BigDecimal.valueOf(44590),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(10),
                8,
                20,
                BigDecimal.ZERO,
                List.of(taker, maker, degen)
        );
    }

    @Test
    public void testTriggerStopLossForBankruptTraderWithoutInsuranceFund() {
        Market market = createOrderBook(20, 20, 100);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, degenUser));
        Optional<Position> degenPositionOptional = positionRepository.findByUserAndMarket(degenUser, market);
        Assertions.assertTrue(degenPositionOptional.isPresent());
        orderService.create(getCreateOrderRequest(market.getId(), degenPositionOptional.get().getLiquidationPrice().add(BigDecimal.ONE), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.STOP_MARKET, degenUser).setStopTrigger(StopTrigger.LAST_PRICE));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(44590), BigDecimal.valueOf(20),
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(45090).multiply(BigDecimal.valueOf(11))
                .multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(44590)
                        .multiply(BigDecimal.valueOf(9).multiply(market.getMarginRequirement())));
        BigDecimal degenMargin = BigDecimal.ZERO;
        BigDecimal makerInitialMargin = BigDecimal.valueOf(47160).multiply(BigDecimal.valueOf(8).multiply(market.getMarginRequirement()));
        BigDecimal unrealisedProfit = (BigDecimal.valueOf(-500).divide(BigDecimal.valueOf(44990), dps, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(44990)).multiply(BigDecimal.valueOf(11));
        BigDecimal makerMaintenanceMargin = BigDecimal.valueOf(44990).multiply(BigDecimal.valueOf(11)).multiply(market.getMarginRequirement());
        BigDecimal unrealisedProfitMargin = (unrealisedProfit.abs().subtract(makerMaintenanceMargin)).max(BigDecimal.ZERO);
        BigDecimal makerMargin = makerInitialMargin.add(makerMaintenanceMargin).add(unrealisedProfitMargin);
        BigDecimal takerFee = BigDecimal.valueOf(45090).multiply(BigDecimal.valueOf(11)).multiply(market.getTakerFee());
        BigDecimal degenFee = BigDecimal.valueOf(44490).multiply(market.getMakerFee()).add(BigDecimal.valueOf(45610).multiply(market.getMakerFee()));
        BigDecimal makerFee = degenFee.add(takerFee);
        BigDecimal realisedProfit = (BigDecimal.valueOf(20).divide(BigDecimal.valueOf(45610), dps, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(45590));
        List<Transaction> lossSocializationTxns = transactionRepository.findAll().stream()
                .filter(t -> t.getType().equals(TransactionType.LOSS_SOCIALIZATION))
                .collect(Collectors.toList());
        Optional<Transaction> makerLossTxn = lossSocializationTxns.stream()
                .filter(t -> t.getUser().getId().equals(makerUser.getId())).findFirst();
        Optional<Transaction> takerLossTxn = lossSocializationTxns.stream()
                .filter(t -> t.getUser().getId().equals(takerUser.getId())).findFirst();
        Assertions.assertTrue(makerLossTxn.isPresent());
        Assertions.assertTrue(takerLossTxn.isPresent());
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee).add(takerLossTxn.get().getAmount()))
                .setAverageEntryPrice(BigDecimal.valueOf(45090))
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(11))
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)))
                .setTradeCount(11)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee).add(realisedProfit).add(makerLossTxn.get().getAmount()))
                .setAverageEntryPrice(BigDecimal.valueOf(44990))
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(11))
                .setRealisedProfit(makerFee.add(realisedProfit))
                .setTradeCount(13)
                .setFee(makerFee);
        Trader degen = new Trader()
                .setMargin(degenMargin)
                .setBalance(BigDecimal.valueOf(0))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(degenUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(BigDecimal.valueOf(-1000))
                .setTradeCount(2)
                .setFee(degenFee.multiply(BigDecimal.valueOf(-1)));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        degen.setAvailableBalance(BigDecimal.ZERO);
        degen.setBalance(BigDecimal.ZERO);
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(11),
                BigDecimal.valueOf(44490),
                BigDecimal.valueOf(44390),
                BigDecimal.valueOf(44590),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(10),
                8,
                20,
                BigDecimal.ZERO,
                List.of(taker, maker, degen)
        );
    }

    @Test
    public void testCannotSelfTradeWithCrossingLimitOrder() {
        Market market = createOrderBook(2, 2);
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45605), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45611), BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.LIMIT, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement());
        BigDecimal makerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(45611).multiply(market.getMarginRequirement()));
        BigDecimal takerFee = BigDecimal.valueOf(45610).multiply(market.getTakerFee());
        BigDecimal makerFee = BigDecimal.valueOf(45610).multiply(market.getMakerFee());
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.ONE)
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)))
                .setTradeCount(1)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.ONE)
                .setRealisedProfit(makerFee)
                .setTradeCount(1)
                .setFee(makerFee);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45605),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                2,
                2,
                BigDecimal.ZERO,
                List.of(maker, taker)
        );
    }

    @Test
    public void testCannotSelfTradeWithMarketOrder() {
        Market market = createOrderBook(2, 2);
        orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45605), BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        BigDecimal takerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement());
        BigDecimal makerMargin = BigDecimal.valueOf(45610).multiply(market.getMarginRequirement()).add(BigDecimal.valueOf(45611).multiply(market.getMarginRequirement()));
        BigDecimal takerFee = BigDecimal.valueOf(45610).multiply(market.getTakerFee());
        BigDecimal makerFee = BigDecimal.valueOf(45610).multiply(market.getMakerFee());
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.ONE)
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)))
                .setTradeCount(1)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee))
                .setAverageEntryPrice(BigDecimal.valueOf(45610))
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.ONE)
                .setRealisedProfit(makerFee)
                .setTradeCount(1)
                .setFee(makerFee);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45590),
                BigDecimal.valueOf(45605),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                2,
                2,
                BigDecimal.ZERO,
                List.of(maker, taker)
        );
    }

    @Test
    public void testReduceOnlyWithAmendOrderSizeDownBuy() {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Order order1 = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(48000), BigDecimal.valueOf(0.8),
                MarketSide.SELL, OrderType.LIMIT, takerUser).setReduceOnly(true));
        Order order2 = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(48000), BigDecimal.valueOf(0.5),
                MarketSide.SELL, OrderType.LIMIT, takerUser).setReduceOnly(true));
        Optional<Order> orderOptional1 = orderRepository.findById(order1.getId());
        Assertions.assertTrue(orderOptional1.isPresent());
        Optional<Order> orderOptional2 = orderRepository.findById(order2.getId());
        Assertions.assertTrue(orderOptional2.isPresent());
        Assertions.assertEquals(orderOptional1.get().getQuantity().doubleValue(), 0.8d);
        Assertions.assertEquals(orderOptional2.get().getQuantity().doubleValue(), 0.2d);
    }

    @Test
    public void testReduceOnlyWithoutAmendOrderSizeDownBuy() {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(48000), BigDecimal.valueOf(0.5),
                MarketSide.SELL, OrderType.LIMIT, takerUser).setReduceOnly(true));
        Optional<Order> orderOptional = orderRepository.findById(order.getId());
        Assertions.assertTrue(orderOptional.isPresent());
        Assertions.assertEquals(orderOptional.get().getQuantity().doubleValue(), 0.5d);
    }

    @Test
    public void testReduceOnlyWithAmendOrderSizeDownSell() {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        Order order1 = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(40000), BigDecimal.valueOf(0.8),
                MarketSide.BUY, OrderType.LIMIT, takerUser).setReduceOnly(true));
        Order order2 = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(40000), BigDecimal.valueOf(0.5),
                MarketSide.BUY, OrderType.LIMIT, takerUser).setReduceOnly(true));
        Optional<Order> orderOptional1 = orderRepository.findById(order1.getId());
        Assertions.assertTrue(orderOptional1.isPresent());
        Optional<Order> orderOptional2 = orderRepository.findById(order2.getId());
        Assertions.assertTrue(orderOptional2.isPresent());
        Assertions.assertEquals(orderOptional1.get().getQuantity().doubleValue(), 0.8d);
        Assertions.assertEquals(orderOptional2.get().getQuantity().doubleValue(), 0.2d);
    }

    @Test
    public void testReduceOnlyWithoutAmendOrderSizeDownSell() {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser));
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(40000), BigDecimal.valueOf(0.5),
                MarketSide.SELL, OrderType.LIMIT, takerUser).setReduceOnly(true));
        Optional<Order> orderOptional = orderRepository.findById(order.getId());
        Assertions.assertTrue(orderOptional.isPresent());
        Assertions.assertEquals(orderOptional.get().getQuantity().doubleValue(), 0.5d);
    }

    @Test
    public void testReduceOnlyFailWhenDoesNotReduceSizeWithNoPositionBuy() {
        Market market = createOrderBook(3, 3);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45590), BigDecimal.ONE,
                    MarketSide.BUY, OrderType.LIMIT, makerUser).setReduceOnly(true));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_CREATE_REDUCE_ONLY_ORDER);
        }
    }

    @Test
    public void testReduceOnlyFailWhenDoesNotReduceSizeWithNoPositionSell() {
        Market market = createOrderBook(3, 3);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45650), BigDecimal.ONE,
                    MarketSide.SELL, OrderType.LIMIT, makerUser).setReduceOnly(true));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_CREATE_REDUCE_ONLY_ORDER);
        }
    }

    @Test
    public void testReduceOnlyFailWhenSameDirection() {
        Market market = createOrderBook(3, 3);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        try {
            orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                    MarketSide.SELL, OrderType.MARKET, takerUser).setReduceOnly(true));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_CREATE_REDUCE_ONLY_ORDER);
        }
    }

    @Test
    public void testReduceOnlyWithoutExcess() {
        Market market = createOrderBook(2, 2);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.BUY, OrderType.MARKET, takerUser).setReduceOnly(true));
        BigDecimal makerMargin = BigDecimal.valueOf(45611).multiply(market.getMarginRequirement());
        BigDecimal takerFee = BigDecimal.valueOf(45610).multiply(market.getTakerFee()).add(BigDecimal.valueOf(45590).multiply(market.getTakerFee()));
        BigDecimal makerFee = BigDecimal.valueOf(45610).multiply(market.getMakerFee()).add(BigDecimal.valueOf(45590).multiply(market.getMakerFee()));
        BigDecimal realisedProfit = (BigDecimal.valueOf(20).divide(BigDecimal.valueOf(45590), dps, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(45610));
        Trader taker = new Trader()
                .setMargin(BigDecimal.ZERO)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee).subtract(realisedProfit))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(takerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)).subtract(realisedProfit))
                .setTradeCount(2)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee).add(realisedProfit))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(makerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(makerFee.add(realisedProfit))
                .setTradeCount(2)
                .setFee(makerFee);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.ZERO,
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45589),
                BigDecimal.valueOf(45611),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                1,
                1,
                BigDecimal.ZERO,
                List.of(maker, taker)
        );
    }

    @Test
    public void testReduceOnlyWithExcess() {
        Market market = createOrderBook(2, 2);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(2),
                MarketSide.BUY, OrderType.MARKET, takerUser).setReduceOnly(true));
        BigDecimal makerMargin = BigDecimal.valueOf(45611).multiply(market.getMarginRequirement());
        BigDecimal takerFee = BigDecimal.valueOf(45610).multiply(market.getTakerFee()).add(BigDecimal.valueOf(45590).multiply(market.getTakerFee()));
        BigDecimal makerFee = BigDecimal.valueOf(45610).multiply(market.getMakerFee()).add(BigDecimal.valueOf(45590).multiply(market.getMakerFee()));
        BigDecimal realisedProfit = (BigDecimal.valueOf(20).divide(BigDecimal.valueOf(45590), dps, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(45610));
        Trader taker = new Trader()
                .setMargin(BigDecimal.ZERO)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee).subtract(realisedProfit))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(takerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(takerFee.multiply(BigDecimal.valueOf(-1)).subtract(realisedProfit))
                .setTradeCount(2)
                .setFee(takerFee.multiply(BigDecimal.valueOf(-1)));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee).add(realisedProfit))
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(makerUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.ZERO)
                .setRealisedProfit(makerFee.add(realisedProfit))
                .setTradeCount(2)
                .setFee(makerFee);
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        validateMarketState(
                market.getId(),
                BigDecimal.ZERO,
                BigDecimal.valueOf(45610),
                BigDecimal.valueOf(45589),
                BigDecimal.valueOf(45611),
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(1),
                1,
                1,
                BigDecimal.ZERO,
                List.of(maker, taker)
        );
    }

    @Test
    public void testCreateOrderFailsWithMissingSize() {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.ONE, null,
                    MarketSide.SELL, OrderType.LIMIT, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_QUANTITY_MANDATORY);
        }
    }

    @Test
    public void testCreateOrderFailsWithMissingType() {
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
    public void testCreateOrderFailsWithNegativeQuantity() {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.ONE, BigDecimal.valueOf(-1),
                    MarketSide.SELL, OrderType.LIMIT, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.NEGATIVE_QUANTITY);
        }
    }

    @Test
    public void testCreateOrderFailsWithNegativePrice() {
        Market market = createOrderBook(1, 1);
        try {
            orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(-1), BigDecimal.valueOf(1),
                    MarketSide.SELL, OrderType.LIMIT, takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.NEGATIVE_PRICE);
        }
    }

    @Test
    public void testCreateOrderFailsWithMissingSide() {
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
    public void testCreateOrderFailsWithMissingPrice() {
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
    public void testCreateOrderFailsWithMissingMarket() {
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
            final BigDecimal createQuantity,
            final BigDecimal amendSize,
            final BigDecimal expectedMargin
    ) throws InterruptedException {
        Market market = createOrderBook(1, 1);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order order = orderService.create(getCreateOrderRequest(market.getId(), createPrice, createQuantity,
                side, OrderType.LIMIT, takerUser));
        Optional<Account> accountOptional = accountRepository.findByUserAndAsset(takerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        BigDecimal startingBalance = BigDecimal.valueOf(INITIAL_BALANCE);
        BigDecimal marginBalance = order.getPrice().multiply(order.getQuantity()).multiply(market.getMarginRequirement());
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
        amendOrderRequest.setQuantity(amendSize);
        orderService.amend(amendOrderRequest);
        order = orderRepository.findById(order.getId()).orElse(new Order());
        accountOptional = accountRepository.findByUserAndAsset(takerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        if(amendSize == null) {
            Assertions.assertEquals(order.getQuantity().setScale(dps, RoundingMode.HALF_UP),
                    createQuantity.setScale(dps, RoundingMode.HALF_UP));
        } else {
            Assertions.assertEquals(order.getQuantity().setScale(dps, RoundingMode.HALF_UP),
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
    public void testAmendOrderFailsWithInsufficientMargin() {
        Market market = createOrderBook(1, 1);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.BUY, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45600));
        amendOrderRequest.setQuantity(BigDecimal.valueOf(100000000));
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INSUFFICIENT_MARGIN);
        }
        Optional<Account> accountOptional = accountRepository.findByUserAndAsset(takerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        BigDecimal startingBalance = BigDecimal.valueOf(INITIAL_BALANCE);
        BigDecimal marginBalance = order.getPrice().multiply(BigDecimal.ONE).multiply(market.getMarginRequirement());
        BigDecimal availableBalance = startingBalance.subtract(marginBalance);
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                marginBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                startingBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                availableBalance.setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendOrderFailsWithImmediateExecutionBuy() {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.BUY, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45620));
        amendOrderRequest.setQuantity(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_AMEND_WOULD_EXECUTE);
        }
    }

    @Test
    public void testAmendOrderFreelyWhenAskEmpty() {
        Market market = createOrderBook(1, 0);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.BUY, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45620));
        amendOrderRequest.setQuantity(BigDecimal.ONE);
        order = orderService.amend(amendOrderRequest);
        order = orderRepository.findById(order.getId()).orElse(new Order());
        Assertions.assertEquals(order.getPrice().setScale(dps, RoundingMode.HALF_UP),
                amendOrderRequest.getPrice().setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendOrderFreelyWhenBidEmpty() {
        Market market = createOrderBook(0, 1);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45580));
        amendOrderRequest.setQuantity(BigDecimal.ONE);
        order = orderService.amend(amendOrderRequest);
        order = orderRepository.findById(order.getId()).orElse(new Order());
        Assertions.assertEquals(order.getPrice().setScale(dps, RoundingMode.HALF_UP),
                amendOrderRequest.getPrice().setScale(dps, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendOrderFailsWithImmediateExecutionSell() {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45580));
        amendOrderRequest.setQuantity(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_AMEND_WOULD_EXECUTE);
        }
    }

    @Test
    public void testAmendOrderFailsWhenPermissionDenied() {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(), BigDecimal.valueOf(45600), BigDecimal.ONE,
                MarketSide.SELL, OrderType.LIMIT, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(makerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45602));
        amendOrderRequest.setQuantity(BigDecimal.ONE);
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
        amendOrderRequest.setQuantity(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Test
    public void testAmendOrderFailsWithInvalidType() {
        Market market = createOrderBook(1, 1);
        Order order = orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(0.1),
                MarketSide.SELL, OrderType.MARKET, takerUser));
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest();
        amendOrderRequest.setId(order.getId());
        amendOrderRequest.setUser(takerUser);
        amendOrderRequest.setPrice(BigDecimal.valueOf(45602));
        amendOrderRequest.setQuantity(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_TYPE);
        }
    }

    @Test
    public void testAmendOrderFailsWithInvalidStatus() {
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
        amendOrderRequest.setQuantity(BigDecimal.ONE);
        try {
            orderService.amend(amendOrderRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    @Test
    public void testBuyStopExceedsLiquidation() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(0.5),
                MarketSide.SELL, OrderType.MARKET, degenUser));
        Optional<Position> degenPositionOptional = positionRepository.findByUserAndMarket(degenUser, market);
        Assertions.assertTrue(degenPositionOptional.isPresent());
        Position position = degenPositionOptional.get();
        try {
            orderService.create(getCreateOrderRequest(
                    market.getId(), position.getLiquidationPrice().add(BigDecimal.ONE),
                    BigDecimal.ONE, MarketSide.BUY, OrderType.STOP_MARKET, degenUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.EXCEEDS_LIQUIDATION_PRICE);
        }
    }

    @Test
    public void testSellStopExceedsLiquidation() {
        Market market = createOrderBook(1, 1);
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(0.5),
                MarketSide.BUY, OrderType.MARKET, degenUser));
        Optional<Position> degenPositionOptional = positionRepository.findByUserAndMarket(degenUser, market);
        Assertions.assertTrue(degenPositionOptional.isPresent());
        Position position = degenPositionOptional.get();
        try {
            orderService.create(getCreateOrderRequest(
                    market.getId(), position.getLiquidationPrice().subtract(BigDecimal.ONE),
                    BigDecimal.ONE, MarketSide.SELL, OrderType.STOP_MARKET, degenUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.EXCEEDS_LIQUIDATION_PRICE);
        }
    }

    private BigDecimal testLiquidation(
            final MarketSide side,
            final int liquidationOffset,
            final boolean withInsuranceFund,
            Market market
    ) {
        if(withInsuranceFund) {
            market = market.setInsuranceFund(BigDecimal.valueOf(1000000));
            market = marketRepository.save(market);
        }
        orderService.create(getCreateOrderRequest(market.getId(), null, BigDecimal.valueOf(1.5),
                side, OrderType.MARKET, degenUser));
        orderService.create(getCreateOrderRequest(market.getId(), side.equals(MarketSide.SELL) ? BigDecimal.valueOf(1) :
                        BigDecimal.valueOf(1000000), BigDecimal.valueOf(0.0001),
                side.equals(MarketSide.BUY) ? MarketSide.SELL : MarketSide.BUY, OrderType.LIMIT, degenUser));
        Optional<Position> positionDegenOptional = positionRepository.findByUserAndMarket(degenUser, market);
        Assertions.assertTrue(positionDegenOptional.isPresent());
        Position positionDegen = positionDegenOptional.get();
        BigDecimal price = side.equals(MarketSide.SELL) ?
                positionDegen.getLiquidationPrice().add(BigDecimal.valueOf(liquidationOffset)) :
                positionDegen.getLiquidationPrice().subtract(BigDecimal.valueOf(liquidationOffset));
        orderService.create(getCreateOrderRequest(market.getId(), price, BigDecimal.valueOf(5000),
                orderService.getOtherSide(side), OrderType.LIMIT, takerUser));
        return price;
    }

    @Test
    public void testLiquidationShortPosition() {
        Market market = createOrderBook(100, 100, 100);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        BigDecimal liqPrice = testLiquidation(MarketSide.SELL, 10, false, market);
        BigDecimal makerAverageEntryPrice = BigDecimal.valueOf(45860);
        BigDecimal takerAverageEntryPrice = BigDecimal.valueOf(45710);
        BigDecimal makerFee = (BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45810).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45910).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46010).multiply(BigDecimal.valueOf(0.5)))
                .add(BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(0.5))))
                .multiply(market.getMakerFee());
        BigDecimal takerFee = (BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45810).multiply(BigDecimal.valueOf(1))))
                .multiply(market.getTakerFee().multiply(BigDecimal.valueOf(-1)));
        BigDecimal averageEntry = ((BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(0.5)))))
                .divide(BigDecimal.valueOf(1.5), dps, RoundingMode.HALF_UP);
        BigDecimal profitPart1 = ((BigDecimal.valueOf(45610).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45610)));
        BigDecimal profitPart2 = ((BigDecimal.valueOf(45710).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45710)));
        BigDecimal makerRealisedProfit = profitPart1.add(profitPart2).add(makerFee);
        BigDecimal treasuryFee = BigDecimal.ZERO;
        BigDecimal makerMargin = BigDecimal.ZERO;
        OrderBook orderBook = orderService.getOrderBook(market);
        for(OrderBookItem item : orderBook.getAsks()) {
            makerMargin = makerMargin.add(item.getPrice().multiply(item.getQuantity().multiply(market.getMarginRequirement())));
        }
        makerMargin = makerMargin.add(BigDecimal.valueOf(3).multiply(makerAverageEntryPrice).multiply(market.getMarginRequirement()));
        BigDecimal takerMargin = BigDecimal.valueOf(4997).multiply(liqPrice).multiply(market.getMarginRequirement());
        takerMargin = takerMargin.add(BigDecimal.valueOf(3).multiply(takerAverageEntryPrice).multiply(market.getMarginRequirement()));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerRealisedProfit))
                .setAverageEntryPrice(makerAverageEntryPrice)
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(3))
                .setRealisedProfit(makerRealisedProfit)
                .setTradeCount(7)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee))
                .setAverageEntryPrice(takerAverageEntryPrice)
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(3))
                .setRealisedProfit(takerFee)
                .setTradeCount(3)
                .setFee(takerFee);
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        Trader degen = new Trader()
                .setMargin(BigDecimal.ZERO)
                .setAvailableBalance(BigDecimal.ZERO)
                .setBalance(BigDecimal.ZERO)
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(degenUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.valueOf(0))
                .setRealisedProfit(BigDecimal.valueOf(-1000))
                .setTradeCount(4)
                .setFee(makerFee.abs().subtract(takerFee.abs()).multiply(BigDecimal.valueOf(-1)));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(46010),
                liqPrice,
                BigDecimal.valueOf(46010),
                BigDecimal.valueOf(4997),
                BigDecimal.valueOf(0.5),
                100,
                96,
                treasuryFee,
                List.of(degen, maker, taker)
        );
    }

    @Test
    public void testLiquidationLongPosition() {
        Market market = createOrderBook(100, 100, 100);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        BigDecimal liqPrice = testLiquidation(MarketSide.BUY, 10, false, market);
        BigDecimal makerAverageEntryPrice = BigDecimal.valueOf(45340);
        BigDecimal takerAverageEntryPrice = BigDecimal.valueOf(45490);
        BigDecimal makerFee = (BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45390).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45290).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45190).multiply(BigDecimal.valueOf(0.5)))
                .add(BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(0.5))))
                .multiply(market.getMakerFee());
        BigDecimal takerFee = (BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45390).multiply(BigDecimal.valueOf(1))))
                .multiply(market.getTakerFee().multiply(BigDecimal.valueOf(-1)));
        BigDecimal averageEntry = ((BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(0.5)))))
                .divide(BigDecimal.valueOf(1.5), dps, RoundingMode.HALF_UP);
        BigDecimal profitPart1 = ((BigDecimal.valueOf(45590).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45590)));
        BigDecimal profitPart2 = ((BigDecimal.valueOf(45490).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45490)));
        BigDecimal makerRealisedProfit = profitPart1.abs().add(profitPart2.abs()).add(makerFee);
        BigDecimal treasuryFee = BigDecimal.ZERO;
        BigDecimal makerMargin = BigDecimal.ZERO;
        OrderBook orderBook = orderService.getOrderBook(market);
        int i = 0;
        for(OrderBookItem item : orderBook.getAsks()) {
            if(item.getQuantity().doubleValue() > 1) {
                i++;
                continue;
            }
            if(i <= 3) {
                i++;
                continue;
            }
            BigDecimal size = item.getQuantity();
            if(i == 4) {
                size = BigDecimal.valueOf(0.5);
            }
            i++;
            makerMargin = makerMargin.add(item.getPrice().multiply(size.multiply(market.getMarginRequirement())));
        }
        makerMargin = makerMargin.add(BigDecimal.valueOf(3).multiply(makerAverageEntryPrice).multiply(market.getMarginRequirement()));
        BigDecimal takerMargin = BigDecimal.valueOf(4997).multiply(liqPrice).multiply(market.getMarginRequirement());
        takerMargin = takerMargin.add(BigDecimal.valueOf(3).multiply(takerAverageEntryPrice).multiply(market.getMarginRequirement()));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerRealisedProfit))
                .setAverageEntryPrice(makerAverageEntryPrice)
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(3))
                .setRealisedProfit(makerRealisedProfit)
                .setTradeCount(7)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee))
                .setAverageEntryPrice(takerAverageEntryPrice)
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(3))
                .setRealisedProfit(takerFee)
                .setTradeCount(3)
                .setFee(takerFee);
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        Trader degen = new Trader()
                .setMargin(BigDecimal.ZERO)
                .setAvailableBalance(BigDecimal.ZERO)
                .setBalance(BigDecimal.ZERO)
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(degenUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.valueOf(0))
                .setRealisedProfit(BigDecimal.valueOf(-1000))
                .setTradeCount(4)
                .setFee(makerFee.abs().subtract(takerFee.abs()).multiply(BigDecimal.valueOf(-1)));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(45190),
                BigDecimal.valueOf(45190),
                liqPrice,
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(4997),
                96,
                100,
                treasuryFee,
                List.of(degen, maker, taker)
        );
    }

    @Test
    public void testLiquidationShortPositionWithLossSocialization() {
        Market market = createOrderBook(100, 100, 100);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        BigDecimal liqPrice = testLiquidation(MarketSide.SELL, 300, false, market);
        BigDecimal makerAverageEntryPrice = BigDecimal.valueOf(46010);
        BigDecimal takerAverageEntryPrice = BigDecimal.valueOf(45860);
        BigDecimal makerFee = (BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45810).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45910).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46010).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46110).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46210).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46310).multiply(BigDecimal.valueOf(0.5)))
                .add(BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(0.5))))
                .multiply(market.getMakerFee());
        BigDecimal takerFee = (BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45810).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45910).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46010).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46110).multiply(BigDecimal.valueOf(1))))
                .multiply(market.getTakerFee().multiply(BigDecimal.valueOf(-1)));
        BigDecimal averageEntry = ((BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(0.5)))))
                .divide(BigDecimal.valueOf(1.5), dps, RoundingMode.HALF_UP);
        BigDecimal profitPart1 = ((BigDecimal.valueOf(45610).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45610)));
        BigDecimal profitPart2 = ((BigDecimal.valueOf(45710).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45710)));
        BigDecimal makerRealisedProfit = profitPart1.add(profitPart2).add(makerFee);
        BigDecimal treasuryFee = BigDecimal.ZERO;
        BigDecimal makerMargin = BigDecimal.ZERO;
        OrderBook orderBook = orderService.getOrderBook(market);
        for(OrderBookItem item : orderBook.getAsks()) {
            makerMargin = makerMargin.add(item.getPrice().multiply(item.getQuantity().multiply(market.getMarginRequirement())));
        }
        makerMargin = makerMargin.add(BigDecimal.valueOf(6).multiply(makerAverageEntryPrice).multiply(market.getMarginRequirement()));
        BigDecimal takerMargin = BigDecimal.valueOf(4994).multiply(liqPrice).multiply(market.getMarginRequirement());
        takerMargin = takerMargin.add(BigDecimal.valueOf(6).multiply(takerAverageEntryPrice).multiply(market.getMarginRequirement()));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerRealisedProfit))
                .setAverageEntryPrice(makerAverageEntryPrice)
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(6))
                .setRealisedProfit(makerRealisedProfit)
                .setTradeCount(10)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee))
                .setAverageEntryPrice(takerAverageEntryPrice)
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(6))
                .setRealisedProfit(takerFee)
                .setTradeCount(6)
                .setFee(takerFee);
        List<Transaction> lossSocializationTxns = transactionRepository.findAll().stream()
                .filter(t -> t.getType().equals(TransactionType.LOSS_SOCIALIZATION)).collect(Collectors.toList());
        BigDecimal lossSocializationTaker = lossSocializationTxns.stream()
                .filter(t -> t.getUser().getId().equals(takerUser.getId()))
                .findFirst().orElse(new Transaction().setAmount(BigDecimal.ZERO)).getAmount();
        BigDecimal lossSocializationMaker = lossSocializationTxns.stream()
                .filter(t -> t.getUser().getId().equals(takerUser.getId()))
                .findFirst().orElse(new Transaction().setAmount(BigDecimal.ZERO)).getAmount();
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()).add(lossSocializationMaker));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()).add(lossSocializationTaker));
        maker.setBalance(maker.getBalance().add(lossSocializationMaker));
        taker.setBalance(taker.getBalance().add(lossSocializationTaker));
        Trader degen = new Trader()
                .setMargin(BigDecimal.ZERO)
                .setAvailableBalance(BigDecimal.ZERO)
                .setBalance(BigDecimal.ZERO)
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(degenUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.valueOf(0))
                .setRealisedProfit(BigDecimal.valueOf(-1000))
                .setTradeCount(4)
                .setFee(makerFee.abs().subtract(takerFee.abs()).multiply(BigDecimal.valueOf(-1)));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(6),
                BigDecimal.valueOf(46310),
                liqPrice,
                BigDecimal.valueOf(46310),
                BigDecimal.valueOf(4994),
                BigDecimal.valueOf(0.5),
                100,
                93,
                treasuryFee,
                List.of(taker, maker, degen)
        );
    }

    @Test
    public void testLiquidationShortPositionWithInsuranceFund() {
        Market market = createOrderBook(100, 100, 100);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        BigDecimal liqPrice = testLiquidation(MarketSide.SELL, 300, true, market);
        BigDecimal makerAverageEntryPrice = BigDecimal.valueOf(46010);
        BigDecimal takerAverageEntryPrice = BigDecimal.valueOf(45860);
        BigDecimal makerFee = (BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45810).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45910).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46010).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46110).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46210).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46310).multiply(BigDecimal.valueOf(0.5)))
                .add(BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(0.5))))
                .multiply(market.getMakerFee());
        BigDecimal takerFee = (BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45810).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45910).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46010).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(46110).multiply(BigDecimal.valueOf(1))))
                .multiply(market.getTakerFee().multiply(BigDecimal.valueOf(-1)));
        BigDecimal averageEntry = ((BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(0.5)))))
                .divide(BigDecimal.valueOf(1.5), dps, RoundingMode.HALF_UP);
        BigDecimal profitPart1 = ((BigDecimal.valueOf(45610).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45610)));
        BigDecimal profitPart2 = ((BigDecimal.valueOf(45710).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45710)));
        BigDecimal makerRealisedProfit = profitPart1.add(profitPart2).add(makerFee);
        BigDecimal treasuryFee = BigDecimal.ZERO;
        BigDecimal makerMargin = BigDecimal.ZERO;
        OrderBook orderBook = orderService.getOrderBook(market);
        for(OrderBookItem item : orderBook.getAsks()) {
            makerMargin = makerMargin.add(item.getPrice().multiply(item.getQuantity().multiply(market.getMarginRequirement())));
        }
        makerMargin = makerMargin.add(BigDecimal.valueOf(6).multiply(makerAverageEntryPrice).multiply(market.getMarginRequirement()));
        BigDecimal takerMargin = BigDecimal.valueOf(4994).multiply(liqPrice).multiply(market.getMarginRequirement());
        takerMargin = takerMargin.add(BigDecimal.valueOf(6).multiply(takerAverageEntryPrice).multiply(market.getMarginRequirement()));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerRealisedProfit))
                .setAverageEntryPrice(makerAverageEntryPrice)
                .setUser(makerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(6))
                .setRealisedProfit(makerRealisedProfit)
                .setTradeCount(10)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee))
                .setAverageEntryPrice(takerAverageEntryPrice)
                .setUser(takerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(6))
                .setRealisedProfit(takerFee)
                .setTradeCount(6)
                .setFee(takerFee);
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        Trader degen = new Trader()
                .setMargin(BigDecimal.ZERO)
                .setAvailableBalance(BigDecimal.ZERO)
                .setBalance(BigDecimal.ZERO)
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(degenUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.valueOf(0))
                .setRealisedProfit(BigDecimal.valueOf(-1000))
                .setTradeCount(4)
                .setFee(makerFee.abs().subtract(takerFee.abs()).multiply(BigDecimal.valueOf(-1)));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(6),
                BigDecimal.valueOf(46310),
                liqPrice,
                BigDecimal.valueOf(46310),
                BigDecimal.valueOf(4994),
                BigDecimal.valueOf(0.5),
                100,
                93,
                treasuryFee,
                List.of(taker, maker, degen)
        );
    }

    @Test
    public void testLiquidationLongPositionWithLossSocialization() {
        Market market = createOrderBook(100, 100, 100);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        BigDecimal liqPrice = testLiquidation(MarketSide.BUY, 300, false, market);
        BigDecimal makerAverageEntryPrice = BigDecimal.valueOf(45190);
        BigDecimal takerAverageEntryPrice = BigDecimal.valueOf(45340);
        BigDecimal makerFee = (BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45390).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45290).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45190).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45090).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(44990).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(44890).multiply(BigDecimal.valueOf(0.5)))
                .add(BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(0.5))))
                .multiply(market.getMakerFee());
        BigDecimal takerFee = (BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45390).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45290).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45190).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45090).multiply(BigDecimal.valueOf(1))))
                .multiply(market.getTakerFee().multiply(BigDecimal.valueOf(-1)));
        BigDecimal averageEntry = ((BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(0.5)))))
                .divide(BigDecimal.valueOf(1.5), dps, RoundingMode.HALF_UP);
        BigDecimal profitPart1 = ((BigDecimal.valueOf(45590).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45590)));
        BigDecimal profitPart2 = ((BigDecimal.valueOf(45490).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45490)));
        BigDecimal makerRealisedProfit = profitPart1.abs().add(profitPart2.abs()).add(makerFee);
        BigDecimal treasuryFee = BigDecimal.ZERO;
        BigDecimal makerMargin = BigDecimal.ZERO;
        OrderBook orderBook = orderService.getOrderBook(market);
        int i = 0;
        for(OrderBookItem item : orderBook.getAsks()) {
            if(item.getQuantity().doubleValue() > 1) {
                i++;
                continue;
            }
            if(i <= 6) {
                i++;
                continue;
            }
            BigDecimal size = item.getQuantity();
            if(i == 7) {
                size = BigDecimal.valueOf(0.5);
            }
            i++;
            makerMargin = makerMargin.add(item.getPrice().multiply(size.multiply(market.getMarginRequirement())));
        }
        makerMargin = makerMargin.add(BigDecimal.valueOf(6).multiply(makerAverageEntryPrice).multiply(market.getMarginRequirement()));
        BigDecimal takerMargin = BigDecimal.valueOf(4994).multiply(liqPrice).multiply(market.getMarginRequirement());
        takerMargin = takerMargin.add(BigDecimal.valueOf(6).multiply(takerAverageEntryPrice).multiply(market.getMarginRequirement()));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerRealisedProfit))
                .setAverageEntryPrice(makerAverageEntryPrice)
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(6))
                .setRealisedProfit(makerRealisedProfit)
                .setTradeCount(10)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee))
                .setAverageEntryPrice(takerAverageEntryPrice)
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(6))
                .setRealisedProfit(takerFee)
                .setTradeCount(6)
                .setFee(takerFee);
        List<Transaction> lossSocializationTxns = transactionRepository.findAll().stream()
                .filter(t -> t.getType().equals(TransactionType.LOSS_SOCIALIZATION)).collect(Collectors.toList());
        BigDecimal lossSocializationTaker = lossSocializationTxns.stream()
                .filter(t -> t.getUser().getId().equals(takerUser.getId()))
                .findFirst().orElse(new Transaction().setAmount(BigDecimal.ZERO)).getAmount();
        BigDecimal lossSocializationMaker = lossSocializationTxns.stream()
                .filter(t -> t.getUser().getId().equals(takerUser.getId()))
                .findFirst().orElse(new Transaction().setAmount(BigDecimal.ZERO)).getAmount();
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()).add(lossSocializationMaker));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()).add(lossSocializationTaker));
        maker.setBalance(maker.getBalance().add(lossSocializationMaker));
        taker.setBalance(taker.getBalance().add(lossSocializationTaker));
        Trader degen = new Trader()
                .setMargin(BigDecimal.ZERO)
                .setAvailableBalance(BigDecimal.ZERO)
                .setBalance(BigDecimal.ZERO)
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(degenUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.valueOf(0))
                .setRealisedProfit(BigDecimal.valueOf(-1000))
                .setTradeCount(4)
                .setFee(makerFee.abs().subtract(takerFee.abs()).multiply(BigDecimal.valueOf(-1)));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(6),
                BigDecimal.valueOf(44890),
                BigDecimal.valueOf(44890),
                liqPrice,
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(4994),
                93,
                100,
                treasuryFee,
                List.of(maker, taker, degen)
        );
    }

    @Test
    public void testLiquidationLongPositionWithInsuranceFund() {
        Market market = createOrderBook(100, 100, 100);
        int dps = market.getSettlementAsset().getDecimalPlaces();
        BigDecimal liqPrice = testLiquidation(MarketSide.BUY, 300, true, market);
        BigDecimal makerAverageEntryPrice = BigDecimal.valueOf(45190);
        BigDecimal takerAverageEntryPrice = BigDecimal.valueOf(45340);
        BigDecimal makerFee = (BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45390).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45290).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45190).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45090).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(44990).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(44890).multiply(BigDecimal.valueOf(0.5)))
                .add(BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(0.5))))
                .multiply(market.getMakerFee());
        BigDecimal takerFee = (BigDecimal.valueOf(45590).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45490).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45390).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45290).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45190).multiply(BigDecimal.valueOf(1)))
                .add(BigDecimal.valueOf(45090).multiply(BigDecimal.valueOf(1))))
                .multiply(market.getTakerFee().multiply(BigDecimal.valueOf(-1)));
        BigDecimal averageEntry = ((BigDecimal.valueOf(45610).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(45710).multiply(BigDecimal.valueOf(0.5)))))
                .divide(BigDecimal.valueOf(1.5), dps, RoundingMode.HALF_UP);
        BigDecimal profitPart1 = ((BigDecimal.valueOf(45590).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(1).multiply(BigDecimal.valueOf(45590)));
        BigDecimal profitPart2 = ((BigDecimal.valueOf(45490).subtract(averageEntry))
                .divide(averageEntry, dps, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(0.5).multiply(BigDecimal.valueOf(45490)));
        BigDecimal makerRealisedProfit = profitPart1.abs().add(profitPart2.abs()).add(makerFee);
        BigDecimal treasuryFee = BigDecimal.ZERO;
        BigDecimal makerMargin = BigDecimal.ZERO;
        OrderBook orderBook = orderService.getOrderBook(market);
        int i = 0;
        for(OrderBookItem item : orderBook.getAsks()) {
            if(item.getQuantity().doubleValue() > 1) {
                i++;
                continue;
            }
            if(i <= 6) {
                i++;
                continue;
            }
            BigDecimal size = item.getQuantity();
            if(i == 7) {
                size = BigDecimal.valueOf(0.5);
            }
            i++;
            makerMargin = makerMargin.add(item.getPrice().multiply(size.multiply(market.getMarginRequirement())));
        }
        makerMargin = makerMargin.add(BigDecimal.valueOf(6).multiply(makerAverageEntryPrice).multiply(market.getMarginRequirement()));
        BigDecimal takerMargin = BigDecimal.valueOf(4994).multiply(liqPrice).multiply(market.getMarginRequirement());
        takerMargin = takerMargin.add(BigDecimal.valueOf(6).multiply(takerAverageEntryPrice).multiply(market.getMarginRequirement()));
        Trader maker = new Trader()
                .setMargin(makerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(makerRealisedProfit))
                .setAverageEntryPrice(makerAverageEntryPrice)
                .setUser(makerUser)
                .setSide(MarketSide.BUY)
                .setOpenVolume(BigDecimal.valueOf(6))
                .setRealisedProfit(makerRealisedProfit)
                .setTradeCount(10)
                .setFee(makerFee);
        Trader taker = new Trader()
                .setMargin(takerMargin)
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE).add(takerFee))
                .setAverageEntryPrice(takerAverageEntryPrice)
                .setUser(takerUser)
                .setSide(MarketSide.SELL)
                .setOpenVolume(BigDecimal.valueOf(6))
                .setRealisedProfit(takerFee)
                .setTradeCount(6)
                .setFee(takerFee);
        maker.setAvailableBalance(maker.getBalance().subtract(maker.getMargin()));
        taker.setAvailableBalance(taker.getBalance().subtract(taker.getMargin()));
        Trader degen = new Trader()
                .setMargin(BigDecimal.ZERO)
                .setAvailableBalance(BigDecimal.ZERO)
                .setBalance(BigDecimal.ZERO)
                .setAverageEntryPrice(BigDecimal.ZERO)
                .setUser(degenUser)
                .setSide(null)
                .setOpenVolume(BigDecimal.valueOf(0))
                .setRealisedProfit(BigDecimal.valueOf(-1000))
                .setTradeCount(4)
                .setFee(makerFee.abs().subtract(takerFee.abs()).multiply(BigDecimal.valueOf(-1)));
        validateMarketState(
                market.getId(),
                BigDecimal.valueOf(6),
                BigDecimal.valueOf(44890),
                BigDecimal.valueOf(44890),
                liqPrice,
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(4994),
                93,
                100,
                treasuryFee,
                List.of(maker, taker, degen)
        );

    }

    @Data
    @Accessors(chain = true)
    private static class Trader {
        private MarketSide side;
        private BigDecimal averageEntryPrice;
        private BigDecimal margin;
        private BigDecimal availableBalance;
        private BigDecimal balance;
        private BigDecimal realisedProfit;
        private BigDecimal openVolume;
        private int tradeCount;
        private User user;
        private BigDecimal fee;
    }

    private void validateMarketState(
            final UUID marketId,
            final BigDecimal openVolume,
            final BigDecimal lastPrice,
            final BigDecimal bidPrice,
            final BigDecimal askPrice,
            final BigDecimal bidSize,
            final BigDecimal askSize,
            final int bidCount,
            final int askCount,
            final BigDecimal treasuryFee,
            final List<Trader> traders
    ) {
        Optional<Market> marketOptional = marketRepository.findById(marketId);
        Assertions.assertTrue(marketOptional.isPresent());
        Market market = marketOptional.get();
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Assertions.assertEquals(market.getOpenVolume().setScale(dps, RoundingMode.HALF_UP),
                openVolume.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(market.getLastPrice().setScale(dps, RoundingMode.HALF_UP),
                lastPrice.setScale(dps, RoundingMode.HALF_UP));
        Optional<Asset> assetOptional = assetRepository.findById(market.getSettlementAsset().getId());
        Assertions.assertTrue(assetOptional.isPresent());
        Assertions.assertEquals(assetOptional.get().getTreasuryBalance().setScale(dps, RoundingMode.HALF_UP),
                treasuryFee.setScale(dps, RoundingMode.HALF_UP));
        OrderBook orderBook = orderService.getOrderBook(market);
        Assertions.assertEquals(orderBook.getBids().size(), bidCount);
        Assertions.assertEquals(orderBook.getAsks().size(), askCount);
        Assertions.assertTrue(orderBook.getBids().get(0).getQuantity().doubleValue() <= bidSize.doubleValue());
        Assertions.assertTrue(orderBook.getAsks().get(0).getQuantity().doubleValue() <= askSize.doubleValue());
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice().setScale(dps, RoundingMode.HALF_UP),
                bidPrice.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().setScale(dps, RoundingMode.HALF_UP),
                askPrice.setScale(dps, RoundingMode.HALF_UP));
        for(Trader trader : traders) {
            Optional<Position> positionOptional = positionRepository.findByUserAndMarket(trader.getUser(), market);
            Assertions.assertTrue(positionOptional.isPresent());
            Position position = positionOptional.get();
            Optional<Account> accountOptional = accountRepository.findByUserAndAsset(
                    trader.getUser(), market.getSettlementAsset());
            Assertions.assertTrue(accountOptional.isPresent());
            Assertions.assertEquals(position.getQuantity().setScale(dps, RoundingMode.HALF_UP),
                    trader.getOpenVolume().setScale(dps, RoundingMode.HALF_UP));
            Assertions.assertEquals(position.getAverageEntryPrice().setScale(dps, RoundingMode.HALF_UP),
                    trader.getAverageEntryPrice().setScale(dps, RoundingMode.HALF_UP));
            Assertions.assertEquals(position.getSide(), trader.getSide());
            List<Transaction> txns = transactionRepository
                    .findByUserAndAsset(trader.getUser(), market.getSettlementAsset())
                    .stream()
                    .filter(t -> t.getType().equals(TransactionType.FEE))
                    .collect(Collectors.toList());
            Assertions.assertEquals(txns.size(), trader.getTradeCount());
            BigDecimal feeFromTxns = BigDecimal.ZERO;
            for(int i=0; i<trader.getTradeCount(); i++) {
                feeFromTxns = feeFromTxns.add(txns.get(i).getAmount());
                Assertions.assertEquals(txns.get(i).getType(), TransactionType.FEE);
            }
            Assertions.assertEquals(feeFromTxns.setScale(dps, RoundingMode.HALF_UP),
                    trader.getFee().setScale(dps, RoundingMode.HALF_UP));
            BigDecimal gain = BigDecimal.ZERO;
            if(trader.getAverageEntryPrice().doubleValue() > 0) {
                gain = market.getMarkPrice().subtract(trader.getAverageEntryPrice())
                        .divide(trader.getAverageEntryPrice(), dps, RoundingMode.HALF_UP);
            }
            BigDecimal unrealisedProfit = gain.multiply(trader.getOpenVolume())
                    .multiply(trader.getAverageEntryPrice());
            if(market.getMarkPrice().doubleValue() > position.getAverageEntryPrice().doubleValue() &&
                    MarketSide.SELL.equals(position.getSide()) && unrealisedProfit.doubleValue() > 0) {
                unrealisedProfit = unrealisedProfit.multiply(BigDecimal.valueOf(-1));
            }
            if(market.getMarkPrice().doubleValue() < position.getAverageEntryPrice().doubleValue() &&
                    position.getSide().equals(MarketSide.BUY) && unrealisedProfit.doubleValue() > 0) {
                unrealisedProfit = unrealisedProfit.multiply(BigDecimal.valueOf(-1));
            }
            if(market.getMarkPrice().doubleValue() < position.getAverageEntryPrice().doubleValue() &&
                    MarketSide.SELL.equals(position.getSide()) && unrealisedProfit.doubleValue() < 0) {
                unrealisedProfit = unrealisedProfit.multiply(BigDecimal.valueOf(-1));
            }
            if(market.getMarkPrice().doubleValue() > position.getAverageEntryPrice().doubleValue() &&
                    MarketSide.BUY.equals(position.getSide()) && unrealisedProfit.doubleValue() < 0) {
                unrealisedProfit = unrealisedProfit.multiply(BigDecimal.valueOf(-1));
            }
            Assertions.assertEquals(unrealisedProfit.setScale(dps, RoundingMode.HALF_UP),
                    position.getUnrealisedPnl().setScale(dps, RoundingMode.HALF_UP));
            Assertions.assertEquals(trader.getRealisedProfit().doubleValue(),
                    position.getRealisedPnl().doubleValue(), 0.001d);
            Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                    trader.getMargin().setScale(dps, RoundingMode.HALF_UP));
            Assertions.assertEquals(accountOptional.get().getAvailableBalance().doubleValue(),
                    trader.getAvailableBalance().doubleValue(), 1d);
            Assertions.assertEquals(accountOptional.get().getBalance().doubleValue(),
                    trader.getBalance().doubleValue(), 1d);
            Assertions.assertEquals(accountOptional.get().getAvailableBalance()
                    .add(accountOptional.get().getMarginBalance()).doubleValue(),
                    accountOptional.get().getBalance().doubleValue(), 1d);
            List<Transaction> settlementTxns = transactionRepository
                    .findByUserAndAsset(trader.getUser(), market.getSettlementAsset())
                    .stream().filter(t -> !t.getType().equals(TransactionType.LOSS_SOCIALIZATION))
                    .collect(Collectors.toList());
            double sumTxns = settlementTxns.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
            Assertions.assertEquals(sumTxns, position.getRealisedPnl().doubleValue(), 0.001d);
        }
    }
}