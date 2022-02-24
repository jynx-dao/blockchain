package com.jynx.pro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class PolygonService {
    public BigDecimal getPriceAt(
            final String symbol,
            final Long time
    ) {
        return BigDecimal.ZERO;
    }
}