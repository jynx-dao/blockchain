package com.jynx.pro.service;

import com.jynx.pro.Application;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "SNAPSHOT_SERVICE_TEST", matches = "true")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class SnapshotServiceTest extends IntegrationTest {

    @Autowired
    private SnapshotService snapshotService;

    @BeforeEach
    public void setup() {
        initializeState();
        databaseTransactionManager.createTransaction();
    }

    @AfterEach
    public void shutdown() {
        databaseTransactionManager.commit();
        clearState();
    }

    @Test
    public void testSaveSnapshotAndChunks() {
        Assertions.assertEquals(1, configRepository.findAll().size());
        snapshotService.clearState();
        databaseTransactionManager.commit();
        databaseTransactionManager.createTransaction();
        Assertions.assertEquals(0, configRepository.findAll().size());
        String content = "{\"data\":[{\"uuidSeed\":106,\"governanceTokenAddress\":\"0x0\",\"minEnactmentDelay\":1,\"minOpenDelay\":1,\"minClosingDelay\":1,\"networkFee\":1.00,\"minProposerStake\":1,\"participationThreshold\":0.66,\"approvalThreshold\":0.66,\"bridgeAddress\":\"0x0\",\"ethConfirmations\":50,\"activeValidatorCount\":1,\"backupValidatorCount\":1,\"validatorBond\":0.00,\"validatorMinDelegation\":0.00,\"snapshotFrequency\":100,\"asyncTaskFrequency\":60,\"snapshotChunkRows\":10}],\"entityName\":\"com.jynx.pro.entity.Config\"}";
        snapshotService.saveChunk(content);
        Assertions.assertEquals(1, configRepository.findAll().size());
    }
}