package com.jynx.pro.helper;

import com.jynx.pro.ethereum.DAI;
import com.jynx.pro.ethereum.JYNX;
import com.jynx.pro.ethereum.JYNX_Distribution;
import com.jynx.pro.ethereum.JynxPro_Bridge;
import com.jynx.pro.utils.PriceUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigDecimal;
import java.math.BigInteger;

@Slf4j
@Component
public class EthereumHelper {

    @Getter
    private JYNX jynxToken;
    @Getter
    private DAI daiToken;
    @Getter
    private JynxPro_Bridge jynxProBridge;
    @Getter
    private JYNX_Distribution jynxDistribution;
    @Autowired
    private PriceUtils priceUtils;

    private Web3j getWeb3j(
            final String ganacheHost,
            final Integer ganachePort
    ) {
        String provider = String.format("http://%s:%s", ganacheHost, ganachePort);
        return Web3j.build(new HttpService(provider));
    }

    private DAI deployDaiToken(
            final String ganacheHost,
            final Integer ganachePort,
            final String privateKey
    ) throws Exception {
        Web3j web3j = getWeb3j(ganacheHost, ganachePort);
        Credentials credentials = Credentials.create(privateKey);
        String _name = "DAI";
        String _symbol = "DAI";
        BigInteger _decimals = BigInteger.valueOf(18);
        BigInteger total_supply_whole_tokens = BigInteger.valueOf(1000000000).multiply(BigInteger.TEN);
        return DAI.deploy(web3j, credentials, new DefaultGasProvider(), _name, _symbol,
                _decimals, total_supply_whole_tokens).send();
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
            String address = Credentials.create(privateKey).getAddress();
            BigInteger amount = priceUtils.toBigInteger(BigDecimal.valueOf(100000));
            daiToken.issue(address, amount).send();
        } catch(Exception e) {
            log.error("Failed to deploy contracts", e);
        }
    }

    public void depositAsset(
            final String asset,
            final BigInteger amount,
            final String jynxKey
    ) {
        try {
            TransactionReceipt transactionReceipt = jynxProBridge.deposit_asset(
                    asset, amount, Hex.decodeHex(jynxKey)).send();
            log.error(transactionReceipt.getTransactionHash());
        } catch(Exception e) {
            log.error("Failed to deposit asset", e);
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
            log.error("Failed to approve JYNX", e);
        }
    }

    public void approveDai(
            final String address,
            final BigInteger amount
    ) {
        try {
            TransactionReceipt transactionReceipt = daiToken.approve(address, amount).send();
            log.info(transactionReceipt.getTransactionHash());
        } catch(Exception e) {
            log.error("Failed to approve DAI", e);
        }
    }

    public void removeTokens(
            final String jynxKey,
            final BigInteger amount
    ) {
        try {
            TransactionReceipt transactionReceipt = jynxProBridge.remove_stake(amount, Hex.decodeHex(jynxKey)).send();
            log.info(transactionReceipt.getTransactionHash());
        } catch(Exception e) {
            log.error("Failed to unstake tokens", e);
        }
    }

    public void stakeTokens(
            final String jynxKey,
            final BigInteger amount
    ) {
        try {
            TransactionReceipt transactionReceipt = jynxProBridge.add_stake(amount, Hex.decodeHex(jynxKey)).send();
            log.info(transactionReceipt.getTransactionHash());
        } catch(Exception e) {
            log.error("Failed to stake tokens", e);
        }
    }
}