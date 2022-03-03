package com.jynx.pro.service;

import com.jynx.pro.error.ErrorCode;
import com.jynx.pro.ethereum.ERC20Detailed;
import com.jynx.pro.ethereum.JynxPro_Bridge;
import com.jynx.pro.ethereum.type.EthereumType;
import com.jynx.pro.exception.JynxProException;
import com.jynx.pro.utils.PriceUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

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

    @Autowired
    private AccountService accountService;
    @Autowired
    private StakeService stakeService;
    @Autowired
    private EventService eventService;
    @Autowired
    private ConfigService configService;
    @Autowired
    private PriceUtils priceUtils;

    private final Event ADD_STAKE = new Event("AddStake", Arrays.asList(
            EthereumType.ADDRESS,
            EthereumType.UINT256_INDEXED,
            EthereumType.BYTES32_INDEXED
    ));

    private final Event REMOVE_STAKE = new Event("RemoveStake", Arrays.asList(
            EthereumType.ADDRESS,
            EthereumType.UINT256_INDEXED,
            EthereumType.BYTES32_INDEXED
    ));

    private final Event DEPOSIT_ASSET = new Event("DepositAsset", Arrays.asList(
            EthereumType.ADDRESS,
            EthereumType.ADDRESS_INDEXED,
            EthereumType.UINT256_INDEXED,
            EthereumType.BYTES32_INDEXED
    ));

    private final String ADD_STAKE_HASH = EventEncoder.encode(ADD_STAKE);
    private final String REMOVE_STAKE_HASH = EventEncoder.encode(REMOVE_STAKE);
    private final String DEPOSIT_ASSET_HASH = EventEncoder.encode(DEPOSIT_ASSET);

    /**
     * Decode address event parameters from Ethereum
     *
     * @param ethLog the {@link Log} instance
     * @param idx the parameter index
     *
     * @return the address as a string
     */
    private String decodeAddress(
            final Log ethLog,
            final int idx
    ) {
        return ((Address) FunctionReturnDecoder.decodeIndexedValue(ethLog.getTopics().get(idx),
                EthereumType.ADDRESS)).getValue();
    }

    /**
     * Decode uint256 event parameters from Ethereum
     *
     * @param ethLog the {@link Log} instance
     * @param idx the parameter index
     *
     * @return the numeric value
     */
    private BigInteger decodeUint256(
            final Log ethLog,
            final int idx
    ) {
        return ((Uint256) FunctionReturnDecoder.decodeIndexedValue(ethLog.getTopics().get(idx),
                EthereumType.UINT256)).getValue();
    }

    /**
     * Decode bytes32 event parameters from Ethereum
     *
     * @param ethLog the {@link Log} instance
     * @param idx the parameter index
     *
     * @return the Hex string
     */
    private String decodeBytes32(
            final Log ethLog,
            final int idx
    ) {
        return Hex.encodeHexString(((Bytes32) FunctionReturnDecoder.decodeIndexedValue(ethLog.getTopics().get(idx),
                EthereumType.BYTES32)).getValue());
    }

    /**
     * Processes confirmed events (i.e. after sufficient Ethereum blocks have been mined)
     */
    public List<com.jynx.pro.entity.Event> confirmEvents() {
        List<com.jynx.pro.entity.Event> confirmedEvents = new ArrayList<>();
        try {
            BigInteger blockNumber = getWeb3j().ethBlockNumber().send().getBlockNumber();
            List<com.jynx.pro.entity.Event> events = eventService.getUnconfirmed();
            for(com.jynx.pro.entity.Event event : events) {
                Optional<TransactionReceipt> transactionOptional = getWeb3j()
                        .ethGetTransactionReceipt(event.getHash()).send().getTransactionReceipt();
                long confirmations = blockNumber.longValue() - event.getBlockNumber();
                if(transactionOptional.isPresent() && confirmations >= configService.get().getEthConfirmations()) {
                    if(matchEvent(transactionOptional.get().getLogs(), event)) {
                        confirmedEvents.add(eventService.confirm(event));
                    }
                }
            }
        } catch(Exception e) {
            log.error("Failed to confirm events", e);
        }
        return confirmedEvents;
    }

    /**
     * Match a pending {@link Event} with the Ethereum {@link Log}s
     *
     * @param logs {@link Log}s from Ethereum tx
     * @param event unconfirmed {@link Event}
     *
     * @return true if event is valid
     */
    private boolean matchEvent(
            final List<Log> logs,
            final com.jynx.pro.entity.Event event
    ) {
        boolean result = false;
        for(Log ethLog : logs) {
            String eventHash = ethLog.getTopics().get(0);
            if (eventHash.equals(ADD_STAKE_HASH)) {
                BigInteger amount = decodeUint256(ethLog, 1);
                String jynxKey = decodeBytes32(ethLog, 2);
                if(event.getAmount().setScale(10, RoundingMode.HALF_UP)
                        .equals(priceUtils.fromBigInteger(amount).setScale(10, RoundingMode.HALF_UP)) &&
                        event.getUser().getPublicKey().equals(jynxKey)) {
                    result = true;
                }
            } else if (eventHash.equals(REMOVE_STAKE_HASH)) {
                BigInteger amount = decodeUint256(ethLog, 1);
                String jynxKey = decodeBytes32(ethLog, 2);
                if(event.getAmount().setScale(10, RoundingMode.HALF_UP)
                        .equals(priceUtils.fromBigInteger(amount).setScale(10, RoundingMode.HALF_UP)) &&
                        event.getUser().getPublicKey().equals(jynxKey)) {
                    result = true;
                }
            } else if (eventHash.equals(DEPOSIT_ASSET_HASH)) {
                String asset = decodeAddress(ethLog, 1);
                BigInteger amount = decodeUint256(ethLog, 2);
                String jynxKey = decodeBytes32(ethLog, 3);
                if(event.getAmount().setScale(10, RoundingMode.HALF_UP)
                        .equals(priceUtils.fromBigInteger(amount).setScale(10, RoundingMode.HALF_UP)) &&
                        event.getUser().getPublicKey().equals(jynxKey) &&
                        event.getAsset().equals(asset)) {
                    result = true;
                }
            }
        }
        return result;
    }

    /**
     * Initialize the Ethereum event filters
     */
    public void initializeFilters() {
        EthFilter bridgeFilter = new EthFilter(DefaultBlockParameterName.EARLIEST,
                DefaultBlockParameterName.LATEST, configService.get().getBridgeAddress());
        getWeb3j().ethLogFlowable(bridgeFilter).subscribe(ethLog -> {
            String eventHash = ethLog.getTopics().get(0);
            String txHash = ethLog.getTransactionHash();
            BigInteger blockNumber = ethLog.getBlockNumber();
            // TODO - the proposer should deliver these events via deliverTx [??]
            // TODO - else this is effectively happening "off-chain"
            if(eventHash.equals(ADD_STAKE_HASH)) {
                BigInteger amount = decodeUint256(ethLog, 1);
                String jynxKey = decodeBytes32(ethLog, 2);
                stakeService.add(amount, jynxKey, blockNumber.longValue(), txHash);
            } else if(eventHash.equals(REMOVE_STAKE_HASH)) {
                BigInteger amount = decodeUint256(ethLog, 1);
                String jynxKey = decodeBytes32(ethLog, 2);
                stakeService.remove(amount, jynxKey, blockNumber.longValue(), txHash);
            } else if(eventHash.equals(DEPOSIT_ASSET_HASH)) {
                String asset = decodeAddress(ethLog, 1);
                BigInteger amount = decodeUint256(ethLog, 2);
                String jynxKey = decodeBytes32(ethLog, 3);
                accountService.deposit(asset, amount, jynxKey, blockNumber.longValue(), txHash);
            }
        });
    }

    /**
     * Gets an instance of the {@link Web3j} interface
     *
     * @return {@link Web3j} interface
     */
    private Web3j getWeb3j() {
        // TODO - allow https or http
        String provider = String.format("http://%s:%s", rpcHost, rpcPort);
        return Web3j.build(new HttpService(provider));
    }

    /**
     * Gets an instance of the deployed {@link ERC20Detailed} contract
     *
     * @param contractAddress contract address
     *
     * @return {@link ERC20Detailed} contract
     */
    private ERC20Detailed getERC20Contract(
            final String contractAddress
    ) {
        return ERC20Detailed.load(contractAddress, getWeb3j(),
                Credentials.create(privateKey), new DefaultGasProvider());
    }

    /**
     * Gets the total supply of an ERC20 token
     *
     * @param contractAddress contract address
     *
     * @return the total supply
     */
    public BigDecimal totalSupply(
            final String contractAddress
    ) {
        try {
            ERC20Detailed erc20contract = getERC20Contract(contractAddress);
            double modifier = BigInteger.TEN.pow(erc20contract.decimals().send().intValue()).doubleValue();
            return BigDecimal.valueOf(erc20contract.totalSupply().send().doubleValue() / modifier);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new JynxProException(ErrorCode.CANNOT_GET_SUPPLY);
        }
    }

    /**
     * Gets the decimal places of an ERC20 token
     *
     * @param contractAddress contract address
     *
     * @return the decimal places
     */
    public int decimalPlaces(
            final String contractAddress
    ) {
        try {
            ERC20Detailed erc20contract = getERC20Contract(contractAddress);
            return erc20contract.decimals().send().intValue();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new JynxProException(ErrorCode.CANNOT_GET_DECIMAL_PLACES);
        }
    }

    /**
     * Withdraw assets from the Jynx bridge
     *
     * @param destinations the destination wallet of each withdrawal
     * @param amounts the withdrawal amounts
     * @param assets the asset for each withdrawal
     *
     * @return {@link TransactionReceipt} from Ethereum
     */
    public TransactionReceipt withdrawAssets(
            final List<String> destinations,
            final List<BigInteger> amounts,
            final List<String> assets
    ) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            JynxPro_Bridge jynxProBridge = JynxPro_Bridge.load(configService.get().getBridgeAddress(), getWeb3j(),
                    credentials, new DefaultGasProvider());
            BigInteger nonce = getNonce();
            List<Address> destinationsForSig = destinations.stream().map(Address::new)
                    .collect(Collectors.toList());
            List<Uint256> amountsForSig = amounts.stream().map(Uint256::new)
                    .collect(Collectors.toList());
            List<Address> assetsForSig = assets.stream().map(Address::new)
                    .collect(Collectors.toList());
            List<Type> args = Arrays.asList(new DynamicArray<>(Address.class, destinationsForSig),
                    new DynamicArray<>(Uint256.class, amountsForSig), new DynamicArray<>(Address.class, assetsForSig),
                    new Uint256(nonce), new Utf8String("withdraw_assets"));
            byte[] signature = getSignature(args, credentials);
            return jynxProBridge.withdraw_assets(destinations, amounts, assets, nonce, signature).send();
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            throw new JynxProException(ErrorCode.CANNOT_WITHDRAW_ASSETS);
        }
    }

    /**
     * Remove an asset to the Jynx bridge
     *
     * @param asset the ERC20 contract address
     *
     * @return {@link TransactionReceipt} from Ethereum
     */
    public TransactionReceipt removeAsset(
            final String asset
    ) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            JynxPro_Bridge jynxProBridge = JynxPro_Bridge.load(configService.get().getBridgeAddress(), getWeb3j(),
                    credentials, new DefaultGasProvider());
            BigInteger nonce = getNonce();
            List<Type> args = Arrays.asList(new Address(asset), new Uint256(nonce), new Utf8String("remove_asset"));
            byte[] signature = getSignature(args, credentials);
            return jynxProBridge.remove_asset(asset, nonce, signature).send();
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            throw new JynxProException(ErrorCode.CANNOT_REMOVE_ASSET);
        }
    }

    /**
     * Add an asset to the Jynx bridge
     *
     * @param asset the ERC20 contract address
     *
     * @return {@link TransactionReceipt} from Ethereum
     */
    public TransactionReceipt addAsset(
            final String asset
    ) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            JynxPro_Bridge jynxProBridge = JynxPro_Bridge.load(configService.get().getBridgeAddress(), getWeb3j(),
                    credentials, new DefaultGasProvider());
            BigInteger nonce = getNonce();
            List<Type> args = Arrays.asList(new Address(asset), new Uint256(nonce), new Utf8String("add_asset"));
            byte[] signature = getSignature(args, credentials);
            return jynxProBridge.add_asset(asset, nonce, signature).send();
        } catch(Exception e) {
            log.error(e.getMessage(), e);
            throw new JynxProException(ErrorCode.CANNOT_ADD_ASSET);
        }
    }

    /**
     * Get a signature for use with the Jynx bridge
     *
     * @param args the function arguments
     * @param credentials {@link Credentials} containing the signing key
     *
     * @return signature in byte-array format
     */
    private byte[] getSignature(
            final List<Type> args,
            final Credentials credentials
    ) throws DecoderException {
        Function function = new Function("", args, Collections.emptyList());
        String encodedArgs = FunctionEncoder.encode(function).substring(10);
        List<Type> args2 = Arrays.asList(new DynamicBytes(Hex.decodeHex(encodedArgs)), new Address(credentials.getAddress()));
        function = new Function("", args2, Collections.emptyList());
        encodedArgs = FunctionEncoder.encode(function).substring(10);
        Sign.SignatureData signatureData = Sign.signMessage(Hex.decodeHex(encodedArgs),
                ECKeyPair.create(Hex.decodeHex(privateKey.substring(2))));
        String signatureString = toSignatureString(signatureData).substring(2);
        return Hex.decodeHex(signatureString);
    }

    /**
     * Convert Ethereum signature data to a Hex string
     *
     * @param sig {@link org.web3j.crypto.Sign.SignatureData}
     *
     * @return the Hex string
     */
    private String toSignatureString(
            final Sign.SignatureData sig
    ) {
        return String.format("0x%s%s%s", Hex.encodeHexString(sig.getR()),
                Hex.encodeHexString(sig.getS()), Hex.encodeHexString(sig.getV()));
    }

    /**
     * Generates a secure random 256-bit nonce
     *
     * @return the nonce as {@link BigInteger}
     */
    private BigInteger getNonce() {
        return new BigInteger(256, new SecureRandom());
    }
}