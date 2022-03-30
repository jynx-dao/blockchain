package com.jynx.pro.service;

import com.jynx.pro.constant.KlineInterval;
import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.constant.WebSocketChannelType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.Trade;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.handler.SocketHandler;
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
    private SocketHandler socketHandler;

    /**
     * Save a new {@link Trade}
     *
     * @param market the {@link Market}
     * @param passiveOrder the passive {@link Order}
     * @param takerOrder the taker {@link Order}
     * @param price the trade price
     * @param quantity the trade size
     * @param side the {@link MarketSide}
     *
     * @return {@link Trade}
     */
    public Trade save(
            final Market market,
            final Order passiveOrder,
            final Order takerOrder,
            final BigDecimal price,
            final BigDecimal quantity,
            final MarketSide side
    ) {
        Trade trade = tradeRepository.save(new Trade()
                .setMakerOrder(passiveOrder)
                .setTakerOrder(takerOrder)
                .setMarket(market)
                .setId(uuidUtils.next())
                .setPrice(price)
                .setExecuted(configService.getTimestamp())
                .setQuantity(quantity)
                .setSide(side));
        socketHandler.sendMessage(WebSocketChannelType.TRADES, market.getId(), trade);
        return trade;
    }

    /**
     * Save a new {@link Trade}
     *
     * @param market the {@link Market}
     * @param takerOrder the taker {@link Order}
     * @param price the trade price
     * @param quantity the trade size
     * @param side the {@link MarketSide}
     *
     * @return {@link Trade}
     */
    public Trade save(
            final Market market,
            final Order takerOrder,
            final BigDecimal price,
            final BigDecimal quantity,
            final MarketSide side
    ) {
        return save(market, null, takerOrder, price, quantity, side);
    }

    /**
     * Build candlestick data from trades and interval
     *
     * @param interval {@link KlineInterval}
     * @param trades {@link List<Trade>}
     *
     * @return {@link List<Kline>}
     */
    public List<Kline> getKline(
            final KlineInterval interval,
            final List<Trade> trades
    ) {
        if(interval == null) {
            throw new JynxProException(ErrorCode.INTERVAL_MANDATORY);
        }
        long intervalSeconds = getIntervalInSeconds(interval);
        List<Kline> kline = new ArrayList<>();
        long from = trades.get(0).getExecuted();
        long to = trades.get(trades.size()-1).getExecuted();
        while(from < to) {
            final Long intervalFrom = from;
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
                volume = intervalTrades.stream()
                        .map(Trade::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            kline.add(new Kline()
                    .setOpen(open)
                    .setHigh(high)
                    .setLow(low)
                    .setClose(close)
                    .setTimestamp(intervalFrom)
                    .setVolume(volume));
            from += intervalSeconds;
        }
        return kline;
    }

    /**
     * Convert a {@link KlineInterval} into seconds
     *
     * @param interval {@link KlineInterval}
     *
     * @return interval in seconds
     */
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