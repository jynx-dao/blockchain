package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.WithdrawalStatus;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.BatchValidatorRequest;
import com.jynx.pro.request.CreateWithdrawalRequest;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import org.testcontainers.utility.DockerImageName;
import org.web3j.utils.Convert;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Slf4j
@Testcontainers
//@Disabled
@ActiveProfiles("tendermint")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EthereumServiceTest extends IntegrationTest {

    private static final String JYNX_KEY = "02d47b3068c9ff8e25eec7c83b74eb2c61073a1862f925b644b4b234c21e83dd";

    public static GenericContainer tendermint;

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
            blockchainGateway.setValidatorPublicKey(publicKey);
            ethereumService.setValidatorPrivateKey(privateKey);
            ethereumService.setValidatorPublicKey(publicKey);
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

    @Test
    public void testGetTokenSupply() {
        BigDecimal jynxTotalSupply = ethereumService.totalSupply(
                ethereumHelper.getJynxToken().getContractAddress());
        Assertions.assertEquals(Convert.toWei("1000000000", Convert.Unit.WEI)
                .setScale(1, RoundingMode.HALF_UP), jynxTotalSupply
                .setScale(1, RoundingMode.HALF_UP));
    }

    @Test
    public void testGetTokenSupplyWithError() {
        try {
            ethereumService.totalSupply("12345");
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_GET_SUPPLY);
        }
    }

    @Test
    public void testDecimalPlacesWithError() {
        try {
            ethereumService.decimalPlaces("12345");
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_GET_DECIMAL_PLACES);
        }
    }

    @Test
    public void testWithdrawAssetsWithError() {
        try {
            ethereumService.withdrawAssets(List.of("12345"), List.of(BigInteger.ONE), List.of("12345"));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_WITHDRAW_ASSETS);
        }
    }

    @Test
    public void testRemoveAssetWithError() {
        try {
            ethereumService.removeAsset(null);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_REMOVE_ASSET);
        }
    }

    @Test
    public void testAddAssetWithError() {
        try {
            ethereumService.addAsset(null);
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.CANNOT_ADD_ASSET);
        }
    }

    private void stakeTokens(double expectedStake, boolean unstake) throws InterruptedException {
        long modifier = (long) Math.pow(10, 18);
        BigInteger amount = BigInteger.valueOf(100).multiply(BigInteger.valueOf(modifier));
        ethereumHelper.approveJynx(ethereumHelper.getJynxProBridge().getContractAddress(), amount);
        if(unstake) {
            ethereumHelper.removeTokens(JYNX_KEY, amount);
        } else {
            ethereumHelper.stakeTokens(JYNX_KEY, amount);
        }
        Thread.sleep(30000L);
        List<Event> events = readOnlyRepository.getEventsByConfirmed(false);
        Assertions.assertEquals(events.size(), 0);
        Optional<User> user = readOnlyRepository.getUserByPublicKey(JYNX_KEY);
        Assertions.assertTrue(user.isPresent());
        Optional<Stake> stake = readOnlyRepository.getStakeByUser(user.get());
        Assertions.assertTrue(stake.isPresent());
        Assertions.assertEquals(stake.get().getAmount().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(expectedStake).setScale(2, RoundingMode.HALF_UP));
    }

    private Asset depositAsset() throws Exception {
        Asset asset = createAndEnactAsset(true);
        boolean assetActive = ethereumHelper.getJynxProBridge().assets(asset.getAddress()).send();
        Assertions.assertTrue(assetActive);
        BigInteger amount = priceUtils.toBigInteger(BigDecimal.TEN);
        ethereumHelper.approveDai(ethereumHelper.getJynxProBridge().getContractAddress(), amount);
        ethereumHelper.depositAsset(asset.getAddress(), amount, JYNX_KEY);
        Thread.sleep(30000L);
        ethereumService.confirmEvents(new BatchValidatorRequest());
        List<Event> events = eventRepository.findByConfirmed(false);
        Assertions.assertEquals(events.size(), 0);
        Optional<User> user = userRepository.findByPublicKey(JYNX_KEY);
        Assertions.assertTrue(user.isPresent());
        Optional<Account> account = accountRepository.findByUserAndAsset(user.get(), asset);
        Assertions.assertTrue(account.isPresent());
        Assertions.assertEquals(account.get().getBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.TEN.setScale(2, RoundingMode.HALF_UP));
        Assertions.assertEquals(account.get().getAvailableBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.TEN.setScale(2, RoundingMode.HALF_UP));
        Assertions.assertEquals(account.get().getMarginBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        return asset;
    }

    private void withdrawAsset(
            final Asset asset
    ) {
        Optional<User> user = userRepository.findByPublicKey(JYNX_KEY);
        Assertions.assertTrue(user.isPresent());
        CreateWithdrawalRequest request = new CreateWithdrawalRequest();
        request.setUser(user.get());
        request.setAssetId(asset.getId());
        request.setAmount(BigDecimal.TEN);
        request.setDestination(ethereumHelper.getJynxToken().getContractAddress());
        Withdrawal withdrawal = accountService.createWithdrawal(request);
        accountService.processWithdrawals();
        Optional<Withdrawal> withdrawalOptional = withdrawalRepository.findById(withdrawal.getId());
        Assertions.assertTrue(withdrawalOptional.isPresent());
        Assertions.assertEquals(withdrawalOptional.get().getStatus(), WithdrawalStatus.DEBITED);
        Optional<Account> account = accountRepository.findByUserAndAsset(user.get(), asset);
        Assertions.assertTrue(account.isPresent());
        Assertions.assertEquals(account.get().getBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        Assertions.assertEquals(account.get().getAvailableBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        Assertions.assertEquals(account.get().getMarginBalance().setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    public void testStakeAndRemoveTokens() throws InterruptedException {
        stakeTokens(100, false);
        stakeTokens(0, true);
    }

    @Test
    @Disabled
    public void testDepositAndWithdrawAsset() throws Exception {
        Asset asset = depositAsset();
        withdrawAsset(asset);
    }

    @Test
    public void testConfirmEventsFailed() {
        ganache.stop();
        setupComplete = false;
        List<Event> events = ethereumService.confirmEvents(new BatchValidatorRequest());
        Assertions.assertEquals(events.size(), 0);
    }
}
