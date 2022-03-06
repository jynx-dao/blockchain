package com.jynx.pro.blockchain;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.jynx.pro.constant.TendermintTransaction;
import com.jynx.pro.entity.Order;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.manager.AppStateManager;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.model.CheckTxResult;
import com.jynx.pro.request.*;
import com.jynx.pro.service.*;
import com.jynx.pro.utils.CryptoUtils;
import com.jynx.pro.utils.JSONUtils;
import io.grpc.stub.StreamObserver;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tendermint.abci.ABCIApplicationGrpc;
import tendermint.abci.Types;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private ValidatorService validatorService;
    @Autowired
    private AppStateManager appStateManager;
    @Autowired
    private DatabaseTransactionManager databaseTransactionManager;
    @Autowired
    private JSONUtils jsonUtils;
    @Autowired
    private CryptoUtils cryptoUtils;
    @Setter
    @Value("${validator.address}")
    private String validatorAddress;
    @Setter
    @Value("${validator.private.key}")
    private String validatorPrivateKey;
    @Setter
    @Value("${validator.public.key}")
    private String validatorPublicKey;
    @Value("${batch.block.frequency}")
    private Integer batchBlockFrequency;

    private static final Set<String> nonceHistory = new HashSet<>();

    // TODO - remove duplicated code by using generics for deliverTx and checkTx

    private static final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final Map<TendermintTransaction, Function<String, Object>> deliverTransactions = new HashMap<>();
    private final Map<TendermintTransaction, Consumer<String>> checkTransactions = new HashMap<>();

    private void setupCheckTransactions() {
        checkTransactions.put(TendermintTransaction.CREATE_ORDER, this::checkCreateOrder);
        checkTransactions.put(TendermintTransaction.CANCEL_ORDER, this::checkCancelOrder);
        checkTransactions.put(TendermintTransaction.AMEND_ORDER, this::checkAmendOrder);
        checkTransactions.put(TendermintTransaction.CREATE_ORDER_MANY, this::checkCreateOrderMany);
        checkTransactions.put(TendermintTransaction.CANCEL_ORDER_MANY, this::checkCancelOrderMany);
        checkTransactions.put(TendermintTransaction.AMEND_ORDER_MANY, this::checkAmendOrderMany);
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
        deliverTransactions.put(TendermintTransaction.CREATE_ORDER_MANY, this::createOrderMany);
        deliverTransactions.put(TendermintTransaction.CANCEL_ORDER_MANY, this::cancelOrderMany);
        deliverTransactions.put(TendermintTransaction.AMEND_ORDER_MANY, this::amendOrderMany);
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

    /**
     * Get {@link TendermintTransaction} from base64 transaction payload
     *
     * @param tx the transaction payload
     *
     * @return {@link TendermintTransaction}
     */
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
        verifySignature(jsonUtils.fromJson(txAsJson, CastVoteRequest.class));
    }

    private void checkConfirmEthereumEvents(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, BatchValidatorRequest.class), true);
    }

    private void checkSyncProposals(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, BatchValidatorRequest.class), true);
    }

    private void checkSettleMarkets(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, BatchValidatorRequest.class), true);
    }

    private void checkCreateWithdrawal(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, CreateWithdrawalRequest.class));
    }

    private void checkCancelWithdrawal(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, SingleItemRequest.class));
    }

    private void checkAddMarket(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, AddMarketRequest.class));
    }

    private void checkAmendMarket(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, AmendMarketRequest.class));
    }

    private void checkSuspendMarket(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, SingleItemRequest.class));
    }

    private void checkUnsuspendMarket(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, SingleItemRequest.class));
    }

    private void checkAddAsset(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, AddAssetRequest.class));
    }

    private void checkSuspendAsset(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, SingleItemRequest.class));
    }

    private void checkUnsuspendAsset(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, SingleItemRequest.class));
    }

    private void checkCreateOrder(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, CreateOrderRequest.class));
    }

    private void checkCancelOrder(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, CancelOrderRequest.class));
    }

    private void checkAmendOrder(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, AmendOrderRequest.class));
    }

    private void checkCreateOrderMany(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, BulkCreateOrderRequest.class));
    }

    private void checkCancelOrderMany(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, BulkCancelOrderRequest.class));
    }

    private void checkAmendOrderMany(
            final String txAsJson
    ) {
        verifySignature(jsonUtils.fromJson(txAsJson, BulkAmendOrderRequest.class));
    }

    private Object confirmEthereumEvents(
            final String txAsJson
    ) {
        BatchValidatorRequest request = jsonUtils.fromJson(txAsJson, BatchValidatorRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return ethereumService.confirmEvents();
    }

    private Object syncProposals(
            final String txAsJson
    ) {
        BatchValidatorRequest request = jsonUtils.fromJson(txAsJson, BatchValidatorRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return proposalService.sync();
    }

    private Object settleMarkets(
            final String txAsJson
    ) {
        BatchValidatorRequest request = jsonUtils.fromJson(txAsJson, BatchValidatorRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return marketService.settleMarkets();
    }

    private Object castVote(
            final String txAsJson
    ) {
        CastVoteRequest request = jsonUtils.fromJson(txAsJson, CastVoteRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return proposalService.vote(request);
    }

    private Object createWithdrawal(
            final String txAsJson
    ) {
        CreateWithdrawalRequest request = jsonUtils.fromJson(txAsJson, CreateWithdrawalRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return accountService.createWithdrawal(request);
    }

    private Object cancelWithdrawal(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return accountService.cancelWithdrawal(request);
    }

    private Object addMarket(
            final String txAsJson
    ) {
        AddMarketRequest request = jsonUtils.fromJson(txAsJson, AddMarketRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return marketService.proposeToAdd(request);
    }

    private Object amendMarket(
            final String txAsJson
    ) {
        AmendMarketRequest request = jsonUtils.fromJson(txAsJson, AmendMarketRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return marketService.proposeToAmend(request);
    }

    private Object suspendMarket(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return marketService.proposeToSuspend(request);
    }

    private Object unsuspendMarket(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return marketService.proposeToUnsuspend(request);
    }

    private Object addAsset(
            final String txAsJson
    ) {
        AddAssetRequest request = jsonUtils.fromJson(txAsJson, AddAssetRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return assetService.proposeToAdd(request);
    }

    private Object suspendAsset(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return assetService.proposeToSuspend(request);
    }

    private Object unsuspendAsset(
            final String txAsJson
    ) {
        SingleItemRequest request = jsonUtils.fromJson(txAsJson, SingleItemRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return assetService.proposeToUnsuspend(request);
    }

    private Object createOrder(
            final String txAsJson
    ) {
        CreateOrderRequest request = jsonUtils.fromJson(txAsJson, CreateOrderRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return orderService.create(request);
    }

    private Object cancelOrder(
            final String txAsJson
    ) {
        CancelOrderRequest request = jsonUtils.fromJson(txAsJson, CancelOrderRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return orderService.cancel(request);
    }

    private Object amendOrder(
            final String txAsJson
    ) {
        AmendOrderRequest request = jsonUtils.fromJson(txAsJson, AmendOrderRequest.class);
        synchronized (nonceHistory) {
            nonceHistory.add(request.getNonce());
        }
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return orderService.amend(request);
    }

    /**
     * Create multiple orders at once
     *
     * @param txAsJson the transaction payload
     *
     * @return {@link List<Order>}
     */
    private Object createOrderMany(
            final String txAsJson
    ) {
        BulkCreateOrderRequest request = jsonUtils.fromJson(txAsJson, BulkCreateOrderRequest.class);
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return orderService.createMany(request);
    }

    /**
     * Cancel multiple orders at once
     *
     * @param txAsJson the transaction payload
     *
     * @return {@link List<Order>}
     */
    private Object cancelOrderMany(
            final String txAsJson
    ) {
        BulkCancelOrderRequest request = jsonUtils.fromJson(txAsJson, BulkCancelOrderRequest.class);
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return orderService.cancelMany(request);
    }

    /**
     * Amend multiple orders at once
     *
     * @param txAsJson the transaction payload
     *
     * @return {@link List<Order>}
     */
    private Object amendOrderMany(
            final String txAsJson
    ) {
        BulkAmendOrderRequest request = jsonUtils.fromJson(txAsJson, BulkAmendOrderRequest.class);
        request.setUser(userService.getAndCreate(request.getPublicKey()));
        return orderService.amendMany(request);
    }

    /**
     * Verify the signature of a {@link SignedRequest}
     *
     * @param request {@link SignedRequest}
     * @param isValidator check if the public key belongs to an active validator
     */
    private void verifySignature(
            final SignedRequest request,
            final boolean isValidator
    ) {
        String signature = request.getSignature();
        String publicKey = request.getPublicKey();
        if(request.getNonce() == null) {
            throw new JynxProException(ErrorCode.NONCE_MANDATORY);
        }
        log.debug("{} = {}", request.getNonce(), request);
        if(nonceHistory.contains(request.getNonce())) {
            throw new JynxProException(ErrorCode.NONCE_ALREADY_USED);
        }
        if(isValidator) {
            try {
                String publicKeyAsBase64 = Base64.getEncoder().encodeToString(Hex.decodeHex(request.getPublicKey()));
                boolean result = validatorService.isValidator(publicKeyAsBase64);
                if(!result) {
                    throw new JynxProException(ErrorCode.SIGNATURE_INVALID);
                }
            } catch(Exception e) {
                log.error(e.getMessage(), e);
                throw new JynxProException(ErrorCode.SIGNATURE_INVALID);
            }
        }
        request.setSignature(null);
        request.setPublicKey(null);
        String message = jsonUtils.toJson(request);
        boolean result = cryptoUtils.verify(signature, message, publicKey);
        if(!result) {
            throw new JynxProException(ErrorCode.SIGNATURE_INVALID);
        }
    }

    /**
     * Verify the signature of a {@link SignedRequest}
     *
     * @param request {@link SignedRequest}
     */
    private void verifySignature(
            final SignedRequest request
    ) {
        verifySignature(request, false);
    }

    private Object deliverTransaction(
            final String tx,
            final TendermintTransaction tendermintTx
    ) {
        String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
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
        try {
            checkTransactions.get(tendermintTx).accept(txAsJson);
            return new CheckTxResult().setCode(0);
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            return new CheckTxResult().setCode(1).setError(e.getMessage());
        }
    }

    /**
     * Get the block timestamp in milliseconds
     *
     * @param time {@link Timestamp}
     *
     * @return timestamp in milliseconds
     */
    private Long getBlockTimeAsMillis(
            final Timestamp time
    ) {
        return (time.getSeconds() * 1000) + Math.round(time.getNanos() / 1000000d);
    }

    /**
     * Get {@link BatchValidatorRequest} signed by the node
     *
     * @param address the address of the node
     * @param height the current block height
     *
     * @return {@link BatchValidatorRequest}
     */
    private BatchValidatorRequest getBatchRequest(
            final String address,
            final long height
    ) {
        BatchValidatorRequest request = new BatchValidatorRequest()
                .setAddress(address)
                .setHeight(height);
        request.setNonce(ethereumService.getNonce().toString());
        String message = jsonUtils.toJson(request);
        String hexPrivateKey = Hex.encodeHexString(Base64.getDecoder().decode(validatorPrivateKey));
        String hexPublicKey = Hex.encodeHexString(Base64.getDecoder().decode(validatorPublicKey));
        String sig = cryptoUtils.sign(message, hexPrivateKey).orElse("");
        request.setSignature(sig);
        request.setPublicKey(hexPublicKey);
        return request;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initChain(tendermint.abci.Types.RequestInitChain request,
                          io.grpc.stub.StreamObserver<tendermint.abci.Types.ResponseInitChain> responseObserver) {
        Types.ResponseInitChain resp = Types.ResponseInitChain.newBuilder().build();
        databaseTransactionManager.createTransaction();
        String appState = request.getAppStateBytes().toStringUtf8();
        try {
            configService.initializeNetworkConfig(new JSONObject(appState));
        } catch (JSONException e) {
            log.error(e.getMessage(), e);
            log.error(ErrorCode.INVALID_APP_STATE);
        }
        request.getValidatorsList().forEach(v -> {
            String publicKey = Base64.getEncoder().encodeToString(
                    request.getValidatorsList().get(0).getPubKey().getEd25519().toByteArray());
            validatorService.add(publicKey);
        });
        databaseTransactionManager.commit();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void beginBlock(Types.RequestBeginBlock req, StreamObserver<Types.ResponseBeginBlock> responseObserver) {
        Types.ResponseBeginBlock resp = Types.ResponseBeginBlock.newBuilder().build();
        databaseTransactionManager.createTransaction();
        configService.setTimestamp(getBlockTimeAsMillis(req.getHeader().getTime()));
        String proposerAddress = Hex.encodeHexString(req.getHeader().getProposerAddress().toByteArray());
        long blockHeight = req.getHeader().getHeight();
        if(validatorAddress.toLowerCase(Locale.ROOT).equals(proposerAddress.toLowerCase(Locale.ROOT)) &&
                blockHeight % batchBlockFrequency == 0 && blockHeight > 1) {
            executorService.submit(() -> {
                try {
                    tendermintClient.confirmEthereumEvents(getBatchRequest(proposerAddress, blockHeight));
                } catch (Exception e) {
                    log.error(ErrorCode.CONFIRM_ETHEREUM_EVENTS_FAILED, e);
                }
            });
            executorService.submit(() -> {
                try {
                    tendermintClient.settleMarkets(getBatchRequest(proposerAddress, blockHeight));
                } catch (Exception e) {
                    log.error(ErrorCode.SETTLE_MARKETS_FAILED, e);
                }
            });
            executorService.submit(() -> {
                try {
                    tendermintClient.syncProposals(getBatchRequest(proposerAddress, blockHeight));
                } catch(Exception e) {
                    log.error(ErrorCode.SYNC_PROPOSALS_FAILED, e);
                }
            });
            // TODO - propagate latest Ethereum events
        }
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void endBlock(Types.RequestEndBlock req, StreamObserver<Types.ResponseEndBlock> responseObserver) {
        // TODO - update validators
        Types.ResponseEndBlock resp = Types.ResponseEndBlock.newBuilder().build();
        appStateManager.setBlockHeight(req.getHeight());
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void commit(Types.RequestCommit req, StreamObserver<Types.ResponseCommit> responseObserver) {
        Types.ResponseCommit resp = Types.ResponseCommit.newBuilder()
                .setData(ByteString.copyFrom(appStateManager.getStateAsBytes()))
                .build();
        databaseTransactionManager.commit();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void echo(Types.RequestEcho request, StreamObserver<Types.ResponseEcho> responseObserver) {
        Types.ResponseEcho response = Types.ResponseEcho.newBuilder().setMessage(request.getMessage()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void info(Types.RequestInfo request, StreamObserver<Types.ResponseInfo> responseObserver) {
        Types.ResponseInfo response = Types.ResponseInfo.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush(Types.RequestFlush request, StreamObserver<Types.ResponseFlush> responseObserver) {
        Types.ResponseFlush response = Types.ResponseFlush.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    // TODO - implement snapshot functions
}