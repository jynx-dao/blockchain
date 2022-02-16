package com.jynx.pro.helper;

import com.jynx.pro.ethereum.ERC20;
import com.jynx.pro.ethereum.JYNX;
import com.jynx.pro.ethereum.JYNX_Distribution;
import com.jynx.pro.ethereum.JynxPro_Bridge;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Convert;

import java.math.BigInteger;

@Slf4j
@Component
public class EthereumHelper {

    @Getter
    private JYNX jynxToken;
    @Getter
    private ERC20 daiToken;
    @Getter
    private JynxPro_Bridge jynxProBridge;
    @Getter
    private JYNX_Distribution jynxDistribution;

    private Web3j getWeb3j(
            final String ganacheHost,
            final Integer ganachePort
    ) {
        String provider = String.format("http://%s:%s", ganacheHost, ganachePort);
        return Web3j.build(new HttpService(provider));
    }

    private ERC20 deployDaiToken(
            final String ganacheHost,
            final Integer ganachePort,
            final String privateKey
    ) throws Exception {
        Web3j web3j = getWeb3j(ganacheHost, ganachePort);
        Credentials credentials = Credentials.create(privateKey);
        return ERC20.deploy(web3j, credentials, new DefaultGasProvider()).send();
    }

    private JYNX deployJynxToken(
            final String ganacheHost,
            final Integer ganachePort,
            final String privateKey
    ) throws Exception {
        Web3j web3j = getWeb3j(ganacheHost, ganachePort);
        Credentials credentials = Credentials.create(privateKey);
        String _name = "Jynx DAO";
        String _symbol = "JYNX";
        BigInteger _decimals = BigInteger.valueOf(18);
        BigInteger total_supply_whole_tokens = BigInteger.valueOf(1000000000);
        return JYNX.deploy(web3j, credentials, new DefaultGasProvider(), _name, _symbol, _decimals,
                    total_supply_whole_tokens, jynxDistribution.getContractAddress()).send();
    }

    private JYNX_Distribution deployJynxDistribution(
            final String ganacheHost,
            final Integer ganachePort,
            final String privateKey
    ) throws Exception {
        Web3j web3j = getWeb3j(ganacheHost, ganachePort);
        Credentials credentials = Credentials.create(privateKey);
        return JYNX_Distribution.deploy(web3j, credentials, new DefaultGasProvider(),
                daiToken.getContractAddress()).send();
    }

    private JynxPro_Bridge deployJynxBridge(
            final String ganacheHost,
            final Integer ganachePort,
            final String privateKey
    ) throws Exception {
        Web3j web3j = getWeb3j(ganacheHost, ganachePort);
        Credentials credentials = Credentials.create(privateKey);
        return JynxPro_Bridge.deploy(web3j, credentials, new DefaultGasProvider(), jynxToken.getContractAddress(),
                jynxDistribution.getContractAddress(), BigInteger.valueOf(667)).send();
    }

    public void deploy(
            final String ganacheHost,
            final Integer ganachePort,
            final String privateKey
    ) {
        try {
            daiToken = deployDaiToken(ganacheHost, ganachePort, privateKey);
            jynxDistribution = deployJynxDistribution(ganacheHost, ganachePort, privateKey);
            jynxToken = deployJynxToken(ganacheHost, ganachePort, privateKey);
            jynxProBridge = deployJynxBridge(ganacheHost, ganachePort, privateKey);
        } catch(Exception e) {
            log.error("Failed to deploy contracts", e);
        }
    }

    public void approveJynx(
            final String address,
            final BigInteger amount
    ) {
        try {
            TransactionReceipt transactionReceipt = jynxToken.approve(address, amount).send();
            log.info(transactionReceipt.getTransactionHash());
        } catch(Exception e) {
            log.error("Failed to stake tokens", e);
        }
    }

    public void stakeTokens(
            final String jynxKey,
            final BigInteger amount
    ) {
        try {
            TransactionReceipt transactionReceipt = jynxProBridge.add_stake(amount, Hex.decode(jynxKey)).send();
            log.info(transactionReceipt.getTransactionHash());
        } catch(Exception e) {
            log.error("Failed to stake tokens", e);
        }
    }
}