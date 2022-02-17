package com.jynx.pro.service;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.ethereum.ERC20Detailed;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.repository.EventRepository;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class EthereumService {

    private final int REQUIRED_BLOCKS = 5;

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
    @Setter
    @Value("${eth.required.confirmations}")
    private Integer requiredConfirmations; // TODO - network parameter?

    @Autowired
    private AccountService accountService;
    @Autowired
    private StakeService stakeService;
    @Autowired
    private EventService eventService;

    private Address decodeAddress(
            final Log ethLog,
            final int idx
    ) {
        return (Address) FunctionReturnDecoder.decodeIndexedValue(ethLog.getTopics().get(idx),
                new TypeReference<Address>() {});
    }

    private Uint256 decodeUint256(
            final Log ethLog,
            final int idx
    ) {
        return (Uint256) FunctionReturnDecoder.decodeIndexedValue(ethLog.getTopics().get(idx),
                new TypeReference<Uint256>() {});
    }

    private Bytes32 decodeBytes32(
            final Log ethLog,
            final int idx
    ) {
        return (Bytes32) FunctionReturnDecoder.decodeIndexedValue(ethLog.getTopics().get(idx),
                new TypeReference<Bytes32>() {});
    }

    public void confirmEvents() {
        try {
            BigInteger blockNumber = getWeb3j().ethBlockNumber().send().getBlockNumber();
            List<com.jynx.pro.entity.Event> events = eventService.getUnconfirmed();
            for(com.jynx.pro.entity.Event event : events) {
                Optional<Transaction> transactionOptional = getWeb3j()
                        .ethGetTransactionByHash(event.getHash()).send().getTransaction();
                long confirmations = blockNumber.longValue() - event.getBlockNumber();
                if(transactionOptional.isPresent() && confirmations >= requiredConfirmations) {
                    eventService.confirm(event);
                }
            }
        } catch(Exception e) {
            log.error("Failed to confirm events", e);
        }
    }

    public void initializeFilters() {
        EthFilter bridgeFilter = new EthFilter(DefaultBlockParameterName.EARLIEST,
                DefaultBlockParameterName.LATEST, bridgeAddress);
        final Event addStakeEvent = new Event("AddStake", Arrays.asList(
                new TypeReference<Address>(false) {},
                new TypeReference<Uint256>(true) {},
                new TypeReference<Bytes32>(true) {}
        ));
        final Event removeStakeEvent = new Event("RemoveStake", Arrays.asList(
                new TypeReference<Address>(false) {},
                new TypeReference<Uint256>(true) {},
                new TypeReference<Bytes32>(true) {}
        ));
        final Event depositAssetEvent = new Event("DepositAsset", Arrays.asList(
                new TypeReference<Address>(false) {},
                new TypeReference<Address>(true) {},
                new TypeReference<Uint256>(true) {},
                new TypeReference<Bytes32>(true) {}
        ));
        final String addStakeEventHash = EventEncoder.encode(addStakeEvent);
        final String removeStakeEventHash = EventEncoder.encode(removeStakeEvent);
        final String depositAssetEventHash = EventEncoder.encode(depositAssetEvent);
        getWeb3j().ethLogFlowable(bridgeFilter).subscribe(ethLog -> {
            String eventHash = ethLog.getTopics().get(0);
            String txHash = ethLog.getTransactionHash();
            BigInteger blockNumber = ethLog.getBlockNumber();
            if(eventHash.equals(addStakeEventHash)) {
                Uint256 amount = decodeUint256(ethLog, 1);
                Bytes32 jynxKey = decodeBytes32(ethLog, 2);
                stakeService.add(amount.getValue(), Hex.encodeHexString(jynxKey.getValue()),
                        blockNumber.longValue(), txHash);
            } else if(eventHash.equals(removeStakeEventHash)) {
                Uint256 amount = decodeUint256(ethLog, 1);
                Bytes32 jynxKey = decodeBytes32(ethLog, 2);
                stakeService.remove(amount.getValue(), Hex.encodeHexString(jynxKey.getValue()),
                        blockNumber.longValue(), txHash);
            } else if(eventHash.equals(depositAssetEventHash)) {
                Address asset = decodeAddress(ethLog, 1);
                Uint256 amount = decodeUint256(ethLog, 2);
                Bytes32 jynxKey = decodeBytes32(ethLog, 3);
                accountService.deposit(asset.getValue(), amount.getValue(), Hex.encodeHexString(jynxKey.getValue()),
                        blockNumber.longValue(), txHash);
            }
        });
    }

    private Web3j getWeb3j() {
        String provider = String.format("http://%s:%s", rpcHost, rpcPort);
        return Web3j.build(new HttpService(provider));
    }

    private ERC20Detailed getERC20Contract(
            final String erc20contractAddress
    ) {
        return ERC20Detailed.load(erc20contractAddress, getWeb3j(),
                Credentials.create(privateKey), new DefaultGasProvider());
    }

    public BigDecimal totalSupply(
            final String contractAddress
    ) {
        try {
            ERC20Detailed erc20contract = getERC20Contract(contractAddress);
            double modifier = BigInteger.TEN.pow(erc20contract.decimals().send().intValue()).doubleValue();
            return BigDecimal.valueOf(erc20contract.totalSupply().send().doubleValue() / modifier);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new JynxProException(ErrorCode.CANNOT_GET_JYNX_SUPPLY);
        }
    }
}