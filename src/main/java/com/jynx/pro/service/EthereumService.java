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
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Convert;

import javax.annotation.PostConstruct;
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
    @Setter
    @Value("${jynx.pro.bridge.address}")
    private String bridgeAddress;

    @PostConstruct
    private void setup() {
        EthFilter bridgeFilter = new EthFilter(DefaultBlockParameterName.EARLIEST,
                DefaultBlockParameterName.LATEST, bridgeAddress)
                .addOptionalTopics("AddSigner", "RemoveSigner", "AddAsset", "DisableAsset", "EnabledAsset",
                        "DepositAsset", "WithdrawAsset", "AddStake", "RemoveStake");
        // TODO - depending on the event we handle it differently
        getWeb3j().ethLogFlowable(bridgeFilter).subscribe(ethLog -> log.info(ethLog.toString()));
    }

    private Web3j getWeb3j() {
        String provider = String.format("http://%s:%s", rpcHost, rpcPort);
        return Web3j.build(new HttpService(provider));
    }

    private ERC20 getERC20Contract(
            final String erc20contractAddress
    ) {
        return ERC20.load(erc20contractAddress, getWeb3j(), Credentials.create(privateKey), new DefaultGasProvider());
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