package com.jynx.pro.service;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class BinanceService {

    private static final String API_URL = "https://api.binance.com/%s";

    /**
     * Get the price at a specific point in time
     *
     * @param symbol the market ticker
     * @param time the point in time
     *
     * @return the price
     */
    public BigDecimal getPriceAt(
            final String symbol,
            final Long time
    ) {
        Long from = (time - 60) * 1000;
        Long to = (time + 60) * 1000;
        String path = "api/v3/klines?symbol=%s&interval=1m&startTime=%s&endTime=%s&limit=1";
        String url = String.format(API_URL, String.format(path, symbol, from, to));
        try {
            HttpResponse<JsonNode> response = Unirest.get(url).asJson();
            int size = response.getBody().getArray().length();
            if(size == 0) {
                throw new JynxProException(ErrorCode.CANNOT_GET_BINANCE_PRICE);
            }
            double price = response.getBody().getArray().getJSONArray(size-1).getDouble(4);
            return BigDecimal.valueOf(price);
        } catch(Exception e) {
            throw new JynxProException(ErrorCode.CANNOT_GET_BINANCE_PRICE);
        }
    }
}