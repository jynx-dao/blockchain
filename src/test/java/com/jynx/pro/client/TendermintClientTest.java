package com.jynx.pro.client;

import com.jynx.pro.Application;
import com.jynx.pro.blockchain.TendermintClient;
import com.jynx.pro.constant.*;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.manager.AppStateManager;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.request.*;
import com.jynx.pro.response.TransactionResponse;
import com.jynx.pro.service.IntegrationTest;
import com.jynx.pro.utils.CryptoUtils;
import com.jynx.pro.utils.JSONUtils;
import com.jynx.pro.utils.SleepUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;

@Slf4j
@Testcontainers
@ActiveProfiles("tendermint")
@DisabledIfEnvironmentVariable(named = "TRAVIS_CI", matches = "true")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TendermintClientTest extends IntegrationTest {

    @Autowired
    private TendermintClient tendermintClient;
    @Autowired
    private AppStateManager appStateManager;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private SleepUtils sleepUtils;
    @Autowired
    private CryptoUtils cryptoUtils;
    @Autowired
    private JSONUtils jsonUtils;
    @LocalServerPort
    private int port;

    public static GenericContainer tendermint;

    @BeforeEach
    public void setup() {
        initializeState(true);
        tendermint =
                new GenericContainer(DockerImageName.parse("tendermint/tendermint:v0.33.8"))
                        .withExposedPorts(26657)
                        .withCommand("node --abci grpc --proxy_app tcp://host.docker.internal:26658")
                        .withExtraHost("host.docker.internal", "host-gateway");
        tendermint.start();
        int port = tendermint.getFirstMappedPort();
        String host = String.format("http://%s", tendermint.getHost());
        tendermintClient.setBaseUri(host);
        tendermintClient.setPort(port);
        log.info("Tendermint URL = {}:{}", host, port);
        waitForBlockchain();
    }

    @AfterEach
    public void shutdown() {
        tendermint.stop();
        appStateManager.setBlockHeight(0);
        clearState();
    }

    private Asset addAsset() {
        AddAssetRequest request = new AddAssetRequest()
                .setAddress("0x0")
                .setName("USDC")
                .setDecimalPlaces(5)
                .setType(AssetType.ERC20);
        long[] times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        TransactionResponse<Proposal> txResponse = tendermintClient.addAsset(request);
        Assertions.assertEquals(txResponse.getItem().getStatus(), ProposalStatus.CREATED);
        ResponseEntity<Asset[]> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/asset/all", port), Asset[].class);
        Asset[] assetArray = responseEntity.getBody();
        Assertions.assertNotNull(assetArray);
        Assertions.assertEquals(assetArray.length, 2);
        Assertions.assertEquals(assetArray[1].getStatus(), AssetStatus.PENDING);
        Assertions.assertEquals(assetArray[1].getId(), txResponse.getItem().getLinkedId());
        return assetArray[0];
    }

    @Test
    public void testAddAsset() {
        addAsset();
    }

    @Test
    public void testSuspendAndUnsuspendAsset() {
        Asset asset = addAsset();
        SingleItemRequest request = new SingleItemRequest()
                .setId(asset.getId());
        long[] times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        tendermintClient.suspendAsset(request);
        syncProposals();
        ResponseEntity<Asset> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/asset/%s", port, asset.getId().toString()), Asset.class);
        asset = responseEntity.getBody();
        Assertions.assertNotNull(asset);
        Assertions.assertEquals(asset.getStatus(), AssetStatus.SUSPENDED);
        tendermintClient.unsuspendAsset(request);
        syncProposals();
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/asset/%s", port, asset.getId().toString()), Asset.class);
        asset = responseEntity.getBody();
        Assertions.assertNotNull(asset);
        Assertions.assertEquals(asset.getStatus(), AssetStatus.ACTIVE);
    }

    @Test
    public void testAddAssetFailsWithoutOpenTime() {
        AddAssetRequest request = new AddAssetRequest()
                .setAddress("0x0")
                .setName("Test asset")
                .setDecimalPlaces(5)
                .setType(AssetType.ERC20);
        long[] times = proposalTimes();
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        try {
            tendermintClient.addAsset(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.UNKNOWN_ERROR);
        }
    }

    @Test
    public void testAddAssetFailsWithTooManyDecimalPlaces() {
        AddAssetRequest request = new AddAssetRequest()
                .setAddress("0x0")
                .setName("Test asset")
                .setDecimalPlaces(10)
                .setType(AssetType.ERC20);
        long[] times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        try {
            tendermintClient.addAsset(request);
            Assertions.fail();
        } catch(Exception e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.TOO_MANY_DECIMAL_PLACES);
        }
    }

    private void syncProposals() {
        sleepUtils.sleep(100L);
        EmptyRequest emptyRequest = new EmptyRequest();
        emptyRequest.setPublicKey("50505050505050505050505050505050");
        tendermintClient.syncProposals(emptyRequest);
        sleepUtils.sleep(100L);
    }

    private Asset getDai() {
        ResponseEntity<Asset[]> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/asset/all", port), Asset[].class);
        Asset[] assetArray = responseEntity.getBody();
        Assertions.assertNotNull(assetArray);
        Asset asset = null;
        for (Asset value : assetArray) {
            if (value.getName().equals("DAI")) {
                asset = value;
            }
        }
        return asset;
    }

    private Market addMarket() {
        Asset asset = getDai();
        Assertions.assertNotNull(asset);
        syncProposals();
        AddMarketRequest request = new AddMarketRequest()
                .setName("Tesla Motors")
                .setSettlementAssetId(asset.getId())
                .setMarginRequirement(BigDecimal.valueOf(0.01))
                .setTickSize(1)
                .setStepSize(1)
                .setSettlementFrequency(8)
                .setMakerFee(BigDecimal.valueOf(0.001))
                .setTakerFee(BigDecimal.valueOf(0.001))
                .setLiquidationFee(BigDecimal.valueOf(0.001))
                .setOracleKey("TSLA")
                .setOracleType(OracleType.POLYGON);
        long[] times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        TransactionResponse<Proposal> txResponse = tendermintClient.addMarket(request);
        syncProposals();
        Assertions.assertEquals(txResponse.getItem().getStatus(), ProposalStatus.CREATED);
        ResponseEntity<Market> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s", port,
                        txResponse.getItem().getLinkedId().toString()), Market.class);
        Market market = responseEntity.getBody();
        Assertions.assertNotNull(market);
        Assertions.assertEquals(market.getStatus(), MarketStatus.ACTIVE);
        Assertions.assertEquals(market.getId(), txResponse.getItem().getLinkedId());
        return market;
    }

    @Test
    public void testAddMarket() {
        addMarket();
    }

    @Test
    public void testSuspendAndUnsuspendMarket() {
        Market market = addMarket();
        SingleItemRequest request = new SingleItemRequest()
                .setId(market.getId());
        long[] times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        tendermintClient.suspendMarket(request);
        syncProposals();
        ResponseEntity<Market> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s", port, market.getId().toString()), Market.class);
        market = responseEntity.getBody();
        Assertions.assertNotNull(market);
        Assertions.assertEquals(market.getStatus(), MarketStatus.SUSPENDED);
        tendermintClient.unsuspendMarket(request);
        syncProposals();
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s", port, market.getId().toString()), Market.class);
        market = responseEntity.getBody();
        Assertions.assertNotNull(market);
        Assertions.assertEquals(market.getStatus(), MarketStatus.ACTIVE);
    }

    @Test
    public void testCreateOrder() {
        Market market = addMarket();
        CreateOrderRequest buyRequest = new CreateOrderRequest()
                .setTag(OrderTag.USER_GENERATED)
                .setType(OrderType.LIMIT)
                .setPostOnly(true)
                .setQuantity(BigDecimal.ONE)
                .setPrice(BigDecimal.ONE)
                .setSide(MarketSide.BUY)
                .setMarketId(market.getId());
        CreateOrderRequest sellRequest = new CreateOrderRequest()
                .setTag(OrderTag.USER_GENERATED)
                .setType(OrderType.LIMIT)
                .setPostOnly(true)
                .setQuantity(BigDecimal.ONE)
                .setPrice(BigDecimal.valueOf(1.1))
                .setSide(MarketSide.SELL)
                .setMarketId(market.getId());
        String message = jsonUtils.toJson(buyRequest);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        buyRequest.setPublicKey(takerUser.getPublicKey());
        buyRequest.setSignature(sig);
        message = jsonUtils.toJson(sellRequest);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        sellRequest.setPublicKey(takerUser.getPublicKey());
        sellRequest.setSignature(sig);
        tendermintClient.createOrder(buyRequest);
        tendermintClient.createOrder(sellRequest);
        ResponseEntity<OrderBook> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port, market.getId().toString()), OrderBook.class);
        OrderBook orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertTrue(orderBook.getAsks().size() > 0);
        Assertions.assertTrue(orderBook.getBids().size() > 0);
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(),
                BigDecimal.valueOf(1.1).doubleValue(), 0.0001d);
        Assertions.assertEquals(orderBook.getBids().get(0).getPrice().doubleValue(),
                BigDecimal.valueOf(1).doubleValue(), 0.0001d);
    }

    @Test
    public void testCancelOrder() {
        Market market = addMarket();
        CreateOrderRequest request = new CreateOrderRequest()
                .setTag(OrderTag.USER_GENERATED)
                .setType(OrderType.LIMIT)
                .setPostOnly(true)
                .setQuantity(BigDecimal.ONE)
                .setPrice(BigDecimal.valueOf(1.1))
                .setSide(MarketSide.SELL)
                .setMarketId(market.getId());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        TransactionResponse<Order> newOrder = tendermintClient.createOrder(request);
        ResponseEntity<OrderBook> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port, market.getId().toString()), OrderBook.class);
        OrderBook orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertTrue(orderBook.getAsks().size() > 0);
        Assertions.assertEquals(0, orderBook.getBids().size());
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(),
                BigDecimal.valueOf(1.1).doubleValue(), 0.0001d);
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest()
                .setId(newOrder.getItem().getId());
        message = jsonUtils.toJson(cancelOrderRequest);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        cancelOrderRequest.setPublicKey(takerUser.getPublicKey());
        cancelOrderRequest.setSignature(sig);
        tendermintClient.cancelOrder(cancelOrderRequest);
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port, market.getId().toString()), OrderBook.class);
        orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(0, orderBook.getBids().size());
    }

    @Test
    public void testAmendOrder() {
        Market market = addMarket();
        CreateOrderRequest request = new CreateOrderRequest()
                .setTag(OrderTag.USER_GENERATED)
                .setType(OrderType.LIMIT)
                .setPostOnly(true)
                .setQuantity(BigDecimal.ONE)
                .setPrice(BigDecimal.valueOf(1.1))
                .setSide(MarketSide.SELL)
                .setMarketId(market.getId());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        TransactionResponse<Order> newOrder = tendermintClient.createOrder(request);
        ResponseEntity<OrderBook> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port, market.getId().toString()), OrderBook.class);
        OrderBook orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertTrue(orderBook.getAsks().size() > 0);
        Assertions.assertEquals(0, orderBook.getBids().size());
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(),
                BigDecimal.valueOf(1.1).doubleValue(), 0.0001d);
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest()
                .setId(newOrder.getItem().getId())
                .setPrice(BigDecimal.valueOf(1.2));
        message = jsonUtils.toJson(amendOrderRequest);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        amendOrderRequest.setPublicKey(takerUser.getPublicKey());
        amendOrderRequest.setSignature(sig);
        tendermintClient.amendOrder(amendOrderRequest);
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port, market.getId().toString()), OrderBook.class);
        orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertTrue(orderBook.getAsks().size() > 0);
        Assertions.assertEquals(0, orderBook.getBids().size());
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(),
                BigDecimal.valueOf(1.2).doubleValue(), 0.0001d);
    }

    private void waitForBlockchain() {
        long blockHeight = appStateManager.getBlockHeight();
        while(blockHeight < 1) {
            blockHeight = appStateManager.getBlockHeight();
            log.info("Block height = {}", blockHeight);
            sleepUtils.sleep(500L);
        }
    }
}
