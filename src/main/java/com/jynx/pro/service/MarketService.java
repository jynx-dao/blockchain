package com.jynx.pro.service;

import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.constant.ProposalStatus;
import com.jynx.pro.constant.ProposalType;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.MarketRepository;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@Transactional
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

    public Market proposeToAdd(
            final AddMarketRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Asset settlementAsset = assetService.get(request.getSettlementAssetId());
        if(request.getOracles().size() == 0) {
            throw new JynxProException(ErrorCode.ORACLE_NOT_DEFINED);
        }
        Market market = new Market()
                .setName(request.getName())
                .setSettlementAsset(settlementAsset)
                .setDecimalPlaces(request.getDecimalPlaces())
                .setInitialMargin(request.getInitialMargin())
                .setMaintenanceMargin(request.getMaintenanceMargin())
                .setTickSize(request.getTickSize())
                .setStepSize(request.getStepSize())
                .setSettlementFrequency(request.getSettlementFrequency())
                .setMakerFee(request.getMakerFee())
                .setTakerFee(request.getTakerFee())
                .setStatus(MarketStatus.PENDING)
                .setId(uuidUtils.next());
        market = marketRepository.save(market);
        oracleService.save(request.getOracles(), market);
        proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), market.getId(), ProposalType.ADD_MARKET);
        return market;
    }

    public Market proposeToAmend(
            final UUID id,
            final Integer decimalPlaces,
            final BigDecimal initialMargin,
            final BigDecimal maintenanceMargin,
            final Integer tickSize,
            final Integer stepSize,
            final Integer settlementFrequency,
            final BigDecimal makerFee,
            final BigDecimal takerFee,
            final Long openTime,
            final Long closingTime,
            final Long enactmentTime,
            final User user
    ) {
        stakeService.checkProposerStake(user);
        proposalService.checkProposalTimes(openTime, closingTime, enactmentTime);
        Market market = marketRepository.findById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
        if(!maintenanceMargin.equals(market.getMaintenanceMargin())) {
            market.setPendingMaintenanceMargin(maintenanceMargin);
        }
        if(!decimalPlaces.equals(market.getDecimalPlaces())) {
            market.setPendingDecimalPlaces(decimalPlaces);
        }
        if(!initialMargin.equals(market.getInitialMargin())) {
            market.setPendingInitialMargin(initialMargin);
        }
        if(!makerFee.equals(market.getMakerFee())) {
            market.setPendingMakerFee(makerFee);
        }
        if(!takerFee.equals(market.getTakerFee())) {
            market.setPendingTakerFee(takerFee);
        }
        if(!stepSize.equals(market.getStepSize())) {
            market.setPendingStepSize(stepSize);
        }
        if(!tickSize.equals(market.getTickSize())) {
            market.setPendingTickSize(tickSize);
        }
        if(!settlementFrequency.equals(market.getSettlementFrequency())) {
            market.setPendingSettlementFrequency(settlementFrequency);
        }
        market = marketRepository.save(market);
        proposalService.create(user, openTime, closingTime, enactmentTime, market.getId(), ProposalType.AMEND_MARKET);
        return market;
    }

    public Market proposeToSuspend(
            final UUID id,
            final Long openTime,
            final Long closingTime,
            final Long enactmentTime,
            final User user
    ) {
        stakeService.checkProposerStake(user);
        proposalService.checkProposalTimes(openTime, closingTime, enactmentTime);
        Market market = marketRepository.findById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
        proposalService.create(user, openTime, closingTime, enactmentTime, market.getId(), ProposalType.SUSPEND_MARKET);
        return market;
    }

    public Market proposeToUnsuspend(
            final UUID id,
            final Long openTime,
            final Long closingTime,
            final Long enactmentTime,
            final User user
    ) {
        stakeService.checkProposerStake(user);
        proposalService.checkProposalTimes(openTime, closingTime, enactmentTime);
        Market market = marketRepository.findById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
        proposalService.create(user, openTime, closingTime, enactmentTime, market.getId(), ProposalType.UNSUSPEND_MARKET);
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
        Market market = marketRepository.findById(proposal.getLinkedId())
                .orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
        market.setStatus(status);
        marketRepository.save(market);
    }

    public void add(
            final Proposal proposal
    ) {
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
        if(!proposal.getStatus().equals(ProposalStatus.ENACTED)) {
            throw new JynxProException(ErrorCode.PROPOSAL_NOT_ENACTED);
        }
        Market market = marketRepository.findById(proposal.getLinkedId())
                .orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
        if(!Objects.isNull(market.getPendingDecimalPlaces())) {
            market.setDecimalPlaces(market.getPendingDecimalPlaces());
        }
        if(!Objects.isNull(market.getPendingInitialMargin())) {
            market.setInitialMargin(market.getPendingInitialMargin());
        }
        if(!Objects.isNull(market.getPendingMaintenanceMargin())) {
            market.setMaintenanceMargin(market.getPendingMaintenanceMargin());
        }
        if(!Objects.isNull(market.getPendingMakerFee())) {
            market.setMakerFee(market.getPendingMakerFee());
        }
        if(!Objects.isNull(market.getPendingTakerFee())) {
            market.setTakerFee(market.getPendingTakerFee());
        }
        if(!Objects.isNull(market.getPendingSettlementFrequency())) {
            market.setSettlementFrequency(market.getPendingSettlementFrequency());
        }
        if(!Objects.isNull(market.getPendingTickSize())) {
            market.setTickSize(market.getTickSize());
        }
        if(!Objects.isNull(market.getPendingStepSize())) {
            market.setStepSize(market.getTickSize());
        }
        // TODO - orders breaching the new step size, tick size, or decimal places will be cancelled
        // TODO - orders breaching the new margin requirements will be cancelled
        // TODO - positions that would breach the new margin requirements will be closed out
        marketRepository.save(market);
    }

    public void suspend(
            final Proposal proposal
    ) {
        // TODO - the market can only be settled (it cannot be unsuspended until it has been) and all orders will be cancelled
        updateStatus(proposal, MarketStatus.SUSPENDED);
    }

    public void unsuspend(
            final Proposal proposal
    ) {
        // TODO - the market can only be unsuspended if it was settled, so when it opens again nobody will have any positions and all orders will be cancelled
        updateStatus(proposal, MarketStatus.ACTIVE);
    }
}