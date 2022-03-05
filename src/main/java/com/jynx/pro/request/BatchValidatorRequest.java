package com.jynx.pro.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public class BatchValidatorRequest extends SignedRequest {
    private String address;
    private Long height;
}