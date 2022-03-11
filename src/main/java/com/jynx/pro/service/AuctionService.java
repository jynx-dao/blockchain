package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.entity.AuctionTrigger;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.Position;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.repository.AuctionTriggerRepository;
import com.jynx.pro.repository.MarketRepository;
import com.jynx.pro.repository.PositionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuctionService {

    @Autowired
    private AuctionTriggerRepository auctionTriggerRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private MarketRepository marketRepository;
    @Autowired
    private PositionService positionService;
    @Autowired
    private PositionRepository positionRepository;

    /**
     * Check auction triggers for a market
     *
     * @param orderBook {@link OrderBook}
     * @param openVolume the open volume
     */
    public boolean checkTriggers(
            final BigDecimal openVolume,
            final OrderBook orderBook,
            final List<AuctionTrigger> triggers
    ) {
        if(orderBook.getBids().size() == 0 || orderBook.getAsks().size() == 0) {
            return true;
        }
        boolean auctionTriggered = false;
        BigDecimal bestBid = orderBook.getBids().get(0).getPrice();
        BigDecimal bestAsk = orderBook.getAsks().get(0).getPrice();
        for(AuctionTrigger trigger : triggers) {
            BigDecimal bidThreshold = bestBid.multiply(BigDecimal.ONE.subtract(trigger.getDepth()));
            BigDecimal askThreshold = bestAsk.multiply(BigDecimal.ONE.add(trigger.getDepth()));
            double bidDepth = orderBook.getBids().stream()
                    .filter(ob -> ob.getPrice().doubleValue() >= bidThreshold.doubleValue())
                    .mapToDouble(ob -> ob.getQuantity().doubleValue())
                    .sum();
            double askDepth = orderBook.getAsks().stream()
                    .filter(ob -> ob.getPrice().doubleValue() <= askThreshold.doubleValue())
                    .mapToDouble(ob -> ob.getQuantity().doubleValue())
                    .sum();
            double bidOpenVolumeRatio = bidDepth / openVolume.doubleValue();
            double askOpenVolumeRatio = askDepth / openVolume.doubleValue();
            if(bidOpenVolumeRatio < trigger.getOpenVolumeRatio().doubleValue() ||
                    askOpenVolumeRatio < trigger.getOpenVolumeRatio().doubleValue()) {
                auctionTriggered = true;
                break;
            }
        }
        return auctionTriggered;
    }

    /**
     * Enter market auction if triggered
     *
     * @param market {@link Market}
     */
    public void enterAuction(
            final Market market
    ) {
        BigDecimal openVolume = market.getOpenVolume();
        OrderBook orderBook = orderService.getOrderBook(market);
        List<AuctionTrigger> triggers = auctionTriggerRepository.findByMarketId(market.getId());
        boolean triggered = checkTriggers(openVolume, orderBook, triggers);
        if(triggered) {
            market.setStatus(MarketStatus.AUCTION);
            marketRepository.save(market);
        }
    }

    /**
     * Gets the uncrossing price of an auction
     *
     * @param market {@link Market}
     *
     * @return the uncrossing price
     */
    public BigDecimal getUncrossingPrice(
            final Market market
    ) {
        int dps = market.getSettlementAsset().getDecimalPlaces();
        OrderBook orderBook = orderService.getOrderBook(market);
        if(orderBook.getBids().size() == 0 || orderBook.getAsks().size() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal bestBid = orderBook.getBids().get(0).getPrice();
        BigDecimal bestAsk = orderBook.getAsks().get(0).getPrice();
        List<OrderBookItem> crossingAsks = orderBook.getAsks().stream()
                .filter(ob -> ob.getPrice().doubleValue() <= bestBid.doubleValue())
                .collect(Collectors.toList());
        List<OrderBookItem> crossingBids = orderBook.getAsks().stream()
                .filter(ob -> ob.getPrice().doubleValue() >= bestAsk.doubleValue())
                .collect(Collectors.toList());
        if(crossingAsks.size() == 0 || crossingBids.size() == 0) {
            return (bestBid.add(bestAsk)).divide(BigDecimal.valueOf(2), dps, RoundingMode.HALF_UP);
        }
        double sumCrossingAsks = crossingAsks.stream().mapToDouble(ob -> ob.getQuantity().doubleValue()).sum();
        double sumCrossingBids = crossingBids.stream().mapToDouble(ob -> ob.getQuantity().doubleValue()).sum();
        double sumProductCrossingAsks = crossingAsks.stream().mapToDouble(ob ->
                ob.getQuantity().multiply(ob.getPrice()).doubleValue()).sum();
        double sumProductCrossingBids = crossingBids.stream().mapToDouble(ob ->
                ob.getQuantity().multiply(ob.getPrice()).doubleValue()).sum();
        double vwapCrossingBids = sumProductCrossingBids / sumCrossingBids;
        double vwapCrossingAsks = sumProductCrossingAsks / sumCrossingAsks;
        return BigDecimal.valueOf((vwapCrossingBids + vwapCrossingAsks) / 2);
    }

    /**
     * Updates a {@link Map} which tracks the open volume of a position as orders become
     * executed at the uncrossing of an auction
     *
     * @param order {@link Order} to execute
     * @param expectedOpenVolumeByUser the map tracking user positions
     * @param modifier modifier to handle long / short orders
     */
    private void modifyOpenVolumeForPosition(
            final Order order,
            final Map<UUID, BigDecimal> expectedOpenVolumeByUser,
            final BigDecimal modifier
    ) {
        Position position = positionService.getAndCreate(order.getUser(), order.getMarket());
        UUID userId = order.getUser().getId();
        expectedOpenVolumeByUser.putIfAbsent(userId, position.getQuantity());
        if(position.getSide().equals(MarketSide.BUY)) {
            expectedOpenVolumeByUser.put(userId,
                    expectedOpenVolumeByUser.get(userId).add(order.getRemainingQuantity().multiply(modifier)));
        } else if(position.getSide().equals(MarketSide.SELL)) {
            expectedOpenVolumeByUser.put(userId,
                    expectedOpenVolumeByUser.get(userId).subtract(order.getRemainingQuantity().multiply(modifier)));
        }
    }

    /**
     * Get the change in open volume at uncrossing of an auction
     *
     * @param market {@link Market}
     * @param uncrossingPrice the uncrossing price
     *
     * @return the change in open volume
     */
    public BigDecimal getOpenVolumeDeltaAtUncrossing(
            final Market market,
            final BigDecimal uncrossingPrice
    ) {
        Map<UUID, BigDecimal> expectedOpenVolumeByUser = new HashMap<>();
        List<Order> openOrders = orderService.getOpenLimitOrders(market);
        List<Order> buyOrders = openOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.BUY) &&
                        o.getPrice().doubleValue() >= uncrossingPrice.doubleValue())
                .collect(Collectors.toList());
        List<Order> sellOrders = openOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.SELL) &&
                        o.getPrice().doubleValue() <= uncrossingPrice.doubleValue())
                .collect(Collectors.toList());
        for(Order order : buyOrders) {
            modifyOpenVolumeForPosition(order, expectedOpenVolumeByUser, BigDecimal.ONE);
        }
        for(Order order : sellOrders) {
            modifyOpenVolumeForPosition(order, expectedOpenVolumeByUser, BigDecimal.valueOf(-1));
        }
        BigDecimal deltaOpenVolume = BigDecimal.ZERO;
        for(Map.Entry<UUID, BigDecimal> set : expectedOpenVolumeByUser.entrySet()) {
            Optional<Position> positionOptional = positionRepository.findById(set.getKey());
            if(positionOptional.isPresent()) {
                deltaOpenVolume = deltaOpenVolume.add(set.getValue().subtract(positionOptional.get().getQuantity()));
            }
        }
        return deltaOpenVolume;
    }

    /**
     * Get the {@link OrderBook} as it would exist after uncrossing of an auction
     *
     * @param market {@link Market}
     *
     * @return {@link OrderBook}
     */
    public OrderBook getOrderBookAfterUncrossing(
            final Market market
    ) {
        OrderBook orderBook = orderService.getOrderBook(market);
        BigDecimal uncrossingPrice = getUncrossingPrice(market);
        List<OrderBookItem> asks = orderBook.getAsks().stream()
                .filter(ob -> ob.getPrice().doubleValue() >= uncrossingPrice.doubleValue())
                .collect(Collectors.toList());
        List<OrderBookItem> bids = orderBook.getBids().stream()
                .filter(ob -> ob.getPrice().doubleValue() <= uncrossingPrice.doubleValue())
                .collect(Collectors.toList());
        return new OrderBook().setBids(bids).setAsks(asks);
    }

    /**
     * Exit an auction when all conditions are met to do so
     *
     * @param market {@link Market}
     */
    public void exitAuction(
            final Market market
    ) {
        BigDecimal uncrossingPrice = getUncrossingPrice(market);
        BigDecimal expectedOpenVolume = market.getOpenVolume().add(
                getOpenVolumeDeltaAtUncrossing(market, uncrossingPrice));
        OrderBook expectedOrderBook = getOrderBookAfterUncrossing(market);
        // TODO - to determine if the auction can exit, we need to simulate uncrossing
        // and also check that the liquidity leftover would satisfy every trigger
        // with some reasonable buffer [is 20% enough?]
    }
}