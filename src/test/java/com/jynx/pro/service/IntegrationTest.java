package com.jynx.pro.service;

import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.helper.EthereumHelper;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.request.CreateOrderRequest;
import com.jynx.pro.utils.PriceUtils;
import com.jynx.pro.utils.SleepUtils;
import com.jynx.pro.utils.UUIDUtils;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

public abstract class IntegrationTest {

    protected static final String PRIVATE_KEY = "1498b5467a63dffa2dc9d9e069caf075d16fc33fdd4c3b01bfadae6433767d93";
    protected static final String PUBLIC_KEY = "b7a3c12dc0c8c748ab07525b701122b88bd78f600c76342d27f25e5f92444cde";

    @Autowired
    protected UUIDUtils uuidUtils;
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
    protected PriceUtils priceUtils;
    @Autowired
    protected SleepUtils sleepUtils;
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
    protected AccountService accountService;
    @Autowired
    protected DatabaseTransactionManager databaseTransactionManager;
    @Autowired
    protected OrderService orderService;

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

    protected Asset createAndEnactAsset(
            final boolean activate
    ) {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        sleepUtils.sleep(100L);
        if(activate) {
            configService.setTimestamp(nowAsMillis());
        }
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        asset = assetRepository.findById(asset.getId()).orElse(new Asset());
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
    ) {
        Asset asset = createAndEnactAsset(true);
        Market market = marketService.proposeToAdd(getAddMarketRequest(asset));
        Assertions.assertEquals(market.getStatus(), MarketStatus.PENDING);
        sleepUtils.sleep(100L);
        if(activate) {
            configService.setTimestamp(nowAsMillis());
        }
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
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
                .setDecimalPlaces(8);
        request.setUser(user);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        return request;
    }

    protected Market createOrderBook(
            final int bids,
            final int asks
    ) throws InterruptedException {
        return createOrderBook(bids, asks, 1);
    }

    protected Market createOrderBook(
            final int bids,
            final int asks,
            final int stepSize
    ) throws InterruptedException {
        Market market = createAndEnactMarket(true);
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
        return LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    protected long[] proposalTimes() {
        long ts = nowAsMillis();
        long open = ts + 1;
        long close = ts + 2;
        long enactment = ts + 3;
        return new long[]{ open, close, enactment };
    }

    protected void clearState() {
        databaseTransactionManager.createTransaction();
        orderHistoryRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
        marketRepository.deleteAll();
        oracleRepository.deleteAll();
        accountRepository.deleteAll();
        depositRepository.deleteAll();
        transactionRepository.deleteAll();
        withdrawalRepository.deleteAll();
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
                    .setUuidSeed(1L)
                    .setEthConfirmations(0)
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
                .setPublicKey("22222222222222222222222222222222")
                .setUsername("test-user2");
        makerUser = userRepository.save(makerUser);
        degenUser = new User()
                .setId(uuidUtils.next())
                .setPublicKey("33333333333333333333333333333333")
                .setUsername("test-user3");
        degenUser = userRepository.save(degenUser);
        stakeRepository.save(new Stake().setId(uuidUtils.next()).setUser(makerUser).setAmount(BigDecimal.valueOf(500000000L)));
        stakeRepository.save(new Stake().setId(uuidUtils.next()).setUser(takerUser).setAmount(BigDecimal.valueOf(700000000L)));
        if(createAsset) {
            createAndEnactAsset(true);
        }
        databaseTransactionManager.commit();
    }

    protected void initializeState() {
        initializeState(false);
    }
}
