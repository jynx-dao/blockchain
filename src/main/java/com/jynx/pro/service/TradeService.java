package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.Trade;
import com.jynx.pro.repository.TradeRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class TradeService {

    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private UUIDUtils uuidUtils;
    @Autowired
    private ConfigService configService;

    public void save(
            final Market market,
            final Order passiveOrder,
            final Order takerOrder,
            final BigDecimal price,
            final BigDecimal size,
            final MarketSide side
    ) {
        tradeRepository.save(new Trade()
                .setMakerOrder(passiveOrder)
                .setTakerOrder(takerOrder)
                .setMarket(market)
                .setId(uuidUtils.next())
                .setPrice(price)
                .setExecuted(configService.getTimestamp())
                .setSize(size)
                .setSide(side));
    }
}