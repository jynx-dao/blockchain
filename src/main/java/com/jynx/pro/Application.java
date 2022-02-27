package com.jynx.pro;

import com.jynx.pro.blockchain.BlockchainGateway;
import com.jynx.pro.grpc.GRPCServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Application implements CommandLineRunner {

    @Autowired
    private BlockchainGateway gateway;

    @Value("${grpc.enabled}")
    private Boolean grpcEnabled;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if(grpcEnabled) {
            GRPCServer server = new GRPCServer(gateway, 26658);
            server.start();
            server.blockUntilShutdown();
        }
    }
}