package com.jynx.pro.grpc;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class GRPCServer {

    private final Server server;
    private final int port;

    public GRPCServer(BindableService service, int port) {
        this.port = port;
        this.server = ServerBuilder.forPort(port).addService(service).build();
    }

    public void start() throws IOException {
        server.start();
        log.info("gRPC server started, listening on {}", this.port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("shutting down gRPC server since JVM is shutting down");
            this.stop();
            log.info("server shut down");
        }));
    }

    private void stop() {
        server.shutdown();
    }

    public void blockUntilShutdown() throws InterruptedException {
        server.awaitTermination();
    }
}