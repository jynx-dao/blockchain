package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Position;
import com.jynx.pro.entity.User;
import com.jynx.pro.repository.PositionRepository;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class PositionService {

    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private UUIDUtils uuidUtils;

    private Position getAndCreate(
            final User user,
            final Market market
    ) {
        Position position = get(user, market)
                .orElse(new Position()
                        .setId(uuidUtils.next())
                        .setUser(user)
                        .setMarket(market)
                        .setSize(BigDecimal.ZERO)
                        .setAllocatedMargin(BigDecimal.ZERO)
                        .setAverageEntryPrice(BigDecimal.ZERO)
                        .setBankruptcyPrice(BigDecimal.ZERO)
                        .setLiquidationPrice(BigDecimal.ZERO)
                        .setRealisedPnl(BigDecimal.ZERO)
                        .setUnrealisedPnl(BigDecimal.ZERO));
        return positionRepository.save(position);
    }

    private Optional<Position> get(
            final User user,
            final Market market
    ) {
        return positionRepository.findByUserAndMarket(user, market);
    }

    public void update(
            final Market market,
            final BigDecimal price,
            final BigDecimal size,
            final User user,
            final MarketSide side
    ) {
        Position position = getAndCreate(user, market);
        if(position.getSide() == null) {
            position.setSide(side);
        }
        BigDecimal averageEntryPrice = getAverageEntryPrice(position.getAverageEntryPrice(), price,
                position.getSize(), size);
        BigDecimal sizeDelta = side.equals(position.getSide()) ? size : size.multiply(BigDecimal.valueOf(-1));
        BigDecimal realisedProfit = BigDecimal.ZERO;
        if(sizeDelta.doubleValue() < 0) {
            BigDecimal closingSize = sizeDelta.abs().min(position.getSize()).multiply(price);
            BigDecimal gain = price.subtract(position.getAverageEntryPrice()).abs()
                    .divide(position.getAverageEntryPrice(), 5, RoundingMode.HALF_UP);
            if(position.getSide().equals(MarketSide.BUY) &&
                    price.doubleValue() < position.getAverageEntryPrice().doubleValue()) {
                gain = gain.multiply(BigDecimal.valueOf(-1));
            }
            if(position.getSide().equals(MarketSide.SELL) &&
                    price.doubleValue() > position.getAverageEntryPrice().doubleValue()) {
                gain = gain.multiply(BigDecimal.valueOf(-1));
            }
            realisedProfit = gain.multiply(closingSize);
        }
        position.setSize(position.getSize().add(sizeDelta));
        if(position.getSize().doubleValue() < 0) {
            position.setSize(position.getSize().multiply(BigDecimal.valueOf(-1)));
            position.setSide(orderService.getOtherSide(position.getSide()));
            averageEntryPrice = price;
        }
        position.setAverageEntryPrice(averageEntryPrice);
        position.setRealisedPnl(position.getRealisedPnl().add(realisedProfit));
        position.setRealisedPnl(position.getUnrealisedPnl().subtract(realisedProfit));
        accountService.bookProfit(user, market, realisedProfit);
        // TODO - allocated margin needs to be updated too
        positionRepository.save(position);
    }

    private BigDecimal getAverageEntryPrice(
            final BigDecimal price1,
            final BigDecimal price2,
            final BigDecimal size1,
            final BigDecimal size2
    ) {
        BigDecimal product1 = price1.multiply(size1);
        BigDecimal product2 = price2.multiply(size2);
        BigDecimal sumProduct = product1.add(product2);
        return sumProduct.divide(size1.add(size2), 4, RoundingMode.HALF_UP);
    }
}