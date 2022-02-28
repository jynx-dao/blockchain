package com.jynx.pro.client;

import com.jynx.pro.Application;
import com.jynx.pro.blockchain.TendermintClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Slf4j
@Testcontainers
@ActiveProfiles("tendermint")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TendermintClientTest {

    @Autowired
    private TendermintClient tendermintClient;

    @Container
    public static GenericContainer tendermint =
            new GenericContainer(DockerImageName.parse("tendermint/tendermint:v0.33.8"))
            .withCommand("node --abci grpc --proxy_app tcp://host.docker.internal:26658")
            .withExtraHost("host.docker.internal", "host-gateway");

    @Test
    public void test() throws InterruptedException {
        Thread.sleep(5000L);
    }
}