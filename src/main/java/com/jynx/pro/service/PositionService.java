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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PositionService {

    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private UUIDUtils uuidUtils;

    /**
     * Get a {@link Position} and create one if it doesn't exist
     *
     * @param user the {@link User}
     * @param market the {@link Market}
     *
     * @return the {@link Position}
     */
    public Position getAndCreate(
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

    /**
     * Get a position by user and market
     *
     * @param user {@link User}
     * @param market {@link Market}
     *
     * @return optional {@link Position}
     */
    private Optional<Position> get(
            final User user,
            final Market market
    ) {
        return positionRepository.findByUserAndMarket(user, market);
    }

    /**
     * Update a position after a new trade executes
     *
     * @param market {@link Market}
     * @param price the price of the trade
     * @param size the size of the trade
     * @param user {@link User}
     * @param side the {@link MarketSide} of the trade
     */
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
        BigDecimal originalPositionSize = position.getSize();
        int dps = market.getSettlementAsset().getDecimalPlaces();
        BigDecimal averageEntryPrice = getAverageEntryPrice(position.getAverageEntryPrice(), price,
                position.getSize(), size, dps);
        BigDecimal sizeDelta = side.equals(position.getSide()) ? size : size.multiply(BigDecimal.valueOf(-1));
        BigDecimal realisedProfit = BigDecimal.ZERO;
        if(sizeDelta.doubleValue() < 0) {
            averageEntryPrice = position.getAverageEntryPrice();
            BigDecimal closingSize = sizeDelta.abs().min(position.getSize()).multiply(price);
            BigDecimal gain = price.subtract(position.getAverageEntryPrice()).abs()
                    .divide(position.getAverageEntryPrice(), dps, RoundingMode.HALF_UP);
            gain = flipGain(position, gain, price);
            realisedProfit = gain.multiply(closingSize);
        }
        position.setSize(position.getSize().add(sizeDelta));
        if(position.getSize().doubleValue() < 0) {
            position.setSize(position.getSize().multiply(BigDecimal.valueOf(-1)));
            position.setSide(orderService.getOtherSide(position.getSide()));
            averageEntryPrice = price;
        }
        position.setAverageEntryPrice(averageEntryPrice);
        if(realisedProfit.doubleValue() != 0) {
            BigDecimal unrealisedProfitRatio = BigDecimal.ONE.subtract(sizeDelta.abs()
                    .divide(originalPositionSize, dps, RoundingMode.HALF_UP));
            position.setRealisedPnl(position.getRealisedPnl().add(realisedProfit));
            position.setUnrealisedPnl(unrealisedProfitRatio.multiply(position.getUnrealisedPnl()));
            accountService.bookProfit(user, market, realisedProfit);
        }
        if(position.getSize().setScale(dps, RoundingMode.HALF_UP)
                .equals(BigDecimal.ZERO.setScale(dps, RoundingMode.HALF_UP))) {
            position.setSide(null);
            position.setAverageEntryPrice(BigDecimal.ZERO);
        }
        positionRepository.save(position);
    }

    /**
     * Calculate volume-weighted average price from two data-points
     *
     * @param price1 the first price
     * @param price2 the second price
     * @param size1 the first size
     * @param size2 the second size
     * @param dps decimal places to use
     *
     * @return the volume-weighted average price
     */
    private BigDecimal getAverageEntryPrice(
            final BigDecimal price1,
            final BigDecimal price2,
            final BigDecimal size1,
            final BigDecimal size2,
            final int dps
    ) {
        BigDecimal product1 = price1.multiply(size1);
        BigDecimal product2 = price2.multiply(size2);
        BigDecimal sumProduct = product1.add(product2);
        return sumProduct.divide(size1.add(size2), dps, RoundingMode.HALF_UP);
    }

    /**
     * Invert the gain if it's the wrong way around
     *
     * @param position {@link Position}
     * @param gain the gain %
     * @param price the current price
     *
     * @return the updated gain %
     */
    private BigDecimal flipGain(
            final Position position,
            final BigDecimal gain,
            final BigDecimal price
    ) {
        if(position.getSide().equals(MarketSide.BUY) &&
                price.doubleValue() < position.getAverageEntryPrice().doubleValue() &&
                gain.doubleValue() > 0) {
            return gain.multiply(BigDecimal.valueOf(-1));
        }
        if(position.getSide().equals(MarketSide.BUY) &&
                price.doubleValue() > position.getAverageEntryPrice().doubleValue() &&
                gain.doubleValue() < 0) {
            return gain.multiply(BigDecimal.valueOf(-1));
        }
        if(position.getSide().equals(MarketSide.SELL) &&
                price.doubleValue() > position.getAverageEntryPrice().doubleValue() &&
                gain.doubleValue() > 0) {
            return gain.multiply(BigDecimal.valueOf(-1));
        }
        if(position.getSide().equals(MarketSide.SELL) &&
                price.doubleValue() < position.getAverageEntryPrice().doubleValue() &&
                gain.doubleValue() < 0) {
            return gain.multiply(BigDecimal.valueOf(-1));
        }
        return gain;
    }

    /**
     * Calculate the open volume of given market
     *
     * @param market {@link Market}
     *
     * @return the open volume
     */
    public BigDecimal calculateOpenVolume(
            final Market market
    ) {
        List<Position> positions = positionRepository.findByMarket(market).stream()
                .filter(p -> p.getSize().doubleValue() > 0 && p.getSide().equals(MarketSide.BUY))
                .collect(Collectors.toList());
        return BigDecimal.valueOf(positions.stream().mapToDouble(p -> p.getSize().doubleValue()).sum());
    }

    /**
     * Update the unrealised profit of open positions and refresh margin allocations
     *
     * @param market {@link Market}
     */
    public void updateUnrealisedProfit(
            final Market market
    ) {
        List<Position> positions = positionRepository.findByMarket(market);
        for(Position position : positions) {
            if(position.getSize().doubleValue() == 0) {
                continue;
            }
            BigDecimal gain = position.getAverageEntryPrice().subtract(market.getMarkPrice())
                    .divide(position.getAverageEntryPrice(),
                            market.getSettlementAsset().getDecimalPlaces(), RoundingMode.HALF_UP);
            gain = flipGain(position, gain, market.getMarkPrice());
            BigDecimal unrealisedProfit = gain.multiply(position.getSize().multiply(position.getAverageEntryPrice()))
                    .setScale(market.getSettlementAsset().getDecimalPlaces(), RoundingMode.HALF_UP);
            position.setUnrealisedPnl(unrealisedProfit);
        }
        positions = positionRepository.saveAll(positions);
        for(Position position : positions) {
            BigDecimal margin = orderService.getMarginRequirement(market, position.getUser());
            accountService.allocateMargin(margin, position.getUser(), market.getSettlementAsset());
        }
    }
}