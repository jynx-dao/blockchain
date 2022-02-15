package com.jynx.pro.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class OrderBookItem {
    private BigDecimal price;
    private BigDecimal size;
}