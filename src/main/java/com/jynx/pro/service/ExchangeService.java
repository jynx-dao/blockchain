package com.jynx.pro.service;

import java.math.BigDecimal;

public interface ExchangeService {
    BigDecimal getPriceAt(String symbol, Long time);
}
