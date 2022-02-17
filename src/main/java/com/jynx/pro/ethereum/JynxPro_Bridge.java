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
    public static final String BINARY = "608060405260006008553480156200001657600080fd5b5060405162001a4338038062001a438339810160408190526200003991620000cb565b600180546001600160a01b038086166001600160a01b031992831617835560008054918616919092161781556009805461ffff851661ffff19909116179055338152600560205260408120805460ff19169092179091556008805491620000a0836200011f565b919050555050505062000149565b80516001600160a01b0381168114620000c657600080fd5b919050565b600080600060608486031215620000e157600080fd5b620000ec84620000ae565b9250620000fc60208501620000ae565b9150604084015161ffff811681146200011457600080fd5b809150509250925092565b60006000198214156200014257634e487b7160e01b600052601160045260246000fd5b5060010190565b6118ea80620001596000396000f3fe608060405234801561001057600080fd5b50600436106101215760003560e01c8063be66bc9c116100ad578063e0d2431211610071578063e0d24312146102b6578063f11b8188146102c9578063f7683932146102ec578063f8e3a660146102ff578063fda30ce81461031257600080fd5b8063be66bc9c14610254578063c76de35814610274578063c9ea6eea14610287578063d12c525a1461029a578063dd01ba0b146102a357600080fd5b8063736c0d5b116100f4578063736c0d5b146101bf57806398c5f73e146101e2578063a91eafd2146101f5578063ba73659a1461022e578063bc7b7d131461024157600080fd5b80634422545f146101265780634dff94c51461014c578063518417421461017f578063660c6f99146101aa575b600080fd5b6009546101349061ffff1681565b60405161ffff90911681526020015b60405180910390f35b61016f61015a3660046111bb565b60066020526000908152604090205460ff1681565b6040519015158152602001610143565b600054610192906001600160a01b031681565b6040516001600160a01b039091168152602001610143565b6101bd6101b83660046111d4565b610325565b005b61016f6101cd366004611212565b60056020526000908152604090205460ff1681565b6101bd6101f03660046112eb565b610430565b610220610203366004611342565b600360209081526000928352604080842090915290825290205481565b604051908152602001610143565b61016f61023c36600461136c565b61058a565b600154610192906001600160a01b031681565b610220610262366004611212565b60026020526000908152604090205481565b6101bd6102823660046112eb565b610856565b6101bd6102953660046113d9565b610921565b61022060085481565b6101bd6102b13660046111d4565b6109f8565b6101bd6102c43660046114b6565b610b52565b61016f6102d7366004611212565b60046020526000908152604090205460ff1681565b6101bd6102fa3660046115c1565b610e0c565b6101bd61030d3660046112eb565b610f3f565b6101bd6103203660046112eb565b611090565b3360009081526003602090815260408083208484529091528120805484929061034f90849061160a565b9091555050336000908152600260205260408120805484929061037390849061160a565b90915550506001546040516323b872dd60e01b8152336004820152306024820152604481018490526001600160a01b03909116906323b872dd906064016020604051808303816000875af11580156103cf573d6000803e3d6000fd5b505050506040513d601f19601f820116820180604052508101906103f39190611622565b50604051338152819083907f3bb3e1583e0be0f495212a4057fd0d9019b68aa5e4a6024336cd63875cd697a9906020015b60405180910390a35050565b6001600160a01b03831660009081526005602052604090205460ff166104945760405162461bcd60e51b81526020600482015260146024820152732ab9b2b91034b9903737ba10309039b4b3b732b960611b60448201526064015b60405180910390fd5b604080516001600160a01b0385166020820152908101839052606080820152600d60808201526c3932b6b7bb32afb9b4b3b732b960991b60a082015260009060c00160405160208183030381529060405290506104f282828561058a565b61050e5760405162461bcd60e51b815260040161048b90611644565b6001600160a01b0384166000908152600560205260408120805460ff19169055600880549161053c8361166f565b9190505550836001600160a01b03167f497a8bc361c322f1353bdb19a75c263ae04ac79e816100cc70dfb70d15e85a318460405161057c91815260200190565b60405180910390a250505050565b60006041845161059a919061169c565b156105de5760405162461bcd60e51b81526020600482015260146024820152730c4c2c840e6d2cedcc2e8eae4ca40d8cadccee8d60631b604482015260640161048b565b60008281526006602052604090205460ff161561062a5760405162461bcd60e51b815260206004820152600a6024820152691b9bdb98d9481d5cd95960b21b604482015260640161048b565b60008084336040516020016106409291906116b0565b60408051601f19818403018152919052805160209182012091505b865161066890602061160a565b81101561080b578681018051602082015160409092015190919060001a7f7fffffffffffffffffffffffffffffff5d576e7357a4501ddfe92f46681b20a08211156106ea5760405162461bcd60e51b815260206004820152601260248201527136b0b63630b136329039b4b39032b93937b960711b604482015260640161048b565b601b8160ff16101561070457610701601b82611716565b90505b6040805160008082526020820180845288905260ff841692820192909252606081018590526080810184905260019060a0016020604051602081039080840390855afa158015610758573d6000803e3d6000fd5b505060408051601f1901516001600160a01b03811660009081526005602052919091205490925060ff16905080156107b3575060008681526007602090815260408083206001600160a01b038516845290915290205460ff16155b156107f35760008681526007602090815260408083206001600160a01b03851684529091529020805460ff19166001179055866107ef8161173b565b9750505b50505050604181610804919061160a565b905061065b565b506000848152600660205260409020805460ff1916600117905560095460085461ffff9091169061084160ff85166103e861175b565b61084b919061177a565b119695505050505050565b604080516001600160a01b0385166020820152908101839052606080820152600c60808201526b1c995b5bdd9957d85cdcd95d60a21b60a082015260009060c00160405160208183030381529060405290506108b382828561058a565b6108cf5760405162461bcd60e51b815260040161048b90611644565b6001600160a01b03841660008181526004602052604090819020805460ff19169055517f4fc1eea408d8ac4a2183d526e254ca649c7f864b51614136a84ab511938759669061057c9086815260200190565b60008260405160200161096291815260406020820181905260149082015273636c61696d5f6e6574776f726b5f746f6b656e7360601b606082015260800190565b604051602081830303815290604052905061097e82828561058a565b61099a5760405162461bcd60e51b815260040161048b90611644565b60008054604080516323c613df60e11b815290516001600160a01b039092169263478c27be9260048084019382900301818387803b1580156109db57600080fd5b505af11580156109ef573d6000803e3d6000fd5b50505050505050565b336000908152600360209081526040808320848452909152902054821115610a555760405162461bcd60e51b815260206004820152601060248201526f4e6f7420656e6f756768207374616b6560801b604482015260640161048b565b33600090815260036020908152604080832084845290915281208054849290610a7f90849061178e565b90915550503360009081526002602052604081208054849290610aa390849061178e565b909155505060015460405163a9059cbb60e01b8152336004820152602481018490526001600160a01b039091169063a9059cbb906044016020604051808303816000875af1158015610af9573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610b1d9190611622565b50604051338152819083907fbf29d42e4b7958feaf33266d1c701ac5e3da189d437c92b1f4e8ba64f8a7ae8b90602001610424565b8351855114610bbc5760405162461bcd60e51b815260206004820152603060248201527f616d6f756e747320616e642064657374696e6174696f6e73206d75737420626560448201526f040cae2eac2d840d2dc40d8cadccee8d60831b606482015260840161048b565b8251855114610c335760405162461bcd60e51b815260206004820152603860248201527f61737365745f61646472657373657320616e642064657374696e6174696f6e7360448201527f206d75737420626520657175616c20696e206c656e6774680000000000000000606482015260840161048b565b600085858585604051602001610c4c94939291906117e9565b6040516020818303038152906040529050610c6882828561058a565b610c845760405162461bcd60e51b815260040161048b90611644565b60005b86518110156109ef57848181518110610ca257610ca2611883565b60200260200101516001600160a01b031663a9059cbb888381518110610cca57610cca611883565b6020026020010151888481518110610ce457610ce4611883565b60200260200101516040518363ffffffff1660e01b8152600401610d1d9291906001600160a01b03929092168252602082015260400190565b6020604051808303816000875af1158015610d3c573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610d609190611622565b50858181518110610d7357610d73611883565b6020026020010151858281518110610d8d57610d8d611883565b60200260200101516001600160a01b0316888381518110610db057610db0611883565b60200260200101516001600160a01b03167fbb1c528ca254542ba3097499865a4525b31ddd7ef6385c80ebeb316d0ae0f59b87604051610df291815260200190565b60405180910390a480610e0481611899565b915050610c87565b6001600160a01b03831660009081526004602052604090205460ff16610e805760405162461bcd60e51b815260206004820152602360248201527f4465706f73697473206e6f7420656e61626c656420666f7220746869732061736044820152621cd95d60ea1b606482015260840161048b565b6040516323b872dd60e01b8152336004820152306024820152604481018390526001600160a01b038416906323b872dd906064016020604051808303816000875af1158015610ed3573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610ef79190611622565b50604051338152819083906001600160a01b038616907fcb06e69df03d341dd481e575a34aaf003e2af876dc077f3c54b39eff9f6cb7fb9060200160405180910390a4505050565b6001600160a01b03831660009081526005602052604090205460ff1615610fa85760405162461bcd60e51b815260206004820152601860248201527f5573657220697320616c72656164792061207369676e65720000000000000000604482015260640161048b565b604080516001600160a01b0385166020820152908101839052606080820152600a60808201526930b2322fb9b4b3b732b960b11b60a082015260009060c001604051602081830303815290604052905061100382828561058a565b61101f5760405162461bcd60e51b815260040161048b90611644565b6001600160a01b0384166000908152600560205260408120805460ff19166001179055600880549161105083611899565b9190505550836001600160a01b03167ff9a3b2c9bc1f8ead8d9e72ca4c7ba3ec55f2e4a128a8fccdec03f577d64637b68460405161057c91815260200190565b604080516001600160a01b0385166020820152908101839052606080820152600960808201526818591917d85cdcd95d60ba1b60a082015260009060c00160405160208183030381529060405290506110ea82828561058a565b6111065760405162461bcd60e51b815260040161048b90611644565b6001600160a01b03841660009081526004602052604090205460ff16156111665760405162461bcd60e51b8152602060048201526014602482015273417373657420616c72656164792065786973747360601b604482015260640161048b565b6001600160a01b03841660008181526004602052604090819020805460ff19166001179055517f60abe041d1b76452cbc2c27bcfff40ca2f40a0440f2663684bcd3b69c44fe76c9061057c9086815260200190565b6000602082840312156111cd57600080fd5b5035919050565b600080604083850312156111e757600080fd5b50508035926020909101359150565b80356001600160a01b038116811461120d57600080fd5b919050565b60006020828403121561122457600080fd5b61122d826111f6565b9392505050565b634e487b7160e01b600052604160045260246000fd5b604051601f8201601f1916810167ffffffffffffffff8111828210171561127357611273611234565b604052919050565b600082601f83011261128c57600080fd5b813567ffffffffffffffff8111156112a6576112a6611234565b6112b9601f8201601f191660200161124a565b8181528460208386010111156112ce57600080fd5b816020850160208301376000918101602001919091529392505050565b60008060006060848603121561130057600080fd5b611309846111f6565b925060208401359150604084013567ffffffffffffffff81111561132c57600080fd5b6113388682870161127b565b9150509250925092565b6000806040838503121561135557600080fd5b61135e836111f6565b946020939093013593505050565b60008060006060848603121561138157600080fd5b833567ffffffffffffffff8082111561139957600080fd5b6113a58783880161127b565b945060208601359150808211156113bb57600080fd5b506113c88682870161127b565b925050604084013590509250925092565b600080604083850312156113ec57600080fd5b82359150602083013567ffffffffffffffff81111561140a57600080fd5b6114168582860161127b565b9150509250929050565b600067ffffffffffffffff82111561143a5761143a611234565b5060051b60200190565b600082601f83011261145557600080fd5b8135602061146a61146583611420565b61124a565b82815260059290921b8401810191818101908684111561148957600080fd5b8286015b848110156114ab5761149e816111f6565b835291830191830161148d565b509695505050505050565b600080600080600060a086880312156114ce57600080fd5b853567ffffffffffffffff808211156114e657600080fd5b6114f289838a01611444565b965060209150818801358181111561150957600080fd5b8801601f81018a1361151a57600080fd5b803561152861146582611420565b81815260059190911b8201840190848101908c83111561154757600080fd5b928501925b828410156115655783358252928501929085019061154c565b9850505050604088013591508082111561157e57600080fd5b61158a89838a01611444565b94506060880135935060808801359150808211156115a757600080fd5b506115b48882890161127b565b9150509295509295909350565b6000806000606084860312156115d657600080fd5b6115df846111f6565b95602085013595506040909401359392505050565b634e487b7160e01b600052601160045260246000fd5b6000821982111561161d5761161d6115f4565b500190565b60006020828403121561163457600080fd5b8151801515811461122d57600080fd5b60208082526011908201527014da59db985d1d5c99481a5b9d985b1a59607a1b604082015260600190565b60008161167e5761167e6115f4565b506000190190565b634e487b7160e01b600052601260045260246000fd5b6000826116ab576116ab611686565b500690565b604081526000835180604084015260005b818110156116de57602081870181015160608684010152016116c1565b818111156116f0576000606083860101525b506001600160a01b0393909316602083015250601f91909101601f191601606001919050565b600060ff821660ff84168060ff03821115611733576117336115f4565b019392505050565b600060ff821660ff811415611752576117526115f4565b60010192915050565b6000816000190483118215151615611775576117756115f4565b500290565b60008261178957611789611686565b500490565b6000828210156117a0576117a06115f4565b500390565b600081518084526020808501945080840160005b838110156117de5781516001600160a01b0316875295820195908201906001016117b9565b509495945050505050565b60a0815260006117fc60a08301876117a5565b82810360208481019190915286518083528782019282019060005b8181101561183357845183529383019391830191600101611817565b5050848103604086015261184781886117a5565b6060860196909652508385036080909401939093525050600f82526e77697468647261775f61737365747360881b908201526040019392505050565b634e487b7160e01b600052603260045260246000fd5b60006000198214156118ad576118ad6115f4565b506001019056fea2646970667358221220d00e99a80066b00fb8e38648b74825463b989f5514e7e0c7e6dae5b0d651757764736f6c634300080b0033";

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
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event ADDSIGNER_EVENT = new Event("AddSigner", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event ADDSTAKE_EVENT = new Event("AddStake", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>(true) {}, new TypeReference<Bytes32>(true) {}));
    ;

    public static final Event DEPOSITASSET_EVENT = new Event("DepositAsset", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>(true) {}, new TypeReference<Bytes32>(true) {}));
    ;

    public static final Event REMOVEASSET_EVENT = new Event("RemoveAsset", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event REMOVESIGNER_EVENT = new Event("RemoveSigner", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    public static final Event REMOVESTAKE_EVENT = new Event("RemoveStake", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}, new TypeReference<Uint256>(true) {}, new TypeReference<Bytes32>(true) {}));
    ;

    public static final Event WITHDRAWASSET_EVENT = new Event("WithdrawAsset", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>(true) {}, new TypeReference<Uint256>() {}));
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
            typedResponse.asset = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.asset = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
            typedResponse.signer = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.signer = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
            typedResponse.amount = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.jynx_key = (byte[]) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.amount = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.jynx_key = (byte[]) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
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
            typedResponse.asset = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.amount = (BigInteger) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.jynx_key = (byte[]) eventValues.getIndexedValues().get(2).getValue();
            typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.asset = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.amount = (BigInteger) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.jynx_key = (byte[]) eventValues.getIndexedValues().get(2).getValue();
                typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
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
            typedResponse.asset = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.asset = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
            typedResponse.signer = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.signer = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
            typedResponse.amount = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.jynx_key = (byte[]) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.amount = (BigInteger) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.jynx_key = (byte[]) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.user = (String) eventValues.getNonIndexedValues().get(0).getValue();
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
            typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.asset = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.amount = (BigInteger) eventValues.getIndexedValues().get(2).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.user = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.asset = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.amount = (BigInteger) eventValues.getIndexedValues().get(2).getValue();
                typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
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

    public RemoteFunctionCall<TransactionReceipt> deposit_asset(String _address, BigInteger _amount, byte[] _jynx_key) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_DEPOSIT_ASSET, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _address), 
                new org.web3j.abi.datatypes.generated.Uint256(_amount), 
                new org.web3j.abi.datatypes.generated.Bytes32(_jynx_key)), 
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
        public BigInteger amount;

        public byte[] jynx_key;

        public String user;
    }

    public static class DepositAssetEventResponse extends BaseEventResponse {
        public String asset;

        public BigInteger amount;

        public byte[] jynx_key;

        public String user;
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
        public BigInteger amount;

        public byte[] jynx_key;

        public String user;
    }

    public static class WithdrawAssetEventResponse extends BaseEventResponse {
        public String user;

        public String asset;

        public BigInteger amount;

        public BigInteger nonce;
    }
}
