package com.jynx.pro.request;

import com.jynx.pro.constant.AssetType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class AddAssetRequest extends ProposalRequest {
    private String name;
    private AssetType type;
    private String address;
    private Integer decimalPlaces;
}