package com.jynx.pro.controller;

import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.Trade;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.response.MultipleItemResponse;
import com.jynx.pro.response.SingleItemResponse;
import com.jynx.pro.service.MarketService;
import com.jynx.pro.service.OrderService;
import com.jynx.pro.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Controller
@RequestMapping("/market")
public class MarketController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private MarketService marketService;
    @Autowired
    private TradeService tradeService;

    @GetMapping("/{id}/order-book")
    public ResponseEntity<SingleItemResponse<OrderBook>> getOrderBook(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new SingleItemResponse<OrderBook>().setItem(orderService.getOrderBook(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SingleItemResponse<Market>> getById(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new SingleItemResponse<Market>().setItem(marketService.get(id)));
    }

    @GetMapping("/{id}/trades")
    public ResponseEntity<MultipleItemResponse<Trade>> getTrades(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Trade>().setItems(tradeService.getByMarketId(id)));
    }

    @GetMapping("/{id}/kline")
    public ResponseEntity<MultipleItemResponse<Object>> getKline(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Object>()); // TODO - implement this
    }

    @GetMapping("/{id}/quote")
    public ResponseEntity<MultipleItemResponse<Object>> getQuote(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Object>()); // TODO - implement this
    }

    @GetMapping("/{id}/statistics")
    public ResponseEntity<MultipleItemResponse<Object>> getStatistics(
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(new MultipleItemResponse<Object>()); // TODO - implement this
    }
}
