package com.jynx.pro.controller;

import com.jynx.pro.constant.KlineInterval;
import com.jynx.pro.constant.OrderBookType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Proposal;
import com.jynx.pro.entity.Trade;
import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.model.Kline;
import com.jynx.pro.model.MarketStatistics;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.Quote;
import com.jynx.pro.request.AddMarketRequest;
import com.jynx.pro.request.AmendMarketRequest;
import com.jynx.pro.request.SingleItemRequest;
import com.jynx.pro.service.OrderBookService;
import com.jynx.pro.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/market")
public class MarketController extends AbstractController {

    @Autowired
    private TradeService tradeService;
    @Autowired
    private OrderBookService orderBookService;

    /**
     * Get market by ID
     *
     * @param id the market ID
     *
     * @return {@link Market}
     */
    private Market getMarket(
            final UUID id
    ) {
        return readOnlyRepository.getMarketById(id)
                .orElseThrow(() -> new JynxProException(ErrorCode.MARKET_NOT_FOUND));
    }

    @GetMapping("/{id}/order-book")
    public ResponseEntity<OrderBook> getOrderBook(
            @PathVariable("id") UUID id,
            @RequestParam(value = "depth", defaultValue = "0.01") BigDecimal depth,
            @RequestParam(value = "type", defaultValue = "L1") OrderBookType type
    ) {
        return ResponseEntity.ok(orderBookService.getOrderBook(type, getMarket(id), depth, true));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Market> getById(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(getMarket(id));
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
        List<Trade> trades = readOnlyRepository.getTradesByMarketIdAndExecutedGreaterThanAndExecutedLessThan(
                        id, from, to).stream()
                .sorted(Comparator.comparing(Trade::getExecuted))
                .collect(Collectors.toList());
        return ResponseEntity.ok(tradeService.getKline(interval, trades));
    }

    @GetMapping("/{id}/quote")
    public ResponseEntity<Quote> getQuote(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(orderBookService.getQuote(getMarket(id), true));
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<MarketStatistics> getStatistics(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(readOnlyRepository.getStatisticsByMarketId(id));
    }

    @PostMapping
    public ResponseEntity<Proposal> add(
            @RequestBody AddMarketRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.addMarket(request).getItem());
    }

    @PutMapping
    public ResponseEntity<Proposal> amend(
            @RequestBody AmendMarketRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.amendMarket(request).getItem());
    }

    @PostMapping("/suspend")
    public ResponseEntity<Proposal> suspend(
            @RequestBody SingleItemRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.suspendMarket(request).getItem());
    }

    @PostMapping("/unsuspend")
    public ResponseEntity<Proposal> unsuspend(
            @RequestBody SingleItemRequest request
    ) {
        return ResponseEntity.ok(tendermintClient.unsuspendMarket(request).getItem());
    }
}
