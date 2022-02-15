package com.jynx.pro.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class OrderBook {
    private List<OrderBookItem> bids = new ArrayList<>();
    private List<OrderBookItem> asks = new ArrayList<>();
}
