package com.jynx.pro.service;

import com.jynx.pro.constant.*;
import com.jynx.pro.entity.*;
import com.jynx.pro.helper.EthereumHelper;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.utils.PriceUtils;
import com.jynx.pro.utils.UUIDUtils;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public abstract class IntegrationTest {

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

    protected static final String ETH_ADDRESS = "0xd7E1236C08731C3632519DCd1A581bFe6876a3B2";
    protected static final String PRIVATE_KEY = "0xb219d340d8e6aacdca54cecf104e6998b21411c9858ff1d25324a98d38ed034c";
    private static final String GANACHE_CMD = String
            .format("ganache-cli --gasLimit 100000000 --account=\"%s,1000000000000000000000\"", PRIVATE_KEY);
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
    ) throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(100L);
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
    ) throws InterruptedException {
        Asset asset = createAndEnactAsset(true);
        Market market = marketService.proposeToAdd(getAddMarketRequest(asset));
        Oracle oracle = new Oracle()
                .setId(UUID.randomUUID())
                .setType(OracleType.SIGNED_DATA)
                .setUser(makerUser)
                .setMarket(market)
                .setStatus(OracleStatus.ACTIVE);
        oracleRepository.save(oracle);
        Assertions.assertEquals(market.getStatus(), MarketStatus.PENDING);
        Thread.sleep(100L);
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
        request.setMinOracleCount(1);
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
        oracleRepository.deleteAll();
        orderHistoryRepository.deleteAll();
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        positionRepository.deleteAll();
        marketRepository.deleteAll();
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
    }

    protected void initializeState() {
        if(!setupComplete) {
            ethereumHelper.deploy(ganache.getHost(), ganache.getFirstMappedPort(), PRIVATE_KEY);
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
                .setPublicKey("11111111111111111111111111111111")
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
    }
}
