package com.jynx.pro.service;

import java.math.BigDecimal;

public interface ExchangeService {

    /**
     * Get the most recent price before the specified timestamp
     *
     * @param symbol the market's symbol
     * @param time the cut-off timestamp
     *
     * @return the asset's price
     */
    BigDecimal getPriceAt(String symbol, Long time);
}
