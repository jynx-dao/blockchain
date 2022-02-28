package com.jynx.pro.request;

import com.jynx.pro.entity.User;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class SignedRequest extends TendermintRequest {
    public User user;
    public String signature;
    public String publicKey;
}