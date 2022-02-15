package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Oracle;
import com.jynx.pro.entity.OracleType;
import com.jynx.pro.error.ErrorCode;
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

import java.util.Collections;
import java.util.List;

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
        createAndEnactMarket();
    }

    @Test
    public void testSuspendMarket() throws InterruptedException {
        Market market = createAndEnactMarket();
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(market.getId());
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToSuspend(request);
        Thread.sleep(3000L);
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
        Market market = createAndEnactMarket();
        long[] times = proposalTimes();
        SingleItemRequest request = new SingleItemRequest().setId(market.getId());
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(takerUser);
        marketService.proposeToSuspend(request);
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getStatus(), MarketStatus.SUSPENDED);
        marketService.proposeToUnsuspend(request);
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getStatus(), MarketStatus.ACTIVE);
    }
}
