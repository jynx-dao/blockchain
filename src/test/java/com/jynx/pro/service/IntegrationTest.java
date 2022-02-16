package com.jynx.pro.service;

import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.AssetType;
import com.jynx.pro.constant.MarketStatus;
import com.jynx.pro.constant.OracleType;
import com.jynx.pro.entity.*;
import com.jynx.pro.helper.EthereumHelper;
import com.jynx.pro.repository.*;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.utils.UUIDUtils;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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

    protected static final String PRIVATE_KEY = "0x4b077050dd12f33bb78773d957d87b0b477f6470017d9d6f0539c3c0683b6eb3";
    private static final String GANACHE_CMD = String
            .format("ganache-cli --gasLimit 100000000 --account=\"%s,1000000000000000000000\"", PRIVATE_KEY);
    @Container
    public GenericContainer ganache = new GenericContainer(DockerImageName.parse("trufflesuite/ganache-cli:latest"))
            .withExposedPorts(8545)
            .withCommand(GANACHE_CMD);

    protected User takerUser;
    protected User makerUser;

    protected Asset createAndEnactAsset(
            final boolean activate
    ) throws InterruptedException {
        Asset asset = assetService.proposeToAdd(getAddAssetRequest(takerUser));
        Assertions.assertEquals(asset.getStatus(), AssetStatus.PENDING);
        Thread.sleep(3000L);
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
                .setBalance(BigDecimal.valueOf(1000000))
                .setAvailableBalance(BigDecimal.valueOf(1000000))
                .setMarginBalance(BigDecimal.ZERO));
        accountRepository.save(new Account().setAsset(asset).setUser(makerUser).setId(uuidUtils.next())
                .setBalance(BigDecimal.valueOf(1000000))
                .setAvailableBalance(BigDecimal.valueOf(1000000))
                .setMarginBalance(BigDecimal.ZERO));
        return asset;
    }

    protected Market createAndEnactMarket() throws InterruptedException {
        Asset asset = createAndEnactAsset(true);
        List<Oracle> oracles = List.of(new Oracle().setType(OracleType.SIGNED_DATA).setIdentifier("price"));
        Market market = marketService.proposeToAdd(getAddMarketRequest(asset, oracles));
        Assertions.assertEquals(market.getStatus(), MarketStatus.PENDING);
        Thread.sleep(3000L);
        configService.setTimestamp(nowAsMillis());
        proposalService.open();
        proposalService.approve();
        proposalService.enact();
        proposalService.reject();
        market = marketRepository.findById(market.getId()).orElse(new Market());
        Assertions.assertEquals(market.getStatus(), MarketStatus.ACTIVE);
        return market;
    }

    protected AddAssetRequest getAddAssetRequest(
            final User user
    ) {
        long[] times = proposalTimes();
        AddAssetRequest request = new AddAssetRequest()
                .setName("USD")
                .setAddress("0x0")
                .setType(AssetType.ERC20)
                .setDecimalPlaces(4);
        request.setUser(user);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        return request;
    }

    protected AddMarketRequest getAddMarketRequest(
            final Asset asset,
            final List<Oracle> oracles
    ) {
        long[] times = proposalTimes();
        AddMarketRequest request = new AddMarketRequest();
        request.setInitialMargin(BigDecimal.valueOf(0.1));
        request.setMaintenanceMargin(BigDecimal.valueOf(0.15));
        request.setName("BTC/USDT");
        request.setStepSize(1);
        request.setTickSize(1);
        request.setSettlementFrequency(8);
        request.setMakerFee(BigDecimal.valueOf(0.001));
        request.setTakerFee(BigDecimal.valueOf(0.001));
        request.setSettlementAssetId(asset.getId());
        request.setUser(takerUser);
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setOracles(oracles);
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
        orderRepository.deleteAll();
        marketRepository.deleteAll();
        accountRepository.deleteAll();
        assetRepository.deleteAll();
        voteRepository.deleteAll();
        proposalRepository.deleteAll();
        eventRepository.deleteAll();
        stakeRepository.deleteAll();
        userRepository.deleteAll();
        configRepository.deleteAll();
    }

    protected void initializeState() {
        ethereumHelper.deploy(ganache.getHost(), ganache.getFirstMappedPort(), PRIVATE_KEY);
        ethereumService.setRpcHost(ganache.getHost());
        ethereumService.setRpcPort(ganache.getFirstMappedPort());
        ethereumService.setBridgeAddress(ethereumHelper.getJynxProBridge().getContractAddress());
        ethereumService.setRequiredConfirmations(0);
        ethereumService.initializeFilters();
        Config config = new Config()
                .setId(1L)
                .setGovernanceTokenAddress(ethereumHelper.getJynxToken().getContractAddress())
                .setMinClosingDelay(1L)
                .setMinEnactmentDelay(1L)
                .setMinOpenDelay(1L)
                .setMinProposerStake(1L)
                .setNetworkFee(BigDecimal.valueOf(0.001))
                .setParticipationThreshold(BigDecimal.valueOf(0.66))
                .setUuidSeed(1L);
        configRepository.save(config);
        configService.setTimestamp(nowAsMillis());
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
        stakeRepository.save(new Stake().setId(uuidUtils.next()).setUser(makerUser).setAmount(BigDecimal.valueOf(500000000L)));
        stakeRepository.save(new Stake().setId(uuidUtils.next()).setUser(takerUser).setAmount(BigDecimal.valueOf(700000000L)));
    }
}
