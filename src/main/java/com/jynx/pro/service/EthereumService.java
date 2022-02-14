package com.jynx.pro.service;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.ethereum.ERC20;
import com.jynx.pro.exception.JynxProException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Convert;

import java.math.BigDecimal;

@Slf4j
@Service
public class EthereumService {

    @Setter
    @Value("${ethereum.rpc.host}")
    private String rpcHost;
    @Setter
    @Value("${ethereum.rpc.port}")
    private Integer rpcPort;
    @Value("${ethereum.private.key}")
    private String privateKey;

    private ERC20 getERC20Contract(
            final String erc20contractAddress
    ) {
        String provider = String.format("http://%s:%s", rpcHost, rpcPort);
        Web3j web3j = Web3j.build(new HttpService(provider));
        return ERC20.load(erc20contractAddress, web3j, Credentials.create(privateKey), new DefaultGasProvider());
    }

    public BigDecimal totalSupply(
            final String contractAddress
    ) {
        try {
            // TODO - this needs to use decimal places for the conversion
            ERC20 erc20contract = getERC20Contract(contractAddress);
            return Convert.fromWei(erc20contract.totalSupply().send().toString(), Convert.Unit.ETHER);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new JynxProException(ErrorCode.CANNOT_GET_JYNX_SUPPLY);
        }
    }
}