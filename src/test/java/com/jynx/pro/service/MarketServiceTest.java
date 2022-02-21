package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Oracle;
import com.jynx.pro.constant.OracleType;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.AmendMarketRequest;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class MarketServiceTest extends IntegrationTest {

    @Autowired
    private MarketService marketService;

    @BeforeEach
    public void setup() {
        initializeState();
    }

    @AfterEach
    public void shutdown() {
        clearState();
    }

    @Test
    public void testAddMarket() throws InterruptedException {
        Asset asset = createAndEnactAsset(true);
        List<Oracle> oracles = List.of(new Oracle().setType(OracleType.SIGNED_DATA).setIdentifier("price"));
        Market market = marketService.proposeToAdd(getAddMarketRequest(asset, oracles));
        Assertions.assertEquals(market.getStatus(), MarketStatus.PENDING);
    }

    @Test
    public void testAddMarketFailsWhenAssetNotActive() throws InterruptedException {
        Asset asset = createAndEnactAsset(false);
        List<Oracle> oracles = List.of(new Oracle().setType(OracleType.SIGNED_DATA).setIdentifier("price"));
        try {
            marketService.proposeToAdd(getAddMarketRequest(asset, oracles));
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ASSET_NOT_ACTIVE);
        }
    }

    @Test
    public void testAddMarketFailsWithNoOracle() throws InterruptedException {
        Asset asset = createAndEnactAsset(true);
        List<Oracle> oracles = Collections.emptyList();
        try {
            marketService.proposeToAdd(getAddMarketRequest(asset, oracles));
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.ORACLE_NOT_DEFINED);
        }
    }

    @Test
    public void testAddMarketAndEnact() throws InterruptedException {
        createAndEnactMarket(true);
    }

    @Test
    public void testSuspendMarket() throws InterruptedException {
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
    public void testUnsuspendMarket() throws InterruptedException {
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
    public void testAmendInitialMargin() throws InterruptedException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getInitialMargin().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.01).setScale(2, RoundingMode.HALF_UP));
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setInitialMargin(BigDecimal.valueOf(0.2));
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
        Assertions.assertEquals(market.getInitialMargin().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.20).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendMaintenanceMargin() throws InterruptedException {
        Market market = createAndEnactMarket(true);
        Assertions.assertEquals(market.getMaintenanceMargin().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.01).setScale(2, RoundingMode.HALF_UP));
        long[] times = proposalTimes();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setMaintenanceMargin(BigDecimal.valueOf(0.2));
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
        Assertions.assertEquals(market.getMaintenanceMargin().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(0.20).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    public void testAmendTickSize() throws InterruptedException {
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
    public void testAmendStepSize() throws InterruptedException {
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
    public void testAmendMakerFee() throws InterruptedException {
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
    public void testAmendTakerFee() throws InterruptedException {
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
    public void testAmendSettlementFrequency() throws InterruptedException {
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
    public void testCannotGetMissingMarket() {
        try {
            marketService.get(UUID.randomUUID());
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.MARKET_NOT_FOUND);
        }
    }
}
