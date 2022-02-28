package com.jynx.pro.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jynx.pro.blockchain.request.TendermintRequest;
import com.jynx.pro.blockchain.response.TendermintResponse;
import com.jynx.pro.blockchain.response.TransactionResponse;
import com.jynx.pro.blockchain.constant.TendermintTransaction;
import com.jynx.pro.entity.*;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.request.*;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class TendermintClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String GET_TX_BASE_URI = "http://localhost:26657/tx?hash=";
    private static final String TX_BASE_URI = "http://localhost:26657/broadcast_tx_sync?tx=";
    private static final String QUERY_BASE_URI = "http://localhost:26657/abci_query?data=";

    private String buildUrl(
            final String baseUri,
            final TendermintRequest request,
            final TendermintTransaction tendermintTx
    ) {
        try {
            if(request == null) return baseUri;
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

    private <T extends TendermintResponse, S extends TendermintRequest> T processQuery(
            final S request,
            final TendermintTransaction tendermintTx,
            final Class<T> responseType,
            final String errorCode
    ) {
        try {
            HttpResponse<JsonNode> response = Unirest.get(buildUrl(QUERY_BASE_URI, request, tendermintTx)).asJson();
            if(response.getStatus() == 200) {
                JSONObject jsonObject = new JSONObject(response.getBody().toString());
                String encodedData = jsonObject
                        .getJSONObject("result")
                        .getJSONObject("response")
                        .getString("value");
                String jsonData = new String(Base64.getDecoder()
                        .decode(encodedData.getBytes(StandardCharsets.UTF_8)));
                return objectMapper.readValue(jsonData, responseType);
            } else {
                log.error(response.getBody().toString());
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
        throw new JynxProException(errorCode);
    }

    public Object getTransaction(
            final String txHash
    ) {
        try {
            HttpResponse<JsonNode> response = Unirest.get(String.format("%s%s", GET_TX_BASE_URI, txHash)).asJson();
            if(response.getStatus() == 200) {
                log.info(response.getBody().toString());
                return null;
            } else {
                log.info(response.getBody().toString());
                return null;
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private void processTransaction(
            final TendermintTransaction tendermintTx,
            final String errorCode
    ) {
        processTransaction(null, null, tendermintTx, errorCode);
    }

    private <S extends TendermintRequest, T> TransactionResponse<T> processTransaction(
            final S request,
            final Class<T> responseType,
            final TendermintTransaction tendermintTx,
            final String errorCode
    ) {
        try {
            HttpResponse<JsonNode> response = Unirest.get(buildUrl(TX_BASE_URI, request, tendermintTx)).asJson();
            if(response.getStatus() == 200) {
                if(responseType == null) return null;
                JSONObject jsonObject = new JSONObject(response.getBody().toString());
                String hash = jsonObject
                        .getJSONObject("result")
                        .getString("hash");
                String encodedMessage = jsonObject
                        .getJSONObject("result")
                        .getString("log");
                String jsonData = new String(Base64.getDecoder()
                        .decode(encodedMessage.getBytes(StandardCharsets.UTF_8)));
                // TODO - query the result of the transaction here because log will be empty when using async methods
                return new TransactionResponse<T>().setHash(hash)
                        .setItem(objectMapper.readValue(jsonData, responseType));
            } else {
                log.error(response.getBody().toString());
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }
        throw new JynxProException(errorCode);
    }

    public void confirmEthereumEvents() {
        processTransaction(TendermintTransaction.CONFIRM_ETHEREUM_EVENTS, ErrorCode.CONFIRM_ETHEREUM_EVENTS_FAILED);
    }

    public void settleMarkets() {
        processTransaction(TendermintTransaction.SETTLE_MARKETS, ErrorCode.SETTLE_MARKETS_FAILED);
    }

    public void syncProposals() {
        processTransaction(TendermintTransaction.SYNC_PROPOSALS, ErrorCode.SYNC_PROPOSALS_FAILED);
    }

    public TransactionResponse<Withdrawal> createWithdrawal(
            final CreateWithdrawalRequest request
    ) {
        return processTransaction(request, Withdrawal.class,
                TendermintTransaction.CREATE_WITHDRAWAL, ErrorCode.CREATE_WITHDRAWAL_FAILED);
    }

    public TransactionResponse<Withdrawal> cancelWithdrawal(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Withdrawal.class,
                TendermintTransaction.CANCEL_WITHDRAWAL, ErrorCode.CANCEL_WITHDRAWAL_FAILED);
    }

    public TransactionResponse<Order> createOrder(
            final CreateOrderRequest request
    ) {
        return processTransaction(request, Order.class,
                TendermintTransaction.CREATE_ORDER, ErrorCode.CREATE_ORDER_FAILED);
    }

    public TransactionResponse<Order> amendOrder(
            final AmendOrderRequest request
    ) {
        return processTransaction(request, Order.class,
                TendermintTransaction.AMEND_ORDER, ErrorCode.AMEND_ORDER_FAILED);
    }

    public TransactionResponse<Order> cancelOrder(
            final CancelOrderRequest request
    ) {
        return processTransaction(request, Order.class,
                TendermintTransaction.CANCEL_ORDER, ErrorCode.CANCEL_ORDER_FAILED);
    }

    public TransactionResponse<Market> addMarket(
            final AddMarketRequest request
    ) {
        return processTransaction(request, Market.class,
                TendermintTransaction.ADD_MARKET, ErrorCode.ADD_MARKET_FAILED);
    }

    public TransactionResponse<Market> amendMarket(
            final AmendMarketRequest request
    ) {
        return processTransaction(request, Market.class,
                TendermintTransaction.AMEND_MARKET, ErrorCode.AMEND_MARKET_FAILED);
    }

    public TransactionResponse<Market> suspendMarket(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Market.class,
                TendermintTransaction.SUSPEND_MARKET, ErrorCode.SUSPEND_MARKET_FAILED);
    }

    public TransactionResponse<Market> unsuspendMarket(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Market.class,
                TendermintTransaction.UNSUSPEND_MARKET, ErrorCode.UNSUSPEND_MARKET_FAILED);
    }

    public TransactionResponse<Asset> addAsset(
            final AddAssetRequest request
    ) {
        return processTransaction(request, Asset.class,
                TendermintTransaction.ADD_ASSET, ErrorCode.ADD_ASSET_FAILED);
    }

    public TransactionResponse<Asset> suspendAsset(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Asset.class,
                TendermintTransaction.SUSPEND_ASSET, ErrorCode.SUSPEND_ASSET_FAILED);
    }

    public TransactionResponse<Asset> unsuspendAsset(
            final SingleItemRequest request
    ) {
        return processTransaction(request, Asset.class,
                TendermintTransaction.UNSUSPEND_ASSET, ErrorCode.UNSUSPEND_ASSET_FAILED);
    }

    public TransactionResponse<Vote> castVote(
            final CastVoteRequest request
    ) {
        return processTransaction(request, Vote.class,
                TendermintTransaction.CAST_VOTE, ErrorCode.CAST_VOTE_FAILED);
    }
}