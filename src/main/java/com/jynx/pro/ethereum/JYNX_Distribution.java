package com.jynx.pro.ethereum;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
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
import org.web3j.tuples.generated.Tuple8;
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
public class JYNX_Distribution extends Contract {
    public static final String BINARY = "608060405260006007819055600855600c805460ff1916905534801561002457600080fd5b506040516118b53803806118b5833981016040819052610043916100a7565b600080546001600160a01b0319163390811782556040519091907f8be0079c531659141344cd1fd0a4f28419497f9722a3daafe3b4186f6b6457e0908290a3600380546001600160a81b0319166001600160a01b03929092169190911790556100d7565b6000602082840312156100b957600080fd5b81516001600160a01b03811681146100d057600080fd5b9392505050565b6117cf806100e66000396000f3fe608060405234801561001057600080fd5b50600436106101c45760003560e01c806382f03ef4116100f9578063a789fb0311610097578063f2fde38b11610071578063f2fde38b14610426578063f39bd73b14610439578063f4b9fa7514610442578063f8a711d11461045557600080fd5b8063a789fb03146103ed578063bc7b7d1314610400578063d13f90b41461041357600080fd5b80638e57cf4b116100d35780638e57cf4b146103a15780638f32d59b146103b4578063906a1bf1146103c75780639f80a569146103da57600080fd5b806382f03ef41461037f57806388850be4146103885780638da5cb5b1461039057600080fd5b806351a35bf811610166578063637797ae11610140578063637797ae146102b2578063715018a6146103435780637b31a7791461034b5780637c4922ec1461035457600080fd5b806351a35bf81461028d57806358efb78f1461029657806361d027b3146102a957600080fd5b806336712190116101a257806336712190146102325780634688388514610253578063478c27be1461026657806348019ce91461026e57600080fd5b8063158ef93e146101c95780631f921740146101f25780632749547714610207575b600080fd5b6003546101dd90600160a01b900460ff1681565b60405190151581526020015b60405180910390f35b610205610200366004611497565b610480565b005b60015461021a906001600160a01b031681565b6040516001600160a01b0390911681526020016101e9565b6102456102403660046114da565b610657565b6040519081526020016101e9565b610205610261366004611518565b61076c565b6102056107e1565b600c5461027b9060ff1681565b60405160ff90911681526020016101e9565b61024560065481565b6102056102a436600461154b565b610917565b61024560045481565b6103066102c036600461154b565b600b602052600090815260409020805460018201546002830154600384015460048501546005860154600687015460079097015495969495939492939192909160ff1688565b604080519889526020890197909752958701949094526060860192909252608085015260a084015260c0830152151560e0820152610100016101e9565b610205610a67565b61024560085481565b610245610362366004611566565b600960209081526000928352604080842090915290825290205481565b61024560075481565b610205610adb565b6000546001600160a01b031661021a565b6102456103af366004611566565b610bad565b6000546001600160a01b031633146101dd565b6102056103d536600461154b565b610d15565b6102056103e8366004611599565b610dfb565b6102056103fb3660046115d5565b610fcf565b60025461021a906001600160a01b031681565b6102056104213660046115ff565b611212565b610205610434366004611518565b6113a1565b61024560055481565b60035461021a906001600160a01b031681565b610245610463366004611566565b600a60209081526000928352604080842090915290825290205481565b6000546001600160a01b031633146104b35760405162461bcd60e51b81526004016104aa9061164c565b60405180910390fd5b600354600160a01b900460ff166104dc5760405162461bcd60e51b81526004016104aa90611681565b84841161052b5760405162461bcd60e51b815260206004820152601a60248201527f63616e6e6f7420656e64206265666f7265207374617274696e6700000000000060448201526064016104aa565b6006548611156105765760405162461bcd60e51b81526020600482015260166024820152751b9bdd08195b9bdd59da081d1bdad95b9cc81b19599d60521b60448201526064016104aa565b6040805161010081018252878152600060208083018281528385018a8152606085018a8152608086018a815260a087018a815260c088018a815260e08901888152600c5460ff168952600b909752988720975188559351600188015591516002870155516003860155516004850155516005840155925160068084019190915592516007909201805460ff19169215159290921790915581548892919061061e9084906116ce565b9091555050600c805460ff16906000610636836116e5565b91906101000a81548160ff021916908360ff16021790555050505050505050565b600c5460009060ff1661066c57506000610766565b60008052600b6020527fdf7de25b7f1fd6d0b5205f0e18f1f35bd7b8d84cce336588d184533ce43a6f79544210156106a657506000610766565b6000808052600b6020527fdf7de25b7f1fd6d0b5205f0e18f1f35bd7b8d84cce336588d184533ce43a6f79546106df9062ed4e00611705565b9050630966018060006106f28284611705565b9050600061070086886116ce565b90508142101561076057600061071685426116ce565b905060008461072883620f424061171d565b610732919061173c565b90506000620f4240610744838c61171d565b61074e919061173c565b905061075a89826116ce565b93505050505b93505050505b92915050565b6000546001600160a01b031633146107965760405162461bcd60e51b81526004016104aa9061164c565b600354600160a01b900460ff166107bf5760405162461bcd60e51b81526004016104aa90611681565b600380546001600160a01b0319166001600160a01b0392909216919091179055565b600354600160a01b900460ff1661080a5760405162461bcd60e51b81526004016104aa90611681565b6001546001600160a01b031633146108705760405162461bcd60e51b8152602060048201526024808201527f6f6e6c79206272696467652063616e20636c61696d206e6574776f726b20746f6044820152636b656e7360e01b60648201526084016104aa565b6000610880600554600854610657565b905080600860008282546108949190611705565b909155505060025460015460405163a9059cbb60e01b81526001600160a01b0391821660048201526024810184905291169063a9059cbb906044015b6020604051808303816000875af11580156108ef573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610913919061175e565b5050565b600354600160a01b900460ff166109405760405162461bcd60e51b81526004016104aa90611681565b60ff81166000908152600b602052604090206003015442116109a45760405162461bcd60e51b815260206004820152601a60248201527f646973747269627574696f6e20686173206e6f7420656e64656400000000000060448201526064016104aa565b60ff8082166000908152600b60205260409020600701541615610a095760405162461bcd60e51b815260206004820152601f60248201527f756e736f6c6420746f6b656e7320616c7265616479207265636c61696d65640060448201526064016104aa565b60ff81166000908152600b6020526040812060018101549054610a2c91906116ce565b90508060066000828254610a409190611705565b90915550505060ff166000908152600b60205260409020600701805460ff19166001179055565b6000546001600160a01b03163314610a915760405162461bcd60e51b81526004016104aa9061164c565b600080546040516001600160a01b03909116907f8be0079c531659141344cd1fd0a4f28419497f9722a3daafe3b4186f6b6457e0908390a3600080546001600160a01b0319169055565b6000546001600160a01b03163314610b055760405162461bcd60e51b81526004016104aa9061164c565b600354600160a01b900460ff16610b2e5760405162461bcd60e51b81526004016104aa90611681565b6000610b3e600454600754610657565b90508060076000828254610b529190611705565b90915550506002546001600160a01b031663a9059cbb610b7a6000546001600160a01b031690565b6040516001600160e01b031960e084901b1681526001600160a01b039091166004820152602481018490526044016108d0565b60ff82166000908152600b6020526040812060030154421015610bd257506000610766565b60ff83166000908152600b6020526040902060050154421015610bf757506000610766565b60ff83166000908152600b6020526040812060058101546006909101549091610c208284611705565b60ff871660008181526009602090815260408083206001600160a01b038b1680855290835281842054948452600a835281842090845290915281205492935091610c6a91906116ce565b905081421015610760576000610c8085426116ce565b9050600084610c9283620f424061171d565b610c9c919061173c565b60ff8a166000908152600a602090815260408083206001600160a01b038d16845290915281205491925090620f424090610cd790849061171d565b610ce1919061173c565b60ff8b1660009081526009602090815260408083206001600160a01b038e16845290915290205490915061075a90826116ce565b600354600160a01b900460ff16610d3e5760405162461bcd60e51b81526004016104aa90611681565b6000610d4a8233610bad565b60ff83166000908152600960209081526040808320338452909152812080549293508392909190610d7c908490611705565b909155505060025460405163a9059cbb60e01b8152336004820152602481018390526001600160a01b039091169063a9059cbb906044016020604051808303816000875af1158015610dd2573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610df6919061175e565b505050565b6000546001600160a01b03163314610e255760405162461bcd60e51b81526004016104aa9061164c565b600354600160a01b900460ff16610e4e5760405162461bcd60e51b81526004016104aa90611681565b6002546001600160a01b0384811691161415610ea15760405162461bcd60e51b81526020600482015260126024820152710c6c2dcdcdee840e4cac8cacada4094b29cb60731b60448201526064016104aa565b6040516370a0823160e01b81523060048201526000906001600160a01b038516906370a0823190602401602060405180830381865afa158015610ee8573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610f0c9190611780565b905082811015610f555760405162461bcd60e51b8152602060048201526014602482015273696e73756666696369656e742062616c616e636560601b60448201526064016104aa565b60405163a9059cbb60e01b81526001600160a01b0383811660048301526024820185905285169063a9059cbb906044016020604051808303816000875af1158015610fa4573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610fc8919061175e565b5050505050565b600354600160a01b900460ff16610ff85760405162461bcd60e51b81526004016104aa90611681565b60ff82166000908152600b6020526040902060020154421161105c5760405162461bcd60e51b815260206004820152601860248201527f646973747269627574696f6e206e6f742073746172746564000000000000000060448201526064016104aa565b60ff82166000908152600b602052604090206003015442106110b55760405162461bcd60e51b8152602060048201526012602482015271191a5cdd1c9a589d5d1a5bdb88195b99195960721b60448201526064016104aa565b60ff82166000908152600b60205260408120600181015490546110d891906116ce565b116111105760405162461bcd60e51b81526020600482015260086024820152671cdbdb19081bdd5d60c21b60448201526064016104aa565b60ff82166000908152600b6020526040812060040154611130908361173c565b60ff84166000908152600a60209081526040808320338452909152812080549293508392909190611162908490611705565b909155505060ff83166000908152600b60205260408120600101805483929061118c908490611705565b90915550506003546040516323b872dd60e01b8152336004820152306024820152604481018490526001600160a01b03909116906323b872dd906064016020604051808303816000875af11580156111e8573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061120c919061175e565b50505050565b6000546001600160a01b0316331461123c5760405162461bcd60e51b81526004016104aa9061164c565b600354600160a01b900460ff161561128c5760405162461bcd60e51b8152602060048201526013602482015272185b1c9958591e481a5b9a5d1a585b1a5e9959606a1b60448201526064016104aa565b600180546001600160a01b038681166001600160a01b0319928316179092556002805492881692909116821790556040516370a0823160e01b8152306004820152600091906370a0823190602401602060405180830381865afa1580156112f7573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061131b9190611780565b905080836113298685611705565b6113339190611705565b146113805760405162461bcd60e51b815260206004820152601860248201527f6d75737420616c6c6f6361746520616c6c20746f6b656e73000000000000000060448201526064016104aa565b5060065560055560045550506003805460ff60a01b1916600160a01b179055565b6000546001600160a01b031633146113cb5760405162461bcd60e51b81526004016104aa9061164c565b6113d4816113d7565b50565b6001600160a01b03811661143c5760405162461bcd60e51b815260206004820152602660248201527f4f776e61626c653a206e6577206f776e657220697320746865207a65726f206160448201526564647265737360d01b60648201526084016104aa565b600080546040516001600160a01b03808516939216917f8be0079c531659141344cd1fd0a4f28419497f9722a3daafe3b4186f6b6457e091a3600080546001600160a01b0319166001600160a01b0392909216919091179055565b60008060008060008060c087890312156114b057600080fd5b505084359660208601359650604086013595606081013595506080810135945060a0013592509050565b600080604083850312156114ed57600080fd5b50508035926020909101359150565b80356001600160a01b038116811461151357600080fd5b919050565b60006020828403121561152a57600080fd5b611533826114fc565b9392505050565b803560ff8116811461151357600080fd5b60006020828403121561155d57600080fd5b6115338261153a565b6000806040838503121561157957600080fd5b6115828361153a565b9150611590602084016114fc565b90509250929050565b6000806000606084860312156115ae57600080fd5b6115b7846114fc565b9250602084013591506115cc604085016114fc565b90509250925092565b600080604083850312156115e857600080fd5b6115f18361153a565b946020939093013593505050565b600080600080600060a0868803121561161757600080fd5b611620866114fc565b945061162e602087016114fc565b94979496505050506040830135926060810135926080909101359150565b6020808252818101527f4f776e61626c653a2063616c6c6572206973206e6f7420746865206f776e6572604082015260600190565b6020808252601c908201527f636f6e7472616374206d75737420626520696e697469616c697a656400000000604082015260600190565b634e487b7160e01b600052601160045260246000fd5b6000828210156116e0576116e06116b8565b500390565b600060ff821660ff8114156116fc576116fc6116b8565b60010192915050565b60008219821115611718576117186116b8565b500190565b6000816000190483118215151615611737576117376116b8565b500290565b60008261175957634e487b7160e01b600052601260045260246000fd5b500490565b60006020828403121561177057600080fd5b8151801515811461153357600080fd5b60006020828403121561179257600080fd5b505191905056fea26469706673582212203d65811d3b12b0c7815a9985111f96f0421e30d09f6e7818d09a9d030070356864736f6c634300080b0033";

    public static final String FUNC_BUY_TOKENS = "buy_tokens";

    public static final String FUNC_CLAIM_NETWORK_TOKENS = "claim_network_tokens";

    public static final String FUNC_CLAIM_TOKENS_FOR_DISTRIBUTION = "claim_tokens_for_distribution";

    public static final String FUNC_CLAIM_TREASURY_TOKENS = "claim_treasury_tokens";

    public static final String FUNC_CLAIMED_TOKENS = "claimed_tokens";

    public static final String FUNC_COMMUNITY_POOL = "community_pool";

    public static final String FUNC_CREATE_DISTRIBUTION = "create_distribution";

    public static final String FUNC_DAI = "dai";

    public static final String FUNC_DISTRIBUTION_COUNT = "distribution_count";

    public static final String FUNC_DISTRIBUTION_EVENTS = "distribution_events";

    public static final String FUNC_GET_AVAILABLE_TOKENS_5Y_VESTING = "get_available_tokens_5y_vesting";

    public static final String FUNC_GET_AVAILABLE_TOKENS_FOR_DISTRIBUTION = "get_available_tokens_for_distribution";

    public static final String FUNC_INITIALIZE = "initialize";

    public static final String FUNC_INITIALIZED = "initialized";

    public static final String FUNC_ISOWNER = "isOwner";

    public static final String FUNC_JYNX_PRO_BRIDGE = "jynx_pro_bridge";

    public static final String FUNC_JYNX_TOKEN = "jynx_token";

    public static final String FUNC_NETWORK_POOL = "network_pool";

    public static final String FUNC_NETWORK_POOL_CLAIMED = "network_pool_claimed";

    public static final String FUNC_OWNER = "owner";

    public static final String FUNC_RECLAIM_UNSOLD_TOKENS = "reclaim_unsold_tokens";

    public static final String FUNC_REDEEM_ERC20 = "redeem_erc20";

    public static final String FUNC_RENOUNCEOWNERSHIP = "renounceOwnership";

    public static final String FUNC_TRANSFEROWNERSHIP = "transferOwnership";

    public static final String FUNC_TREASURY = "treasury";

    public static final String FUNC_TREASURY_CLAIMED = "treasury_claimed";

    public static final String FUNC_UPDATE_DAI_ADDRESS = "update_dai_address";

    public static final String FUNC_USER_ALLOCATIONS = "user_allocations";

    public static final Event OWNERSHIPTRANSFERRED_EVENT = new Event("OwnershipTransferred", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}));
    ;

    @Deprecated
    protected JYNX_Distribution(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected JYNX_Distribution(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected JYNX_Distribution(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected JYNX_Distribution(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<OwnershipTransferredEventResponse> getOwnershipTransferredEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(OWNERSHIPTRANSFERRED_EVENT, transactionReceipt);
        ArrayList<OwnershipTransferredEventResponse> responses = new ArrayList<OwnershipTransferredEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            OwnershipTransferredEventResponse typedResponse = new OwnershipTransferredEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.previousOwner = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.newOwner = (String) eventValues.getIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<OwnershipTransferredEventResponse> ownershipTransferredEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, OwnershipTransferredEventResponse>() {
            @Override
            public OwnershipTransferredEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(OWNERSHIPTRANSFERRED_EVENT, log);
                OwnershipTransferredEventResponse typedResponse = new OwnershipTransferredEventResponse();
                typedResponse.log = log;
                typedResponse.previousOwner = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.newOwner = (String) eventValues.getIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<OwnershipTransferredEventResponse> ownershipTransferredEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(OWNERSHIPTRANSFERRED_EVENT));
        return ownershipTransferredEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> buy_tokens(BigInteger id, BigInteger amount) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_BUY_TOKENS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(id), 
                new org.web3j.abi.datatypes.generated.Uint256(amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> claim_network_tokens() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CLAIM_NETWORK_TOKENS, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> claim_tokens_for_distribution(BigInteger id) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CLAIM_TOKENS_FOR_DISTRIBUTION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(id)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> claim_treasury_tokens() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CLAIM_TREASURY_TOKENS, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> claimed_tokens(BigInteger param0, String param1) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_CLAIMED_TOKENS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(param0), 
                new org.web3j.abi.datatypes.Address(160, param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> community_pool() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_COMMUNITY_POOL, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> create_distribution(BigInteger total_tokens, BigInteger start_date, BigInteger end_date, BigInteger usd_rate, BigInteger cliff_timestamp, BigInteger vesting_duration) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CREATE_DISTRIBUTION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(total_tokens), 
                new org.web3j.abi.datatypes.generated.Uint256(start_date), 
                new org.web3j.abi.datatypes.generated.Uint256(end_date), 
                new org.web3j.abi.datatypes.generated.Uint256(usd_rate), 
                new org.web3j.abi.datatypes.generated.Uint256(cliff_timestamp), 
                new org.web3j.abi.datatypes.generated.Uint256(vesting_duration)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> dai() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_DAI, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> distribution_count() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_DISTRIBUTION_COUNT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Tuple8<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, Boolean>> distribution_events(BigInteger param0) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_DISTRIBUTION_EVENTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bool>() {}));
        return new RemoteFunctionCall<Tuple8<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, Boolean>>(function,
                new Callable<Tuple8<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, Boolean>>() {
                    @Override
                    public Tuple8<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, Boolean> call() throws Exception {
                        List<Type> results = executeCallMultipleValueReturn(function);
                        return new Tuple8<BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, Boolean>(
                                (BigInteger) results.get(0).getValue(), 
                                (BigInteger) results.get(1).getValue(), 
                                (BigInteger) results.get(2).getValue(), 
                                (BigInteger) results.get(3).getValue(), 
                                (BigInteger) results.get(4).getValue(), 
                                (BigInteger) results.get(5).getValue(), 
                                (BigInteger) results.get(6).getValue(), 
                                (Boolean) results.get(7).getValue());
                    }
                });
    }

    public RemoteFunctionCall<BigInteger> get_available_tokens_5y_vesting(BigInteger total_balance, BigInteger claimed_balance) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GET_AVAILABLE_TOKENS_5Y_VESTING, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(total_balance), 
                new org.web3j.abi.datatypes.generated.Uint256(claimed_balance)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> get_available_tokens_for_distribution(BigInteger id, String user) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GET_AVAILABLE_TOKENS_FOR_DISTRIBUTION, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(id), 
                new org.web3j.abi.datatypes.Address(160, user)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> initialize(String jynx_token_address, String jynx_bridge_address, BigInteger _treasury, BigInteger _network_pool, BigInteger _community_pool) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_INITIALIZE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, jynx_token_address), 
                new org.web3j.abi.datatypes.Address(160, jynx_bridge_address), 
                new org.web3j.abi.datatypes.generated.Uint256(_treasury), 
                new org.web3j.abi.datatypes.generated.Uint256(_network_pool), 
                new org.web3j.abi.datatypes.generated.Uint256(_community_pool)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> initialized() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_INITIALIZED, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<Boolean> isOwner() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_ISOWNER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<String> jynx_pro_bridge() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_JYNX_PRO_BRIDGE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<String> jynx_token() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_JYNX_TOKEN, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<BigInteger> network_pool() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_NETWORK_POOL, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> network_pool_claimed() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_NETWORK_POOL_CLAIMED, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<String> owner() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_OWNER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeRemoteCallSingleValueReturn(function, String.class);
    }

    public RemoteFunctionCall<TransactionReceipt> reclaim_unsold_tokens(BigInteger id) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_RECLAIM_UNSOLD_TOKENS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(id)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> redeem_erc20(String erc20_address, BigInteger amount, String destination) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REDEEM_ERC20, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, erc20_address), 
                new org.web3j.abi.datatypes.generated.Uint256(amount), 
                new org.web3j.abi.datatypes.Address(160, destination)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> renounceOwnership() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_RENOUNCEOWNERSHIP, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> transferOwnership(String newOwner) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TRANSFEROWNERSHIP, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, newOwner)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> treasury() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_TREASURY, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> treasury_claimed() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_TREASURY_CLAIMED, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> update_dai_address(String dai_address) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_UPDATE_DAI_ADDRESS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, dai_address)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> user_allocations(BigInteger param0, String param1) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_USER_ALLOCATIONS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint8(param0), 
                new org.web3j.abi.datatypes.Address(160, param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    @Deprecated
    public static JYNX_Distribution load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new JYNX_Distribution(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static JYNX_Distribution load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new JYNX_Distribution(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static JYNX_Distribution load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new JYNX_Distribution(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static JYNX_Distribution load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new JYNX_Distribution(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<JYNX_Distribution> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider, String dai_address) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, dai_address)));
        return deployRemoteCall(JYNX_Distribution.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
    }

    public static RemoteCall<JYNX_Distribution> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider, String dai_address) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, dai_address)));
        return deployRemoteCall(JYNX_Distribution.class, web3j, transactionManager, contractGasProvider, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<JYNX_Distribution> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit, String dai_address) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, dai_address)));
        return deployRemoteCall(JYNX_Distribution.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<JYNX_Distribution> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit, String dai_address) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, dai_address)));
        return deployRemoteCall(JYNX_Distribution.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    public static class OwnershipTransferredEventResponse extends BaseEventResponse {
        public String previousOwner;

        public String newOwner;
    }
}
