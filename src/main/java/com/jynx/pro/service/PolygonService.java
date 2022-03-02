package com.jynx.pro.service;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class PolygonService implements ExchangeService {

    @Setter
    @Value("${polygon.api.key}")
    private String apiKey;

    private static final String API_URL = "https://api.polygon.io/%s";

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
        long from = (time - 120) * 1000;
        long to = (time + 120) * 1000;
        String path = "v2/aggs/ticker/%s/range/1/minute/%s/%s?apiKey=%s";
        String url = String.format(API_URL, String.format(path, symbol, from, to, apiKey));
        HttpResponse<JsonNode> response = null;
        try {
            response = Unirest.get(url).asJson();
            int size = response.getBody().getObject().getInt("resultsCount");
            if(size == 0) {
                throw new JynxProException(ErrorCode.CANNOT_GET_POLYGON_PRICE);
            }
            double price = response.getBody().getObject().getJSONArray("results")
                    .getJSONObject(size-1).getDouble("c");
            return BigDecimal.valueOf(price);
        } catch(Exception e) {
            log.info(response != null ? response.getBody().toString() : e.getMessage());
            throw new JynxProException(ErrorCode.CANNOT_GET_POLYGON_PRICE);
        }
    }
}