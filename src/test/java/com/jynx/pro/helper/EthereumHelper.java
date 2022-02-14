package com.jynx.pro.helper;

import com.jynx.pro.ethereum.JYNX;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;

@Slf4j
@Component
public class EthereumHelper {

    @Getter
    JYNX jynxTokenContract;

    public void deploy(
            final String ganacheHost,
            final Integer ganachePort,
            final String privateKey
    ) {
        String provider = String.format("http://%s:%s", ganacheHost, ganachePort);
        Web3j web3j = Web3j.build(new HttpService(provider));
        Credentials credentials = Credentials.create(privateKey);
        String _name = "Jynx DAO";
        String _symbol = "JYNX";
        BigInteger _decimals = BigInteger.valueOf(18);
        BigInteger total_supply_whole_tokens = BigInteger.valueOf(1000000000);
        try {
            jynxTokenContract = JYNX.deploy(web3j, credentials, new DefaultGasProvider(), _name, _symbol, _decimals,
                    total_supply_whole_tokens).send();
            log.info("JYNX token contract deployed at: {}", jynxTokenContract.getContractAddress());
        } catch(Exception e) {
            log.error("Failed to deploy JYNX token", e);
        }
    }
}