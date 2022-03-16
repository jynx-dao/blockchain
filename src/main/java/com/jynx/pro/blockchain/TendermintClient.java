package com.jynx.pro.blockchain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jynx.pro.constant.TendermintTransaction;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.*;
import com.jynx.pro.response.TransactionResponse;
import com.jynx.pro.utils.SleepUtils;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Component
public class TendermintClient {

    @Setter
    @Value("${tendermint.base.uri}")
    private String baseUri;
    @Setter
    @Value("${tendermint.port}")
    private Integer port;
    @Autowired
    private SleepUtils sleepUtils;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String GET_TX_BASE_URI = "/tx?hash=";
    private static final String TX_BASE_URI = "/broadcast_tx_sync?tx=";

    /**
     * Build Tendermint URL for delivering a transaction with base64 payload
     *
     * @param baseUri the base URI
     * @param request the request object
     * @param tendermintTx the {@link TendermintTransaction}
     * @param <S> the request object type
     *
     * @return the constructed URL with base64 payload
     */
    private <S> String buildUrl(
            final String baseUri,
            final S request,
            final TendermintTransaction tendermintTx
    ) {
        try {
            String data = objectMapper.writeValueAsString(request);
            JSONObject jsonObject = new JSONObject(data);
            jsonObject.put("tendermintTx", tendermintTx.name());
            String encodedData = Base64.getEncoder().encodeToString(jsonObject.toString()
                    .getBytes(StandardCharsets.UTF_8));
            return baseUri + "%22" + encodedData + "%22";
        } catch(Exception e) {
            log.error(ErrorCode.FAILED_TO_BUILD_URL, e);
            throw new JynxProException(ErrorCode.FAILED_TO_BUILD_URL);
        }
    }

    /**
     * Fetch a transaction from Tendermint using its hash
     *
     * @param txHash the transaction hash
     * @param responseType the response object type
     * @param <T> the response object type
     *
     * @return the response object
     */
    public <T> Optional<T> getTransaction(
            final String txHash,
            final Class<T> responseType
    ) {
        String logMessage;
        try {
            HttpResponse<JsonNode> response = Unirest.get(String.format("%s:%s%s%s",
                    baseUri, port, GET_TX_BASE_URI, String.format("0x%s", txHash))).asJson();
            if (!response.getBody().getObject().has("result")) return Optional.empty();
            logMessage = response.getBody().getObject().getJSONObject("result")
                    .getJSONObject("tx_result").getString("log");
            log.debug(logMessage);
        } catch(Exception e) {
            log.debug(e.getMessage(), e);
            throw new JynxProException("Could not get transaction result.");
        }
        try {
            return Optional.of(objectMapper.readValue(logMessage, responseType));
        } catch(JsonProcessingException e) {
            log.debug(e.getMessage());
            throw new JynxProException(logMessage);
        }
    }

    /**
     * Send an asynchronous transaction to Tendermint and blocks execution until the transaction has been
     * included in a new block
     *
     * @param request the request object type
     * @param responseType the response object type
     * @param tendermintTx the {@link TendermintTransaction}
     * @param errorCode the error message to use if something goes wrong
     * @param <S> the request object type
     * @param <T> the response object type
     *
     * @return {@link TransactionResponse<T>}
     */
    private <S, T> TransactionResponse<T> processTransaction(
            final S request,
            final Class<T> responseType,
            final TendermintTransaction tendermintTx,
            final String errorCode
    ) {
        try {
            HttpResponse<JsonNode> response = Unirest.get(buildUrl(
                    String.format("%s:%s%s", baseUri, port, TX_BASE_URI), request, tendermintTx)).asJson();
            if (response.getStatus() == 200) {
                JSONObject jsonObject;
                String hash;
                try {
                    jsonObject = new JSONObject(response.getBody().toString());
                    hash = jsonObject
                            .getJSONObject("result")
                            .getString("hash");
                } catch (Exception e) {
                    log.debug(response.getBody().toString());
                    throw new JynxProException(e.getMessage());
                }
                Optional<T> resultOptional = Optional.empty();
                for(int i=0; i<10; i++) {
                    resultOptional = getTransaction(hash, responseType);
                    if(resultOptional.isPresent()) break;
                    sleepUtils.sleep(200L);
                }
                return new TransactionResponse<T>().setHash(hash)
                        .setItem(resultOptional.orElseThrow(() -> new JynxProException(errorCode)));
            } else {
                log.debug(response.getBody().toString());
                throw new JynxProException(errorCode);
            }
        } catch(UnirestException e) {
            log.debug(e.getMessage(), e);
            throw new JynxProException(errorCode);
        }
    }

    /**
     * Monitor market auctions
     *
     * @param request {@link BatchValidatorRequest}
     */
    public void monitorAuctions(
            final BatchValidatorRequest request
    ) {
        processTransaction(request, Object.class,
                TendermintTransaction.MONITOR_AUCTIONS, ErrorCode.MONITOR_AUCTIONS_FAILED);
    }

    /**
     * Confirm Ethereum events
     *
     * @param request {@link BatchValidatorRequest}
     */
    public void confirmEthereumEvents(
            final BatchValidatorRequest request
    ) {
        processTransaction(request, Object.class,
                TendermintTransaction.CONFIRM_ETHEREUM_EVENTS, ErrorCode.CONFIRM_ETHEREUM_EVENTS_FAILED);
    }

    /**
     * Settle markets
     *
     * @param request {@link BatchValidatorRequest}
     */
    public void settleMarkets(
            final BatchValidatorRequest request
    ) {
        processTransaction(request, Object.class,
                TendermintTransaction.SETTLE_MARKETS, ErrorCode.SETTLE_MARKETS_FAILED);
    }

    /**
     * Sync proposals
     *
     * @param request {@link BatchValidatorRequest}
     */
    public void syncProposals(
            final BatchValidatorRequest request
    ) {
        processTransaction(request, Object.class,
                TendermintTransaction.SYNC_PROPOSALS, ErrorCode.SYNC_PROPOSALS_FAILED);
    }

    /**
     * Batch withdrawals
     *
     * @param request {@link BatchWithdrawalRequest}
     */
    public void batchWithdrawals(
            final BatchWithdrawalRequest request
    ) {
        processTransaction(request, Object.class,
                TendermintTransaction.BATCH_WITHDRAWALS, ErrorCode.BATCH_WITHDRAWALS_FAILED);
    }

    /**
     * Sign withdrawals
     *
     * @param request {@link BulkSignWithdrawalRequest}
     */
    public void signWithdrawals(
            final BulkSignWithdrawalRequest request
    ) {
        processTransaction(request, Object.class,
                TendermintTransaction.SIGN_WITHDRAWAL_BATCHES, ErrorCode.SIGN_WITHDRAWALS_FAILED);
    }

    /**
     * Sign bridge updates
     *
     * @param request {@link BulkSignBridgeUpdateRequest}
     */
    public void signBridgeUpdates(
            final BulkSignBridgeUpdateRequest request
    ) {
        processTransaction(request, Object.class,
                TendermintTransaction.SIGN_BRIDGE_UPDATES, ErrorCode.SIGN_BRIDGE_UPDATED_FAILED);
    }

    /**
     * Debit withdrawals
     *
     * @param request
     */
    public void debitWithdrawals(
            final DebitWithdrawalsRequest request
    ) {
        processTransaction(request, Object.class,
                TendermintTransaction.DEBIT_WITHDRAWALS, ErrorCode.DEBIT_WITHDRAWALS_FAILED);
    }

    /**
     * Add stake
     *
     * @param request {@link UpdateStakeRequest}
     */
    public void addStake(
            final UpdateStakeRequest request
    ) {
        processTransaction(request, Event.class,
                TendermintTransaction.ADD_STAKE, ErrorCode.ADD_STAKE_FAILED);
    }

    /**
     * Remove stake
     *
     * @param request {@link UpdateStakeRequest}
     */
    public void removeStake(
            final UpdateStakeRequest request
    ) {
        processTransaction(request, Event.class,
                TendermintTransaction.REMOVE_STAKE, ErrorCode.REMOVE_STAKE_FAILED);
    }

    /**
     * Deposit asset
     *
     * @param request {@link DepositAssetRequest}
     */
    public void depositAsset(
            final DepositAssetRequest request
    ) {
        processTransaction(request, Event.class,
                TendermintTransaction.DEPOSIT_ASSET, ErrorCode.DEPOSIT_ASSET_FAILED);
    }

    /**
     * Create withdrawal
     *
     * @param request {@link CreateWithdrawalRequest}
     *
     * @return {@link TransactionResponse<Withdrawal>}
     */
    public TransactionResponse<Withdrawal> createWithdrawal(
            final CreateWithdrawalRequest request
    ) {
        return processTransaction(request, Withdrawal.class,
                TendermintTransaction.CREATE_WITHDRAWAL, ErrorCode.CREATE_WITHDRAWAL_FAILED);
    }

    /**
     * Cancel withdrawal
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link TransactionResponse<Withdrawal>}
     */
    public TransactionResponse<Withdrawal> cancelWithdrawal(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Withdrawal.class,
                TendermintTransaction.CANCEL_WITHDRAWAL, ErrorCode.CANCEL_WITHDRAWAL_FAILED);
    }

    /**
     * Create order
     *
     * @param request {@link CreateOrderRequest}
     *
     * @return {@link TransactionResponse<Order>}
     */
    public TransactionResponse<Order> createOrder(
            final CreateOrderRequest request
    ) {
        return processTransaction(request, Order.class,
                TendermintTransaction.CREATE_ORDER, ErrorCode.CREATE_ORDER_FAILED);
    }

    /**
     * Amend order
     *
     * @param request {@link AmendOrderRequest}
     *
     * @return {@link TransactionResponse<Order>}
     */
    public TransactionResponse<Order> amendOrder(
            final AmendOrderRequest request
    ) {
        return processTransaction(request, Order.class,
                TendermintTransaction.AMEND_ORDER, ErrorCode.AMEND_ORDER_FAILED);
    }

    /**
     * Cancel order
     *
     * @param request {@link CancelOrderRequest}
     *
     * @return {@link TransactionResponse<Order>}
     */
    public TransactionResponse<Order> cancelOrder(
            final CancelOrderRequest request
    ) {
        return processTransaction(request, Order.class,
                TendermintTransaction.CANCEL_ORDER, ErrorCode.CANCEL_ORDER_FAILED);
    }

    /**
     * Create many orders
     *
     * @param request {@link BulkCreateOrderRequest}
     *
     * @return {@link TransactionResponse<Order[]>}
     */
    public TransactionResponse<Order[]> createOrderMany(
            final BulkCreateOrderRequest request
    ) {
        return processTransaction(request, Order[].class,
                TendermintTransaction.CREATE_ORDER_MANY, ErrorCode.CREATE_ORDER_FAILED);
    }

    /**
     * Amend many orders
     *
     * @param request {@link BulkAmendOrderRequest}
     *
     * @return {@link TransactionResponse<Order[]>}
     */
    public TransactionResponse<Order[]> amendOrderMany(
            final BulkAmendOrderRequest request
    ) {
        return processTransaction(request, Order[].class,
                TendermintTransaction.AMEND_ORDER_MANY, ErrorCode.AMEND_ORDER_FAILED);
    }

    /**
     * Cancel many orders
     *
     * @param request {@link BulkCancelOrderRequest}
     *
     * @return {@link TransactionResponse<Order[]>}
     */
    public TransactionResponse<Order[]> cancelOrderMany(
            final BulkCancelOrderRequest request
    ) {
        return processTransaction(request, Order[].class,
                TendermintTransaction.CANCEL_ORDER_MANY, ErrorCode.CANCEL_ORDER_FAILED);
    }

    /**
     * Add market
     *
     * @param request {@link AddMarketRequest}
     *
     * @return {@link TransactionResponse<Proposal>}
     */
    public TransactionResponse<Proposal> addMarket(
            final AddMarketRequest request
    ) {
        return processTransaction(request, Proposal.class,
                TendermintTransaction.ADD_MARKET, ErrorCode.ADD_MARKET_FAILED);
    }

    /**
     * Amend market
     *
     * @param request {@link AmendMarketRequest}
     *
     * @return {@link TransactionResponse<Proposal>}
     */
    public TransactionResponse<Proposal> amendMarket(
            final AmendMarketRequest request
    ) {
        return processTransaction(request, Proposal.class,
                TendermintTransaction.AMEND_MARKET, ErrorCode.AMEND_MARKET_FAILED);
    }

    /**
     * Suspend market
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link TransactionResponse<Proposal>}
     */
    public TransactionResponse<Proposal> suspendMarket(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Proposal.class,
                TendermintTransaction.SUSPEND_MARKET, ErrorCode.SUSPEND_MARKET_FAILED);
    }

    /**
     * Unsuspend market
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link TransactionResponse<Proposal>}
     */
    public TransactionResponse<Proposal> unsuspendMarket(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Proposal.class,
                TendermintTransaction.UNSUSPEND_MARKET, ErrorCode.UNSUSPEND_MARKET_FAILED);
    }

    /**
     * Add asset
     *
     * @param request {@link AddAssetRequest}
     *
     * @return {@link TransactionResponse<Proposal>}
     */
    public TransactionResponse<Proposal> addAsset(
            final AddAssetRequest request
    ) {
        return processTransaction(request, Proposal.class,
                TendermintTransaction.ADD_ASSET, ErrorCode.ADD_ASSET_FAILED);
    }

    /**
     * Suspend asset
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link TransactionResponse<Proposal>}
     */
    public TransactionResponse<Proposal> suspendAsset(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Proposal.class,
                TendermintTransaction.SUSPEND_ASSET, ErrorCode.SUSPEND_ASSET_FAILED);
    }

    /**
     * Unsuspend asset
     *
     * @param request {@link SingleItemRequest}
     *
     * @return {@link TransactionResponse<Proposal>}
     */
    public TransactionResponse<Proposal> unsuspendAsset(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Proposal.class,
                TendermintTransaction.UNSUSPEND_ASSET, ErrorCode.UNSUSPEND_ASSET_FAILED);
    }

    /**
     * Cast vote
     *
     * @param request {@link CastVoteRequest}
     *
     * @return {@link TransactionResponse<Vote>}
     */
    public TransactionResponse<Vote> castVote(
            final CastVoteRequest request
    ) {
        return processTransaction(request, Vote.class,
                TendermintTransaction.CAST_VOTE, ErrorCode.CAST_VOTE_FAILED);
    }
}