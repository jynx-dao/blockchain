package com.jynx.pro.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class ProposalRequest extends SignedRequest {
    protected Long openTime;
    protected Long closingTime;
    protected Long enactmentTime;
}