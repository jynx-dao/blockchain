package com.jynx.pro.blockchain;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.jynx.pro.constant.TendermintTransaction;
import com.jynx.pro.entity.BridgeUpdate;
import com.jynx.pro.entity.Snapshot;
import com.jynx.pro.entity.Validator;
import com.jynx.pro.entity.WithdrawalBatch;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.manager.AppStateManager;
import com.jynx.pro.manager.DatabaseTransactionManager;
import com.jynx.pro.model.CheckTxResult;
import com.jynx.pro.model.TransactionConfig;
import com.jynx.pro.repository.ReadOnlyRepository;
import com.jynx.pro.request.*;
import com.jynx.pro.service.*;
import com.jynx.pro.utils.CryptoUtils;
import com.jynx.pro.utils.JSONUtils;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tendermint.abci.ABCIApplicationGrpc;
import tendermint.abci.Types;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
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
    private SnapshotService snapshotService;
    @Autowired
    private AppStateManager appStateManager;
    @Autowired
    private DatabaseTransactionManager databaseTransactionManager;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;
    @Autowired
    private JSONUtils jsonUtils;
    @Autowired
    private CryptoUtils cryptoUtils;
    @Getter
    @Setter
    @Value("${validator.address}")
    private String validatorAddress;
    @Getter
    @Setter
    @Value("${validator.private.key}")
    private String validatorPrivateKey;
    @Getter
    @Setter
    @Value("${validator.public.key}")
    private String validatorPublicKey;
    @Value("${enable.snapshots}")
    private Boolean enableSnapshots;

    private static final Set<String> nonceHistory = new HashSet<>();
    private static final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final Map<TendermintTransaction, TransactionConfig> transactionSettings = new HashMap<>();

    /**
     * Initializes the settings for protected transactions, which can only be executed by an active validator
     */
    private void initializeProtectedTransactionSettings() {
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
        transactionSettings.put(TendermintTransaction.EXECUTE_BRIDGE_UPDATES,
                new TransactionConfig<ExecuteBridgeUpdatesRequest>()
                        .setDeliverFn(bridgeUpdateService::executeBridgeUpdates)
                        .setProtectedFn(true)
                        .setRequestType(ExecuteBridgeUpdatesRequest.class));
        transactionSettings.put(TendermintTransaction.DISTRIBUTE_REWARDS,
                new TransactionConfig<BatchValidatorRequest>()
                        .setDeliverFn(accountService::distributeRewards)
                        .setProtectedFn(true)
                        .setRequestType(BatchValidatorRequest.class));
    }

    /**
     * Initializes the transaction settings for public functions, which can be executed by anybody
     */
    private void initializedPublicTransactionSettings() {
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
        transactionSettings.put(TendermintTransaction.ADD_DELEGATION,
                new TransactionConfig<UpdateDelegationRequest>()
                        .setDeliverFn(validatorService::addDelegation)
                        .setProtectedFn(false)
                        .setRequestType(UpdateDelegationRequest.class));
        transactionSettings.put(TendermintTransaction.REMOVE_DELEGATION,
                new TransactionConfig<UpdateDelegationRequest>()
                        .setDeliverFn(validatorService::removeDelegation)
                        .setProtectedFn(false)
                        .setRequestType(UpdateDelegationRequest.class));
        transactionSettings.put(TendermintTransaction.VALIDATOR_APPLICATION,
                new TransactionConfig<ValidatorApplicationRequest>()
                        .setDeliverFn(validatorService::apply)
                        .setProtectedFn(false)
                        .setRequestType(ValidatorApplicationRequest.class));
        transactionSettings.put(TendermintTransaction.VALIDATOR_RESIGNATION,
                new TransactionConfig<SingleItemRequest>()
                        .setDeliverFn(validatorService::resign)
                        .setProtectedFn(false)
                        .setRequestType(SingleItemRequest.class));
    }

    /**
     * Initialize transaction settings
     */
    private void initializeSettings() {
        initializedPublicTransactionSettings();
        initializeProtectedTransactionSettings();
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
                    log.info(request.toString());
                    throw new JynxProException(ErrorCode.SIGNATURE_INVALID);
                }
            } catch(Exception e) {
                log.info(request.toString());
                log.error(e.getMessage(), e);
                throw new JynxProException(ErrorCode.SIGNATURE_INVALID);
            }
        }
        request.setSignature(null);
        request.setPublicKey(null);
        String message = jsonUtils.toJson(request);
        boolean result = cryptoUtils.verify(signature, message, publicKey);
        if(!result) {
            log.info(request.toString());
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
            checkDelegationThreshold(tendermintTx);
            return new CheckTxResult().setCode(0);
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            return new CheckTxResult().setCode(1).setError(e.getMessage());
        }
    }

    /**
     * Check if the minimum total delegation has been met
     *
     * @param tendermintTx {@link TendermintTransaction}
     */
    private void checkDelegationThreshold(
            final TendermintTransaction tendermintTx
    ) {
        List<TendermintTransaction> validTransactions = List.of(TendermintTransaction.ADD_DELEGATION,
                TendermintTransaction.REMOVE_DELEGATION);
        if(!validTransactions.contains(tendermintTx)) {
            List<Validator> validators = readOnlyRepository.getAllByEntity(Validator.class);
            BigDecimal totalDelegation = validators.stream().map(Validator::getDelegation)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if(totalDelegation.doubleValue() < configService.getStatic().getMinTotalDelegation().doubleValue()) {
                throw new JynxProException(ErrorCode.INSUFFICIENT_TOTAL_DELEGATION);
            }
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
     * Confirm Ethereum events
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
     * Distribute rewards
     *
     * @param proposerAddress the proposer address
     * @param blockHeight the current block height
     */
    private void distributeRewards(
            final String proposerAddress,
            final long blockHeight
    ) {
        executorService.submit(() -> tendermintClient.distributeRewards(
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
                    .setBridgeNonce(ethereumService.getNonce().toString());
            request.setNonce(ethereumService.getNonce().toString());
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
                    BigDecimal balance = ethereumService.getBalance(ethereumService.getAddress());
                    BigInteger gasPrice = ethereumService.getGasPrice().divide(BigInteger.valueOf(1000000000));
                    if(balance.doubleValue() >= configService.getStatic().getValidatorMinEthBalance().doubleValue() &&
                            gasPrice.intValue() < configService.getStatic().getEthMaxGasPrice()) {
                        tendermintClient.debitWithdrawals(request);
                        withdrawalService.withdrawSignedBatches(batches);
                    }
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
     * Execute bridge updates
     */
    private void executeBridgeUpdates() {
        executorService.submit(() -> {
            try {
                List<BridgeUpdate> updates = bridgeUpdateService.getUnprocessedBridgeUpdates();
                if (updates.size() > 0) {
                    List<UUID> ids = updates.stream().map(BridgeUpdate::getId).collect(Collectors.toList());
                    ExecuteBridgeUpdatesRequest request = new ExecuteBridgeUpdatesRequest().setUpdateIds(ids);
                    ethereumService.addSignatureToRequest(request);
                    BigDecimal balance = ethereumService.getBalance(ethereumService.getAddress());
                    BigInteger gasPrice = ethereumService.getGasPrice().divide(BigInteger.valueOf(1000000000));
                    if(balance.doubleValue() >= configService.getStatic().getValidatorMinEthBalance().doubleValue() &&
                            gasPrice.intValue() < configService.getStatic().getEthMaxGasPrice()) {
                        tendermintClient.executeBridgeUpdates(request);
                        bridgeUpdateService.processSignedUpdates(updates);
                    }
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
        // TODO - the frequency of each task should be defined independently
        confirmEthereumEvents(proposerAddress, blockHeight);
        settleMarkets(proposerAddress, blockHeight);
        syncProposals(proposerAddress, blockHeight);
        batchWithdrawals();
        monitorAuctions(proposerAddress, blockHeight);
        signWithdrawals();
        withdrawSignedBatches();
        signBridgeUpdates();
        executeBridgeUpdates();
        distributeRewards(proposerAddress, blockHeight);
        // TODO - add a task to build the candlestick data
        // TODO - add a task to archive 'old' data
    }

    /**
     * Get the ETH address of a validator from Genesis
     *
     * @param validators {@link JSONArray} of validator config
     *
     * @return the ETH address
     */
    private String getEthAddressFromGenesis(
            final JSONArray validators,
            final String publicKey
    ) {
        String ethAddress = null;
        for(int i=0; i<validators.length(); i++) {
            JSONObject obj1 = validators.optJSONObject(i);
            if(obj1 != null) {
                JSONObject obj2 = obj1.optJSONObject(publicKey);
                if(obj2 != null) {
                    ethAddress = obj2.optString("ethAddress");
                }
            }
        }
        return ethAddress;
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
        JSONArray validators = new JSONArray();
        try {
            JSONObject appStateAsJson = new JSONObject(appState);
            validators = appStateAsJson.getJSONArray("validators");
            configService.initializeNetworkConfig(appStateAsJson);
            snapshotService.initializeSnapshots();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.error(ErrorCode.INVALID_APP_STATE);
        }
        final JSONArray finalValidators = validators;
        request.getValidatorsList().forEach(v -> {
            String publicKey = Base64.getEncoder().encodeToString(
                    request.getValidatorsList().get(0).getPubKey().getEd25519().toByteArray());
            String address = validatorService.getTendermintAddress(publicKey);
            String ethAddress = getEthAddressFromGenesis(finalValidators, publicKey);
            validatorService.addFromGenesis(publicKey, address, ethAddress);
        });
        databaseTransactionManager.commit();
        ethereumService.initializeFilters();
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
        validatorService.updateValidatorPerformance(req.getByzantineValidatorsList(),
                req.getLastCommitInfo().getVotesList(), req.getHeader().getHeight()-1);
        configService.setTimestamp(getBlockTimeAsMillis(req.getHeader().getTime()));
        String proposerAddress = Hex.encodeHexString(req.getHeader().getProposerAddress().toByteArray());
        long blockHeight = req.getHeader().getHeight();
        if(validatorAddress.toLowerCase(Locale.ROOT).equals(proposerAddress.toLowerCase(Locale.ROOT)) &&
                blockHeight % configService.getStatic().getAsyncTaskFrequency() == 0 && blockHeight > 1) {
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
        Types.ResponseEndBlock.Builder builder = Types.ResponseEndBlock.newBuilder();
        appStateManager.setBlockHeight(req.getHeight());
        validatorService.updateValidators(req.getHeight(), builder);
        responseObserver.onNext(builder.build());
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
        long blockHeight = appStateManager.getBlockHeight();
        if(blockHeight % configService.getStatic().getSnapshotFrequency() == 0 && enableSnapshots) {
            snapshotService.capture(blockHeight);
        }
        databaseTransactionManager.commit();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void echo(Types.RequestEcho req, StreamObserver<Types.ResponseEcho> responseObserver) {
        Types.ResponseEcho resp = Types.ResponseEcho.newBuilder().setMessage(req.getMessage()).build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void info(Types.RequestInfo req, StreamObserver<Types.ResponseInfo> responseObserver) {
        Types.ResponseInfo resp = Types.ResponseInfo.newBuilder().build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush(Types.RequestFlush req, StreamObserver<Types.ResponseFlush> responseObserver) {
        Types.ResponseFlush resp = Types.ResponseFlush.newBuilder().build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void listSnapshots(Types.RequestListSnapshots req,
                              StreamObserver<Types.ResponseListSnapshots> responseObserver) {
        List<Snapshot> snapshots = snapshotService.getLatestSnapshots(10L);
        Types.ResponseListSnapshots resp = Types.ResponseListSnapshots.newBuilder()
                .addAllSnapshots(snapshots.stream().map(s -> Types.Snapshot.newBuilder()
                        .setChunks(s.getTotalChunks())
                        .setHeight(s.getBlockHeight())
                        .setFormat(s.getFormat())
                        .setHash(ByteString.copyFromUtf8(s.getHash()))
                        .setMetadata(ByteString.copyFromUtf8(s.getHash()))
                        .build()).collect(Collectors.toList()))
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void offerSnapshot(Types.RequestOfferSnapshot req,
                              StreamObserver<Types.ResponseOfferSnapshot> responseObserver) {
        snapshotService.clearState();
        appStateManager.setBlockHeight(req.getSnapshot().getHeight());
        appStateManager.setAppState(new BigInteger(req.getAppHash().toByteArray()).intValue());
        Types.ResponseOfferSnapshot response = Types.ResponseOfferSnapshot.newBuilder()
                .setResult(Types.ResponseOfferSnapshot.Result.ACCEPT)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadSnapshotChunk(Types.RequestLoadSnapshotChunk req,
                                  StreamObserver<Types.ResponseLoadSnapshotChunk> responseObserver) {
        Types.ResponseLoadSnapshotChunk.Builder builder = Types.ResponseLoadSnapshotChunk.newBuilder();
        Optional<String> chunkContent = snapshotService.getChunkContent(req.getHeight(), req.getChunk());
        chunkContent.ifPresent(s -> builder.setChunk(ByteString.copyFromUtf8(s)));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applySnapshotChunk(Types.RequestApplySnapshotChunk req,
                                   StreamObserver<Types.ResponseApplySnapshotChunk> responseObserver) {
        snapshotService.saveChunk(req.getChunk().toStringUtf8());
        Types.ResponseApplySnapshotChunk resp = Types.ResponseApplySnapshotChunk.newBuilder()
                .setResult(Types.ResponseApplySnapshotChunk.Result.ACCEPT)
                .build();
        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }
}