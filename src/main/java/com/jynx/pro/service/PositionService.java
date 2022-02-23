package com.jynx.pro.service;

import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.CancelOrderRequest;
import com.jynx.pro.request.CreateOrderRequest;
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
public class PositionService {

    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private MarketRepository marketRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ConfigService configService;
    @Autowired
    private OrderRepository orderRepository;
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
        BigDecimal sizeDelta = side.equals(position.getSide()) ? size : size.multiply(BigDecimal.valueOf(-1));
        if(sizeDelta.doubleValue() < 0) {
            BigDecimal closingSize = sizeDelta.abs().doubleValue() < position.getSize().doubleValue() ?
                    sizeDelta.abs() : position.getSize();
            BigDecimal closingNotionalSize = closingSize.multiply(price);
            BigDecimal gain = price.subtract(position.getAverageEntryPrice()).abs()
                    .divide(position.getAverageEntryPrice(), dps, RoundingMode.HALF_UP);
            gain = flipGain(position, gain, price);
            BigDecimal realisedProfit = gain.multiply(closingNotionalSize);
            position.setSize(position.getSize().add(sizeDelta));
            if(position.getSize().doubleValue() < 0) {
                position.setSize(position.getSize().multiply(BigDecimal.valueOf(-1)));
                position.setSide(orderService.getOtherSide(position.getSide()));
                position.setAverageEntryPrice(price);
            }
            BigDecimal unrealisedProfitRatio = BigDecimal.ONE.subtract(closingSize.abs()
                    .divide(originalPositionSize, dps, RoundingMode.HALF_UP));
            position.setRealisedPnl(position.getRealisedPnl().add(realisedProfit));
            position.setUnrealisedPnl(unrealisedProfitRatio.multiply(position.getUnrealisedPnl()));
            accountService.bookProfit(user, market, realisedProfit);
            BigDecimal margin = orderService.getMarginRequirement(market, user);
            accountService.allocateMargin(margin, user, market.getSettlementAsset());
            updateLiquidationPrice(position);
        } else {
            BigDecimal averageEntryPrice = getAverageEntryPrice(position.getAverageEntryPrice(), price,
                    position.getSize(), size, dps);
            position.setSize(position.getSize().add(sizeDelta));
            position.setAverageEntryPrice(averageEntryPrice);
        }
        if(position.getSize().setScale(dps, RoundingMode.HALF_UP)
                .equals(BigDecimal.ZERO.setScale(dps, RoundingMode.HALF_UP))) {
            position.setSide(null);
            position.setAverageEntryPrice(BigDecimal.ZERO);
            position.setLeverage(BigDecimal.ZERO);
            position.setLiquidationPrice(BigDecimal.ZERO);
            position.setLatestMarkPrice(BigDecimal.ZERO);
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
            updateLiquidationPrice(position);
        }
        positionRepository.saveAll(positions);
    }

    /**
     * Update the liquidation price of given position
     *
     * @param position {@link Position}
     */
    private void updateLiquidationPrice(
            final Position position
    ) {
        Market market = position.getMarket();
        int dps = market.getSettlementAsset().getDecimalPlaces();
        Account account = accountService.getAndCreate(position.getUser(), market.getSettlementAsset());
        BigDecimal leverage = BigDecimal.ZERO;
        BigDecimal liquidationPrice = BigDecimal.ZERO;
        if(account.getBalance().doubleValue() > 0) {
            leverage = (position.getSize().multiply(position.getAverageEntryPrice()))
                    .divide(account.getBalance(), dps, RoundingMode.HALF_UP);
        }
        if(leverage.doubleValue() > 0) {
            BigDecimal effectiveMargin = BigDecimal.ONE.divide(leverage, dps, RoundingMode.HALF_UP)
                    .multiply(market.getLiquidationFee()
                            .divide(market.getMarginRequirement(), dps, RoundingMode.HALF_UP));
            liquidationPrice = position.getSide().equals(MarketSide.BUY) ?
                    (BigDecimal.ONE.subtract(effectiveMargin)).multiply(position.getAverageEntryPrice()) :
                    (BigDecimal.ONE.add(effectiveMargin)).multiply(position.getAverageEntryPrice());
        }
        position.setLeverage(leverage);
        position.setLiquidationPrice(liquidationPrice);
        position.setLatestMarkPrice(market.getMarkPrice());
    }

    /**
     * Check if a position is distressed
     *
     * @param position {@link Position}
     * @param markPrice the current mark price
     *
     * @return true / false
     */
    private boolean isDistressed(
            final Position position,
            final BigDecimal markPrice
    ) {
        return (position.getSide().equals(MarketSide.BUY) &&
                markPrice.doubleValue() <= position.getLiquidationPrice().doubleValue()) ||
                (position.getSide().equals(MarketSide.SELL) &&
                        markPrice.doubleValue() >= position.getLiquidationPrice().doubleValue());
    }

    /**
     * Liquidate a position when the user has excess balance
     *
     * @param market {@link Market}
     * @param account {@link Account}
     * @param position {@link Position}
     */
    private void liquidateWithExcessBalance(
            final Market market,
            final Account account,
            final Position position
    ) {
        market.setInsuranceFund(market.getInsuranceFund().add(account.getBalance()));
        Transaction transaction = new Transaction()
                .setTimestamp(configService.getTimestamp())
                .setAsset(position.getMarket().getSettlementAsset())
                .setAmount(account.getBalance().multiply(BigDecimal.valueOf(-1)))
                .setType(TransactionType.LIQUIDATION_DEBIT)
                .setUser(position.getUser());
        transactionRepository.save(transaction);
    }

    /**
     * Liquidate a position with loss socialization
     *
     * @param market {@link Market}
     * @param account {@link Account}
     */
    public void claimLossBySocialization(
            final Market market,
            final Account account
    ) {
        BigDecimal insuranceFund = market.getInsuranceFund();
        BigDecimal lossToSocialize = account.getBalance().abs().subtract(insuranceFund);
        market.setInsuranceFund(BigDecimal.ZERO);
        socializeLosses(market, lossToSocialize);
        Transaction transaction = new Transaction()
                .setTimestamp(configService.getTimestamp())
                .setAsset(market.getSettlementAsset())
                .setAmount(insuranceFund.add(lossToSocialize))
                .setType(TransactionType.LIQUIDATION_CREDIT)
                .setUser(account.getUser());
        transactionRepository.save(transaction);
    }

    /**
     * Liquidate a position using the insurance fund
     *
     * @param market {@link Market}
     * @param account {@link Account}
     */
    public void claimLossFromInsuranceFund(
            final Market market,
            final Account account
    ) {
        market.setInsuranceFund(market.getInsuranceFund().subtract(account.getBalance().abs()));
        Transaction transaction = new Transaction()
                .setTimestamp(configService.getTimestamp())
                .setAsset(market.getSettlementAsset())
                .setAmount(account.getBalance().multiply(BigDecimal.valueOf(-1)))
                .setType(TransactionType.LIQUIDATION_CREDIT)
                .setUser(account.getUser());
        transactionRepository.save(transaction);
    }

    /**
     * Closes distressed positions for given market
     *
     * @param markPrice the current mark price
     * @param market {@link Market}
     */
    public void executeLiquidations(
            final BigDecimal markPrice,
            final Market market
    ) {
        List<Position> losingPositions = positionRepository.findByMarket(market).stream()
                .filter(p -> p.getSize().doubleValue() > 0)
                .filter(p -> p.getUnrealisedPnl().doubleValue() < 0)
                .sorted(Comparator.comparing(Position::getLeverage).reversed().thenComparing(Position::getSize))
                .collect(Collectors.toList());
        List<UUID> liquidatedPositionIds = new ArrayList<>();
        for(Position position : losingPositions) {
            if(isDistressed(position, markPrice)) {
                CreateOrderRequest createOrderRequest = new CreateOrderRequest();
                createOrderRequest.setTag(OrderTag.LIQUIDATION);
                createOrderRequest.setUser(position.getUser());
                createOrderRequest.setType(OrderType.MARKET);
                createOrderRequest.setMarketId(position.getMarket().getId());
                createOrderRequest.setSide(orderService.getOtherSide(position.getSide()));
                createOrderRequest.setSize(position.getSize());
                orderService.create(createOrderRequest, true);
                cancelOrders(market, position);
                Account account = accountService.getAndCreate(
                        position.getUser(), position.getMarket().getSettlementAsset());
                liquidatedPositionIds.add(position.getId());
                if(account.getBalance().doubleValue() > 0) {
                    liquidateWithExcessBalance(market, account, position);
                } else if(account.getBalance().doubleValue() < 0) {
                    if(market.getInsuranceFund().doubleValue() < account.getBalance().abs().doubleValue()) {
                        claimLossBySocialization(market, account);
                    } else {
                        claimLossFromInsuranceFund(market, account);
                    }
                }
                account.setBalance(BigDecimal.ZERO);
                account.setAvailableBalance(BigDecimal.ZERO);
                account.setMarginBalance(BigDecimal.ZERO);
                accountRepository.save(account);
            }
        }
        List<Position> liquidatedPositions = positionRepository.findByIdIn(liquidatedPositionIds);
        for(Position position : liquidatedPositions) {
            List<Transaction> transactions = transactionRepository.findByUserAndAsset(
                    position.getUser(), market.getSettlementAsset());
            BigDecimal txSum = BigDecimal.valueOf(transactions.stream()
                    .mapToDouble(t -> t.getAmount().doubleValue()).sum());
            position.setRealisedPnl(txSum);
            position.setUnrealisedPnl(BigDecimal.ZERO);
            position.setSide(null);
            position.setAverageEntryPrice(BigDecimal.ZERO);
            position.setLeverage(BigDecimal.ZERO);
            position.setLiquidationPrice(BigDecimal.ZERO);
            position.setLatestMarkPrice(BigDecimal.ZERO);
        }
        positionRepository.saveAll(liquidatedPositions);
    }

    /**
     * Cancel all orders when position is liquidated
     *
     * @param market {@link Market}
     * @param position {@link Position}
     */
    private void cancelOrders(
            final Market market,
            final Position position
    ) {
        List<Order> stopOrders = orderRepository.findByStatusInAndTypeAndMarketAndUser(
                List.of(OrderStatus.OPEN), OrderType.STOP_MARKET, market, position.getUser());
        List<Order> limitOrders = orderRepository.findByStatusInAndTypeAndMarketAndUser(List.of(OrderStatus.OPEN,
                OrderStatus.PARTIALLY_FILLED), OrderType.LIMIT, market, position.getUser());
        stopOrders.forEach(order -> {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(order.getUser());
            request.setId(order.getId());
            orderService.cancel(request);
        });
        limitOrders.forEach(order -> {
            CancelOrderRequest request = new CancelOrderRequest();
            request.setUser(order.getUser());
            request.setId(order.getId());
            orderService.cancel(request);
        });
    }

    /**
     * Close out winning positions to socialize the loss
     *
     * @param market {@link Market}
     * @param lossToSocialize loss amount to socialize
     */
    private void socializeLosses(
            final Market market,
            final BigDecimal lossToSocialize
    ) {
        double lossThreshold = 0.001d;
        BigDecimal remainingLoss = lossToSocialize;
        while(remainingLoss.doubleValue() > lossThreshold) {
            List<Position> socializationQueue = getLossSocializationQueue(market);
            for (Position position : socializationQueue) {
                int dps = position.getMarket().getSettlementAsset().getDecimalPlaces();
                BigDecimal positionRatio = remainingLoss.divide(position.getUnrealisedPnl(), dps, RoundingMode.HALF_UP);
                BigDecimal lossSocializationSize = position.getSize().multiply(positionRatio);
                BigDecimal orderSize = position.getSize().doubleValue() >= lossSocializationSize.doubleValue() ?
                        lossSocializationSize : position.getSize();
                CreateOrderRequest createOrderRequest = new CreateOrderRequest();
                createOrderRequest.setTag(OrderTag.LOSS_SOCIALIZATION);
                createOrderRequest.setUser(position.getUser());
                createOrderRequest.setType(OrderType.MARKET);
                createOrderRequest.setMarketId(position.getMarket().getId());
                createOrderRequest.setSide(orderService.getOtherSide(position.getSide()));
                createOrderRequest.setSize(orderSize);
                orderService.create(createOrderRequest);
                Transaction latestTx = transactionRepository.findByUserAndAsset(
                                position.getUser(), position.getMarket().getSettlementAsset())
                        .stream()
                        .filter(t -> t.getType().equals(TransactionType.SETTLEMENT))
                        .max(Comparator.comparing(Transaction::getId))
                        .orElseThrow(() -> new JynxProException(ErrorCode.FATAL_ERROR));
                remainingLoss = remainingLoss.subtract(latestTx.getAmount());
                BigDecimal amountToDebit = remainingLoss.doubleValue() > 0 ?
                        latestTx.getAmount() : latestTx.getAmount().add(remainingLoss);
                Transaction transaction = new Transaction()
                        .setTimestamp(configService.getTimestamp())
                        .setAsset(position.getMarket().getSettlementAsset())
                        .setAmount(amountToDebit.multiply(BigDecimal.valueOf(-1)))
                        .setType(TransactionType.LOSS_SOCIALIZATION)
                        .setUser(position.getUser());
                transactionRepository.save(transaction);
                Account account = accountService.getAndCreate(
                        position.getUser(), position.getMarket().getSettlementAsset());
                account.setBalance(account.getBalance().subtract(amountToDebit));
                account.setAvailableBalance(account.getAvailableBalance().subtract(amountToDebit));
                accountRepository.save(account);
                if (remainingLoss.doubleValue() <= lossThreshold) {
                    break;
                }
            }
        }
    }

    /**
     * Get a ranked list of positions to use when socializing losses
     *
     * @return list of {@link Position}s
     */
    public List<Position> getLossSocializationQueue(
            final Market market
    ) {
        return positionRepository.findByMarket(market).stream()
                .filter(p -> p.getSize().doubleValue() > 0)
                .filter(p -> p.getUnrealisedPnl().doubleValue() > 0)
                .sorted(Comparator.comparing(Position::getLeverage).reversed()
                        .thenComparing(Position::getUnrealisedPnl).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Update passive liquidity for given market
     *
     * @param market {@link Market}
     */
    public void updatePassiveLiquidity(
            final Market market
    ) {
        // TODO - implement method when passive liquidity is supported
    }

    /**
     * Save a position
     *
     * @param position {@link Position}
     *
     * @return the updated {@link Position}
     */
    public Position save(
            final Position position
    ) {
        return positionRepository.save(position);
    }
}