package com.jynx.pro;

import com.jynx.pro.blockchain.BlockchainGateway;
import com.jynx.pro.grpc.GRPCServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@SpringBootApplication
public class Application implements CommandLineRunner {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Autowired
    private BlockchainGateway gateway;

    @Value("${grpc.enabled}")
    private Boolean grpcEnabled;
    @Value("${grpc.block.enabled}")
    private Boolean grpcBlockEnabled;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        if(grpcEnabled) {
            File userDirectory = FileUtils.getUserDirectory();
            Files.createDirectories(Paths.get(String.format("%s/.jynx", userDirectory.toPath())));
            Files.createDirectories(Paths.get(String.format("%s/.jynx/snapshots", userDirectory.toPath())));
            GRPCServer server = new GRPCServer(gateway, 26658);
            server.start();
            if(grpcBlockEnabled) {
                server.blockUntilShutdown();
            }
        }
    }
}