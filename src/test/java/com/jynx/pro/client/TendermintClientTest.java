package com.jynx.pro.client;

import com.jynx.pro.Application;
import com.jynx.pro.blockchain.BlockchainGateway;
import com.jynx.pro.blockchain.TendermintClient;
import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
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
import org.json.JSONObject;
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
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import org.testcontainers.utility.DockerImageName;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Testcontainers
@ActiveProfiles("tendermint")
@DisabledIfEnvironmentVariable(named = "TRAVIS_CI", matches = "true")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TendermintClientTest extends IntegrationTest {

    @Autowired
    private TendermintClient tendermintClient;
    @Autowired
    private BlockchainGateway blockchainGateway;
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

    // TODO - remove direct calls to TendermintClient and proxy everything via REST (it will increase test coverage)

    private void updateTendermintKeys(
            final String dest
    ) {
        try {
            InputStream is = new FileInputStream(dest);
            String jsonTxt = IOUtils.toString(is, "UTF-8");
            JSONObject json = new JSONObject(jsonTxt);
            String address = json.getString("address");
            String privateKey = json.getJSONObject("priv_key").getString("value");
            String publicKey = json.getJSONObject("pub_key").getString("value");
            blockchainGateway.setValidatorAddress(address);
            blockchainGateway.setValidatorPrivateKey(privateKey);
            blockchainGateway.setValidatorPublicKey(publicKey );
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @BeforeEach
    public void setup() {
        initializeState(true);
        tendermint =
                new GenericContainer(DockerImageName.parse("tendermint/tendermint:v0.34.14"))
                        .withExposedPorts(26657)
                        .withCommand("node --abci grpc --proxy_app tcp://host.docker.internal:26658")
                        .withExtraHost("host.docker.internal", "host-gateway");
        tendermint.start();
        String dest = "target/priv_validator_key.json";
        tendermint.copyFileFromContainer("/tendermint/config/priv_validator_key.json", dest);
        updateTendermintKeys(dest);
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

    private Proposal getAssetProposal(
            final long openOffset,
            final long closeOffset,
            final long enactOffset
    ) {
        AddAssetRequest request = new AddAssetRequest()
                .setAddress("0x0")
                .setName("USDC")
                .setDecimalPlaces(5)
                .setType(AssetType.ERC20);
        long[] times = proposalTimes(openOffset, closeOffset, enactOffset);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        TransactionResponse<Proposal> txResponse = tendermintClient.addAsset(request);
        return txResponse.getItem();
    }

    private Asset addAsset() {
        Proposal proposal = getAssetProposal(1, 2, 3);
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        ResponseEntity<Asset[]> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/asset/all", port), Asset[].class);
        Asset[] assetArray = responseEntity.getBody();
        Assertions.assertNotNull(assetArray);
        Assertions.assertEquals(assetArray.length, 2);
        Assertions.assertEquals(assetArray[1].getStatus(), AssetStatus.PENDING);
        Assertions.assertEquals(assetArray[1].getId(), proposal.getLinkedId());
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
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        tendermintClient.suspendAsset(request);
        sleepUtils.sleep(2000L);
        ResponseEntity<Asset> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/asset/%s", port, asset.getId().toString()), Asset.class);
        asset = responseEntity.getBody();
        Assertions.assertNotNull(asset);
        Assertions.assertEquals(asset.getStatus(), AssetStatus.SUSPENDED);
        request = new SingleItemRequest()
                .setId(asset.getId());
        times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setNonce(ethereumService.getNonce().toString());
        message = jsonUtils.toJson(request);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        tendermintClient.unsuspendAsset(request);
        sleepUtils.sleep(2000L);
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
        request.setNonce(ethereumService.getNonce().toString());
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
        request.setNonce(ethereumService.getNonce().toString());
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
        sleepUtils.sleep(2000L);
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
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        TransactionResponse<Proposal> txResponse = tendermintClient.addMarket(request);
        sleepUtils.sleep(2000L);
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
    public void testAmendMarket() {
        Market market = addMarket();
        AmendMarketRequest request = new AmendMarketRequest()
                .setId(market.getId())
                .setStepSize(2);
        long[] times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        tendermintClient.amendMarket(request);
        sleepUtils.sleep(2000L);
        ResponseEntity<Market> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s", port, market.getId().toString()), Market.class);
        market = responseEntity.getBody();
        Assertions.assertNotNull(market);
        Assertions.assertEquals(market.getStepSize(), 2);
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
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        tendermintClient.suspendMarket(request);
        sleepUtils.sleep(2000L);
        ResponseEntity<Market> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s", port, market.getId().toString()), Market.class);
        market = responseEntity.getBody();
        Assertions.assertNotNull(market);
        Assertions.assertEquals(market.getStatus(), MarketStatus.SUSPENDED);
        request = new SingleItemRequest()
                .setId(market.getId());
        times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setNonce(ethereumService.getNonce().toString());
        message = jsonUtils.toJson(request);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        tendermintClient.unsuspendMarket(request);
        sleepUtils.sleep(2000L);
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s", port, market.getId().toString()), Market.class);
        market = responseEntity.getBody();
        Assertions.assertNotNull(market);
        Assertions.assertEquals(market.getStatus(), MarketStatus.ACTIVE);
    }

    @Test
    public void testBulkOrderRequests() {
        Market market = addMarket();
        List<CreateOrderRequest> createRequest = new ArrayList<>();
        for(int i=0; i<10; i++) {
            createRequest.add(new CreateOrderRequest()
                    .setTag(OrderTag.USER_GENERATED)
                    .setType(OrderType.LIMIT)
                    .setPostOnly(true)
                    .setQuantity(BigDecimal.ONE)
                    .setPrice(BigDecimal.ONE)
                    .setSide(MarketSide.BUY)
                    .setMarketId(market.getId()));
        }
        BulkCreateOrderRequest bulkCreateRequest = new BulkCreateOrderRequest()
                .setOrders(createRequest);
        bulkCreateRequest.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(bulkCreateRequest);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        bulkCreateRequest.setPublicKey(takerUser.getPublicKey());
        bulkCreateRequest.setSignature(sig);
        TransactionResponse<Order[]> txResponse = tendermintClient.createOrderMany(bulkCreateRequest);
        Assertions.assertEquals(txResponse.getItem().length, 10);
        ResponseEntity<OrderBook> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port,
                        market.getId().toString()), OrderBook.class);
        OrderBook orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(10, orderBook.getBids().size());
        for(int i=0; i<10; i++) {
            Assertions.assertEquals(orderBook.getBids().get(i).getQuantity().doubleValue(), 1d, 0.0001d);
        }
        List<AmendOrderRequest> amendRequest = new ArrayList<>();
        for(int i=0; i<10; i++) {
            amendRequest.add(new AmendOrderRequest()
                    .setId(txResponse.getItem()[i].getId())
                    .setQuantity(BigDecimal.TEN));
        }
        BulkAmendOrderRequest bulkAmendRequest = new BulkAmendOrderRequest()
                .setOrders(amendRequest);
        bulkAmendRequest.setNonce(ethereumService.getNonce().toString());
        message = jsonUtils.toJson(bulkAmendRequest);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        bulkAmendRequest.setPublicKey(takerUser.getPublicKey());
        bulkAmendRequest.setSignature(sig);
        txResponse = tendermintClient.amendOrderMany(bulkAmendRequest);
        Assertions.assertEquals(txResponse.getItem().length, 10);
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port,
                        market.getId().toString()), OrderBook.class);
        orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(10, orderBook.getBids().size());
        for(int i=0; i<10; i++) {
            Assertions.assertEquals(orderBook.getBids().get(i).getQuantity().doubleValue(), 10d, 0.0001d);
        }
        List<CancelOrderRequest> cancelRequest = new ArrayList<>();
        for(int i=0; i<10; i++) {
            cancelRequest.add(new CancelOrderRequest()
                    .setId(txResponse.getItem()[i].getId()));
        }
        BulkCancelOrderRequest bulkCancelRequest = new BulkCancelOrderRequest()
                .setOrders(cancelRequest);
        bulkCancelRequest.setNonce(ethereumService.getNonce().toString());
        message = jsonUtils.toJson(bulkCancelRequest);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        bulkCancelRequest.setPublicKey(takerUser.getPublicKey());
        bulkCancelRequest.setSignature(sig);
        txResponse = tendermintClient.cancelOrderMany(bulkCancelRequest);
        Assertions.assertEquals(txResponse.getItem().length, 10);
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port,
                        market.getId().toString()), OrderBook.class);
        orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertEquals(0, orderBook.getAsks().size());
        Assertions.assertEquals(0, orderBook.getBids().size());
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
        sellRequest.setNonce(ethereumService.getNonce().toString());
        buyRequest.setNonce(ethereumService.getNonce().toString());
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
                String.format("http://localhost:%s/market/%s/order-book", port,
                        market.getId().toString()), OrderBook.class);
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
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        TransactionResponse<Order> newOrder = tendermintClient.createOrder(request);
        ResponseEntity<OrderBook> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port,
                        market.getId().toString()), OrderBook.class);
        OrderBook orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertTrue(orderBook.getAsks().size() > 0);
        Assertions.assertEquals(0, orderBook.getBids().size());
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(),
                BigDecimal.valueOf(1.1).doubleValue(), 0.0001d);
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest()
                .setId(newOrder.getItem().getId());
        cancelOrderRequest.setNonce(ethereumService.getNonce().toString());
        message = jsonUtils.toJson(cancelOrderRequest);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        cancelOrderRequest.setPublicKey(takerUser.getPublicKey());
        cancelOrderRequest.setSignature(sig);
        tendermintClient.cancelOrder(cancelOrderRequest);
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port,
                        market.getId().toString()), OrderBook.class);
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
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setPublicKey(takerUser.getPublicKey());
        request.setSignature(sig);
        TransactionResponse<Order> newOrder = tendermintClient.createOrder(request);
        ResponseEntity<OrderBook> responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port,
                        market.getId().toString()), OrderBook.class);
        OrderBook orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertTrue(orderBook.getAsks().size() > 0);
        Assertions.assertEquals(0, orderBook.getBids().size());
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(),
                BigDecimal.valueOf(1.1).doubleValue(), 0.0001d);
        AmendOrderRequest amendOrderRequest = new AmendOrderRequest()
                .setId(newOrder.getItem().getId())
                .setPrice(BigDecimal.valueOf(1.2));
        amendOrderRequest.setNonce(ethereumService.getNonce().toString());
        message = jsonUtils.toJson(amendOrderRequest);
        sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        amendOrderRequest.setPublicKey(takerUser.getPublicKey());
        amendOrderRequest.setSignature(sig);
        tendermintClient.amendOrder(amendOrderRequest);
        responseEntity = this.restTemplate.getForEntity(
                String.format("http://localhost:%s/market/%s/order-book", port,
                        market.getId().toString()), OrderBook.class);
        orderBook = responseEntity.getBody();
        Assertions.assertNotNull(orderBook);
        Assertions.assertTrue(orderBook.getAsks().size() > 0);
        Assertions.assertEquals(0, orderBook.getBids().size());
        Assertions.assertEquals(orderBook.getAsks().get(0).getPrice().doubleValue(),
                BigDecimal.valueOf(1.2).doubleValue(), 0.0001d);
    }

    @Test
    public void testVote() {
        Proposal proposal = getAssetProposal(-1000, 10000, 20000);
        sleepUtils.sleep(2000L);
        CastVoteRequest castVoteRequest = new CastVoteRequest()
                .setId(proposal.getId())
                .setInFavour(true);
        castVoteRequest.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(castVoteRequest);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY2).orElse("");
        castVoteRequest.setSignature(sig);
        castVoteRequest.setPublicKey(PUBLIC_KEY2);
        TransactionResponse<Vote> txResponse = tendermintClient.castVote(castVoteRequest);
        Assertions.assertEquals(txResponse.getItem().getInFavour(), true);
    }

    private Withdrawal createWithdrawal() {
        Asset asset = addAsset();
        sleepUtils.sleep(2000L);
        CreateWithdrawalRequest request = new CreateWithdrawalRequest()
                .setAmount(BigDecimal.TEN)
                .setAssetId(asset.getId())
                .setDestination("0x0A41");
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setSignature(sig);
        request.setPublicKey(PUBLIC_KEY);
        TransactionResponse<Withdrawal> txResponse = tendermintClient.createWithdrawal(request);
        Assertions.assertEquals(txResponse.getItem().getStatus(), WithdrawalStatus.PENDING);
        return txResponse.getItem();
    }

    @Test
    public void testCreateWithdrawal() {
        createWithdrawal();
    }

    @Test
    public void testCancelWithdrawal() {
        Withdrawal withdrawal = createWithdrawal();
        SingleItemRequest request = new SingleItemRequest()
                .setId(withdrawal.getId());
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String sig = cryptoUtils.sign(message, PRIVATE_KEY).orElse("");
        request.setSignature(sig);
        request.setPublicKey(PUBLIC_KEY);
        TransactionResponse<Withdrawal> txResponse = tendermintClient.cancelWithdrawal(request);
        Assertions.assertEquals(txResponse.getItem().getStatus(), WithdrawalStatus.CANCELED);
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
