package com.jynx.pro.ethereum;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.4.1.
 */
@SuppressWarnings("rawtypes")
public class JYNX extends Contract {
    public static final String BINARY = "60806040523480156200001157600080fd5b5060405162000e5e38038062000e5e83398101604081905262000034916200037e565b84848482600090805190602001906200004f9291906200020b565b508151620000659060019060208501906200020b565b5080600260006101000a81548160ff021916908360ff160217905550505050806001600160a01b031663158ef93e6040518163ffffffff1660e01b8152600401602060405180830381865afa158015620000c3573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190620000e991906200042e565b156200013b5760405162461bcd60e51b815260206004820181905260248201527f646973747269627574696f6e20616c726561647920696e697469616c697a6564604482015260640160405180910390fd5b60006200014d60ff8516600a6200056e565b6200015990846200057c565b60058190559050600062000170612710836200059e565b9050600081600554620001849190620005c1565b6001600160a01b03851660008181526003602052604080822084905533825280822086905551929350909160008051602062000e3e83398151915290620001ce9085815260200190565b60405180910390a3604051828152339060009060008051602062000e3e8339815191529060200160405180910390a3505050505050505062000618565b8280546200021990620005db565b90600052602060002090601f0160209004810192826200023d576000855562000288565b82601f106200025857805160ff191683800117855562000288565b8280016001018555821562000288579182015b82811115620002885782518255916020019190600101906200026b565b50620002969291506200029a565b5090565b5b808211156200029657600081556001016200029b565b634e487b7160e01b600052604160045260246000fd5b600082601f830112620002d957600080fd5b81516001600160401b0380821115620002f657620002f6620002b1565b604051601f8301601f19908116603f01168101908282118183101715620003215762000321620002b1565b816040528381526020925086838588010111156200033e57600080fd5b600091505b8382101562000362578582018301518183018401529082019062000343565b83821115620003745760008385830101525b9695505050505050565b600080600080600060a086880312156200039757600080fd5b85516001600160401b0380821115620003af57600080fd5b620003bd89838a01620002c7565b96506020880151915080821115620003d457600080fd5b50620003e388828901620002c7565b945050604086015160ff81168114620003fb57600080fd5b6060870151608088015191945092506001600160a01b03811681146200042057600080fd5b809150509295509295909350565b6000602082840312156200044157600080fd5b815180151581146200045257600080fd5b9392505050565b634e487b7160e01b600052601160045260246000fd5b600181815b80851115620004b057816000190482111562000494576200049462000459565b80851615620004a257918102915b93841c939080029062000474565b509250929050565b600082620004c95750600162000568565b81620004d85750600062000568565b8160018114620004f15760028114620004fc576200051c565b600191505062000568565b60ff84111562000510576200051062000459565b50506001821b62000568565b5060208310610133831016604e8410600b841016171562000541575081810a62000568565b6200054d83836200046f565b806000190482111562000564576200056462000459565b0290505b92915050565b6000620004528383620004b8565b600081600019048311821515161562000599576200059962000459565b500290565b600082620005bc57634e487b7160e01b600052601260045260246000fd5b500490565b600082821015620005d657620005d662000459565b500390565b600181811c90821680620005f057607f821691505b602082108114156200061257634e487b7160e01b600052602260045260246000fd5b50919050565b61081680620006286000396000f3fe608060405234801561001057600080fd5b50600436106100a95760003560e01c80633950935111610071578063395093511461012957806370a082311461013c57806395d89b4114610165578063a457c2d71461016d578063a9059cbb14610180578063dd62ed3e1461019357600080fd5b806306fdde03146100ae578063095ea7b3146100cc57806318160ddd146100ef57806323b872dd14610101578063313ce56714610114575b600080fd5b6100b66101cc565b6040516100c39190610625565b60405180910390f35b6100df6100da366004610696565b61025e565b60405190151581526020016100c3565b6005545b6040519081526020016100c3565b6100df61010f3660046106c0565b610274565b60025460405160ff90911681526020016100c3565b6100df610137366004610696565b6102c5565b6100f361014a3660046106fc565b6001600160a01b031660009081526003602052604090205490565b6100b66102fb565b6100df61017b366004610696565b61030a565b6100df61018e366004610696565b610340565b6100f36101a1366004610717565b6001600160a01b03918216600090815260046020908152604080832093909416825291909152205490565b6060600080546101db9061074a565b80601f01602080910402602001604051908101604052809291908181526020018280546102079061074a565b80156102545780601f1061022957610100808354040283529160200191610254565b820191906000526020600020905b81548152906001019060200180831161023757829003601f168201915b5050505050905090565b600061026b33848461034d565b50600192915050565b6000610281848484610477565b6001600160a01b0384166000908152600460209081526040808320338085529252909120546102bb9186916102b690866105e3565b61034d565b5060019392505050565b3360008181526004602090815260408083206001600160a01b0387168452909152812054909161026b9185906102b69086610606565b6060600180546101db9061074a565b3360008181526004602090815260408083206001600160a01b0387168452909152812054909161026b9185906102b690866105e3565b600061026b338484610477565b6001600160a01b0383166103b45760405162461bcd60e51b8152602060048201526024808201527f45524332303a20617070726f76652066726f6d20746865207a65726f206164646044820152637265737360e01b60648201526084015b60405180910390fd5b6001600160a01b0382166104155760405162461bcd60e51b815260206004820152602260248201527f45524332303a20617070726f766520746f20746865207a65726f206164647265604482015261737360f01b60648201526084016103ab565b6001600160a01b0383811660008181526004602090815260408083209487168084529482529182902085905590518481527f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b92591015b60405180910390a3505050565b6001600160a01b0383166104db5760405162461bcd60e51b815260206004820152602560248201527f45524332303a207472616e736665722066726f6d20746865207a65726f206164604482015264647265737360d81b60648201526084016103ab565b6001600160a01b03821661053d5760405162461bcd60e51b815260206004820152602360248201527f45524332303a207472616e7366657220746f20746865207a65726f206164647260448201526265737360e81b60648201526084016103ab565b6001600160a01b03831660009081526003602052604090205461056090826105e3565b6001600160a01b03808516600090815260036020526040808220939093559084168152205461058f9082610606565b6001600160a01b0380841660008181526003602052604090819020939093559151908516907fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef9061046a9085815260200190565b6000828211156105f5576105f5610785565b6105ff82846107b1565b9392505050565b60008061061383856107c8565b9050838110156105ff576105ff610785565b600060208083528351808285015260005b8181101561065257858101830151858201604001528201610636565b81811115610664576000604083870101525b50601f01601f1916929092016040019392505050565b80356001600160a01b038116811461069157600080fd5b919050565b600080604083850312156106a957600080fd5b6106b28361067a565b946020939093013593505050565b6000806000606084860312156106d557600080fd5b6106de8461067a565b92506106ec6020850161067a565b9150604084013590509250925092565b60006020828403121561070e57600080fd5b6105ff8261067a565b6000806040838503121561072a57600080fd5b6107338361067a565b91506107416020840161067a565b90509250929050565b600181811c9082168061075e57607f821691505b6020821081141561077f57634e487b7160e01b600052602260045260246000fd5b50919050565b634e487b7160e01b600052600160045260246000fd5b634e487b7160e01b600052601160045260246000fd5b6000828210156107c3576107c361079b565b500390565b600082198211156107db576107db61079b565b50019056fea2646970667358221220db11931f41a0bb09a8e14b26bbe312047b5cc492c6f172ed73c0acc1480d82e164736f6c634300080b0033ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    public static final String FUNC_ALLOWANCE = "allowance";

    public static final String FUNC_APPROVE = "approve";

    public static final String FUNC_BALANCEOF = "balanceOf";

    public static final String FUNC_DECIMALS = "decimals";

    public static final String FUNC_DECREASEALLOWANCE = "decreaseAllowance";

    public static final String FUNC_INCREASEALLOWANCE = "increaseAllowance";

    public static final String FUNC_NAME = "name";

    public static final String FUNC_SYMBOL = "symbol";

    public static final String FUNC_TOTALSUPPLY = "totalSupply";

    public static final String FUNC_TRANSFER = "transfer";

    public static final String FUNC_TRANSFERFROM = "transferFrom";

    public static final Event APPROVAL_EVENT = new Event("Approval", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event TRANSFER_EVENT = new Event("Transfer", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    @Deprecated
    protected JYNX(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected JYNX(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected JYNX(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected JYNX(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<ApprovalEventResponse> getApprovalEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(APPROVAL_EVENT, transactionReceipt);
        ArrayList<ApprovalEventResponse> responses = new ArrayList<ApprovalEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            ApprovalEventResponse typedResponse = new ApprovalEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.spender = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<ApprovalEventResponse> approvalEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, ApprovalEventResponse>() {
            @Override
            public ApprovalEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(APPROVAL_EVENT, log);
                ApprovalEventResponse typedResponse = new ApprovalEventResponse();
                typedResponse.log = log;
                typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.spender = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<ApprovalEventResponse> approvalEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(APPROVAL_EVENT));
        return approvalEventFlowable(filter);
    }

    public List<TransferEventResponse> getTransferEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(TRANSFER_EVENT, transactionReceipt);
        ArrayList<TransferEventResponse> responses = new ArrayList<TransferEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            TransferEventResponse typedResponse = new TransferEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<TransferEventResponse> transferEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, TransferEventResponse>() {
            @Override
            public TransferEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(TRANSFER_EVENT, log);
                TransferEventResponse typedResponse = new TransferEventResponse();
                typedResponse.log = log;
                typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<TransferEventResponse> transferEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(TRANSFER_EVENT));
        return transferEventFlowable(filter);
    }

    public RemoteFunctionCall<BigInteger> allowance(String owner, String spender) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_ALLOWANCE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, owner), 
                new org.web3j.abi.datatypes.Address(160, spender)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> approve(String spender, BigInteger value) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_APPROVE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, spender), 
                new org.web3j.abi.datatypes.generated.Uint256(value)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> balanceOf(String account) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_BALANCEOF, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, account)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> decimals() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_DECIMALS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> decreaseAllowance(String spender, BigInteger subtractedValue) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_DECREASEALLOWANCE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, spender), 
                new org.web3j.abi.datatypes.generated.Uint256(subtractedValue)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> increaseAllowance(String spender, BigInteger addedValue) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_INCREASEALLOWANCE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, spender), 
                new org.web3j.abi.datatypes.generated.Uint256(addedValue)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> name() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_NAME, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> symbol() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_SYMBOL, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> totalSupply() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_TOTALSUPPLY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> transfer(String recipient, BigInteger amount) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TRANSFER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, recipient), 
                new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> transferFrom(String sender, String recipient, BigInteger amount) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TRANSFERFROM, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, sender), 
                new org.web3j.abi.datatypes.Address(160, recipient), 
                new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static JYNX load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new JYNX(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static JYNX load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new JYNX(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static JYNX load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new JYNX(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static JYNX load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new JYNX(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<JYNX> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider, String _name, String _symbol, BigInteger _decimals, BigInteger total_supply_whole_tokens, String jynx_distribution_address) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(_name), 
                new org.web3j.abi.datatypes.Utf8String(_symbol), 
                new org.web3j.abi.datatypes.generated.Uint8(_decimals), 
                new org.web3j.abi.datatypes.generated.Uint256(total_supply_whole_tokens), 
                new org.web3j.abi.datatypes.Address(160, jynx_distribution_address)));
        return deployRemoteCall(JYNX.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
    }

    public static RemoteCall<JYNX> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider, String _name, String _symbol, BigInteger _decimals, BigInteger total_supply_whole_tokens, String jynx_distribution_address) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(_name), 
                new org.web3j.abi.datatypes.Utf8String(_symbol), 
                new org.web3j.abi.datatypes.generated.Uint8(_decimals), 
                new org.web3j.abi.datatypes.generated.Uint256(total_supply_whole_tokens), 
                new org.web3j.abi.datatypes.Address(160, jynx_distribution_address)));
        return deployRemoteCall(JYNX.class, web3j, transactionManager, contractGasProvider, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<JYNX> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit, String _name, String _symbol, BigInteger _decimals, BigInteger total_supply_whole_tokens, String jynx_distribution_address) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(_name), 
                new org.web3j.abi.datatypes.Utf8String(_symbol), 
                new org.web3j.abi.datatypes.generated.Uint8(_decimals), 
                new org.web3j.abi.datatypes.generated.Uint256(total_supply_whole_tokens), 
                new org.web3j.abi.datatypes.Address(160, jynx_distribution_address)));
        return deployRemoteCall(JYNX.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<JYNX> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit, String _name, String _symbol, BigInteger _decimals, BigInteger total_supply_whole_tokens, String jynx_distribution_address) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(_name), 
                new org.web3j.abi.datatypes.Utf8String(_symbol), 
                new org.web3j.abi.datatypes.generated.Uint8(_decimals), 
                new org.web3j.abi.datatypes.generated.Uint256(total_supply_whole_tokens), 
                new org.web3j.abi.datatypes.Address(160, jynx_distribution_address)));
        return deployRemoteCall(JYNX.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    public static class ApprovalEventResponse extends BaseEventResponse {
        public String owner;

        public String spender;

        public BigInteger value;
    }

    public static class TransferEventResponse extends BaseEventResponse {
        public String from;

        public String to;

        public BigInteger value;
    }
}
