package com.jynx.pro.blockchain;

import com.google.protobuf.ByteString;
import com.jynx.pro.constant.TendermintTransaction;
import com.jynx.pro.entity.User;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.manager.AppStateManager;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.model.CheckTxResult;
import com.jynx.pro.request.*;
import com.jynx.pro.service.*;
import com.jynx.pro.utils.JSONUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import static io.grpc.stub.ClientCalls.asyncUnaryCall;

@Slf4j
@Component
public class BlockchainGateway extends ABCIApplicationGrpc.ABCIApplicationImplBase {

    @Autowired
    private TendermintClient tendermintClient;
    @Autowired
    private OrderService orderService;
    @Autowired
    private MarketService marketService;
    @Autowired
    private AssetService assetService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private EthereumService ethereumService;
    @Autowired
    private ProposalService proposalService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private UserService userService;
    @Autowired
    private AppStateManager appStateManager;
    @Autowired
    private DatabaseTransactionManager databaseTransactionManager;
    @Autowired
    private JSONUtils jsonUtils;
    @Value("${validator.address}")
    private String validatorAddress;

    private final Map<TendermintTransaction, Function<String, Object>> deliverTransactions = new HashMap<>();
    private final Map<TendermintTransaction, Consumer<String>> checkTransactions = new HashMap<>();

    private void setupCheckTransactions() {
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
        checkTransactions.put(TendermintTransaction.CONFIRM_ETHEREUM_EVENTS, this::checkConfirmEthereumEvents);
        checkTransactions.put(TendermintTransaction.SYNC_PROPOSALS, this::checkSyncProposals);
        checkTransactions.put(TendermintTransaction.SETTLE_MARKETS, this::checkSettleMarkets);
        checkTransactions.put(TendermintTransaction.CAST_VOTE, this::checkCastVote);
    }

    private void setupDeliverTransactions() {
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
        deliverTransactions.put(TendermintTransaction.CONFIRM_ETHEREUM_EVENTS, this::confirmEthereumEvents);
        deliverTransactions.put(TendermintTransaction.SYNC_PROPOSALS, this::syncProposals);
        deliverTransactions.put(TendermintTransaction.SETTLE_MARKETS, this::settleMarkets);
        deliverTransactions.put(TendermintTransaction.CAST_VOTE, this::castVote);
    }

    @PostConstruct
    private void setup() {
        setupDeliverTransactions();
        setupCheckTransactions();
    }

    private TendermintTransaction getTendermintTx(
            final String tx
    ) {
        try {
            String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
            JSONObject jsonObject = new JSONObject(txAsJson);
            return TendermintTransaction.valueOf(jsonObject.getString("tendermintTx"));
        } catch(Exception e) {
            return TendermintTransaction.UNKNOWN;
        }
    }

    private void checkCastVote(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkConfirmEthereumEvents(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkSyncProposals(
            final String txAsJson
    ) {
        // TODO
    }

    private void checkSettleMarkets(
            final String txAsJson
    ) {
        // TODO
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

    private Object castVote(
            final String txAsJson
    ) {
//        verifySignature(txAsJson, request); // TODO - verify validator
        return proposalService.vote(jsonUtils.fromJson(txAsJson, CastVoteRequest.class));
    }

    private Object confirmEthereumEvents(
            final String txAsJson
    ) {
//        verifySignature(txAsJson, request); // TODO - verify validator
        return ethereumService.confirmEvents();
    }

    private Object syncProposals(
            final String txAsJson
    ) {
//        verifySignature(txAsJson, request); // TODO - verify validator
        return proposalService.sync();
    }

    private Object settleMarkets(
            final String txAsJson
    ) {
//        verifySignature(txAsJson, request); // TODO - verify validator
        return marketService.settleMarkets();
    }

    private Object createWithdrawal(
            final String txAsJson
    ) {
        CreateWithdrawalRequest request = jsonUtils.fromJson(txAsJson, CreateWithdrawalRequest.class);
        verifySignature(txAsJson, request);
        return accountService.createWithdrawal(request);
    }

    private Object cancelWithdrawal(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        verifySignature(txAsJson, request);
        return accountService.cancelWithdrawal(request);
    }

    private Object addMarket(
            final String txAsJson
    ) {
        AddMarketRequest request = jsonUtils.fromJson(txAsJson, AddMarketRequest.class);
        verifySignature(txAsJson, request);
        return marketService.proposeToAdd(request);
    }

    private Object amendMarket(
            final String txAsJson
    ) {
        AmendMarketRequest request = jsonUtils.fromJson(txAsJson, AmendMarketRequest.class);
        verifySignature(txAsJson, request);
        return marketService.proposeToAmend(request);
    }

    private Object suspendMarket(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        verifySignature(txAsJson, request);
        return marketService.proposeToSuspend(request);
    }

    private Object unsuspendMarket(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        verifySignature(txAsJson, request);
        return marketService.proposeToUnsuspend(request);
    }

    private Object addAsset(
            final String txAsJson
    ) {
        AddAssetRequest request = jsonUtils.fromJson(txAsJson, AddAssetRequest.class);
        verifySignature(txAsJson, request);
        return assetService.proposeToAdd(request);
    }

    private Object suspendAsset(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        verifySignature(txAsJson, request);
        return assetService.proposeToSuspend(request);
    }

    private Object unsuspendAsset(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        verifySignature(txAsJson, request);
        return assetService.proposeToUnsuspend(request);
    }

    private Object createOrder(
            final String txAsJson
    ) {
        CreateOrderRequest request = jsonUtils.fromJson(txAsJson, CreateOrderRequest.class);
        verifySignature(txAsJson, request);
        return orderService.create(request);
    }

    private Object cancelOrder(
            final String txAsJson
    ) {
        CancelOrderRequest request = jsonUtils.fromJson(txAsJson, CancelOrderRequest.class);
        verifySignature(txAsJson, request);
        return orderService.cancel(request);
    }

    private Object amendOrder(
            final String txAsJson
    ) {
        AmendOrderRequest request = jsonUtils.fromJson(txAsJson, AmendOrderRequest.class);
        verifySignature(txAsJson, request);
        return orderService.amend(request);
    }

    private void verifySignature(
            final String txAsJson,
            final SignedRequest request
    ) {
        // TODO - verify the signature
        User user = userService.getAndCreate(request.getPublicKey());
        request.setUser(user);
    }

    private Object deliverTransaction(
            final String tx,
            final TendermintTransaction tendermintTx
    ) {
        String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
        // TODO - extract signature / public key
        try {
            Object result = deliverTransactions.get(tendermintTx).apply(txAsJson);
            appStateManager.update(result.hashCode());
            return result;
        } catch(Exception e) {
            log.info(e.getMessage(), e);
            appStateManager.update(e.getMessage() != null ? e.getMessage().hashCode() : 1);
            throw new JynxProException(e.getMessage());
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
        } catch(Exception e) {
            return new CheckTxResult().setCode(1).setError(e.getMessage());
        }
    }

    @Override
    public void initChain(tendermint.abci.types.Types.RequestInitChain request,
                          io.grpc.stub.StreamObserver<tendermint.abci.types.Types.ResponseInitChain> responseObserver) {
        Types.ResponseInitChain resp = Types.ResponseInitChain.newBuilder().build();
        request.getAppStateBytes(); // TODO - load config from genesis
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
        long millis = (req.getHeader().getTime().getSeconds() * 1000) +
                Math.round(req.getHeader().getTime().getNanos() / 1000000d);
        configService.setTimestamp(millis);
        String proposerAddress = req.getHeader().getProposerAddress().toStringUtf8();
        if(validatorAddress.equals(proposerAddress)) {
            tendermintClient.confirmEthereumEvents();
            tendermintClient.settleMarkets();
            tendermintClient.syncProposals(new SyncProposalsRequest()); // TODO - add public key and signature
            // TODO - propagate latest Ethereum events
            // TODO - risk management / liquidations?? [if we decide it's better to do it once per block...]
        }
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void endBlock(Types.RequestEndBlock req, StreamObserver<Types.ResponseEndBlock> responseObserver) {
        // TODO - update validators
        Types.ResponseEndBlock resp = Types.ResponseEndBlock.newBuilder().build();
        appStateManager.setBlockHeight(req.getHeight());
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void deliverTx(Types.RequestDeliverTx req, StreamObserver<Types.ResponseDeliverTx> responseObserver) {
        String tx = req.getTx().toStringUtf8();
        TendermintTransaction tendermintTx = getTendermintTx(tx);
        Types.ResponseDeliverTx.Builder builder = Types.ResponseDeliverTx.newBuilder();
        try {
            Object result = deliverTransaction(tx, tendermintTx);
            builder.setCode(0);
            builder.setLog(jsonUtils.toJson(result));
        } catch(Exception e) {
            builder.setCode(1);
            builder.setLog(e.getMessage() != null ? e.getMessage() : ErrorCode.UNKNOWN_ERROR);
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

    @Override
    public void flush(Types.RequestFlush request, StreamObserver<Types.ResponseFlush> responseObserver) {
        Types.ResponseFlush response = Types.ResponseFlush.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // TODO - implement snapshot functions
}