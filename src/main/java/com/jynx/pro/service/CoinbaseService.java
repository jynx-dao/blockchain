package com.jynx.pro.service;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class CoinbaseService implements ExchangeService {

    private static final String API_URL = "https://api.exchange.coinbase.com/%s";

    /**
     * Get the price at a specific point in time
     *
     * @param symbol the market ticker
     * @param time the point in time
     *
     * @return the price
     */
    @Override
    public BigDecimal getPriceAt(
            final String symbol,
            final Long time
    ) {
        String fromStr = LocalDateTime.ofEpochSecond(time - 600, 0, ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_DATE_TIME);
        String toStr = LocalDateTime.ofEpochSecond(time + 600, 0, ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_DATE_TIME);
        String path = "products/%s/candles?start=%s&end=%s&granularity=60";
        String url = String.format(API_URL, String.format(path, symbol,
                URLEncoder.encode(fromStr, StandardCharsets.UTF_8),
                URLEncoder.encode(toStr, StandardCharsets.UTF_8)));
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(url).asJson();
            int size = response.getBody().getArray().length();
            if(size == 0) {
                throw new JynxProException(ErrorCode.CANNOT_GET_COINBASE_PRICE);
            }
            double price = response.getBody().getArray().getJSONArray(0).getDouble(4);
            return BigDecimal.valueOf(price);
        } catch(Exception e) {
            log.info(response != null ? response.getBody().toString() : e.getMessage());
            throw new JynxProException(ErrorCode.CANNOT_GET_COINBASE_PRICE);
        }
    }
}