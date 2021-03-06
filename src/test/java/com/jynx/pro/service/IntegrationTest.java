package com.jynx.pro.service;

import com.jynx.pro.blockchain.BlockchainGateway;
import com.jynx.pro.blockchain.TendermintClient;
import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.helper.EthereumHelper;
import com.jynx.pro.manager.AppStateManager;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.request.CreateOrderRequest;
import com.jynx.pro.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public abstract class IntegrationTest {

    protected static final String PRIVATE_KEY = "1498b5467a63dffa2dc9d9e069caf075d16fc33fdd4c3b01bfadae6433767d93";
    protected static final String PUBLIC_KEY = "b7a3c12dc0c8c748ab07525b701122b88bd78f600c76342d27f25e5f92444cde";

    protected static final String PRIVATE_KEY2 = "17f914594153922eb49c240bda6e7272e6d1e68e3fe0340482fc40dcb2f93581";
    protected static final String PUBLIC_KEY2 = "e123a8e4ea5394f84261f3b33b99cf98fc72f00a2c5a5a536ae79131f6f0f451";

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;
    @Autowired
    protected UUIDUtils uuidUtils;
    @Autowired
    protected SleepUtils sleepUtils;
    @Autowired
    protected CryptoUtils cryptoUtils;
    @Autowired
    protected JSONUtils jsonUtils;
    @Autowired
    protected EthereumHelper ethereumHelper;
    @Autowired
    protected EthereumService ethereumService;
    @Autowired
    protected ProposalService proposalService;
    @Autowired
    protected ConfigRepository configRepository;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected AssetRepository assetRepository;
    @Autowired
    protected AccountRepository accountRepository;
    @Autowired
    protected ProposalRepository proposalRepository;
    @Autowired
    protected VoteRepository voteRepository;
    @Autowired
    protected ConfigService configService;
    @Autowired
    protected StakeRepository stakeRepository;
    @Autowired
    protected AssetService assetService;
    @Autowired
    protected MarketRepository marketRepository;
    @Autowired
    protected OracleRepository oracleRepository;
    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected MarketService marketService;
    @Autowired
    protected EventRepository eventRepository;
    @Autowired
    protected ReadOnlyRepository readOnlyRepository;
    @Autowired
    protected PriceUtils priceUtils;
    @Autowired
    protected DepositRepository depositRepository;
    @Autowired
    protected TransactionRepository transactionRepository;
    @Autowired
    protected TradeRepository tradeRepository;
    @Autowired
    protected PositionRepository positionRepository;
    @Autowired
    protected OrderHistoryRepository orderHistoryRepository;
    @Autowired
    protected WithdrawalRepository withdrawalRepository;
    @Autowired
    protected WithdrawalBatchRepository withdrawalBatchRepository;
    @Autowired
    protected WithdrawalBatchSignatureRepository withdrawalBatchSignatureRepository;
    @Autowired
    protected ValidatorRepository validatorRepository;
    @Autowired
    protected AuctionTriggerRepository auctionTriggerRepository;
    @Autowired
    protected AccountService accountService;
    @Autowired
    protected DatabaseTransactionManager databaseTransactionManager;
    @Autowired
    protected OrderService orderService;
    @Autowired
    protected AppStateManager appStateManager;
    @Autowired
    protected TendermintClient tendermintClient;
    @Autowired
    protected BlockchainGateway blockchainGateway;
    @Autowired
    protected BridgeUpdateSignatureRepository bridgeUpdateSignatureRepository;
    @Autowired
    protected BridgeUpdateRepository bridgeUpdateRepository;
    @Autowired
    protected BlockValidatorRepository blockValidatorRepository;
    @Autowired
    protected DelegationRepository delegationRepository;

    protected static final String ETH_ADDRESS = "0xd7E1236C08731C3632519DCd1A581bFe6876a3B2";
    protected static final String ETH_PRIVATE_KEY = "0xb219d340d8e6aacdca54cecf104e6998b21411c9858ff1d25324a98d38ed034c";
    private static final String GANACHE_CMD = String
            .format("ganache-cli --gasLimit 100000000 --account=\"%s,1000000000000000000000\"", ETH_PRIVATE_KEY);
    @Container
    public static GenericContainer ganache = new GenericContainer(DockerImageName.parse("trufflesuite/ganache-cli:latest"))
            .withExposedPorts(8545)
            .withCommand(GANACHE_CMD);

    protected boolean setupComplete = false;

    protected static long INITIAL_BALANCE = 1000000000;

    protected User degenUser;
    protected User takerUser;
    protected User makerUser;

    protected void addToBridge(
            final String address,
            final String nonce
    ) throws DecoderException {
        byte[] signature = ethereumService.getSignatureForAddAsset(
                address, new BigInteger(nonce));
        ethereumService.addAsset(address, new BigInteger(nonce), signature);
    }

    protected void removeFromBridge(
            final String address,
            final String nonce
    ) throws DecoderException {
        byte[] signature = ethereumService.getSignatureForRemoveAsset(
                address, new BigInteger(nonce));
        ethereumService.removeAsset(address, new BigInteger(nonce), signature);
    }

    protected Asset getDai() {
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

    protected void waitForBlockchain() {
        long blockHeight = appStateManager.getBlockHeight();
        while(blockHeight < 1) {
            blockHeight = appStateManager.getBlockHeight();
            log.info("Block height = {}", blockHeight);
            sleepUtils.sleep(500L);
        }
    }

    protected Asset createAndEnactAsset(
            final boolean activate
    ) throws DecoderException {
        AddAssetRequest addAssetRequest = getAddAssetRequest(takerUser);
        Proposal proposal = assetService.proposeToAdd(addAssetRequest);
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        addToBridge(addAssetRequest.getAddress(), proposal.getNonce());
        sleepUtils.sleep(100L);
        if(activate) {
            configService.setTimestamp(nowAsMillis());
        }
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Asset asset = assetRepository.findById(proposal.getLinkedId()).orElse(new Asset());
        Assertions.assertEquals(asset.getStatus(), activate ? AssetStatus.ACTIVE : AssetStatus.PENDING);
        accountRepository.save(new Account().setAsset(asset).setUser(takerUser).setId(uuidUtils.next())
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setAvailableBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setMarginBalance(BigDecimal.ZERO));
        accountRepository.save(new Account().setAsset(asset).setUser(makerUser).setId(uuidUtils.next())
                .setBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setAvailableBalance(BigDecimal.valueOf(INITIAL_BALANCE))
                .setMarginBalance(BigDecimal.ZERO));
        accountRepository.save(new Account().setAsset(asset).setUser(degenUser).setId(uuidUtils.next())
                .setBalance(BigDecimal.valueOf(1000))
                .setAvailableBalance(BigDecimal.valueOf(1000))
                .setMarginBalance(BigDecimal.ZERO));
        return asset;
    }

    protected Market createAndEnactMarket(
            final boolean activate
    ) throws DecoderException {
        return createAndEnactMarket(activate, false);
    }

    protected Market createAndEnactMarket(
            final boolean activate,
            final boolean includeTriggers
    ) throws DecoderException {
        Asset asset = createAndEnactAsset(true);
        List<AddMarketRequest.AuctionTrigger> triggers = new ArrayList<>();
        if(includeTriggers) {
            triggers.add(new AddMarketRequest.AuctionTrigger()
                    .setDepth(BigDecimal.valueOf(0.001)).setOpenVolumeRatio(BigDecimal.ONE));
        }
        Proposal proposal = marketService.proposeToAdd(getAddMarketRequest(asset).setAuctionTriggers(triggers));
        Assertions.assertEquals(proposal.getStatus(), ProposalStatus.CREATED);
        sleepUtils.sleep(100L);
        if(activate) {
            configService.setTimestamp(nowAsMillis());
        }
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        Market market = marketRepository.findById(proposal.getLinkedId()).orElse(new Market());
        Assertions.assertEquals(market.getStatus(), activate ? MarketStatus.ACTIVE : MarketStatus.PENDING);
        return market;
    }

    protected CreateOrderRequest getCreateOrderRequest(
            final UUID marketId,
            final BigDecimal price,
            final BigDecimal quantity,
            final MarketSide side,
            final OrderType type,
            final User user
    ) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPrice(price);
        request.setSide(side);
        request.setQuantity(quantity);
        request.setUser(user);
        request.setMarketId(marketId);
        request.setType(type);
        request.setTag(OrderTag.USER_GENERATED);
        return request;
    }

    protected AddAssetRequest getAddAssetRequest(
            final User user
    ) {
        long[] times = proposalTimes();
        AddAssetRequest request = new AddAssetRequest()
                .setName("DAI")
                .setAddress(ethereumHelper.getDaiToken().getContractAddress())
                .setType(AssetType.ERC20)
                .setDecimalPlaces(8)
                .setBridgeNonce(ethereumService.getNonce().toString());
        request.setUser(user);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        return request;
    }

    protected Market createOrderBook(
            final int bids,
            final int asks
    ) {
        return createOrderBook(bids, asks, 1);
    }

    protected Market createOrderBook(
            final int bids,
            final int asks,
            final int stepSize
    ) {
        Market market = null;
        try {
            market = createAndEnactMarket(true);
        } catch(Exception ignored) {
            Assertions.fail();
            return market;
        }
        int dps = market.getSettlementAsset().getDecimalPlaces();
        for(int i=0; i<bids; i++) {
            Order buyOrder = orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45590-((long) i * stepSize)), BigDecimal.ONE, MarketSide.BUY, OrderType.LIMIT, makerUser));
            Assertions.assertEquals(buyOrder.getStatus(), OrderStatus.OPEN);
        }
        for(int i=0; i<asks; i++) {
            Order sellOrder = orderService.create(getCreateOrderRequest(market.getId(),
                    BigDecimal.valueOf(45610+((long) i * stepSize)), BigDecimal.ONE, MarketSide.SELL, OrderType.LIMIT, makerUser));
            Assertions.assertEquals(sellOrder.getStatus(), OrderStatus.OPEN);
        }
        OrderBook orderBook = orderService.getOrderBook(market);
        Assertions.assertEquals(orderBook.getAsks().size(), asks);
        Assertions.assertEquals(orderBook.getBids().size(), bids);
        BigDecimal marginBalance = BigDecimal.ZERO;
        for(int i=0; i<bids; i++) {
            OrderBookItem item = orderBook.getBids().get(i);
            Assertions.assertEquals(item.getPrice().setScale(dps, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(45590-((long) i * stepSize)).setScale(dps, RoundingMode.HALF_UP));
            marginBalance = marginBalance.add(item.getPrice().multiply(item.getQuantity())
                    .multiply(market.getMarginRequirement()));
        }
        for(int i=0; i<asks; i++) {
            if(i == 0) {
                marginBalance = BigDecimal.ZERO;
            }
            OrderBookItem item = orderBook.getAsks().get(i);
            Assertions.assertEquals(item.getPrice().setScale(dps, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(45610+((long) i * stepSize)).setScale(dps, RoundingMode.HALF_UP));
            marginBalance = marginBalance.add(item.getPrice().multiply(item.getQuantity())
                    .multiply(market.getMarginRequirement()));
        }
        BigDecimal startingBalance = BigDecimal.valueOf(INITIAL_BALANCE);
        BigDecimal availableBalance = startingBalance.subtract(marginBalance);
        Optional<Account> accountOptional = accountRepository
                .findByUserAndAsset(makerUser, market.getSettlementAsset());
        Assertions.assertTrue(accountOptional.isPresent());
        Assertions.assertEquals(accountOptional.get().getMarginBalance().setScale(dps, RoundingMode.HALF_UP),
                marginBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getAvailableBalance().setScale(dps, RoundingMode.HALF_UP),
                availableBalance.setScale(dps, RoundingMode.HALF_UP));
        Assertions.assertEquals(accountOptional.get().getBalance().setScale(dps, RoundingMode.HALF_UP),
                startingBalance.setScale(dps, RoundingMode.HALF_UP));
        return market;
    }

    protected AddMarketRequest getAddMarketRequest(
            final Asset asset
    ) {
        long[] times = proposalTimes();
        AddMarketRequest request = new AddMarketRequest();
        request.setMarginRequirement(BigDecimal.valueOf(0.01));
        request.setName("BTC/USDT");
        request.setStepSize(1);
        request.setTickSize(1);
        request.setSettlementFrequency(8);
        request.setMakerFee(BigDecimal.valueOf(0.001));
        request.setTakerFee(BigDecimal.valueOf(0.001));
        request.setLiquidationFee(BigDecimal.valueOf(0.005));
        request.setSettlementAssetId(asset.getId());
        request.setUser(takerUser);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setOracleKey("BTCUSDT");
        request.setOracleType(OracleType.BINANCE);
        return request;
    }

    protected long nowAsMillis() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    protected long[] proposalTimes(
            final long openOffset,
            final long closeOffset,
            final long enactOffset
    ) {
        long ts = nowAsMillis();
        long open = ts + openOffset;
        long close = ts + closeOffset;
        long enactment = ts + enactOffset;
        return new long[]{ open, close, enactment };
    }

    protected long[] proposalTimes() {
        return proposalTimes(1, 2, 3);
    }

    protected void clearState() {
        databaseTransactionManager.createTransaction();
        withdrawalBatchSignatureRepository.deleteAll();
        orderHistoryRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
        auctionTriggerRepository.deleteAll();
        marketRepository.deleteAll();
        oracleRepository.deleteAll();
        accountRepository.deleteAll();
        depositRepository.deleteAll();
        transactionRepository.deleteAll();
        withdrawalRepository.deleteAll();
        withdrawalBatchRepository.deleteAll();
        bridgeUpdateSignatureRepository.deleteAll();
        bridgeUpdateRepository.deleteAll();
        blockValidatorRepository.deleteAll();
        delegationRepository.deleteAll();
        validatorRepository.deleteAll();
        assetRepository.deleteAll();
        voteRepository.deleteAll();
        proposalRepository.deleteAll();
        eventRepository.deleteAll();
        stakeRepository.deleteAll();
        userRepository.deleteAll();
        configRepository.deleteAll();
        databaseTransactionManager.commit();
    }

    protected void initializeState(
            final boolean createAsset
    ) {
        databaseTransactionManager.createTransaction();
        if(!setupComplete) {
            if(!ganache.isRunning()) {
                ganache.start();
            }
            ethereumHelper.deploy(ganache.getHost(), ganache.getFirstMappedPort(), ETH_PRIVATE_KEY);
            ethereumService.setRpcHost(ganache.getHost());
            ethereumService.setRpcPort(ganache.getFirstMappedPort());
            Config config = new Config()
                    .setId(1L)
                    .setGovernanceTokenAddress(ethereumHelper.getJynxToken().getContractAddress())
                    .setMinClosingDelay(1L)
                    .setMinEnactmentDelay(1L)
                    .setMinOpenDelay(1L)
                    .setMinProposerStake(1L)
                    .setNetworkFee(BigDecimal.valueOf(0.001))
                    .setParticipationThreshold(BigDecimal.valueOf(0.66))
                    .setApprovalThreshold(BigDecimal.valueOf(0.66))
                    .setUuidSeed(1L)
                    .setEthConfirmations(0)
                    .setActiveValidatorCount(1)
                    .setBackupValidatorCount(1)
                    .setAsyncTaskFrequency(1)
                    .setSnapshotFrequency(1)
                    .setSnapshotChunkRows(10)
                    .setValidatorMinDelegation(BigDecimal.ONE)
                    .setValidatorBond(BigDecimal.ONE)
                    .setBridgeAddress(ethereumHelper.getJynxProBridge().getContractAddress());
            configRepository.save(config);
            configService.setTimestamp(nowAsMillis());
            ethereumService.initializeFilters();
            setupComplete = true;
        }
        takerUser = new User()
                .setId(uuidUtils.next())
                .setPublicKey(PUBLIC_KEY)
                .setUsername("test-user1");
        takerUser = userRepository.save(takerUser);
        makerUser = new User()
                .setId(uuidUtils.next())
                .setPublicKey(PUBLIC_KEY2)
                .setUsername("test-user2");
        makerUser = userRepository.save(makerUser);
        degenUser = new User()
                .setId(uuidUtils.next())
                .setPublicKey("33333333333333333333333333333333")
                .setUsername("test-user3");
        degenUser = userRepository.save(degenUser);
        stakeRepository.save(new Stake().setId(uuidUtils.next()).setUser(makerUser).setAmount(BigDecimal.valueOf(500000000L)));
        stakeRepository.save(new Stake().setId(uuidUtils.next()).setUser(takerUser).setAmount(BigDecimal.valueOf(700000000L)));
        databaseTransactionManager.commit();
        databaseTransactionManager.createTransaction();
        if(createAsset) {
            try {
                createAndEnactAsset(true);
            } catch(Exception e) {
                log.error(e.getMessage(), e);
                Assertions.fail();
            }
        }
        databaseTransactionManager.commit();
    }

    protected void initializeState() {
        initializeState(false);
    }
}
