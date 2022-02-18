package com.jynx.pro.service;

import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.AssetType;
import com.jynx.pro.constant.ProposalType;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.AssetRepository;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
public class AssetService {

    @Autowired
    private ProposalService proposalService;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private StakeService stakeService;
    @Autowired
    private EthereumService ethereumService;
    @Autowired
    private UUIDUtils uuidUtils;

    public Asset get(
            final UUID id
    ) {
        return assetRepository.findById(id).orElseThrow(() -> new JynxProException(ErrorCode.ASSET_NOT_FOUND));
    }

    public Asset getByAddress(
            final String assetAddress
    ) {
        return assetRepository.findByAddressAndType(assetAddress, AssetType.ERC20)
                .stream().filter(a -> a.getStatus().equals(AssetStatus.ACTIVE))
                .findFirst().orElseThrow(() -> new JynxProException(ErrorCode.ASSET_NOT_FOUND));
    }

    private void updateStatus(
            final Proposal proposal,
            final AssetStatus status
    ) {
        Asset asset = assetRepository.findById(proposal.getLinkedId())
                .orElseThrow(() -> new JynxProException(ErrorCode.ASSET_NOT_FOUND));
        asset.setStatus(status);
        assetRepository.save(asset);
    }

    public void add(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        Asset asset = get(proposal.getLinkedId());
        ethereumService.addAsset(asset.getAddress());
        updateStatus(proposal, AssetStatus.ACTIVE);
    }

    public void reject(
            final Proposal proposal
    ) {
        updateStatus(proposal, AssetStatus.REJECTED);
    }

    public void suspend(
            final Proposal proposal
    ) {
        // TODO - need to suspend all markets that are using this asset
        proposalService.checkEnacted(proposal);
        Asset asset = get(proposal.getLinkedId());
        ethereumService.removeAsset(asset.getAddress());
        updateStatus(proposal, AssetStatus.SUSPENDED);
    }

    public void unsuspend(
            final Proposal proposal
    ) {
        // TODO - need to suspend all markets that are using this asset
        proposalService.checkEnacted(proposal);
        Asset asset = get(proposal.getLinkedId());
        ethereumService.addAsset(asset.getAddress());
        updateStatus(proposal, AssetStatus.ACTIVE);
    }

    public Asset proposeToAdd(
            final AddAssetRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        List<Asset> assetCheck = assetRepository.findByAddressAndType(request.getAddress(), request.getType());
        if(assetCheck.stream().anyMatch(a -> !a.getStatus().equals(AssetStatus.REJECTED))) {
            throw new JynxProException(ErrorCode.ASSET_EXISTS_ALREADY);
        }
        Asset asset = new Asset()
                .setAddress(request.getAddress())
                .setType(request.getType())
                .setStatus(AssetStatus.PENDING)
                .setName(request.getName())
                .setDecimalPlaces(request.getDecimalPlaces())
                .setId(uuidUtils.next());
        asset = assetRepository.save(asset);
        proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), asset.getId(), ProposalType.ADD_ASSET);
        return asset;
    }

    public void proposeToSuspend(
            final SingleItemRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Asset asset = this.get(request.getId());
        if(!asset.getStatus().equals(AssetStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.ASSET_NOT_ACTIVE);
        }
        proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), asset.getId(), ProposalType.SUSPEND_ASSET);
    }

    public void proposeToUnsuspend(
            final SingleItemRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Asset asset = this.get(request.getId());
        if(!asset.getStatus().equals(AssetStatus.SUSPENDED)) {
            throw new JynxProException(ErrorCode.ASSET_NOT_SUSPENDED);
        }
        proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), asset.getId(), ProposalType.UNSUSPEND_ASSET);
    }
}