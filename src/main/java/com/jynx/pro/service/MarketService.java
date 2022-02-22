package com.jynx.pro.service;

import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.constant.ProposalType;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.MarketRepository;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.request.AmendMarketRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

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
    private OrderService orderService;

    public Market get(
            final UUID id
    ) {
        return marketRepository.findById(id).orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
    }

    public void settleMarkets() {
        // TODO - settle markets using their oracles
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
        if(request.getOracles().size() == 0) {
            throw new JynxProException(ErrorCode.ORACLE_NOT_DEFINED);
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
                .setId(uuidUtils.next());
        market = marketRepository.save(market);
        oracleService.save(request.getOracles(), market);
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
        marketRepository.save(market);
    }
    
    public void add(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        updateStatus(proposal, MarketStatus.ACTIVE);
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
        // TODO - orders breaching the new step size, tick size, or decimal places will be cancelled
        // TODO - orders breaching the new margin requirements will be cancelled
        // TODO - positions that would breach the new margin requirements will be closed out
        // TODO - increasing liquidation fee will force out positions that become distressed
        marketRepository.save(market);
    }

    public void suspend(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        // TODO - the market can only be settled (it cannot be unsuspended until it has been) and all orders will be cancelled
        updateStatus(proposal, MarketStatus.SUSPENDED);
    }

    public void unsuspend(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        // TODO - the market can only be unsuspended if it was settled, so when it opens again nobody will have any positions and all orders will be cancelled
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