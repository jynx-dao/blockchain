package com.jynx.pro.request;

import com.jynx.pro.entity.User;
import lombok.Data;

@Data
public abstract class SignedRequest {
    public User user;
    public String signature;
    public String publicKey;
}