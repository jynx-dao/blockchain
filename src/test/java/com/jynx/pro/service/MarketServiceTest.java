package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.request.AmendMarketRequest;
import com.jynx.pro.request.BatchValidatorRequest;
import com.jynx.pro.request.SingleItemRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "MARKET_SERVICE_TEST", matches = "true")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class MarketServiceTest extends IntegrationTest {

    @Autowired
    private MarketService marketService;

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
    public void testAddMarket() throws DecoderException {
        Asset asset = createAndEnactAsset(true);
        Proposal proposal = marketService.proposeToAdd(getAddMarketRequest(asset));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
    }

    @Test
    public void testAddMarketWithAuctionTriggers() throws DecoderException {
        Asset asset = createAndEnactAsset(true);
        List<AddMarketRequest.AuctionTrigger> triggers = new ArrayList<>();
        triggers.add(new AddMarketRequest.AuctionTrigger()
                .setDepth(BigDecimal.valueOf(0.001)).setOpenVolumeRatio(BigDecimal.ONE));
        Proposal proposal = marketService.proposeToAdd(getAddMarketRequest(asset).setAuctionTriggers(triggers));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        List<AuctionTrigger> auctionTriggers = auctionTriggerRepository.findByMarketId(proposal.getLinkedId());
        Assertions.assertEquals(1, auctionTriggers.size());
    }

    @Test
    public void testAddMarketFailsWhenAssetNotActive() throws DecoderException {
        Asset asset = createAndEnactAsset(false);
        try {
            marketService.proposeToAdd(getAddMarketRequest(asset));
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_NOT_ACTIVE);
        }
    }

    @Test
    public void testAddMarketFailsWithInvalidTakerFee() throws DecoderException {
        Asset asset = createAndEnactAsset(true);
        try {
            AddMarketRequest request = getAddMarketRequest(asset);
            request.setTakerFee(BigDecimal.valueOf(0.0001d));
            marketService.proposeToAdd(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_TAKER_FEE);
        }
    }

    @Test
    public void testAddMarketFailsWithInvalidLiquidationFee() throws DecoderException {
        Asset asset = createAndEnactAsset(true);
        try {
            AddMarketRequest request = getAddMarketRequest(asset);
            request.setLiquidationFee(BigDecimal.valueOf(0.3d));
            marketService.proposeToAdd(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_LIQUIDATION_FEE);
        }
    }

    @Test
    public void testAddMarketFailsWithSignedDataOracle() throws DecoderException {
        Asset asset = createAndEnactAsset(true);
        try {
            AddMarketRequest request = getAddMarketRequest(asset);
            request.setOracleType(OracleType.SIGNED_DATA);
            marketService.proposeToAdd(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.SIGNED_DATA_UNSUPPORTED);
        }
    }

    @Test
    public void testAddMarketAndEnact() throws DecoderException {
        createAndEnactMarket(true);
    }

    @Test
    public void testSuspendMarket() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(market.getId());
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToSuspend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getStatus(), MarketStatus.SUSPENDED);
    }

    @Test
    public void testSuspendMarketFailsWhenNotActive() throws DecoderException {
        Market market = createAndEnactMarket(false);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(market.getId());
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        try {
            marketService.proposeToSuspend(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MARKET_NOT_ACTIVE);
        }
    }

    @Test
    public void testUnsuspendMarketFailsWhenNotActive() throws DecoderException {
        Market market = createAndEnactMarket(true);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(market.getId());
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        try {
            marketService.proposeToUnsuspend(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MARKET_NOT_SUSPENDED);
        }
    }

    @Test
    public void testUnsuspendMarket() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(market.getId());
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToSuspend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getStatus(), MarketStatus.SUSPENDED);
        marketService.proposeToUnsuspend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getStatus(), MarketStatus.ACTIVE);
    }

    @Test
    public void testAmendFailsWithInvalidTakerFee() throws DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getMarginRequirement().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.01).setScale(2, RoundingMode.HALF_UP));
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setTakerFee(BigDecimal.valueOf(0.0005));
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        try {
            marketService.proposeToAmend(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_TAKER_FEE);
        }
    }

    @Test
    public void testAmendFailsWithInvalidLiquidationFee() throws DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getMarginRequirement().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.01).setScale(2, RoundingMode.HALF_UP));
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setLiquidationFee(BigDecimal.valueOf(0.3d));
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        try {
            marketService.proposeToAmend(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.INVALID_LIQUIDATION_FEE);
        }
    }

    @Test
    public void testAmendFailsWhenMarketNotActive() throws DecoderException {
        Market market = createAndEnactMarket(false);
        Assertions.assertEquals(market.getMarginRequirement().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.01).setScale(2, RoundingMode.HALF_UP));
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setTakerFee(BigDecimal.valueOf(0.05d));
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        try {
            marketService.proposeToAmend(request);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MARKET_NOT_ACTIVE);
        }
    }

    @Test
    public void testAmendMarginRequirement() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getMarginRequirement().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.01).setScale(2, RoundingMode.HALF_UP));
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setMarginRequirement(BigDecimal.valueOf(0.2));
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getMarginRequirement().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.20).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendTickSize() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getTickSize(), 1);
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setTickSize(2);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getTickSize(), 2);
    }

    @Test
    public void testAmendAuctionTriggersWithEmptyArray() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true, true);
        Assertions.assertEquals(market.getTickSize(), 1);
        long[] times = proposalTimes();
        List<AddMarketRequest.AuctionTrigger> triggers = new ArrayList<>();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setAuctionTriggers(triggers);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        List<AuctionTrigger> auctionTriggers = auctionTriggerRepository.findByMarketId(market.getId());
        Assertions.assertEquals(0, auctionTriggers.size());
    }

    @Test
    public void testAmendAuctionTriggersWithNewTrigger() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true, true);
        Assertions.assertEquals(market.getTickSize(), 1);
        long[] times = proposalTimes();
        List<AddMarketRequest.AuctionTrigger> triggers = new ArrayList<>();
        triggers.add(new AddMarketRequest.AuctionTrigger().setOpenVolumeRatio(BigDecimal.ONE).setDepth(BigDecimal.TEN));
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setAuctionTriggers(triggers);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        List<AuctionTrigger> auctionTriggers = auctionTriggerRepository.findByMarketId(market.getId());
        Assertions.assertEquals(1, auctionTriggers.size());
        Assertions.assertEquals(10d, auctionTriggers.get(0).getDepth().doubleValue());
        Assertions.assertEquals(1d, auctionTriggers.get(0).getOpenVolumeRatio().doubleValue());
    }

    @Test
    public void testAmendStepSize() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getStepSize(), 1);
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setStepSize(2);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getStepSize(), 2);
    }

    @Test
    public void testAmendMakerFee() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getMakerFee().setScale(3, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.001).setScale(3, RoundingMode.HALF_UP));
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setMakerFee(BigDecimal.valueOf(0.0005));
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getMakerFee().setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.0005).setScale(4, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendTakerFee() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getTakerFee().setScale(3, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.001).setScale(3, RoundingMode.HALF_UP));
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setTakerFee(BigDecimal.valueOf(0.002));
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getTakerFee().setScale(3, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.002).setScale(3, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendSettlementFrequency() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getSettlementFrequency(), 8);
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setSettlementFrequency(4);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getSettlementFrequency(), 4);
    }

    @Test
    public void testAmendLiquidationFree() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getLiquidationFee().doubleValue(), 0.005d);
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setLiquidationFee(BigDecimal.valueOf(0.002d));
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getLiquidationFee().doubleValue(), 0.002d);
    }

    @Test
    public void testAmendmentRejected() throws InterruptedException, DecoderException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getLiquidationFee().doubleValue(), 0.005d);
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setLiquidationFee(BigDecimal.valueOf(0.002d));
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(makerUser);
        marketService.proposeToAmend(request);
        Thread.sleep(100L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketService.get(market.getId());
        Assertions.assertEquals(market.getStatus(), MarketStatus.REJECTED);
    }

    @Test
    public void testCannotGetMissingMarket() {
        try {
            marketService.get(UUID.randomUUID());
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MARKET_NOT_FOUND);
        }
    }

    @Test
    public void testMarketSettlement() {
        Market market = createOrderBook(10, 10, 1);
        market.setLastSettlement(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - 86400);
        market = marketRepository.save(market);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.ONE, MarketSide.SELL, OrderType.MARKET, takerUser));
        marketService.settleMarkets(new BatchValidatorRequest());
        market = marketRepository.findById(market.getId()).orElse(new Market());
        BigDecimal makerFee = BigDecimal.valueOf(45590).multiply(market.getMakerFee());
        List<Position> positions = positionRepository.findByMarket(market);
        List<Account> accounts = accountRepository.findByAsset(market.getSettlementAsset());
        List<Transaction> makerTxns = transactionRepository.findByUserAndAsset(makerUser, market.getSettlementAsset());
        List<Transaction> takerTxns = transactionRepository.findByUserAndAsset(makerUser, market.getSettlementAsset());
        Optional<Transaction> makerSettlement = makerTxns.stream()
                .filter(t -> t.getType().equals(TransactionType.SETTLEMENT)).findFirst();
        Optional<Transaction> takerSettlement = takerTxns.stream()
                .filter(t -> t.getType().equals(TransactionType.SETTLEMENT)).findFirst();
        Optional<Position> makerPosition = positions.stream()
                .filter(p -> p.getUser().getId().equals(makerUser.getId())).findFirst();
        Optional<Position> takerPosition = positions.stream()
                .filter(p -> p.getUser().getId().equals(takerUser.getId())).findFirst();
        Optional<Account> makerAccount = accounts.stream()
                .filter(a -> a.getUser().getId().equals(makerUser.getId())).findFirst();
        Optional<Account> takerAccount = accounts.stream()
                .filter(a -> a.getUser().getId().equals(takerUser.getId())).findFirst();
        Assertions.assertTrue(makerPosition.isPresent());
        Assertions.assertTrue(takerPosition.isPresent());
        Assertions.assertTrue(makerAccount.isPresent());
        Assertions.assertTrue(takerAccount.isPresent());
        Assertions.assertTrue(makerSettlement.isPresent());
        Assertions.assertTrue(takerSettlement.isPresent());
        BigDecimal makerRealisedProfit = makerFee.add(makerSettlement.get().getAmount());
        BigDecimal takerRealisedProfit = makerRealisedProfit.multiply(BigDecimal.valueOf(-1));
        Assertions.assertEquals(makerPosition.get().getRealisedPnl(), makerRealisedProfit);
        Assertions.assertEquals(takerPosition.get().getRealisedPnl(), takerRealisedProfit);
        Assertions.assertEquals(makerAccount.get().getBalance(), BigDecimal.valueOf(INITIAL_BALANCE).add(makerRealisedProfit));
        Assertions.assertEquals(takerAccount.get().getBalance(), BigDecimal.valueOf(INITIAL_BALANCE).add(takerRealisedProfit));
    }

    @Test
    public void testMarketSettlementSkip() {
        Market market = createOrderBook(10, 10, 1);
        orderService.create(getCreateOrderRequest(market.getId(),
                null, BigDecimal.ONE, MarketSide.BUY, OrderType.MARKET, takerUser));
        marketService.settleMarkets(new BatchValidatorRequest());
        BigDecimal makerFee = BigDecimal.valueOf(45610).multiply(market.getMakerFee());
        BigDecimal takerFee = BigDecimal.valueOf(45610).multiply(market.getTakerFee());
        List<Position> positions = positionRepository.findByMarket(market);
        List<Account> accounts = accountRepository.findByAsset(market.getSettlementAsset());
        List<Transaction> makerTxns = transactionRepository.findByUserAndAsset(makerUser, market.getSettlementAsset());
        List<Transaction> takerTxns = transactionRepository.findByUserAndAsset(makerUser, market.getSettlementAsset());
        Optional<Transaction> makerSettlement = makerTxns.stream()
                .filter(t -> t.getType().equals(TransactionType.SETTLEMENT)).findFirst();
        Optional<Transaction> takerSettlement = takerTxns.stream()
                .filter(t -> t.getType().equals(TransactionType.SETTLEMENT)).findFirst();
        Optional<Position> makerPosition = positions.stream()
                .filter(p -> p.getUser().getId().equals(makerUser.getId())).findFirst();
        Optional<Position> takerPosition = positions.stream()
                .filter(p -> p.getUser().getId().equals(takerUser.getId())).findFirst();
        Optional<Account> makerAccount = accounts.stream()
                .filter(a -> a.getUser().getId().equals(makerUser.getId())).findFirst();
        Optional<Account> takerAccount = accounts.stream()
                .filter(a -> a.getUser().getId().equals(takerUser.getId())).findFirst();
        Assertions.assertTrue(makerPosition.isPresent());
        Assertions.assertTrue(takerPosition.isPresent());
        Assertions.assertTrue(makerAccount.isPresent());
        Assertions.assertTrue(takerAccount.isPresent());
        Assertions.assertFalse(makerSettlement.isPresent());
        Assertions.assertFalse(takerSettlement.isPresent());
        Assertions.assertEquals(makerPosition.get().getRealisedPnl(), makerFee);
        Assertions.assertEquals(takerPosition.get().getRealisedPnl(), takerFee.multiply(BigDecimal.valueOf(-1)));
        Assertions.assertEquals(makerAccount.get().getBalance(), BigDecimal.valueOf(INITIAL_BALANCE).add(makerFee));
        Assertions.assertEquals(takerAccount.get().getBalance(), BigDecimal.valueOf(INITIAL_BALANCE).subtract(takerFee));
    }
}
