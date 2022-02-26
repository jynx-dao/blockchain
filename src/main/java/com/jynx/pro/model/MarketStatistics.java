package com.jynx.pro.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
public class MarketStatistics {
    private BigDecimal openVolume;
    private BigDecimal positionCount;
    private BigDecimal volumeWeightedLongPrice;
    private BigDecimal volumeWeightedShortPrice;
}