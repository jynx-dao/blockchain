package com.jynx.pro.service;

import com.jynx.pro.entity.Config;
import com.jynx.pro.entity.Stake;
import com.jynx.pro.entity.User;
import com.jynx.pro.helper.EthereumHelper;
import com.jynx.pro.repository.*;
import com.jynx.pro.utils.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
    protected ProposalRepository proposalRepository;
    @Autowired
    protected VoteRepository voteRepository;
    @Autowired
    protected ConfigService configService;
    @Autowired
    protected StakeRepository stakeRepository;

    protected static final String PRIVATE_KEY = "0x4b077050dd12f33bb78773d957d87b0b477f6470017d9d6f0539c3c0683b6eb3";
    private static final String GANACHE_CMD = String
            .format("ganache-cli --gasLimit 100000000 --account=\"%s,1000000000000000000000\"", PRIVATE_KEY);
    @Container
    public GenericContainer ganache = new GenericContainer(DockerImageName.parse("trufflesuite/ganache-cli:latest"))
            .withExposedPorts(8545)
            .withCommand(GANACHE_CMD);

    protected User takerUser;
    protected User makerUser;

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
        assetRepository.deleteAll();
        voteRepository.deleteAll();
        proposalRepository.deleteAll();
        stakeRepository.deleteAll();
        userRepository.deleteAll();
        configRepository.deleteAll();
    }

    protected void initializeState() {
        ethereumHelper.deploy(ganache.getHost(), ganache.getFirstMappedPort(), PRIVATE_KEY);
        ethereumService.setRpcHost(ganache.getHost());
        ethereumService.setRpcPort(ganache.getFirstMappedPort());
        Config config = new Config()
                .setId(1L)
                .setGovernanceTokenAddress(ethereumHelper.getJynxTokenContract().getContractAddress())
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
        stakeRepository.save(new Stake().setId(uuidUtils.next()).setUser(makerUser).setAmount(500000000L));
        stakeRepository.save(new Stake().setId(uuidUtils.next()).setUser(takerUser).setAmount(700000000L));
    }
}
