package com.jynx.pro.controller;

import com.jynx.pro.constant.KlineInterval;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Trade;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.Kline;
import com.jynx.pro.model.MarketStatistics;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.Quote;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/market")
public class MarketController extends AbstractController {

    @GetMapping("/{id}/order-book")
    public ResponseEntity<OrderBook> getOrderBook(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getOrderBookByMarketId(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Market> getById(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getMarketById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND)));
    }

    @GetMapping("/{id}/trades")
    public ResponseEntity<List<Trade>> getTrades(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getTradesByMarketId(id));
    }

    @GetMapping("/{id}/kline")
    public ResponseEntity<List<Kline>> getKline(
            @PathVariable("id") UUID id,
            @RequestParam("from") Long from,
            @RequestParam("to") Long to,
            @RequestParam("interval") KlineInterval interval
    ) {
        return ResponseEntity.ok(readOnlyRepository.getKline(id, from, to, interval));
    }

    @GetMapping("/{id}/quote")
    public ResponseEntity<Quote> getQuote(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getQuoteByMarketId(id));
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<MarketStatistics> getStatistics(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getStatisticsByMarketId(id));
    }
}
