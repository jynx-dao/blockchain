package com.jynx.pro.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jynx.pro.blockchain.request.TendermintRequest;
import com.jynx.pro.blockchain.response.TendermintResponse;
import com.jynx.pro.blockchain.response.TransactionResponse;
import com.jynx.pro.constant.TendermintTransaction;
import com.jynx.pro.entity.Order;
import com.jynx.pro.entity.Withdrawal;
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
import java.util.List;

@Slf4j
@Component
public class TendermintClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TX_BASE_URI = "http://localhost:26657/broadcast_tx_sync?tx=";
    private static final String QUERY_BASE_URI = "http://localhost:26657/abci_query?data=";

    private String buildUrl(
            final String baseUri,
            final TendermintRequest request,
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

    private <S extends TendermintRequest, T> TransactionResponse<T> processTransaction(
            final S request,
            final Class<T> responseType,
            final TendermintTransaction tendermintTx,
            final String errorCode
    ) {
        try {
            HttpResponse<JsonNode> response = Unirest.get(buildUrl(TX_BASE_URI, request, tendermintTx)).asJson();
            if(response.getStatus() == 200) {
                JSONObject jsonObject = new JSONObject(response.getBody().toString());
                String hash = jsonObject
                        .getJSONObject("result")
                        .getString("hash");
                String encodedMessage = jsonObject
                        .getJSONObject("result")
                        .getString("log");
                String jsonData = new String(Base64.getDecoder()
                        .decode(encodedMessage.getBytes(StandardCharsets.UTF_8)));
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
}