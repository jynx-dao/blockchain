package com.jynx.pro.request;

import com.jynx.pro.constant.OracleType;
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
    private BigDecimal marginRequirement;
    private Integer tickSize;
    private Integer stepSize;
    private Integer settlementFrequency;
    private BigDecimal makerFee;
    private BigDecimal takerFee;
    private BigDecimal liquidationFee;
    private String oracleKey;
    private OracleType oracleType;
    private UUID oracleProvider;
    private List<AuctionTrigger> auctionTriggers = new ArrayList<>();
    @Data
    @Accessors(chain = true)
    public static class AuctionTrigger {
        private BigDecimal depth;
        private BigDecimal openVolumeRatio;
    }
}