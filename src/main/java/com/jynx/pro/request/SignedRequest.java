package com.jynx.pro.request;

import com.jynx.pro.entity.User;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigInteger;

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
public class SignedRequest extends TendermintRequest {
    public User user;
    public String signature;
    public String publicKey;
    public String nonce;
}