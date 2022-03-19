package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.constant.OrderAction;
import com.jynx.pro.constant.OrderStatus;
import com.jynx.pro.entity.*;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.repository.AuctionTriggerRepository;
import com.jynx.pro.repository.MarketRepository;
import com.jynx.pro.repository.OrderHistoryRepository;
import com.jynx.pro.request.BatchValidatorRequest;
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
public class AuctionService {

    @Autowired
    private AuctionTriggerRepository auctionTriggerRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TradeService tradeService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private OrderHistoryRepository orderHistoryRepository;
    @Autowired
    private MarketRepository marketRepository;
    @Autowired
    private UUIDUtils uuidUtils;

    /**
     * Checks if an auction is triggered for a market
     *
     * @param openVolume the open volume
     * @param orderBook {@link OrderBook}
     * @param triggers {@link List<AuctionTrigger>}
     * @param market {@link Market}
     */
    public boolean isAuctionTriggered(
            final BigDecimal openVolume,
            final OrderBook orderBook,
            final List<AuctionTrigger> triggers,
            final Market market
    ) {
        int dps = market.getSettlementAsset().getDecimalPlaces();
        if(orderBook.getBids().size() == 0 || orderBook.getAsks().size() == 0) {
            return true;
        }
        boolean auctionTriggered = false;
        BigDecimal bestBid = orderBook.getBids().get(0).getPrice();
        BigDecimal bestAsk = orderBook.getAsks().get(0).getPrice();
        for(AuctionTrigger trigger : triggers) {
            BigDecimal bidThreshold = bestBid.multiply(BigDecimal.ONE.subtract(trigger.getDepth()));
            BigDecimal askThreshold = bestAsk.multiply(BigDecimal.ONE.add(trigger.getDepth()));
            BigDecimal bidDepth = orderBook.getBids().stream()
                    .filter(ob -> ob.getPrice().doubleValue() >= bidThreshold.doubleValue())
                    .map(OrderBookItem::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal askDepth = orderBook.getAsks().stream()
                    .filter(ob -> ob.getPrice().doubleValue() <= askThreshold.doubleValue())
                    .map(OrderBookItem::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal bidOpenVolumeRatio = bidDepth.divide(openVolume, dps, RoundingMode.HALF_UP);
            BigDecimal askOpenVolumeRatio = askDepth.divide(openVolume, dps, RoundingMode.HALF_UP);
            if(bidOpenVolumeRatio.doubleValue() < trigger.getOpenVolumeRatio().doubleValue() ||
                    askOpenVolumeRatio.doubleValue() < trigger.getOpenVolumeRatio().doubleValue()) {
                auctionTriggered = true;
                break;
            }
        }
        return auctionTriggered;
    }

    /**
     * Enter market auctions if triggered
     */
    public List<Market> enterAuctions() {
        List<Market> markets = marketRepository.findByStatusIn(List.of(MarketStatus.ACTIVE));
        List<Market> triggeredMarkets = new ArrayList<>();
        for(Market market : markets) {
            BigDecimal openVolume = market.getOpenVolume();
            OrderBook orderBook = orderService.getOrderBook(market);
            List<AuctionTrigger> triggers = auctionTriggerRepository.findByMarketId(market.getId());
            if(triggers.size() > 0) {
                boolean triggered = isAuctionTriggered(openVolume, orderBook, triggers, market);
                if (triggered) {
                    market.setStatus(MarketStatus.AUCTION);
                    triggeredMarkets.add(market);
                }
            }
        }
        marketRepository.saveAll(markets);
        return triggeredMarkets;
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
        List<OrderBookItem> crossingBids = orderBook.getBids().stream()
                .filter(ob -> ob.getPrice().doubleValue() >= bestAsk.doubleValue())
                .collect(Collectors.toList());
        if(crossingAsks.size() == 0) {
            return (bestBid.add(bestAsk)).divide(BigDecimal.valueOf(2), dps, RoundingMode.HALF_UP);
        }
        BigDecimal sumCrossingAsks = crossingAsks.stream().map(OrderBookItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCrossingBids = crossingBids.stream().map(OrderBookItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);;
        BigDecimal sumProductCrossingAsks = crossingAsks.stream().map(ob ->
                ob.getQuantity().multiply(ob.getPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumProductCrossingBids = crossingBids.stream().map(ob ->
                ob.getQuantity().multiply(ob.getPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vwapCrossingBids = sumProductCrossingBids.divide(sumCrossingBids, dps, RoundingMode.HALF_UP);
        BigDecimal vwapCrossingAsks = sumProductCrossingAsks.divide(sumCrossingAsks, dps, RoundingMode.HALF_UP);
        return (vwapCrossingBids.add(vwapCrossingAsks)).multiply(BigDecimal.valueOf(0.5));
    }

    /**
     * Get the uncrossing volume when a market is in auction
     *
     * @param market {@link Market}
     *
     * @return the change in open volume
     */
    public BigDecimal getUncrossingVolume(
            final Market market
    ) {
        OrderBook orderBook = orderService.getOrderBook(market);
        if(orderBook.getBids().size() == 0 || orderBook.getAsks().size() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal bestBid = orderBook.getBids().get(0).getPrice();
        BigDecimal bestAsk = orderBook.getAsks().get(0).getPrice();
        List<OrderBookItem> crossingAsks = orderBook.getAsks().stream()
                .filter(ob -> ob.getPrice().doubleValue() <= bestBid.doubleValue())
                .collect(Collectors.toList());
        List<OrderBookItem> crossingBids = orderBook.getBids().stream()
                .filter(ob -> ob.getPrice().doubleValue() >= bestAsk.doubleValue())
                .collect(Collectors.toList());
        BigDecimal sumCrossingAsks = crossingAsks.stream().map(OrderBookItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCrossingBids = crossingBids.stream().map(OrderBookItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sumCrossingAsks.min(sumCrossingBids);
    }

    /**
     * Get the orders that would execute at auction uncrossing
     *
     * @param market {@link Market}
     *
     * @return {@link List<Order>}
     */
    public List<Order> getUncrossingOrders(
            final Market market
    ) {
        List<Order> openOrders = orderService.getOpenLimitOrders(market);
        List<Order> bids = openOrders.stream().filter(o -> o.getSide().equals(MarketSide.BUY))
                .sorted(Comparator.comparing(Order::getPrice).reversed())
                .collect(Collectors.toList());
        List<Order> asks = openOrders.stream().filter(o -> o.getSide().equals(MarketSide.SELL))
                .sorted(Comparator.comparing(Order::getPrice))
                .collect(Collectors.toList());
        if(bids.size() == 0 || asks.size() == 0) {
            return Collections.emptyList();
        }
        BigDecimal bestBid = bids.get(0).getPrice();
        BigDecimal bestAsk = asks.get(0).getPrice();
        List<Order> crossingAsks = asks.stream()
                .filter(ob -> ob.getPrice().doubleValue() <= bestBid.doubleValue())
                .collect(Collectors.toList());
        List<Order> crossingBids = bids.stream()
                .filter(ob -> ob.getPrice().doubleValue() >= bestAsk.doubleValue())
                .collect(Collectors.toList());
        BigDecimal volumeCrossingBids = crossingBids.stream()
                .map(Order::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal volumeCrossingAsks = crossingAsks.stream()
                .map(Order::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Order> uncrossingOrders = new ArrayList<>();
        if(volumeCrossingAsks.doubleValue() > volumeCrossingBids.doubleValue()) {
            uncrossingOrders.addAll(crossingBids);
            BigDecimal matched = volumeCrossingBids;
            for(Order order : crossingAsks) {
                uncrossingOrders.add(order);
                matched = matched.subtract(order.getRemainingQuantity());
                if(matched.doubleValue() < 0d) {
                    break;
                }
            }
        } else if(volumeCrossingBids.doubleValue() > volumeCrossingAsks.doubleValue()) {
            uncrossingOrders.addAll(crossingAsks);
            BigDecimal matched = volumeCrossingAsks;
            for(Order order : crossingBids) {
                uncrossingOrders.add(order);
                matched = matched.subtract(order.getRemainingQuantity());
                if(matched.doubleValue() < 0d) {
                    break;
                }
            }
        } else {
            uncrossingOrders.addAll(crossingAsks);
            uncrossingOrders.addAll(crossingBids);
        }
        return uncrossingOrders;
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
        List<Order> uncrossingOrders = getUncrossingOrders(market);
        BigDecimal volumeBids = uncrossingOrders.stream().filter(o -> o.getSide().equals(MarketSide.BUY))
                .map(Order::getRemainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal volumeAsks = uncrossingOrders.stream().filter(o -> o.getSide().equals(MarketSide.SELL))
                .map(Order::getRemainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Order> openOrders = orderService.getOpenLimitOrders(market);
        List<UUID> uncrossingBids = uncrossingOrders.stream().filter(o -> o.getSide().equals(MarketSide.BUY))
                .map(Order::getId).collect(Collectors.toList());
        List<UUID> uncrossingAsks = uncrossingOrders.stream().filter(o -> o.getSide().equals(MarketSide.SELL))
                .map(Order::getId).collect(Collectors.toList());
        List<OrderBookItem> bids = openOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.BUY))
                .filter(o -> !uncrossingBids.contains(o.getId()))
                .map(o -> new OrderBookItem()
                        .setQuantity(o.getRemainingQuantity())
                        .setPrice(o.getPrice()))
                .collect(Collectors.toList());
        List<OrderBookItem> asks = openOrders.stream()
                .filter(o -> o.getSide().equals(MarketSide.SELL))
                .filter(o -> !uncrossingAsks.contains(o.getId()))
                .map(o -> new OrderBookItem()
                        .setQuantity(o.getRemainingQuantity())
                        .setPrice(o.getPrice()))
                .collect(Collectors.toList());
        if(volumeAsks.doubleValue() > volumeBids.doubleValue()) {
            BigDecimal missingQuantity = volumeAsks.subtract(volumeBids);
            Optional<Order> missingOrderOptional = uncrossingOrders.stream()
                    .filter(o -> o.getSide().equals(MarketSide.SELL))
                    .max(Comparator.comparing(Order::getPrice));
            missingOrderOptional.ifPresent(order -> asks.add(new OrderBookItem()
                    .setPrice(order.getPrice())
                    .setQuantity(missingQuantity)));
        } else if(volumeBids.doubleValue() > volumeAsks.doubleValue()) {
            BigDecimal missingQuantity = volumeBids.subtract(volumeAsks);
            Optional<Order> missingOrderOptional = uncrossingOrders.stream()
                    .filter(o -> o.getSide().equals(MarketSide.BUY))
                    .min(Comparator.comparing(Order::getPrice));
            missingOrderOptional.ifPresent(order -> bids.add(new OrderBookItem()
                    .setPrice(order.getPrice())
                    .setQuantity(missingQuantity)));
        }
        asks.sort(Comparator.comparing(OrderBookItem::getPrice));
        bids.sort(Comparator.comparing(OrderBookItem::getPrice).reversed());
        return new OrderBook().setBids(bids).setAsks(asks);
    }

    /**
     * Fill a crossing order and update the position and account balances
     *
     * @param orders {@link List<Order>}
     * @param uncrossingPrice the uncrossing price
     * @param maximumVolume the maximum volume permitted for all orders
     */
    private void fillCrossingOrders(
            final List<Order> orders,
            final BigDecimal uncrossingPrice,
            final BigDecimal maximumVolume
    ) {
        BigDecimal matched = BigDecimal.ZERO;
        for(Order order : orders) {
            Market market = order.getMarket();
            matched = matched.add(order.getRemainingQuantity());
            BigDecimal quantity = order.getRemainingQuantity();
            order.setStatus(OrderStatus.FILLED);
            if(matched.doubleValue() > maximumVolume.doubleValue()) {
                quantity = quantity.subtract(matched.subtract(maximumVolume));
                order.setStatus(OrderStatus.PARTIALLY_FILLED);
            }
            order.setRemainingQuantity(order.getRemainingQuantity().subtract(quantity));
            accountService.processFees(quantity, uncrossingPrice, order.getUser(), market);
            Trade trade = tradeService.save(market, order, uncrossingPrice, quantity, order.getSide());
            positionService.update(market, uncrossingPrice, quantity, order.getUser(), order.getSide());
            OrderHistory orderHistory = new OrderHistory()
                    .setOrder(order)
                    .setId(uuidUtils.next())
                    .setTrade(trade)
                    .setAction(OrderAction.MATCH_IN_AUCTION)
                    .setUpdated(configService.getTimestamp());
            orderHistoryRepository.save(orderHistory);
        }
    }

    /**
     * Exit auctions when the conditions to do so are met
     */
    public List<Market> exitAuctions() {
        List<Market> markets = marketRepository.findByStatusIn(List.of(MarketStatus.AUCTION));
        List<Market> triggeredMarkets = new ArrayList<>();
        for(Market market : markets) {
            BigDecimal uncrossingPrice = getUncrossingPrice(market);
            BigDecimal expectedOpenVolume = (market.getOpenVolume().add(getUncrossingVolume(market)))
                    .multiply(BigDecimal.valueOf(0.8)); // TODO - use config variable for this ratio
            OrderBook expectedOrderBook = getOrderBookAfterUncrossing(market);
            List<AuctionTrigger> triggers = auctionTriggerRepository.findByMarketId(market.getId());
            if(triggers.size() > 0) {
                boolean auctionTriggered = isAuctionTriggered(expectedOpenVolume, expectedOrderBook, triggers, market);
                if (!auctionTriggered) {
                    triggeredMarkets.add(market);
                    market.setStatus(MarketStatus.ACTIVE);
                    marketRepository.save(market);
                    List<Order> uncrossingOrders = getUncrossingOrders(market);
                    BigDecimal volumeBids = uncrossingOrders.stream().filter(o -> o.getSide().equals(MarketSide.BUY))
                            .map(Order::getRemainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal volumeAsks = uncrossingOrders.stream().filter(o -> o.getSide().equals(MarketSide.SELL))
                            .map(Order::getRemainingQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                    List<Order> uncrossingBids = uncrossingOrders.stream()
                            .filter(o -> o.getSide().equals(MarketSide.BUY))
                            .collect(Collectors.toList());
                    List<Order> uncrossingAsks = uncrossingOrders.stream()
                            .filter(o -> o.getSide().equals(MarketSide.SELL))
                            .collect(Collectors.toList());
                    if(volumeBids.doubleValue() > volumeAsks.doubleValue()) {
                        fillCrossingOrders(uncrossingAsks, uncrossingPrice, volumeAsks);
                        fillCrossingOrders(uncrossingBids, uncrossingPrice, volumeAsks);
                    } else if(volumeAsks.doubleValue() >= volumeBids.doubleValue()) {
                        fillCrossingOrders(uncrossingAsks, uncrossingPrice, volumeBids);
                        fillCrossingOrders(uncrossingBids, uncrossingPrice, volumeBids);
                    }
                    orderService.handleMarkPriceChange(market, uncrossingPrice);
                }
            }
        }
        return triggeredMarkets;
    }

    /**
     * Monitor market auctions
     */
    public List<Market> monitorAuctions(
            final BatchValidatorRequest request
    ) {
        log.debug(request.toString());
        List<Market> markets = new ArrayList<>();
        markets.addAll(enterAuctions());
        markets.addAll(exitAuctions());
        return markets;
    }
}