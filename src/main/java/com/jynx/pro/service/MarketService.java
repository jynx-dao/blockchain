package com.jynx.pro.service;

import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.request.AmendMarketRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MarketService {

    @Autowired
    private AssetService assetService;
    @Autowired
    private MarketRepository marketRepository;
    @Autowired
    private ProposalService proposalService;
    @Autowired
    private UUIDUtils uuidUtils;
    @Autowired
    private StakeService stakeService;
    @Autowired
    private OracleService oracleService;
    @Autowired
    private PositionService positionService;
    @Autowired
    private PositionRepository positionRepository;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private OracleRepository oracleRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AccountRepository accountRepository;

    public Market get(
            final UUID id
    ) {
        return marketRepository.findById(id).orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
    }

    private void settlePosition(
            final Position position,
            final Market market,
            final BigDecimal settlementDelta
    ) {
        BigDecimal settlementAmount = position.getSize().multiply(settlementDelta);
        position.setRealisedPnl(position.getRealisedPnl().add(settlementAmount));
        Account account = accountService.getAndCreate(
                position.getUser(), market.getSettlementAsset());
        account.setBalance(account.getBalance().add(settlementAmount));
        account.setAvailableBalance(account.getAvailableBalance().add(settlementAmount));
        Transaction transaction = new Transaction()
                .setType(TransactionType.SETTLEMENT)
                .setAmount(settlementAmount)
                .setUser(position.getUser())
                .setAsset(market.getSettlementAsset())
                .setTimestamp(configService.getTimestamp());
        transactionRepository.save(transaction);
        accountRepository.save(account);
    }

    public void settleMarkets() {
        List<Market> markets = marketRepository.findByStatusIn(List.of(MarketStatus.ACTIVE, MarketStatus.SUSPENDED));
        for(Market market : markets) {
            int dps = market.getSettlementAsset().getDecimalPlaces();
            long timeSinceSettlement = configService.getTimestamp() - market.getLastSettlement();
            if(timeSinceSettlement > (60 * 60 * 9)) {
                List<Settlement> settlementData = settlementRepository.findBySettlementInterval(market.getSettlementCount() + 1);
                List<Oracle> oracles = oracleRepository.findByMarketAndStatus(market, OracleStatus.ACTIVE);
                slashOffendingOracles(settlementData, oracles);
                if(settlementData.size() >= (oracles.size() * 0.667)) {
                    List<Position> positions = positionRepository
                            .findByMarketAndSizeGreaterThan(market, BigDecimal.ZERO);
                    BigDecimal value = BigDecimal.valueOf(settlementData.stream()
                            .mapToDouble(d -> d.getValue().doubleValue()).average().orElse(0d));
                    if(value.doubleValue() > 0d) {
                        BigDecimal settlementDelta = (value.subtract(market.getMarkPrice()))
                                .divide(market.getMarkPrice(), dps, RoundingMode.HALF_UP);
                        positions.forEach(position -> settlePosition(position, market, settlementDelta));
                        positionRepository.saveAll(positions);
                        market.setLastSettlement(configService.getTimestamp());
                        market.setSettlementCount(market.getSettlementCount() + 1);
                        marketRepository.save(market);
                    }
                }
            }
        }
    }

    private void slashOffendingOracles(
            final List<Settlement> settlementData,
            final List<Oracle> oracles
    ) {
        List<UUID> presentOracleIds = settlementData.stream()
                .map(d -> d.getOracle().getId()).collect(Collectors.toList());
        List<Oracle> missingOracles = oracles.stream().filter(o -> !presentOracleIds.contains(o.getId()))
                .collect(Collectors.toList());
        double[] oracleValues = settlementData.stream().mapToDouble(d -> d.getValue().doubleValue()).toArray();
        double lowerLimit = new Percentile().evaluate(oracleValues, 0.15);
        double upperLimit = new Percentile().evaluate(oracleValues, 0.85);
        List<Oracle> outlierOracles = settlementData
                .stream()
                .filter(d -> d.getValue().doubleValue() < lowerLimit ||
                        d.getValue().doubleValue() > upperLimit)
                .map(Settlement::getOracle)
                .collect(Collectors.toList());
        List<Oracle> oraclesToSlash = new ArrayList<>();
        oraclesToSlash.addAll(missingOracles);
        oraclesToSlash.addAll(outlierOracles);
        HashSet<Object> seen = new HashSet<>();
        oraclesToSlash.removeIf(o -> !seen.add(o.getId()));
        oracleService.slash(oraclesToSlash);
    }

    public Market proposeToAdd(
            final AddMarketRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Asset settlementAsset = assetService.get(request.getSettlementAssetId());
        if(!settlementAsset.getStatus().equals(AssetStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.ASSET_NOT_ACTIVE);
        }
        if(request.getTakerFee().doubleValue() < request.getMakerFee().doubleValue()) {
            throw new JynxProException(ErrorCode.INVALID_TAKER_FEE);
        }
        if(request.getLiquidationFee().doubleValue() > request.getMarginRequirement().doubleValue() * 0.5) {
            throw new JynxProException(ErrorCode.INVALID_LIQUIDATION_FEE);
        }
        Market market = new Market()
                .setName(request.getName())
                .setSettlementAsset(settlementAsset)
                .setMarginRequirement(request.getMarginRequirement())
                .setTickSize(request.getTickSize())
                .setStepSize(request.getStepSize())
                .setSettlementFrequency(request.getSettlementFrequency())
                .setMakerFee(request.getMakerFee())
                .setTakerFee(request.getTakerFee())
                .setLiquidationFee(request.getLiquidationFee())
                .setStatus(MarketStatus.PENDING)
                .setId(uuidUtils.next())
                .setLastSettlement(0L)
                .setMinOracleCount(request.getMinOracleCount());
        market = marketRepository.save(market);
        proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.ADD_MARKET);
        return market;
    }

    public Market proposeToAmend(
            final AmendMarketRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Market market = get(request.getId());
        BigDecimal makerFee = Objects.isNull(request.getMakerFee()) ? market.getMakerFee() : request.getMakerFee();
        BigDecimal takerFee = Objects.isNull(request.getTakerFee()) ? market.getTakerFee() : request.getTakerFee();
        BigDecimal liquidationFee = Objects.isNull(request.getLiquidationFee()) ?
                market.getLiquidationFee() : request.getLiquidationFee();
        BigDecimal marginRequirement = Objects.isNull(request.getMarginRequirement()) ?
                market.getMarginRequirement() : request.getMarginRequirement();
        if(takerFee.doubleValue() < makerFee.doubleValue()) {
            throw new JynxProException(ErrorCode.INVALID_TAKER_FEE);
        }
        if(liquidationFee.doubleValue() > marginRequirement.doubleValue() * 0.5) {
            throw new JynxProException(ErrorCode.INVALID_LIQUIDATION_FEE);
        }
        if(!market.getStatus().equals(MarketStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_ACTIVE);
        }
        if(!Objects.isNull(request.getMarginRequirement())) {
            market.setMarginRequirement(request.getMarginRequirement());
        }
        if(!Objects.isNull(request.getMakerFee())) {
            market.setPendingMakerFee(request.getMakerFee());
        }
        if(!Objects.isNull(request.getTakerFee())) {
            market.setPendingTakerFee(request.getTakerFee());
        }
        if(!Objects.isNull(request.getLiquidationFee())) {
            market.setPendingLiquidationFee(request.getLiquidationFee());
        }
        if(!Objects.isNull(request.getStepSize())) {
            market.setPendingStepSize(request.getStepSize());
        }
        if(!Objects.isNull(request.getTickSize())) {
            market.setPendingTickSize(request.getTickSize());
        }
        if(!Objects.isNull(request.getSettlementFrequency())) {
            market.setPendingSettlementFrequency(request.getSettlementFrequency());
        }
        market = marketRepository.save(market);
        proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.AMEND_MARKET);
        return market;
    }

    public Market proposeToSuspend(
            final SingleItemRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Market market = get(request.getId());
        if(!market.getStatus().equals(MarketStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_ACTIVE);
        }
        proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.SUSPEND_MARKET);
        return market;
    }

    public Market proposeToUnsuspend(
            final SingleItemRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Market market = get(request.getId());
        if(!market.getStatus().equals(MarketStatus.SUSPENDED)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_SUSPENDED);
        }
        proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.UNSUSPEND_MARKET);
        return market;
    }

    /**
     * Updates the status of a market linked to a proposal
     *
     * @param proposal the {@link Proposal}
     * @param status the {@link MarketStatus}
     */
    private void updateStatus(
            final Proposal proposal,
            final MarketStatus status
    ) {
        Market market = get(proposal.getLinkedId());
        market.setStatus(status);
        if(status.equals(MarketStatus.ACTIVE)) {
            market.setLastSettlement(configService.getTimestamp());
        }
        marketRepository.save(market);
    }
    
    public void add(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        Market market = get(proposal.getLinkedId());
        List<Oracle> oracles = oracleRepository.findByMarketAndStatus(market, OracleStatus.ACTIVE);
        if(oracles.size() >= market.getMinOracleCount()) {
            updateStatus(proposal, MarketStatus.ACTIVE);
        }
    }

    public void reject(
            final Proposal proposal
    ) {
        updateStatus(proposal, MarketStatus.REJECTED);
    }

    public void amend(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        Market market = get(proposal.getLinkedId());
        if(!Objects.isNull(market.getPendingMarginRequirement())) {
            market.setMarginRequirement(market.getPendingMarginRequirement());
            market.setMarginRequirement(null);
        }
        if(!Objects.isNull(market.getPendingMakerFee())) {
            market.setMakerFee(market.getPendingMakerFee());
            market.setPendingMakerFee(null);
        }
        if(!Objects.isNull(market.getPendingTakerFee())) {
            market.setTakerFee(market.getPendingTakerFee());
            market.setPendingTakerFee(null);
        }
        if(!Objects.isNull(market.getLiquidationFee())) {
            market.setLiquidationFee(market.getLiquidationFee());
            market.setPendingLiquidationFee(null);
        }
        if(!Objects.isNull(market.getPendingSettlementFrequency())) {
            market.setSettlementFrequency(market.getPendingSettlementFrequency());
            market.setPendingSettlementFrequency(null);
        }
        if(!Objects.isNull(market.getPendingTickSize())) {
            market.setTickSize(market.getPendingTickSize());
            market.setPendingTickSize(null);
        }
        if(!Objects.isNull(market.getPendingStepSize())) {
            market.setStepSize(market.getPendingStepSize());
            market.setPendingStepSize(null);
        }
        // TODO - orders breaching the new step size, tick size, or decimal places will be canceled
        // TODO - orders breaching the new margin requirements will be canceled
        // TODO - positions that would breach the new margin requirements will be closed out
        // TODO - increasing liquidation fee will force out positions that become distressed
        marketRepository.save(market);
    }

    public void suspend(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        // TODO - the market can only be settled (it cannot be unsuspended until it has been) and all orders will be canceled
        updateStatus(proposal, MarketStatus.SUSPENDED);
    }

    public void unsuspend(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        // TODO - the market can only be unsuspended if it was settled, so when it opens again nobody will have any positions and all orders will be canceled
        updateStatus(proposal, MarketStatus.ACTIVE);
    }

    public void updateLastPrice(
            final BigDecimal lastPrice,
            final Market market
    ) {
        market.setLastPrice(lastPrice);
        market.setMarkPrice(orderService.getMidPrice(market));
        market.setOpenVolume(positionService.calculateOpenVolume(market));
        marketRepository.save(market);
        positionService.updateUnrealisedProfit(market);
    }
}