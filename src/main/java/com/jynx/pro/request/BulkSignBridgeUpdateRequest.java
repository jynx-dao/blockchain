package com.jynx.pro.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class BulkSignBridgeUpdateRequest extends SignedRequest {
    private List<SignBridgeUpdateRequest> signatures = new ArrayList<>();
}