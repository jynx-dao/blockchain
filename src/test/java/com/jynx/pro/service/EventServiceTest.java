package com.jynx.pro.service;

import com.jynx.pro.Application;
import com.jynx.pro.constant.EventType;
import com.jynx.pro.entity.Event;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
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

import java.util.UUID;

@Slf4j
@Testcontainers
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "EVENT_SERVICE_TEST", matches = "true")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class EventServiceTest extends IntegrationTest {

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

    @Autowired
    private EventService eventService;

    @Test
    public void testConfirmUnknownEventType() {
        try {
            eventService.confirm(new Event().setType(EventType.DEPOSIT_ASSET).setId(UUID.randomUUID()));
            Assertions.fail();
        } catch(JynxProException e) {
            Assertions.assertEquals(e.getMessage(), ErrorCode.DEPOSIT_NOT_FOUND);
        }
    }
}
