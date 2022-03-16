package com.jynx.pro.service;

import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.AssetType;
import com.jynx.pro.constant.BridgeUpdateType;
import com.jynx.pro.constant.ProposalType;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.BridgeUpdate;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.AssetRepository;
import com.jynx.pro.repository.BridgeUpdateRepository;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private BridgeUpdateRepository bridgeUpdateRepository;
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
     * Create the {@link BridgeUpdate} record if it doesn't exist
     *
     * @param asset {@link Asset}
     * @param type {@link BridgeUpdateType}
     * @param nonce the nonce for the bridge
     */
    private void createBridgeUpdate(
            final Asset asset,
            final BridgeUpdateType type,
            final String nonce
    ) {
        List<BridgeUpdate> bridgeUpdates = bridgeUpdateRepository.findByAssetIdAndType(
                        asset.getId(), type).stream()
                .filter(b -> !b.getComplete())
                .collect(Collectors.toList());
        if(bridgeUpdates.size() == 0) {
            BridgeUpdate bridgeUpdate = new BridgeUpdate()
                    .setAsset(asset)
                    .setNonce(nonce)
                    .setId(uuidUtils.next())
                    .setComplete(false)
                    .setType(type);
            bridgeUpdateRepository.save(bridgeUpdate);
        }
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
        createBridgeUpdate(asset, BridgeUpdateType.ADD_ASSET, proposal.getNonce());
//        ethereumService.addAsset(asset.getAddress()); // TODO - how TF we gonna do this with multiple validators?
        boolean isActive = ethereumService.isAssetActive(asset.getAddress());
        if(isActive) {
            updateStatus(proposal, AssetStatus.ACTIVE);
        }
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
        createBridgeUpdate(asset, BridgeUpdateType.REMOVE_ASSET, proposal.getNonce());
//        ethereumService.removeAsset(asset.getAddress()); // TODO - how TF we gonna do this with multiple validators?
        boolean isActive = ethereumService.isAssetActive(asset.getAddress());
        if(!isActive) {
            updateStatus(proposal, AssetStatus.SUSPENDED);
        }
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
        createBridgeUpdate(asset, BridgeUpdateType.ADD_ASSET, proposal.getNonce());
//        ethereumService.addAsset(asset.getAddress()); // TODO - how TF we gonna do this with multiple validators?
        boolean isActive = ethereumService.isAssetActive(asset.getAddress());
        if(isActive) {
            updateStatus(proposal, AssetStatus.ACTIVE);
        }
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
        checkDuplicatedNonce(request.getBridgeNonce());
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
                request.getEnactmentTime(), asset.getId(), ProposalType.ADD_ASSET, request.getBridgeNonce());
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
        checkDuplicatedNonce(request.getBridgeNonce());
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Asset asset = this.get(request.getId());
        if(!asset.getStatus().equals(AssetStatus.ACTIVE)) {
            throw new JynxProException(ErrorCode.ASSET_NOT_ACTIVE);
        }
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), asset.getId(), ProposalType.SUSPEND_ASSET, request.getBridgeNonce());
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
        checkDuplicatedNonce(request.getBridgeNonce());
        stakeService.checkProposerStake(request.getUser());
        proposalService.checkProposalTimes(request.getOpenTime(), request.getClosingTime(), request.getEnactmentTime());
        Asset asset = this.get(request.getId());
        if(!asset.getStatus().equals(AssetStatus.SUSPENDED)) {
            throw new JynxProException(ErrorCode.ASSET_NOT_SUSPENDED);
        }
        return proposalService.create(request.getUser(), request.getOpenTime(), request.getClosingTime(),
                request.getEnactmentTime(), asset.getId(), ProposalType.UNSUSPEND_ASSET, request.getBridgeNonce());
    }

    /**
     * Check that a nonce has not already been used
     *
     * @param nonce the nonce
     */
    private void checkDuplicatedNonce(
            final String nonce
    ) {
        boolean result = proposalService.existsWithNonce(nonce);
        if(ethereumService.isNonceUsed(nonce) && !result) {
            throw new JynxProException(ErrorCode.NONCE_ALREADY_USED);
        }
    }
}