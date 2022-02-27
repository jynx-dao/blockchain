package com.jynx.pro.blockchain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.jynx.pro.constant.TendermintTransaction;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.request.CreateOrderRequest;
import com.jynx.pro.service.OrderService;
import io.grpc.stub.StreamObserver;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tendermint.abci.types.ABCIApplicationGrpc;
import tendermint.abci.types.Types;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class BlockchainGateway extends ABCIApplicationGrpc.ABCIApplicationImplBase {

    @Autowired
    private OrderService orderService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Accessors(chain = true)
    private static class CheckTxResult {
        private int code;
        private String error;
    }

    private TendermintTransaction getTendermintTx(String tx) {
        try {
            String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
            JSONObject jsonObject = new JSONObject(txAsJson);
            return TendermintTransaction.valueOf(jsonObject.getString("tendermintTx"));
        } catch(Exception e) {
            return TendermintTransaction.UNKNOWN;
        }
    }

    private void deliverTransaction(String tx, TendermintTransaction tendermintTx) {
        String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
        try {
            if (tendermintTx.equals(TendermintTransaction.CREATE_ORDER)) {
                CreateOrderRequest request = objectMapper.readValue(txAsJson, CreateOrderRequest.class);
                orderService.create(request);
            }
        } catch(JsonProcessingException e) {
            log.error(ErrorCode.PARSE_JSON_ERROR, e);
        }
    }

    private CheckTxResult checkTransaction(String tx, TendermintTransaction tendermintTx) {
        String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
        try {
            if (tendermintTx.equals(TendermintTransaction.CREATE_ORDER)) {
                CreateOrderRequest request = objectMapper.readValue(txAsJson, CreateOrderRequest.class);
                // TODO - check validity of request
                return new CheckTxResult().setCode(0);
            }
        } catch(JsonProcessingException e) {
            log.error(ErrorCode.PARSE_JSON_ERROR, e);
        }
        return new CheckTxResult().setCode(1).setError(ErrorCode.UNRECOGNISED_TENDERMINT_TX);
    }

    @Override
    public void initChain(tendermint.abci.types.Types.RequestInitChain request,
                          io.grpc.stub.StreamObserver<tendermint.abci.types.Types.ResponseInitChain> responseObserver) {
        Types.ResponseInitChain resp = Types.ResponseInitChain.newBuilder().build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void checkTx(Types.RequestCheckTx req, StreamObserver<Types.ResponseCheckTx> responseObserver) {
        String tx = req.getTx().toStringUtf8();
        TendermintTransaction tendermintTx = getTendermintTx(tx);
        Types.ResponseCheckTx.Builder builder = Types.ResponseCheckTx.newBuilder();
        CheckTxResult checkTxResult = checkTransaction(tx, tendermintTx);
        builder.setCode(checkTxResult.getCode());
        if(checkTxResult.getError() != null) {
            builder.setLog(checkTxResult.getError());
        }
        Types.ResponseCheckTx resp = builder.setGasWanted(1).build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void beginBlock(Types.RequestBeginBlock req, StreamObserver<Types.ResponseBeginBlock> responseObserver) {
        Types.ResponseBeginBlock resp = Types.ResponseBeginBlock.newBuilder().build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void endBlock(Types.RequestEndBlock req, StreamObserver<Types.ResponseEndBlock> responseObserver) {
        Types.ResponseEndBlock resp = Types.ResponseEndBlock.newBuilder().build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void deliverTx(Types.RequestDeliverTx req, StreamObserver<Types.ResponseDeliverTx> responseObserver) {
        String tx = req.getTx().toStringUtf8();
        TendermintTransaction tendermintTx = getTendermintTx(tx);
        Types.ResponseDeliverTx.Builder builder = Types.ResponseDeliverTx.newBuilder();
        try {
            deliverTransaction(tx, tendermintTx);
        } catch(Exception e) {
            builder.setCode(1);
            builder.setLog(e.getMessage());
            log.error(e.getMessage(), e);
        }
        Types.ResponseDeliverTx resp = builder.setGasWanted(1).build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void commit(Types.RequestCommit req, StreamObserver<Types.ResponseCommit> responseObserver) {
        Types.ResponseCommit resp = Types.ResponseCommit.newBuilder()
                .setData(ByteString.copyFrom(new byte[8]))
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void echo(Types.RequestEcho request, StreamObserver<Types.ResponseEcho> responseObserver) {
        Types.ResponseEcho response = Types.ResponseEcho.newBuilder().setMessage(request.getMessage()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void info(Types.RequestInfo request, StreamObserver<Types.ResponseInfo> responseObserver) {
        Types.ResponseInfo response = Types.ResponseInfo.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void query(Types.RequestQuery req, StreamObserver<Types.ResponseQuery> responseObserver) {
        String data = req.getData().toStringUtf8();
        TendermintTransaction tendermintTx = getTendermintTx(data);
        Types.ResponseQuery.Builder builder = Types.ResponseQuery.newBuilder();
        // TODO - enable queries
//        builder.setValue(ByteString.copyFrom(processQuery(data, tendermintTx), Charset.defaultCharset()));
        Types.ResponseQuery response = builder.build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}