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

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
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

    /**
     * Get {@link Asset} by ID
     *
     * @param id the asset ID
     *
     * @return {@link Asset}
     */
    public Asset get(
            final UUID id
    ) {
        return assetRepository.findById(id).orElseThrow(() -> new JynxProException(ErrorCode.ASSET_NOT_FOUND));
    }

    /**
     * Get {@link Asset} by address
     *
     * @param address the asset address
     *
     * @return {@link Asset}
     */
    public Asset getByAddress(
            final String address
    ) {
        return assetRepository.findByAddressAndType(address, AssetType.ERC20)
                .stream().filter(a -> a.getStatus().equals(AssetStatus.ACTIVE))
                .findFirst().orElseThrow(() -> new JynxProException(ErrorCode.ASSET_NOT_FOUND));
    }

    /**
     * Update the status of an asset for given {@link Proposal}
     *
     * @param proposal {@link Proposal}
     * @param status {@link AssetStatus}
     */
    private void updateStatus(
            final Proposal proposal,
            final AssetStatus status
    ) {
        Asset asset = get(proposal.getLinkedId());
        asset.setStatus(status);
        assetRepository.save(asset);
    }

    /**
     * Add an asset for the given proposal
     *
     * @param proposal {@link Proposal}
     */
    public void add(
            final Proposal proposal
    ) {
        proposalService.checkEnacted(proposal);
        Asset asset = get(proposal.getLinkedId());
        ethereumService.addAsset(asset.getAddress()); // TODO - how TF we gonna do this with multiple validators?
        updateStatus(proposal, AssetStatus.ACTIVE);
    }

    /**
     * Reject a proposal for a new asset
     *
     * @param proposal {@link Proposal}
     */
    public void reject(
            final Proposal proposal
    ) {
        updateStatus(proposal, AssetStatus.REJECTED);
    }

    /**
     * Suspend an asset for the given proposal
     *
     * @param proposal {@link Proposal}
     */
    public void suspend(
            final Proposal proposal
    ) {
        // TODO - need to suspend all markets that are using this asset
        proposalService.checkEnacted(proposal);
        Asset asset = get(proposal.getLinkedId());
        ethereumService.removeAsset(asset.getAddress()); // TODO - how TF we gonna do this with multiple validators?
        updateStatus(proposal, AssetStatus.SUSPENDED);
    }

    /**
     * Unsuspend an asset for the given proposal
     *
     * @param proposal {@link Proposal}
     */
    public void unsuspend(
            final Proposal proposal
    ) {
        // TODO - need to suspend all markets that are using this asset
        proposalService.checkEnacted(proposal);
        Asset asset = get(proposal.getLinkedId());
        ethereumService.addAsset(asset.getAddress()); // TODO - how TF we gonna do this with multiple validators?
        updateStatus(proposal, AssetStatus.ACTIVE);
    }

    /**
     * Propose to add a new {@link Asset}
     *
     * @param request {@link AddAssetRequest}
     *
     * @return {@link Proposal}
     */
    public Proposal proposeToAdd(
            final AddAssetRequest request
    ) {
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        List<Asset> assetCheck = assetRepository.findByAddressAndType(request.getAddress(), request.getType());
        if(assetCheck.stream().anyMatch(a -> !a.getStatus().equals(AssetStatus.REJECTED))) {
            throw new JynxProException(ErrorCode.ASSET_EXISTS_ALREADY);
        }
        if(request.getDecimalPlaces() > 8) {
            throw new JynxProException(ErrorCode.TOO_MANY_DECIMAL_PLACES);
        }
        Asset asset = new Asset()
                .setAddress(request.getAddress())
                .setType(request.getType())
                .setStatus(AssetStatus.PENDING)
                .setName(request.getName())
                .setDecimalPlaces(request.getDecimalPlaces())
                .setId(uuidUtils.next());
        asset = assetRepository.save(asset);
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), asset.getId(), ProposalType.ADD_ASSET);
    }

    /**
     * Propose to suspend an {@link Asset}
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
        Asset asset = this.get(request.getId());
        if(!asset.getStatus().equals(AssetStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.ASSET_NOT_ACTIVE);
        }
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), asset.getId(), ProposalType.SUSPEND_ASSET);
    }

    /**
     * Propose to unsuspend an {@link Asset}
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
        Asset asset = this.get(request.getId());
        if(!asset.getStatus().equals(AssetStatus.SUSPENDED)) {
            throw new JynxProException(ErrorCode.ASSET_NOT_SUSPENDED);
        }
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), asset.getId(), ProposalType.UNSUSPEND_ASSET);
    }
}