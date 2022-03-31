package com.jynx.pro.service;

import com.jynx.pro.constant.MarketSide;
import com.jynx.pro.entity.Market;
import com.jynx.pro.model.OrderBook;
import com.jynx.pro.model.OrderBookItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderBookService {

    @Autowired
    private OrderService orderService;

    /**
     * Gets the current {@link OrderBook} of given {@link Market}
     *
     * @param market the {@link Market}
     *
     * @return the {@link OrderBook}
     */
    public OrderBook getOrderBookL3(
            final Market market
    ) {
        // TODO - need to group by price point
        //  need to add optional limit parameter
        OrderBook orderBook = new OrderBook();
        List<OrderBookItem> bids = orderService.getSideOfBook(market, MarketSide.BUY)
                .stream()
                .map(o -> new OrderBookItem().setQuantity(o.getRemainingQuantity()).setPrice(o.getPrice()))
                .collect(Collectors.toList());
        List<OrderBookItem> asks = orderService.getSideOfBook(market, MarketSide.SELL)
                .stream()
                .map(o -> new OrderBookItem().setQuantity(o.getRemainingQuantity()).setPrice(o.getPrice()))
                .collect(Collectors.toList());
        orderBook.setAsks(asks);
        orderBook.setBids(bids);
        return orderBook;
    }

    // TODO - move code from OrderService to here
    //  add L1, L2 and L3 support
}