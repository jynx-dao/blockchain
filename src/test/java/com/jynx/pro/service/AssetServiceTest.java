package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.ProposalStatus;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.SingleItemRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    }

    @AfterEach
    public void shutdown() {
        clearState();
    }

    @Test
    public void testProposeToAdd() {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToAddErrorOnDuplicate() {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        try {
            assetService.proposeToAdd(getAddAssetRequest(takerUser));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_EXISTS_ALREADY);
        }
    }

    @Test
    public void testProposeToAddAndEnact() throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.ACTIVE);
    }

    @Test
    public void testProposeToAddAndFailToEnactWhenBelowThreshold() throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(makerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        long original = configService.getTimestamp();
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        configService.setTimestamp(original);
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToAddAndFailToEnactWithoutPassingOpenTime() throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToAddAndFailToEnactWithoutPassingEnactTime() throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        long original = configService.getTimestamp();
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        configService.setTimestamp(original);
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToAddAndFailToEnactWhenRejected() throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(makerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.REJECTED);
    }

    @Test
    public void testAddFailWhenNotEnacted() {
        try {
            assetService.add(new Proposal().setStatus(ProposalStatus.OPEN));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.PROPOSAL_NOT_ENACTED);
        }
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
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(makerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.REJECTED);
        asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
    }

    @Test
    public void testProposeToSuspend() throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.ACTIVE);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(asset.getId());
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        assetService.proposeToSuspend(request);
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.CREATED);
        Assertions.assertEquals(proposals.size(), 1);
        Assertions.assertEquals(proposals.get(0).getLinkedId(), asset.getId());
        Thread.sleep(3000L);
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
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(asset.getId());
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
    public void testProposeToUnsuspend() throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.ACTIVE);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(asset.getId());
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        assetService.proposeToSuspend(request);
        List<Proposal> proposals = proposalRepository.findByStatus(ProposalStatus.CREATED);
        Assertions.assertEquals(proposals.size(), 1);
        Assertions.assertEquals(proposals.get(0).getLinkedId(), asset.getId());
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.SUSPENDED);
        SingleItemRequest request2 = new SingleItemRequest().setId(asset.getId());
        request2.setOpenTime(times[0]);
        request2.setClosingTime(times[1]);
        request2.setEnactmentTime(times[2]);
        request2.setUser(takerUser);
        assetService.proposeToUnsuspend(request2);
        proposals = proposalRepository.findByStatus(ProposalStatus.CREATED);
        Assertions.assertEquals(proposals.size(), 1);
        Assertions.assertEquals(proposals.get(0).getLinkedId(), asset.getId());
        Thread.sleep(3000L);
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
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(asset.getId());
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
}