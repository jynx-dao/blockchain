package com.jynx.pro.service;

import com.jynx.pro.constant.KlineInterval;
import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.Trade;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.Kline;
import com.jynx.pro.repository.TradeRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TradeService {

    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private UUIDUtils uuidUtils;
    @Autowired
    private ConfigService configService;
    @Autowired
    private MarketService marketService;

    public Trade save(
            final Market market,
            final Order passiveOrder,
            final Order takerOrder,
            final BigDecimal price,
            final BigDecimal quantity,
            final MarketSide side
    ) {
        return tradeRepository.save(new Trade()
                .setMakerOrder(passiveOrder)
                .setTakerOrder(takerOrder)
                .setMarket(market)
                .setId(uuidUtils.next())
                .setPrice(price)
                .setExecuted(configService.getTimestamp())
                .setQuantity(quantity)
                .setSide(side));
    }

    public List<Trade> getByMarketId(
            final UUID marketId
    ) {
        return tradeRepository.findByMarketId(marketId);
    }

    public List<Trade> getByUserIdAndMarketId(
            final UUID userId,
            final UUID marketId
    ) {
        return tradeRepository.findByTakerOrderUserIdAndMarketId(userId, marketId);
    }

    public List<Kline> getKline(
            final UUID marketId,
            final Long from,
            final Long to,
            final KlineInterval interval
    ) {
        if(from == null) {
            throw new JynxProException(ErrorCode.FROM_MANDATORY);
        }
        if(to == null) {
            throw new JynxProException(ErrorCode.TO_MANDATORY);
        }
        if(marketId == null) {
            throw new JynxProException(ErrorCode.MARKET_ID_MANDATORY);
        }
        if(interval == null) {
            throw new JynxProException(ErrorCode.INTERVAL_MANDATORY);
        }
        if(from > to) {
            throw new JynxProException(ErrorCode.FROM_AFTER_TO);
        }
        Market market = marketService.get(marketId);
        long intervalSeconds = getIntervalInSeconds(interval);
        long duration = to - from;
        long totalIntervals = duration / intervalSeconds;
        long finalFrom = from;
        if(totalIntervals > 100) {
            finalFrom = to - (100 * intervalSeconds);
        }
        long intervalMinutes = intervalSeconds / 60;
        finalFrom = ceilTimestamp(finalFrom, intervalMinutes);
        long finalTo = floorTimestamp(to, intervalMinutes);
        List<Trade> trades = tradeRepository.findByMarketIdAndExecutedGreaterThanAndExecutedLessThan(
                marketId, finalFrom, finalTo).stream()
                .sorted(Comparator.comparing(Trade::getExecuted))
                .collect(Collectors.toList());
        List<Kline> kline = new ArrayList<>();
        while(finalFrom < finalTo) {
            final Long intervalFrom = finalFrom;
            final Long intervalTo = intervalFrom + intervalSeconds;
            List<Trade> intervalTrades = trades.stream()
                    .filter(t -> t.getExecuted() >= intervalFrom && t.getExecuted() < intervalTo)
                    .collect(Collectors.toList());
            BigDecimal high = intervalTrades.stream().max(Comparator.comparing(Trade::getPrice))
                    .orElse(new Trade().setPrice(BigDecimal.ZERO)).getPrice();
            BigDecimal low = intervalTrades.stream().min(Comparator.comparing(Trade::getPrice))
                    .orElse(new Trade().setPrice(BigDecimal.ZERO)).getPrice();
            BigDecimal open = BigDecimal.ZERO;
            BigDecimal close = BigDecimal.ZERO;
            BigDecimal volume = BigDecimal.ZERO;
            if(intervalTrades.size() > 0) {
                open = intervalTrades.get(0).getPrice();
                close = intervalTrades.get(intervalTrades.size() - 1).getPrice();
                volume = BigDecimal.valueOf(intervalTrades.stream()
                        .mapToDouble(t -> t.getQuantity().doubleValue()).sum());
            }
            kline.add(new Kline()
                    .setOpen(open)
                    .setHigh(high)
                    .setLow(low)
                    .setClose(close)
                    .setTimestamp(intervalFrom)
                    .setMarket(market)
                    .setVolume(volume));
            finalFrom += intervalSeconds;
        }
        return kline;
    }

    private long ceilTimestamp(
            long ts,
            long interval
    ) {
        return (long) (Math.ceil((double) ts / (interval * 60)) * (interval * 60));
    }

    private long floorTimestamp(
            long ts,
            long interval
    ) {
        return (long) (Math.floor((double) ts / (interval * 60)) * (interval * 60));
    }

    private long getIntervalInSeconds(
            final KlineInterval interval
    ) {
        String intervalString = interval.name();
        if(intervalString.contains("M")) {
            return Long.parseLong(intervalString.replace("M", "")) * 60;
        } else if(intervalString.contains("H")) {
            return Long.parseLong(intervalString.replace("H", "")) * 60 * 60;
        }
        return Long.parseLong(intervalString.replace("D", "")) * 60 * 60 * 24;
    }
}