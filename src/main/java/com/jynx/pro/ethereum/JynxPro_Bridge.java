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
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
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
public class JynxPro_Bridge extends Contract {
    public static final String BINARY = "608060405260006008553480156200001657600080fd5b5060405162001a4238038062001a428339810160408190526200003991620000cb565b600180546001600160a01b038086166001600160a01b031992831617835560008054918616919092161781556009805461ffff851661ffff19909116179055338152600560205260408120805460ff19169092179091556008805491620000a0836200011f565b919050555050505062000149565b80516001600160a01b0381168114620000c657600080fd5b919050565b600080600060608486031215620000e157600080fd5b620000ec84620000ae565b9250620000fc60208501620000ae565b9150604084015161ffff811681146200011457600080fd5b809150509250925092565b60006000198214156200014257634e487b7160e01b600052601160045260246000fd5b5060010190565b6118e980620001596000396000f3fe608060405234801561001057600080fd5b50600436106101215760003560e01c8063bc7b7d13116100ad578063dd01ba0b11610071578063dd01ba0b146102b6578063e0d24312146102c9578063f11b8188146102dc578063f8e3a660146102ff578063fda30ce81461031257600080fd5b8063bc7b7d1314610254578063be66bc9c14610267578063c76de35814610287578063c9ea6eea1461029a578063d12c525a146102ad57600080fd5b8063660c6f99116100f4578063660c6f99146101bf578063736c0d5b146101d257806398c5f73e146101f5578063a91eafd214610208578063ba73659a1461024157600080fd5b80634422545f146101265780634dff94c51461014c578063518417421461017f57806352814936146101aa575b600080fd5b6009546101349061ffff1681565b60405161ffff90911681526020015b60405180910390f35b61016f61015a3660046111ed565b60066020526000908152604090205460ff1681565b6040519015158152602001610143565b600054610192906001600160a01b031681565b6040516001600160a01b039091168152602001610143565b6101bd6101b8366004611222565b610325565b005b6101bd6101cd36600461124c565b610464565b61016f6101e036600461126e565b60056020526000908152604090205460ff1681565b6101bd610203366004611347565b610571565b610233610216366004611222565b600360209081526000928352604080842090915290825290205481565b604051908152602001610143565b61016f61024f36600461139e565b6106c6565b600154610192906001600160a01b031681565b61023361027536600461126e565b60026020526000908152604090205481565b6101bd610295366004611347565b610992565b6101bd6102a836600461140b565b610a61565b61023360085481565b6101bd6102c436600461124c565b610b38565b6101bd6102d73660046114e8565b610c9c565b61016f6102ea36600461126e565b60046020526000908152604090205460ff1681565b6101bd61030d366004611347565b610f69565b6101bd610320366004611347565b6110be565b6001600160a01b03821660009081526004602052604090205460ff1661039e5760405162461bcd60e51b815260206004820152602360248201527f4465706f73697473206e6f7420656e61626c656420666f7220746869732061736044820152621cd95d60ea1b60648201526084015b60405180910390fd5b6040516323b872dd60e01b8152336004820152306024820152604481018290526001600160a01b038316906323b872dd906064016020604051808303816000875af11580156103f1573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061041591906115f3565b50604080513381526001600160a01b03841660208201529081018290527f7768ee8fb3cc28f20ba97ea5625e853503142e8c6dcd120972d5d2b9216aae00906060015b60405180910390a15050565b3360009081526003602090815260408083208484529091528120805484929061048e90849061162b565b909155505033600090815260026020526040812080548492906104b290849061162b565b90915550506001546040516323b872dd60e01b8152336004820152306024820152604481018490526001600160a01b03909116906323b872dd906064016020604051808303816000875af115801561050e573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061053291906115f3565b5060408051338152602081018490529081018290527f3bb3e1583e0be0f495212a4057fd0d9019b68aa5e4a6024336cd63875cd697a990606001610458565b6001600160a01b03831660009081526005602052604090205460ff166105d05760405162461bcd60e51b81526020600482015260146024820152732ab9b2b91034b9903737ba10309039b4b3b732b960611b6044820152606401610395565b604080516001600160a01b0385166020820152908101839052606080820152600d60808201526c3932b6b7bb32afb9b4b3b732b960991b60a082015260009060c001604051602081830303815290604052905061062e8282856106c6565b61064a5760405162461bcd60e51b815260040161039590611643565b6001600160a01b0384166000908152600560205260408120805460ff1916905560088054916106788361166e565b9091555050604080516001600160a01b0386168152602081018590527f497a8bc361c322f1353bdb19a75c263ae04ac79e816100cc70dfb70d15e85a3191015b60405180910390a150505050565b6000604184516106d6919061169b565b1561071a5760405162461bcd60e51b81526020600482015260146024820152730c4c2c840e6d2cedcc2e8eae4ca40d8cadccee8d60631b6044820152606401610395565b60008281526006602052604090205460ff16156107665760405162461bcd60e51b815260206004820152600a6024820152691b9bdb98d9481d5cd95960b21b6044820152606401610395565b600080843360405160200161077c9291906116af565b60408051601f19818403018152919052805160209182012091505b86516107a490602061162b565b811015610947578681018051602082015160409092015190919060001a7f7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a08211156108265760405162461bcd60e51b815260206004820152601260248201527136b0b63630b136329039b4b39032b93937b960711b6044820152606401610395565b601b8160ff1610156108405761083d601b82611715565b90505b6040805160008082526020820180845288905260ff841692820192909252606081018590526080810184905260019060a0016020604051602081039080840390855afa158015610894573d6000803e3d6000fd5b505060408051601f1901516001600160a01b03811660009081526005602052919091205490925060ff16905080156108ef575060008681526007602090815260408083206001600160a01b038516845290915290205460ff16155b1561092f5760008681526007602090815260408083206001600160a01b03851684529091529020805460ff191660011790558661092b8161173a565b9750505b50505050604181610940919061162b565b9050610797565b506000848152600660205260409020805460ff1916600117905560095460085461ffff9091169061097d60ff85166103e861175a565b6109879190611779565b119695505050505050565b604080516001600160a01b0385166020820152908101839052606080820152600c60808201526b1c995b5bdd9957d85cdcd95d60a21b60a082015260009060c00160405160208183030381529060405290506109ef8282856106c6565b610a0b5760405162461bcd60e51b815260040161039590611643565b6001600160a01b038416600081815260046020908152604091829020805460ff19169055815192835282018590527f4fc1eea408d8ac4a2183d526e254ca649c7f864b51614136a84ab5119387596691016106b8565b600082604051602001610aa291815260406020820181905260149082015273636c61696d5f6e6574776f726b5f746f6b656e7360601b606082015260800190565b6040516020818303038152906040529050610abe8282856106c6565b610ada5760405162461bcd60e51b815260040161039590611643565b60008054604080516323c613df60e11b815290516001600160a01b039092169263478c27be9260048084019382900301818387803b158015610b1b57600080fd5b505af1158015610b2f573d6000803e3d6000fd5b50505050505050565b336000908152600360209081526040808320848452909152902054821115610b955760405162461bcd60e51b815260206004820152601060248201526f4e6f7420656e6f756768207374616b6560801b6044820152606401610395565b33600090815260036020908152604080832084845290915281208054849290610bbf90849061178d565b90915550503360009081526002602052604081208054849290610be390849061178d565b909155505060015460405163a9059cbb60e01b8152336004820152602481018490526001600160a01b039091169063a9059cbb906044016020604051808303816000875af1158015610c39573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610c5d91906115f3565b5060408051338152602081018490529081018290527fbf29d42e4b7958feaf33266d1c701ac5e3da189d437c92b1f4e8ba64f8a7ae8b90606001610458565b8351855114610d065760405162461bcd60e51b815260206004820152603060248201527f616d6f756e747320616e642064657374696e6174696f6e73206d75737420626560448201526f040cae2eac2d840d2dc40d8cadccee8d60831b6064820152608401610395565b8251855114610d7d5760405162461bcd60e51b815260206004820152603860248201527f61737365745f61646472657373657320616e642064657374696e6174696f6e7360448201527f206d75737420626520657175616c20696e206c656e67746800000000000000006064820152608401610395565b600085858585604051602001610d9694939291906117e8565b6040516020818303038152906040529050610db28282856106c6565b610dce5760405162461bcd60e51b815260040161039590611643565b60005b8651811015610b2f57848181518110610dec57610dec611882565b60200260200101516001600160a01b031663a9059cbb888381518110610e1457610e14611882565b6020026020010151888481518110610e2e57610e2e611882565b60200260200101516040518363ffffffff1660e01b8152600401610e679291906001600160a01b03929092168252602082015260400190565b6020604051808303816000875af1158015610e86573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610eaa91906115f3565b507fbb1c528ca254542ba3097499865a4525b31ddd7ef6385c80ebeb316d0ae0f59b878281518110610ede57610ede611882565b6020026020010151868381518110610ef857610ef8611882565b6020026020010151888481518110610f1257610f12611882565b602002602001015187604051610f4f94939291906001600160a01b0394851681529290931660208301526040820152606081019190915260800190565b60405180910390a180610f6181611898565b915050610dd1565b6001600160a01b03831660009081526005602052604090205460ff1615610fd25760405162461bcd60e51b815260206004820152601860248201527f5573657220697320616c72656164792061207369676e657200000000000000006044820152606401610395565b604080516001600160a01b0385166020820152908101839052606080820152600a60808201526930b2322fb9b4b3b732b960b11b60a082015260009060c001604051602081830303815290604052905061102d8282856106c6565b6110495760405162461bcd60e51b815260040161039590611643565b6001600160a01b0384166000908152600560205260408120805460ff19166001179055600880549161107a83611898565b9091555050604080516001600160a01b0386168152602081018590527ff9a3b2c9bc1f8ead8d9e72ca4c7ba3ec55f2e4a128a8fccdec03f577d64637b691016106b8565b604080516001600160a01b0385166020820152908101839052606080820152600960808201526818591917d85cdcd95d60ba1b60a082015260009060c00160405160208183030381529060405290506111188282856106c6565b6111345760405162461bcd60e51b815260040161039590611643565b6001600160a01b03841660009081526004602052604090205460ff16156111945760405162461bcd60e51b8152602060048201526014602482015273417373657420616c72656164792065786973747360601b6044820152606401610395565b6001600160a01b038416600081815260046020908152604091829020805460ff19166001179055815192835282018590527f60abe041d1b76452cbc2c27bcfff40ca2f40a0440f2663684bcd3b69c44fe76c91016106b8565b6000602082840312156111ff57600080fd5b5035919050565b80356001600160a01b038116811461121d57600080fd5b919050565b6000806040838503121561123557600080fd5b61123e83611206565b946020939093013593505050565b6000806040838503121561125f57600080fd5b50508035926020909101359150565b60006020828403121561128057600080fd5b61128982611206565b9392505050565b634e487b7160e01b600052604160045260246000fd5b604051601f8201601f1916810167ffffffffffffffff811182821017156112cf576112cf611290565b604052919050565b600082601f8301126112e857600080fd5b813567ffffffffffffffff81111561130257611302611290565b611315601f8201601f19166020016112a6565b81815284602083860101111561132a57600080fd5b816020850160208301376000918101602001919091529392505050565b60008060006060848603121561135c57600080fd5b61136584611206565b925060208401359150604084013567ffffffffffffffff81111561138857600080fd5b611394868287016112d7565b9150509250925092565b6000806000606084860312156113b357600080fd5b833567ffffffffffffffff808211156113cb57600080fd5b6113d7878388016112d7565b945060208601359150808211156113ed57600080fd5b506113fa868287016112d7565b925050604084013590509250925092565b6000806040838503121561141e57600080fd5b82359150602083013567ffffffffffffffff81111561143c57600080fd5b611448858286016112d7565b9150509250929050565b600067ffffffffffffffff82111561146c5761146c611290565b5060051b60200190565b600082601f83011261148757600080fd5b8135602061149c61149783611452565b6112a6565b82815260059290921b840181019181810190868411156114bb57600080fd5b8286015b848110156114dd576114d081611206565b83529183019183016114bf565b509695505050505050565b600080600080600060a0868803121561150057600080fd5b853567ffffffffffffffff8082111561151857600080fd5b61152489838a01611476565b965060209150818801358181111561153b57600080fd5b8801601f81018a1361154c57600080fd5b803561155a61149782611452565b81815260059190911b8201840190848101908c83111561157957600080fd5b928501925b828410156115975783358252928501929085019061157e565b985050505060408801359150808211156115b057600080fd5b6115bc89838a01611476565b94506060880135935060808801359150808211156115d957600080fd5b506115e6888289016112d7565b9150509295509295909350565b60006020828403121561160557600080fd5b8151801515811461128957600080fd5b634e487b7160e01b600052601160045260246000fd5b6000821982111561163e5761163e611615565b500190565b60208082526011908201527014da59db985d1d5c99481a5b9d985b1a59607a1b604082015260600190565b60008161167d5761167d611615565b506000190190565b634e487b7160e01b600052601260045260246000fd5b6000826116aa576116aa611685565b500690565b604081526000835180604084015260005b818110156116dd57602081870181015160608684010152016116c0565b818111156116ef576000606083860101525b506001600160a01b0393909316602083015250601f91909101601f191601606001919050565b600060ff821660ff84168060ff0382111561173257611732611615565b019392505050565b600060ff821660ff81141561175157611751611615565b60010192915050565b600081600019048311821515161561177457611774611615565b500290565b60008261178857611788611685565b500490565b60008282101561179f5761179f611615565b500390565b600081518084526020808501945080840160005b838110156117dd5781516001600160a01b0316875295820195908201906001016117b8565b509495945050505050565b60a0815260006117fb60a08301876117a4565b82810360208481019190915286518083528782019282019060005b8181101561183257845183529383019391830191600101611816565b5050848103604086015261184681886117a4565b6060860196909652508385036080909401939093525050600f82526e77697468647261775f61737365747360881b908201526040019392505050565b634e487b7160e01b600052603260045260246000fd5b60006000198214156118ac576118ac611615565b506001019056fea26469706673582212203f1a508873de613a6afec8ec76d118cdad399e42f16d419d7479e7508b1ccd8864736f6c634300080b0033";

    public static final String FUNC_ADD_ASSET = "add_asset";

    public static final String FUNC_ADD_SIGNER = "add_signer";

    public static final String FUNC_ADD_STAKE = "add_stake";

    public static final String FUNC_ASSETS = "assets";

    public static final String FUNC_CLAIM_NETWORK_TOKENS = "claim_network_tokens";

    public static final String FUNC_DEPOSIT_ASSET = "deposit_asset";

    public static final String FUNC_JYNX_DISTRIBUTION = "jynx_distribution";

    public static final String FUNC_JYNX_TOKEN = "jynx_token";

    public static final String FUNC_REMOVE_ASSET = "remove_asset";

    public static final String FUNC_REMOVE_SIGNER = "remove_signer";

    public static final String FUNC_REMOVE_STAKE = "remove_stake";

    public static final String FUNC_SIGNER_COUNT = "signer_count";

    public static final String FUNC_SIGNERS = "signers";

    public static final String FUNC_SIGNING_THRESHOLD = "signing_threshold";

    public static final String FUNC_USED_NONCES = "used_nonces";

    public static final String FUNC_USER_STAKE = "user_stake";

    public static final String FUNC_USER_TOTAL_STAKE = "user_total_stake";

    public static final String FUNC_VERIFY_SIGNATURES = "verify_signatures";

    public static final String FUNC_WITHDRAW_ASSETS = "withdraw_assets";

    public static final Event ADDASSET_EVENT = new Event("AddAsset", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event ADDSIGNER_EVENT = new Event("AddSigner", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event ADDSTAKE_EVENT = new Event("AddStake", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bytes32>() {}));
    ;

    public static final Event DEPOSITASSET_EVENT = new Event("DepositAsset", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event REMOVEASSET_EVENT = new Event("RemoveAsset", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event REMOVESIGNER_EVENT = new Event("RemoveSigner", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event REMOVESTAKE_EVENT = new Event("RemoveStake", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>() {}, new TypeReference<Bytes32>() {}));
    ;

    public static final Event WITHDRAWASSET_EVENT = new Event("WithdrawAsset", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Address>() {}, new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {}));
    ;

    @Deprecated
    protected JynxPro_Bridge(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected JynxPro_Bridge(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected JynxPro_Bridge(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected JynxPro_Bridge(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<AddAssetEventResponse> getAddAssetEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(ADDASSET_EVENT, transactionReceipt);
        ArrayList<AddAssetEventResponse> responses = new ArrayList<AddAssetEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            AddAssetEventResponse typedResponse = new AddAssetEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.asset = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<AddAssetEventResponse> addAssetEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, AddAssetEventResponse>() {
            @Override
            public AddAssetEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(ADDASSET_EVENT, log);
                AddAssetEventResponse typedResponse = new AddAssetEventResponse();
                typedResponse.log = log;
                typedResponse.asset = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<AddAssetEventResponse> addAssetEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ADDASSET_EVENT));
        return addAssetEventFlowable(filter);
    }

    public List<AddSignerEventResponse> getAddSignerEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(ADDSIGNER_EVENT, transactionReceipt);
        ArrayList<AddSignerEventResponse> responses = new ArrayList<AddSignerEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            AddSignerEventResponse typedResponse = new AddSignerEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.signer = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<AddSignerEventResponse> addSignerEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, AddSignerEventResponse>() {
            @Override
            public AddSignerEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(ADDSIGNER_EVENT, log);
                AddSignerEventResponse typedResponse = new AddSignerEventResponse();
                typedResponse.log = log;
                typedResponse.signer = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<AddSignerEventResponse> addSignerEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ADDSIGNER_EVENT));
        return addSignerEventFlowable(filter);
    }

    public List<AddStakeEventResponse> getAddStakeEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(ADDSTAKE_EVENT, transactionReceipt);
        ArrayList<AddStakeEventResponse> responses = new ArrayList<AddStakeEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            AddStakeEventResponse typedResponse = new AddStakeEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.jynx_key = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<AddStakeEventResponse> addStakeEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, AddStakeEventResponse>() {
            @Override
            public AddStakeEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(ADDSTAKE_EVENT, log);
                AddStakeEventResponse typedResponse = new AddStakeEventResponse();
                typedResponse.log = log;
                typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.jynx_key = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<AddStakeEventResponse> addStakeEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(ADDSTAKE_EVENT));
        return addStakeEventFlowable(filter);
    }

    public List<DepositAssetEventResponse> getDepositAssetEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(DEPOSITASSET_EVENT, transactionReceipt);
        ArrayList<DepositAssetEventResponse> responses = new ArrayList<DepositAssetEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DepositAssetEventResponse typedResponse = new DepositAssetEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.asset = (String) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<DepositAssetEventResponse> depositAssetEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, DepositAssetEventResponse>() {
            @Override
            public DepositAssetEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(DEPOSITASSET_EVENT, log);
                DepositAssetEventResponse typedResponse = new DepositAssetEventResponse();
                typedResponse.log = log;
                typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.asset = (String) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<DepositAssetEventResponse> depositAssetEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DEPOSITASSET_EVENT));
        return depositAssetEventFlowable(filter);
    }

    public List<RemoveAssetEventResponse> getRemoveAssetEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(REMOVEASSET_EVENT, transactionReceipt);
        ArrayList<RemoveAssetEventResponse> responses = new ArrayList<RemoveAssetEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RemoveAssetEventResponse typedResponse = new RemoveAssetEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.asset = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<RemoveAssetEventResponse> removeAssetEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, RemoveAssetEventResponse>() {
            @Override
            public RemoveAssetEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(REMOVEASSET_EVENT, log);
                RemoveAssetEventResponse typedResponse = new RemoveAssetEventResponse();
                typedResponse.log = log;
                typedResponse.asset = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<RemoveAssetEventResponse> removeAssetEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(REMOVEASSET_EVENT));
        return removeAssetEventFlowable(filter);
    }

    public List<RemoveSignerEventResponse> getRemoveSignerEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(REMOVESIGNER_EVENT, transactionReceipt);
        ArrayList<RemoveSignerEventResponse> responses = new ArrayList<RemoveSignerEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RemoveSignerEventResponse typedResponse = new RemoveSignerEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.signer = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<RemoveSignerEventResponse> removeSignerEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, RemoveSignerEventResponse>() {
            @Override
            public RemoveSignerEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(REMOVESIGNER_EVENT, log);
                RemoveSignerEventResponse typedResponse = new RemoveSignerEventResponse();
                typedResponse.log = log;
                typedResponse.signer = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<RemoveSignerEventResponse> removeSignerEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(REMOVESIGNER_EVENT));
        return removeSignerEventFlowable(filter);
    }

    public List<RemoveStakeEventResponse> getRemoveStakeEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(REMOVESTAKE_EVENT, transactionReceipt);
        ArrayList<RemoveStakeEventResponse> responses = new ArrayList<RemoveStakeEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RemoveStakeEventResponse typedResponse = new RemoveStakeEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.jynx_key = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<RemoveStakeEventResponse> removeStakeEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, RemoveStakeEventResponse>() {
            @Override
            public RemoveStakeEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(REMOVESTAKE_EVENT, log);
                RemoveStakeEventResponse typedResponse = new RemoveStakeEventResponse();
                typedResponse.log = log;
                typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.jynx_key = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<RemoveStakeEventResponse> removeStakeEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(REMOVESTAKE_EVENT));
        return removeStakeEventFlowable(filter);
    }

    public List<WithdrawAssetEventResponse> getWithdrawAssetEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(WITHDRAWASSET_EVENT, transactionReceipt);
        ArrayList<WithdrawAssetEventResponse> responses = new ArrayList<WithdrawAssetEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            WithdrawAssetEventResponse typedResponse = new WithdrawAssetEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.asset = (String) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<WithdrawAssetEventResponse> withdrawAssetEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, WithdrawAssetEventResponse>() {
            @Override
            public WithdrawAssetEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(WITHDRAWASSET_EVENT, log);
                WithdrawAssetEventResponse typedResponse = new WithdrawAssetEventResponse();
                typedResponse.log = log;
                typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.asset = (String) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.amount = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<WithdrawAssetEventResponse> withdrawAssetEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(WITHDRAWASSET_EVENT));
        return withdrawAssetEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> add_asset(String _address, BigInteger _nonce, byte[] _signatures) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_ADD_ASSET, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _address), 
                new org.web3j.abi.datatypes.generated.Uint256(_nonce), 
                new org.web3j.abi.datatypes.DynamicBytes(_signatures)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> add_signer(String _signer, BigInteger _nonce, byte[] _signatures) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_ADD_SIGNER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _signer), 
                new org.web3j.abi.datatypes.generated.Uint256(_nonce), 
                new org.web3j.abi.datatypes.DynamicBytes(_signatures)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> add_stake(BigInteger _amount, byte[] _jynx_key) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_ADD_STAKE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_amount), 
                new org.web3j.abi.datatypes.generated.Bytes32(_jynx_key)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<Boolean> assets(String param0) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_ASSETS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<TransactionReceipt> claim_network_tokens(BigInteger _nonce, byte[] _signatures) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_CLAIM_NETWORK_TOKENS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_nonce), 
                new org.web3j.abi.datatypes.DynamicBytes(_signatures)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> deposit_asset(String _address, BigInteger _amount) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_DEPOSIT_ASSET, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _address), 
                new org.web3j.abi.datatypes.generated.Uint256(_amount)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<String> jynx_distribution() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_JYNX_DISTRIBUTION, 
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

    public RemoteFunctionCall<TransactionReceipt> remove_asset(String _address, BigInteger _nonce, byte[] _signatures) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REMOVE_ASSET, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _address), 
                new org.web3j.abi.datatypes.generated.Uint256(_nonce), 
                new org.web3j.abi.datatypes.DynamicBytes(_signatures)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> remove_signer(String _signer, BigInteger _nonce, byte[] _signatures) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REMOVE_SIGNER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _signer), 
                new org.web3j.abi.datatypes.generated.Uint256(_nonce), 
                new org.web3j.abi.datatypes.DynamicBytes(_signatures)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> remove_stake(BigInteger _amount, byte[] _jynx_key) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REMOVE_STAKE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_amount), 
                new org.web3j.abi.datatypes.generated.Bytes32(_jynx_key)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> signer_count() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_SIGNER_COUNT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Boolean> signers(String param0) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_SIGNERS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<BigInteger> signing_threshold() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_SIGNING_THRESHOLD, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint16>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<Boolean> used_nonces(BigInteger param0) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_USED_NONCES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    public RemoteFunctionCall<BigInteger> user_stake(String param0, byte[] param1) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_USER_STAKE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0), 
                new org.web3j.abi.datatypes.generated.Bytes32(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> user_total_stake(String param0) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_USER_TOTAL_STAKE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, param0)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> verify_signatures(byte[] _signatures, byte[] _message, BigInteger _nonce) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_VERIFY_SIGNATURES, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(_signatures), 
                new org.web3j.abi.datatypes.DynamicBytes(_message), 
                new org.web3j.abi.datatypes.generated.Uint256(_nonce)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> withdraw_assets(List<String> destinations, List<BigInteger> amounts, List<String> asset_addresses, BigInteger _nonce, byte[] _signatures) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_WITHDRAW_ASSETS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(destinations, org.web3j.abi.datatypes.Address.class)), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(amounts, org.web3j.abi.datatypes.generated.Uint256.class)), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(asset_addresses, org.web3j.abi.datatypes.Address.class)), 
                new org.web3j.abi.datatypes.generated.Uint256(_nonce), 
                new org.web3j.abi.datatypes.DynamicBytes(_signatures)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static JynxPro_Bridge load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new JynxPro_Bridge(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static JynxPro_Bridge load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new JynxPro_Bridge(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static JynxPro_Bridge load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new JynxPro_Bridge(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static JynxPro_Bridge load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new JynxPro_Bridge(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<JynxPro_Bridge> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider, String jynx_token_address, String jynx_distribution_address, BigInteger _signing_threshold) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, jynx_token_address), 
                new org.web3j.abi.datatypes.Address(160, jynx_distribution_address), 
                new org.web3j.abi.datatypes.generated.Uint16(_signing_threshold)));
        return deployRemoteCall(JynxPro_Bridge.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
    }

    public static RemoteCall<JynxPro_Bridge> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider, String jynx_token_address, String jynx_distribution_address, BigInteger _signing_threshold) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, jynx_token_address), 
                new org.web3j.abi.datatypes.Address(160, jynx_distribution_address), 
                new org.web3j.abi.datatypes.generated.Uint16(_signing_threshold)));
        return deployRemoteCall(JynxPro_Bridge.class, web3j, transactionManager, contractGasProvider, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<JynxPro_Bridge> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit, String jynx_token_address, String jynx_distribution_address, BigInteger _signing_threshold) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, jynx_token_address), 
                new org.web3j.abi.datatypes.Address(160, jynx_distribution_address), 
                new org.web3j.abi.datatypes.generated.Uint16(_signing_threshold)));
        return deployRemoteCall(JynxPro_Bridge.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<JynxPro_Bridge> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit, String jynx_token_address, String jynx_distribution_address, BigInteger _signing_threshold) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, jynx_token_address), 
                new org.web3j.abi.datatypes.Address(160, jynx_distribution_address), 
                new org.web3j.abi.datatypes.generated.Uint16(_signing_threshold)));
        return deployRemoteCall(JynxPro_Bridge.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    public static class AddAssetEventResponse extends BaseEventResponse {
        public String asset;

        public BigInteger nonce;
    }

    public static class AddSignerEventResponse extends BaseEventResponse {
        public String signer;

        public BigInteger nonce;
    }

    public static class AddStakeEventResponse extends BaseEventResponse {
        public String user;

        public BigInteger amount;

        public byte[] jynx_key;
    }

    public static class DepositAssetEventResponse extends BaseEventResponse {
        public String user;

        public String asset;

        public BigInteger amount;
    }

    public static class RemoveAssetEventResponse extends BaseEventResponse {
        public String asset;

        public BigInteger nonce;
    }

    public static class RemoveSignerEventResponse extends BaseEventResponse {
        public String signer;

        public BigInteger nonce;
    }

    public static class RemoveStakeEventResponse extends BaseEventResponse {
        public String user;

        public BigInteger amount;

        public byte[] jynx_key;
    }

    public static class WithdrawAssetEventResponse extends BaseEventResponse {
        public String user;

        public String asset;

        public BigInteger amount;

        public BigInteger nonce;
    }
}
