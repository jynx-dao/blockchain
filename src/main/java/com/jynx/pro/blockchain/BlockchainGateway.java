package com.jynx.pro.blockchain;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.jynx.pro.constant.TendermintTransaction;
import com.jynx.pro.entity.WithdrawalBatch;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.manager.AppStateManager;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.model.CheckTxResult;
import com.jynx.pro.model.TransactionConfig;
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
import java.util.stream.Collectors;

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
    private StakeService stakeService;
    @Autowired
    private ValidatorService validatorService;
    @Autowired
    private WithdrawalService withdrawalService;
    @Autowired
    private BridgeUpdateService bridgeUpdateService;
    @Autowired
    private AuctionService auctionService;
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
    private static final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final Map<TendermintTransaction, TransactionConfig> transactionSettings = new HashMap<>();

    /**
     * Initialize transaction settings
     */
    private void initializeSettings() {
        transactionSettings.put(TendermintTransaction.CREATE_ORDER,
                new TransactionConfig<CreateOrderRequest>()
                        .setDeliverFn(orderService::create)
                        .setProtectedFn(false)
                        .setRequestType(CreateOrderRequest.class));
        transactionSettings.put(TendermintTransaction.CANCEL_ORDER,
                new TransactionConfig<CancelOrderRequest>()
                        .setDeliverFn(orderService::cancel)
                        .setProtectedFn(false)
                        .setRequestType(CancelOrderRequest.class));
        transactionSettings.put(TendermintTransaction.AMEND_ORDER,
                new TransactionConfig<AmendOrderRequest>()
                        .setDeliverFn(orderService::amend)
                        .setProtectedFn(false)
                        .setRequestType(AmendOrderRequest.class));
        transactionSettings.put(TendermintTransaction.CREATE_ORDER_MANY,
                new TransactionConfig<BulkCreateOrderRequest>()
                        .setDeliverFn(orderService::createMany)
                        .setProtectedFn(false)
                        .setRequestType(BulkCreateOrderRequest.class));
        transactionSettings.put(TendermintTransaction.CANCEL_ORDER_MANY,
                new TransactionConfig<BulkCancelOrderRequest>()
                        .setDeliverFn(orderService::cancelMany)
                        .setProtectedFn(false)
                        .setRequestType(BulkCancelOrderRequest.class));
        transactionSettings.put(TendermintTransaction.AMEND_ORDER_MANY,
                new TransactionConfig<BulkAmendOrderRequest>()
                        .setDeliverFn(orderService::amendMany)
                        .setProtectedFn(false)
                        .setRequestType(BulkAmendOrderRequest.class));
        transactionSettings.put(TendermintTransaction.CREATE_WITHDRAWAL,
                new TransactionConfig<CreateWithdrawalRequest>()
                        .setDeliverFn(accountService::createWithdrawal)
                        .setProtectedFn(false)
                        .setRequestType(CreateWithdrawalRequest.class));
        transactionSettings.put(TendermintTransaction.CANCEL_WITHDRAWAL,
                new TransactionConfig<SingleItemRequest>()
                        .setDeliverFn(accountService::cancelWithdrawal)
                        .setProtectedFn(false)
                        .setRequestType(SingleItemRequest.class));
        transactionSettings.put(TendermintTransaction.ADD_MARKET,
                new TransactionConfig<AddMarketRequest>()
                        .setDeliverFn(marketService::proposeToAdd)
                        .setProtectedFn(false)
                        .setRequestType(AddMarketRequest.class));
        transactionSettings.put(TendermintTransaction.AMEND_MARKET,
                new TransactionConfig<AmendMarketRequest>()
                        .setDeliverFn(marketService::proposeToAmend)
                        .setProtectedFn(false)
                        .setRequestType(AmendMarketRequest.class));
        transactionSettings.put(TendermintTransaction.SUSPEND_MARKET,
                new TransactionConfig<SingleItemRequest>()
                        .setDeliverFn(marketService::proposeToSuspend)
                        .setProtectedFn(false)
                        .setRequestType(SingleItemRequest.class));
        transactionSettings.put(TendermintTransaction.UNSUSPEND_MARKET,
                new TransactionConfig<SingleItemRequest>()
                        .setDeliverFn(marketService::proposeToUnsuspend)
                        .setProtectedFn(false)
                        .setRequestType(SingleItemRequest.class));
        transactionSettings.put(TendermintTransaction.ADD_ASSET,
                new TransactionConfig<AddAssetRequest>()
                        .setDeliverFn(assetService::proposeToAdd)
                        .setProtectedFn(false)
                        .setRequestType(AddAssetRequest.class));
        transactionSettings.put(TendermintTransaction.SUSPEND_ASSET,
                new TransactionConfig<SingleItemRequest>()
                        .setDeliverFn(assetService::proposeToSuspend)
                        .setProtectedFn(false)
                        .setRequestType(SingleItemRequest.class));
        transactionSettings.put(TendermintTransaction.UNSUSPEND_ASSET,
                new TransactionConfig<SingleItemRequest>()
                        .setDeliverFn(assetService::proposeToUnsuspend)
                        .setProtectedFn(false)
                        .setRequestType(SingleItemRequest.class));
        transactionSettings.put(TendermintTransaction.CAST_VOTE,
                new TransactionConfig<CastVoteRequest>()
                        .setDeliverFn(proposalService::vote)
                        .setProtectedFn(false)
                        .setRequestType(CastVoteRequest.class));
        transactionSettings.put(TendermintTransaction.CONFIRM_ETHEREUM_EVENTS,
                new TransactionConfig<BatchValidatorRequest>()
                        .setDeliverFn(ethereumService::confirmEvents)
                        .setProtectedFn(true)
                        .setRequestType(BatchValidatorRequest.class));
        transactionSettings.put(TendermintTransaction.SYNC_PROPOSALS,
                new TransactionConfig<BatchValidatorRequest>()
                        .setDeliverFn(proposalService::sync)
                        .setProtectedFn(true)
                        .setRequestType(BatchValidatorRequest.class));
        transactionSettings.put(TendermintTransaction.SETTLE_MARKETS,
                new TransactionConfig<BatchValidatorRequest>()
                        .setDeliverFn(marketService::settleMarkets)
                        .setProtectedFn(true)
                        .setRequestType(BatchValidatorRequest.class));
        transactionSettings.put(TendermintTransaction.BATCH_WITHDRAWALS,
                new TransactionConfig<BatchWithdrawalRequest>()
                        .setDeliverFn(withdrawalService::batchWithdrawals)
                        .setProtectedFn(true)
                        .setRequestType(BatchWithdrawalRequest.class));
        transactionSettings.put(TendermintTransaction.SIGN_WITHDRAWAL_BATCHES,
                new TransactionConfig<BulkSignWithdrawalRequest>()
                        .setDeliverFn(withdrawalService::saveWithdrawalBatchSignatures)
                        .setProtectedFn(true)
                        .setRequestType(BulkSignWithdrawalRequest.class));
        transactionSettings.put(TendermintTransaction.DEBIT_WITHDRAWALS,
                new TransactionConfig<DebitWithdrawalsRequest>()
                        .setDeliverFn(withdrawalService::debitWithdrawals)
                        .setProtectedFn(true)
                        .setRequestType(DebitWithdrawalsRequest.class));
        transactionSettings.put(TendermintTransaction.ADD_STAKE,
                new TransactionConfig<UpdateStakeRequest>()
                        .setDeliverFn(stakeService::add)
                        .setProtectedFn(true)
                        .setRequestType(UpdateStakeRequest.class));
        transactionSettings.put(TendermintTransaction.REMOVE_STAKE,
                new TransactionConfig<UpdateStakeRequest>()
                        .setDeliverFn(stakeService::remove)
                        .setProtectedFn(true)
                        .setRequestType(UpdateStakeRequest.class));
        transactionSettings.put(TendermintTransaction.DEPOSIT_ASSET,
                new TransactionConfig<DepositAssetRequest>()
                        .setDeliverFn(accountService::deposit)
                        .setProtectedFn(true)
                        .setRequestType(DepositAssetRequest.class));
        transactionSettings.put(TendermintTransaction.MONITOR_AUCTIONS,
                new TransactionConfig<BatchValidatorRequest>()
                        .setDeliverFn(auctionService::monitorAuctions)
                        .setProtectedFn(true)
                        .setRequestType(BatchValidatorRequest.class));
        transactionSettings.put(TendermintTransaction.SIGN_BRIDGE_UPDATES,
                new TransactionConfig<BulkSignBridgeUpdateRequest>()
                        .setDeliverFn(bridgeUpdateService::saveBridgeUpdateSignatures)
                        .setProtectedFn(true)
                        .setRequestType(BulkSignBridgeUpdateRequest.class));
    }

    /**
     * Setup class-level config immediately after construction of Spring Bean
     */
    @PostConstruct
    private void setup() {
        initializeSettings();
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
     * Generic function to handle the delivery of transactions. This function implements the following:
     * 1. Replay protection
     * 2. Updating the app state
     * 3. Propagate write transactions to application
     *
     * @param tx the raw base64 transaction
     * @param tendermintTx {@link TendermintTransaction}
     *
     * @return the response from the application
     */
    private Object deliverTransaction(
            final String tx,
            final TendermintTransaction tendermintTx
    ) {
        String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
        try {
            SignedRequest request = (SignedRequest) jsonUtils.fromJson(txAsJson,
                    transactionSettings.get(tendermintTx).getRequestType());
            nonceHistory.add(request.getNonce());
            request.setUser(userService.getAndCreate(request.getPublicKey()));
            Object result = transactionSettings.get(tendermintTx).getDeliverFn().apply(request);
            appStateManager.update(result.hashCode());
            return result;
        } catch(Exception e) {
            log.info(e.getMessage(), e);
            appStateManager.update(e.getMessage() != null ? e.getMessage().hashCode() : 1);
            throw new JynxProException(e.getMessage());
        }
    }

    /**
     * Generic function to handle the checkTx step. This function implements the following:
     * 1. Signature verification
     * 2. Replay protection
     *
     * @param tx the raw base64 transaction
     * @param tendermintTx {@link TendermintTransaction}
     *
     * @return {@link CheckTxResult}
     */
    private CheckTxResult checkTransaction(
            final String tx,
            final TendermintTransaction tendermintTx
    ) {
        String txAsJson = new String(Base64.getDecoder().decode(tx.getBytes(StandardCharsets.UTF_8)));
        try {
            SignedRequest request = (SignedRequest) jsonUtils.fromJson(txAsJson,
                    transactionSettings.get(tendermintTx).getRequestType());
            verifySignature(request, transactionSettings.get(tendermintTx).isProtectedFn());
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
     * Confrirm Ethereum events
     *
     * @param proposerAddress the proposer address
     * @param blockHeight the current block height
     */
    private void confirmEthereumEvents(
            final String proposerAddress,
            final long blockHeight
    ) {
        executorService.submit(() -> tendermintClient.confirmEthereumEvents(
                getBatchRequest(proposerAddress, blockHeight)));
    }

    /**
     * Settle markets
     *
     * @param proposerAddress the proposer address
     * @param blockHeight the current block height
     */
    private void settleMarkets(
            final String proposerAddress,
            final long blockHeight
    ) {
        executorService.submit(() -> tendermintClient.settleMarkets(
                getBatchRequest(proposerAddress, blockHeight)));
    }

    /**
     * Sync proposals
     *
     * @param proposerAddress the proposer address
     * @param blockHeight the current block height
     */
    private void syncProposals(
            final String proposerAddress,
            final long blockHeight
    ) {
        executorService.submit(() -> tendermintClient.syncProposals(
                getBatchRequest(proposerAddress, blockHeight)));
    }

    /**
     * Batch withdrawals
     */
    private void batchWithdrawals() {
        executorService.submit(() -> {
            BatchWithdrawalRequest request = new BatchWithdrawalRequest()
                    .setNonce(ethereumService.getNonce().toString());
            ethereumService.addSignatureToRequest(request);
            tendermintClient.batchWithdrawals(request);
        });
    }

    /**
     * Monitor auctions
     *
     * @param proposerAddress the proposer address
     * @param blockHeight the current block height
     */
    private void monitorAuctions(
            final String proposerAddress,
            final long blockHeight
    ) {
        executorService.submit(() -> tendermintClient.monitorAuctions(
                getBatchRequest(proposerAddress, blockHeight)));
    }

    /**
     * Sign withdrawals
     */
    private void signWithdrawals() {
        executorService.submit(() -> {
            try {
                BulkSignWithdrawalRequest request = withdrawalService.signBatches(validatorPublicKey);
                ethereumService.addSignatureToRequest(request);
                if(request.getSignatures().size() > 0) {
                    tendermintClient.signWithdrawals(request);
                }
            } catch(Exception e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Withdraw signed batches
     */
    private void withdrawSignedBatches() {
        executorService.submit(() -> {
            try {
                List<WithdrawalBatch> batches = withdrawalService.getUnprocessedWithdrawalBatches();
                if (batches.size() > 0) {
                    List<UUID> ids = batches.stream().map(WithdrawalBatch::getId).collect(Collectors.toList());
                    DebitWithdrawalsRequest request = new DebitWithdrawalsRequest().setBatchIds(ids);
                    ethereumService.addSignatureToRequest(request);
                    // TODO - we should check that the node has sufficient gas before broadcasting the Tendermint tx
                    tendermintClient.debitWithdrawals(request);
                    // TODO - how do we undo the previous Tendermint transactions if the node has insufficient gas??
                    withdrawalService.withdrawSignedBatches(batches);
                }
            } catch(Exception e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Sign bridge updates
     */
    private void signBridgeUpdates() {
        executorService.submit(() -> {
            try {
                BulkSignBridgeUpdateRequest request = bridgeUpdateService.signUpdates(validatorPublicKey);
                ethereumService.addSignatureToRequest(request);
                if(request.getSignatures().size() > 0) {
                    tendermintClient.signBridgeUpdates(request);
                }
            } catch(Exception e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Handles off-chain asynchronous actions
     *
     * @param proposerAddress the proposer's address
     * @param blockHeight the current block height
     */
    private void handleProposerActions(
            final String proposerAddress,
            final long blockHeight
    ) {
        confirmEthereumEvents(proposerAddress, blockHeight);
        settleMarkets(proposerAddress, blockHeight);
        syncProposals(proposerAddress, blockHeight);
        batchWithdrawals();
        monitorAuctions(proposerAddress, blockHeight);
        signWithdrawals();
        withdrawSignedBatches();
        signBridgeUpdates();
        executorService.submit(() -> {
            // TODO - add / remove asset when sufficient signatures have been provided
            // (e.g. this is the equivalent of withdrawalService.withdrawSignedBatches(batches)
        });
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
            handleProposerActions(proposerAddress, blockHeight);
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