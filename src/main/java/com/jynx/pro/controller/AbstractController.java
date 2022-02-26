package com.jynx.pro.controller;

import com.jynx.pro.blockchain.TendermintClient;
import com.jynx.pro.repository.ReadOnlyRepository;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractController {
    @Autowired
    protected TendermintClient tendermintClient;
    @Autowired
    protected ReadOnlyRepository readOnlyRepository;
}