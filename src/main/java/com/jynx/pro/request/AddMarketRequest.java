package com.jynx.pro.request;

import com.jynx.pro.entity.Oracle;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class AddMarketRequest extends ProposalRequest {
    private String name;
    private UUID settlementAssetId;
    private BigDecimal initialMargin;
    private BigDecimal maintenanceMargin;
    private Integer tickSize;
    private Integer stepSize;
    private Integer settlementFrequency;
    private BigDecimal makerFee;
    private BigDecimal takerFee;
    private List<Oracle> oracles = new ArrayList<>();
}