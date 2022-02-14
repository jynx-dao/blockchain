package com.jynx.pro.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class UnsuspendAssetRequest extends ProposalRequest {
    private UUID id;
}