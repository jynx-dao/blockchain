package com.jynx.pro.service;

import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.handler.SocketHandler;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.request.AmendMarketRequest;
import com.jynx.pro.request.BatchValidatorRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
    private ConfigService configService;
    @Autowired
    private OracleRepository oracleRepository;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AuctionTriggerRepository auctionTriggerRepository;
    @Autowired
    private PendingAuctionTriggerRepository pendingAuctionTriggerRepository;
    @Autowired
    private SocketHandler socketHandler;

    /**
     * Get a market by ID if it exists
     *
     * @param id the market ID
     *
     * @return {@link Market}
     */
    public Market get(
            final UUID id
    ) {
        return marketRepository.findById(id).orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
    }

    /**
     * Settle an open position
     *
     * @param position {@link Position}
     * @param market {@link Market}
     * @param value settlement price
     */
    private void settlePosition(
            final Position position,
            final Market market,
            final BigDecimal value
    ) {
        BigDecimal settlementAmount = position.getQuantity().multiply(position.getAverageEntryPrice()).multiply(value);
        if(position.getSide().equals(MarketSide.SELL)) {
            settlementAmount = settlementAmount.multiply(BigDecimal.valueOf(-1));
        }
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
        accountService.save(account);
        accountService.reconcileNegativeBalance(account.getUser(), market);
    }

    /**
     * Settle markets using Oracle price data
     *
     * @param request {@link BatchValidatorRequest}
     *
     * @return {@link List<Market>}
     */
    public List<Market> settleMarkets(
            final BatchValidatorRequest request
    ) {
        log.debug(request.toString());
        List<Market> markets = marketRepository.findByStatusIn(List.of(MarketStatus.ACTIVE, MarketStatus.SUSPENDED));
        for(Market market : markets) {
            int dps = market.getSettlementAsset().getDecimalPlaces();
            long timeSinceSettlement = configService.getTimestamp() - market.getLastSettlement();
            if(timeSinceSettlement > (60 * 60 * 8.5)) {
                List<Position> positions = positionRepository
                        .findByMarketAndQuantityGreaterThan(market, BigDecimal.ZERO);
                BigDecimal value = oracleService.getSettlementValue(market);
                BigDecimal settlementDelta = (value.subtract(market.getMarkPrice()))
                        .divide(market.getMarkPrice(), dps, RoundingMode.HALF_UP);
                positions.forEach(position -> settlePosition(position, market, settlementDelta));
            }
        }
        return markets;
    }

    /**
     * Create a {@link Proposal} to add a market
     *
     * @param request {@link AddMarketRequest}
     *
     * @return {@link Proposal}
     */
    public Proposal proposeToAdd(
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
        Oracle oracle = new Oracle()
                .setStatus(OracleStatus.ACTIVE)
                .setType(request.getOracleType())
                .setId(uuidUtils.next())
                .setKey(request.getOracleKey());
        if(oracle.getType().equals(OracleType.SIGNED_DATA)) {
            throw new JynxProException(ErrorCode.SIGNED_DATA_UNSUPPORTED);
        }
        oracle = oracleRepository.save(oracle);
        Market market = new Market()
                .setOracle(oracle)
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
                .setLastSettlement(0L);
        save(market);
        if(request.getAuctionTriggers().size() > 0) {
            List<AuctionTrigger> auctionTriggers = request.getAuctionTriggers().stream()
                    .map(t -> new AuctionTrigger()
                            .setMarket(market)
                            .setId(uuidUtils.next())
                            .setDepth(t.getDepth())
                            .setOpenVolumeRatio(t.getOpenVolumeRatio()))
                    .collect(Collectors.toList());
            auctionTriggerRepository.saveAll(auctionTriggers);
        }
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.ADD_MARKET);
    }

    /**
     * Create a {@link Proposal} to amend a market
     *
     * @param request {@link AmendMarketRequest}
     *
     * @return {@link Proposal}
     */
    public Proposal proposeToAmend(
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
            market.setPendingMarginRequirement(request.getMarginRequirement());
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
        if(request.getAuctionTriggers().size() > 0) {
            final Market auctionMarket = market;
            pendingAuctionTriggerRepository.findByMarketId(auctionMarket.getId())
                    .forEach(t -> pendingAuctionTriggerRepository.delete(t.getId()));
            List<PendingAuctionTrigger> auctionTriggers = request.getAuctionTriggers().stream()
                    .map(t -> new PendingAuctionTrigger()
                            .setMarket(auctionMarket)
                            .setId(uuidUtils.next())
                            .setDepth(t.getDepth())
                            .setOpenVolumeRatio(t.getOpenVolumeRatio()))
                    .collect(Collectors.toList());
            pendingAuctionTriggerRepository.saveAll(auctionTriggers);
        }
        market = save(market);
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.AMEND_MARKET);
    }

    /**
     * Create a {@link Proposal} to suspend a market
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link Proposal}
     */
    public Proposal proposeToSuspend(
            final SingleItemRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Market market = get(request.getId());
        if(!market.getStatus().equals(MarketStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_ACTIVE);
        }
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.SUSPEND_MARKET);
    }

    /**
     * Create a {@link Proposal} to unsuspend a market
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link Proposal}
     */
    public Proposal proposeToUnsuspend(
            final SingleItemRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Market market = get(request.getId());
        if(!market.getStatus().equals(MarketStatus.SUSPENDED)) {
            throw new JynxProException(ErrorCode.MARKET_NOT_SUSPENDED);
        }
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.UNSUSPEND_MARKET);
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
        save(market);
    }

    /**
     * Activate a {@link Market} and enact its proposal
     *
     * @param proposal {@link Proposal}
     */
    public void add(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        updateStatus(proposal, MarketStatus.ACTIVE);
    }

    /**
     * Reject a {@link Market} by {@link Proposal}
     */
    public void reject(
            final Proposal proposal
    ) {
        updateStatus(proposal, MarketStatus.REJECTED);
    }

    /**
     * Amend a {@link Market} by {@link Proposal}
     */
    public void amend(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        Market market = get(proposal.getLinkedId());
        List<PendingAuctionTrigger> pendingTriggers = pendingAuctionTriggerRepository.findByMarketId(market.getId());
        if(!Objects.isNull(pendingTriggers)) {
            List<AuctionTrigger> triggers = pendingTriggers.stream()
                    .map(t -> new AuctionTrigger()
                            .setDepth(t.getDepth())
                            .setMarket(t.getMarket())
                            .setId(uuidUtils.next())
                            .setOpenVolumeRatio(t.getOpenVolumeRatio()))
                    .collect(Collectors.toList());
            auctionTriggerRepository.findByMarketId(market.getId())
                    .forEach(t -> auctionTriggerRepository.delete(t.getId()));
            pendingAuctionTriggerRepository.findByMarketId(market.getId())
                    .forEach(t -> pendingAuctionTriggerRepository.delete(t.getId()));
            auctionTriggerRepository.saveAll(triggers);
        }
        if(!Objects.isNull(market.getPendingMarginRequirement())) {
            market.setMarginRequirement(market.getPendingMarginRequirement());
            market.setPendingMarginRequirement(null);
        }
        if(!Objects.isNull(market.getPendingMakerFee())) {
            market.setMakerFee(market.getPendingMakerFee());
            market.setPendingMakerFee(null);
        }
        if(!Objects.isNull(market.getPendingTakerFee())) {
            market.setTakerFee(market.getPendingTakerFee());
            market.setPendingTakerFee(null);
        }
        if(!Objects.isNull(market.getPendingLiquidationFee())) {
            market.setLiquidationFee(market.getPendingLiquidationFee());
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
        //  orders breaching the new margin requirements will be canceled
        //  positions that would breach the new margin requirements will be closed out
        //  increasing liquidation fee will force out positions that become distressed
        save(market);
    }

    /**
     * Suspend a {@link Market} by {@link Proposal}
     */
    public void suspend(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        // TODO - the market can only be settled (it cannot be unsuspended
        //  until it has been) and all orders will be canceled
        updateStatus(proposal, MarketStatus.SUSPENDED);
    }

    /**
     * Unsuspend a {@link Market} by {@link Proposal}
     */
    public void unsuspend(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        // TODO - the market can only be unsuspended if it was settled, so when it
        //  opens again nobody will have any positions and all orders will be canceled
        updateStatus(proposal, MarketStatus.ACTIVE);
    }

    /**
     * Updates the market with last traded price and latest mark price
     *
     * @param lastPrice the last traded price
     * @param markPrice the latest mark price
     * @param market {@link Market}
     */
    public void updateLastPrice(
            final BigDecimal lastPrice,
            final BigDecimal markPrice,
            final Market market
    ) {
        market.setLastPrice(lastPrice);
        market.setMarkPrice(markPrice);
        market.setOpenVolume(positionService.calculateOpenVolume(market));
        save(market);
        positionService.updateUnrealisedProfit(market);
    }

    /**
     * Save a market
     *
     * @param market {@link Market}
     *
     * @return {@link Market}
     */
    public Market save(
            final Market market
    ) {
        socketHandler.sendMessage(WebSocketChannelType.MARKETS, market.getId(), market);
        return marketRepository.save(market);
    }
}