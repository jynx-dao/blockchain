package com.jynx.pro.client;

import com.jynx.pro.Application;
import com.jynx.pro.blockchain.TendermintClient;
import com.jynx.pro.constant.AssetStatus;
import com.jynx.pro.constant.AssetType;
import com.jynx.pro.entity.Asset;
import com.jynx.pro.manager.AppStateManager;
import com.jynx.pro.request.AddAssetRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.response.TransactionResponse;
import com.jynx.pro.service.IntegrationTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@Testcontainers
@ActiveProfiles("tendermint")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TendermintClientTest extends IntegrationTest {

    @Autowired
    private TendermintClient tendermintClient;
    @Autowired
    private AppStateManager appStateManager;

    public static GenericContainer tendermint;

    @BeforeEach
    public void setup() {
        initializeState();
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
        clearState();
    }

    @Test
    public void testAddAsset() {
        AddAssetRequest request = new AddAssetRequest()
                .setAddress("0x0")
                .setName("Test asset")
                .setDecimalPlaces(5)
                .setType(AssetType.ERC20);
        long[] times = proposalTimes();
        request.setOpenTime(times[0]);
        request.setClosingTime(times[1]);
        request.setEnactmentTime(times[2]);
        request.setUser(makerUser);
        TransactionResponse<Asset> txResponse = tendermintClient.addAsset(request);
        Assertions.assertEquals(txResponse.getItem().getStatus(), AssetStatus.PENDING);
    }

    @SneakyThrows
    private void waitForBlockchain() {
        long blockHeight = appStateManager.getBlockHeight();
        while(blockHeight < 1) {
            blockHeight = appStateManager.getBlockHeight();
            Thread.sleep(500L);
        }
    }
}