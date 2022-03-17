package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.ProposalStatus;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.entity.Vote;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.CastVoteRequest;
import com.jynx.pro.request.SingleItemRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class AssetServiceTest extends IntegrationTest {

    @Autowired
    private AssetService assetService;

    @BeforeEach
    public void setup() {
        initializeState();
        databaseTransactionManager.createTransaction();
    }

    @AfterEach
    public void shutdown() {
        databaseTransactionManager.commit();
        clearState();
    }

    @Test
    public void testProposeToAdd() {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
    }

    @Test
    public void testProposeToAddErrorOnDuplicate() {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        try {
            assetService.proposeToAdd(getAddAssetRequest(takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_EXISTS_ALREADY);
        }
    }

    @Test
    public void testProposeToAddErrorWithCloseBeforeOpen() {
        AddAssetRequest request = getAddAssetRequest(takerUser);
        request.setClosingTime(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli());
        request.setOpenTime(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()+1000);
        try {
            assetService.proposeToAdd(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CLOSE_BEFORE_OPEN);
        }
    }

    @Test
    public void testProposeToAddErrorWithEnactBeforeClose() {
        AddAssetRequest request = getAddAssetRequest(takerUser);
        request.setOpenTime(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()-1000);
        request.setClosingTime(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()+1000);
        request.setEnactmentTime(LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli());
        try {
            assetService.proposeToAdd(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ENACT_BEFORE_CLOSE);
        }
    }

    @Test
    public void testCannotVoteTwice() {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        CastVoteRequest voteRequest = new CastVoteRequest()
                .setId(proposal.getId())
                .setInFavour(true);
        voteRequest.setUser(takerUser);
        try {
            proposalService.vote(voteRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ALREADY_VOTED);
        }
    }

    @Test
    public void testCannotVoteWhenDisabled() {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        CastVoteRequest voteRequest = new CastVoteRequest()
                .setId(proposal.getId())
                .setInFavour(true);
        voteRequest.setUser(makerUser);
        try {
            proposalService.vote(voteRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.VOTING_DISABLED);
        }
    }

    @Test
    public void testCannotVoteWhenProposalNotFound() {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        CastVoteRequest voteRequest = new CastVoteRequest()
                .setId(UUID.randomUUID())
                .setInFavour(true);
        voteRequest.setUser(makerUser);
        try {
            proposalService.vote(voteRequest);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.PROPOSAL_NOT_FOUND);
        }
    }

    @Test
    public void testVoteWhenProposalOpen() throws InterruptedException {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        CastVoteRequest voteRequest = new CastVoteRequest()
                .setId(proposal.getId())
                .setInFavour(true);
        voteRequest.setUser(makerUser);
        Vote vote = proposalService.vote(voteRequest);
        Assertions.assertEquals(vote.getInFavour(), true);
    }

    @Test
    public void testProposeToAddAndEnact() throws InterruptedException, DecoderException {
        AddAssetRequest request = getAddAssetRequest(takerUser);
        Proposal proposal = assetService.proposeToAdd(request);
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        addToBridge(request.getAddress(), proposal.getNonce());
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.ACTIVE);
    }

    @Test
    public void testProposeToAddAndFailToEnactWhenBelowThreshold() throws InterruptedException {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(makerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        long original = configService.getTimestamp();
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        configService.setTimestamp(original);
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToAddAndFailToEnactWithoutPassingOpenTime() throws InterruptedException {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToAddAndFailToEnactWithoutPassingEnactTime() throws InterruptedException {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        long original = configService.getTimestamp();
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        configService.setTimestamp(original);
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToAddAndFailToEnactWhenRejected() throws InterruptedException {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(makerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.REJECTED);
    }

    @Test
    public void testAddFailWhenAssetMissing() {
        try {
            assetService.add(new Proposal().setStatus(ProposalStatus.ENACTED).setLinkedId(UUID.randomUUID()));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_NOT_FOUND);
        }
    }

    @Test
    public void testCannotGetMissingAsset() {
        try {
            assetService.get(UUID.randomUUID());
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_NOT_FOUND);
        }
    }

    @Test
    public void testProposeToAddDuplicateAllowedWhenPreviouslyRejected() throws InterruptedException {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(makerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.REJECTED);
        proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToSuspend() throws InterruptedException, DecoderException {
        AddAssetRequest addAssetRequest = getAddAssetRequest(takerUser);
        Proposal proposal = assetService.proposeToAdd(addAssetRequest);
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        addToBridge(addAssetRequest.getAddress(), proposal.getNonce());
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.ACTIVE);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(asset.getId()).setBridgeNonce("2");
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        assetService.proposeToSuspend(request);
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.CREATED);
        Assertions.assertEquals(proposals.size(), 1);
        Assertions.assertEquals(proposals.get(0).getLinkedId(), asset.getId());
        Thread.sleep(100L);
        removeFromBridge(asset.getAddress(), "2");
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.SUSPENDED);
    }

    @Test
    public void testProposeToSuspendFailWhenNotActive() throws InterruptedException {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(asset.getId()).setBridgeNonce("1");
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        try {
            assetService.proposeToSuspend(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_NOT_ACTIVE);
        }
    }

    @Test
    public void testProposeToUnsuspend() throws InterruptedException, DecoderException {
        AddAssetRequest addAssetRequest = getAddAssetRequest(takerUser);
        Proposal proposal = assetService.proposeToAdd(addAssetRequest);
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        addToBridge(addAssetRequest.getAddress(), proposal.getNonce());
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.ACTIVE);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(asset.getId()).setBridgeNonce("2");
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        assetService.proposeToSuspend(request);
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.CREATED);
        Assertions.assertEquals(proposals.size(), 1);
        Assertions.assertEquals(proposals.get(0).getLinkedId(), asset.getId());
        Thread.sleep(100L);
        removeFromBridge(asset.getAddress(), "2");
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.SUSPENDED);
        SingleItemRequest request2 = new SingleItemRequest().setId(asset.getId()).setBridgeNonce("3");
        request2.setOpenTime(times[0]);
        request2.setClosingTime(times[1]);
        request2.setEnactmentTime(times[2]);
        request2.setUser(takerUser);
        assetService.proposeToUnsuspend(request2);
        proposals = proposalRepository.findByStatus(ProposalStatus.CREATED);
        Assertions.assertEquals(proposals.size(), 1);
        Assertions.assertEquals(proposals.get(0).getLinkedId(), asset.getId());
        Thread.sleep(100L);
        addToBridge(asset.getAddress(), "3");
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.ACTIVE);
    }

    @Test
    public void testProposeToUnsuspendFailWhenNotActive() throws InterruptedException {
        Proposal proposal = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        Thread.sleep(100L);
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(asset.getId()).setBridgeNonce("2");
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        try {
            assetService.proposeToUnsuspend(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_NOT_SUSPENDED);
        }
    }

    @Test
    public void testGetByAddressFailsWhenMissing() {
        try {
            assetService.getByAddress("12345");
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_NOT_FOUND);
        }
    }
}