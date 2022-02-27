package com.jynx.pro.blockchain;

import com.google.protobuf.ByteString;
import com.jynx.pro.constant.TendermintTransaction;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.manager.AppStateManager;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.request.*;
import com.jynx.pro.service.AccountService;
import com.jynx.pro.service.AssetService;
import com.jynx.pro.service.MarketService;
import com.jynx.pro.service.OrderService;
import com.jynx.pro.utils.JSONUtils;
import io.grpc.stub.StreamObserver;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tendermint.abci.types.ABCIApplicationGrpc;
import tendermint.abci.types.Types;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

@Slf4j
@Component
public class BlockchainGateway extends ABCIApplicationGrpc.ABCIApplicationImplBase {

    @Autowired
    private OrderService orderService;
    @Autowired
    private MarketService marketService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AppStateManager appStateManager;
    @Autowired
    private DatabaseTransactionManager databaseTransactionManager;
    @Autowired
    private JSONUtils jsonUtils;

    private final Map<TendermintTransaction, Function<String, Integer>> deliverTransactions = new HashMap<>();
    private final Map<TendermintTransaction, Consumer<String>> checkTransactions = new HashMap<>();

    @PostConstruct
    private void setup() {
        deliverTransactions.put(TendermintTransaction.CREATE_ORDER, this::createOrder);
        deliverTransactions.put(TendermintTransaction.CANCEL_ORDER, this::cancelOrder);
        deliverTransactions.put(TendermintTransaction.AMEND_ORDER, this::amendOrder);
        deliverTransactions.put(TendermintTransaction.CREATE_WITHDRAWAL, this::createWithdrawal);
        deliverTransactions.put(TendermintTransaction.CANCEL_WITHDRAWAL, this::cancelWithdrawal);
        deliverTransactions.put(TendermintTransaction.ADD_MARKET, this::addMarket);
        deliverTransactions.put(TendermintTransaction.AMEND_MARKET, this::amendMarket);
        deliverTransactions.put(TendermintTransaction.SUSPEND_MARKET, this::suspendMarket);
        deliverTransactions.put(TendermintTransaction.UNSUSPEND_MARKET, this::unsuspendMarket);
        deliverTransactions.put(TendermintTransaction.ADD_ASSET, this::addAsset);
        deliverTransactions.put(TendermintTransaction.SUSPEND_ASSET, this::suspendAsset);
        deliverTransactions.put(TendermintTransaction.UNSUSPEND_ASSET, this::unsuspendAsset);
        checkTransactions.put(TendermintTransaction.CREATE_ORDER, this::checkCreateOrder);
        checkTransactions.put(TendermintTransaction.CANCEL_ORDER, this::checkCancelOrder);
        checkTransactions.put(TendermintTransaction.AMEND_ORDER, this::checkAmendOrder);
        checkTransactions.put(TendermintTransaction.CREATE_WITHDRAWAL, this::checkCreateWithdrawal);
        checkTransactions.put(TendermintTransaction.CANCEL_WITHDRAWAL, this::checkCancelWithdrawal);
        checkTransactions.put(TendermintTransaction.ADD_MARKET, this::checkAddMarket);
        checkTransactions.put(TendermintTransaction.AMEND_MARKET, this::checkAmendMarket);
        checkTransactions.put(TendermintTransaction.SUSPEND_MARKET, this::checkSuspendMarket);
        checkTransactions.put(TendermintTransaction.UNSUSPEND_MARKET, this::checkUnsuspendMarket);
        checkTransactions.put(TendermintTransaction.ADD_ASSET, this::checkAddAsset);
        checkTransactions.put(TendermintTransaction.SUSPEND_ASSET, this::checkSuspendAsset);
        checkTransactions.put(TendermintTransaction.UNSUSPEND_ASSET, this::checkUnsuspendAsset);
    }

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

    private void checkCreateWithdrawal(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkCancelWithdrawal(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkAddMarket(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkAmendMarket(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkSuspendMarket(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkUnsuspendMarket(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkAddAsset(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkSuspendAsset(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkUnsuspendAsset(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkCreateOrder(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkCancelOrder(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkAmendOrder(
            final String txAsJson
    ) {
        // TODO
    }

    private int createWithdrawal(
            final String txAsJson
    ) {
        return accountService.createWithdrawal(jsonUtils.fromJson(txAsJson, CreateWithdrawalRequest.class)).hashCode();
    }

    private int cancelWithdrawal(
            final String txAsJson
    ) {
        return accountService.cancelWithdrawal(jsonUtils.fromJson(txAsJson, SingleItemRequest.class)).hashCode();
    }

    private int addMarket(
            final String txAsJson
    ) {
        return marketService.proposeToAdd(jsonUtils.fromJson(txAsJson, AddMarketRequest.class)).hashCode();
    }

    private int amendMarket(
            final String txAsJson
    ) {
        return marketService.proposeToAmend(jsonUtils.fromJson(txAsJson, AmendMarketRequest.class)).hashCode();
    }

    private int suspendMarket(
            final String txAsJson
    ) {
        return marketService.proposeToSuspend(jsonUtils.fromJson(txAsJson, SingleItemRequest.class)).hashCode();
    }

    private int unsuspendMarket(
            final String txAsJson
    ) {
        return marketService.proposeToUnsuspend(jsonUtils.fromJson(txAsJson, SingleItemRequest.class)).hashCode();
    }

    private int addAsset(
            final String txAsJson
    ) {
        return assetService.proposeToAdd(jsonUtils.fromJson(txAsJson, AddAssetRequest.class)).hashCode();
    }

    private int suspendAsset(
            final String txAsJson
    ) {
        return assetService.proposeToSuspend(jsonUtils.fromJson(txAsJson, SingleItemRequest.class)).hashCode();
    }

    private int unsuspendAsset(
            final String txAsJson
    ) {
        return assetService.proposeToUnsuspend(jsonUtils.fromJson(txAsJson, SingleItemRequest.class)).hashCode();
    }

    private int createOrder(
            final String txAsJson
    ) {
        return orderService.create(jsonUtils.fromJson(txAsJson, CreateOrderRequest.class)).hashCode();
    }

    private int cancelOrder(
            final String txAsJson
    ) {
        return orderService.cancel(jsonUtils.fromJson(txAsJson, CancelOrderRequest.class)).hashCode();
    }

    private int amendOrder(
            final String txAsJson
    ) {
        return orderService.amend(jsonUtils.fromJson(txAsJson, AmendOrderRequest.class)).hashCode();
    }

    private void deliverTransaction(
            final String tx,
            final TendermintTransaction tendermintTx
    ) {
        String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
        // TODO - extract signature / public key
        try {
            appStateManager.update(deliverTransactions.get(tendermintTx).apply(txAsJson));
        } catch(JynxProException e) {
            appStateManager.update(e.hashCode());
        }
    }

    private CheckTxResult checkTransaction(
            final String tx,
            final TendermintTransaction tendermintTx
    ) {
        String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
        // TODO - extract signature / public key
        try {
            checkTransactions.get(tendermintTx).accept(txAsJson);
            return new CheckTxResult().setCode(0);
        } catch(JynxProException e) {
            return new CheckTxResult().setCode(1).setError(e.getMessage());
        }
    }

    @Override
    public void initChain(tendermint.abci.types.Types.RequestInitChain request,
                          io.grpc.stub.StreamObserver<tendermint.abci.types.Types.ResponseInitChain> responseObserver) {
        Types.ResponseInitChain resp = Types.ResponseInitChain.newBuilder().build();
        // TODO - load config from genesis
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
        Types.ResponseCheckTx resp = builder.setGasWanted(1).build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void beginBlock(Types.RequestBeginBlock req, StreamObserver<Types.ResponseBeginBlock> responseObserver) {
        Types.ResponseBeginBlock resp = Types.ResponseBeginBlock.newBuilder().build();
        databaseTransactionManager.createTransaction();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void endBlock(Types.RequestEndBlock req, StreamObserver<Types.ResponseEndBlock> responseObserver) {
        // TODO - update validators
        // TODO - deliver end of block transactions [??]
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
                .setData(ByteString.copyFrom(appStateManager.getStateAsBytes()))
                .build();
        databaseTransactionManager.commit();
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

    // TODO - implement snapshot functions
}