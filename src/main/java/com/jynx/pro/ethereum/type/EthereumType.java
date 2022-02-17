package com.jynx.pro.ethereum.type;

import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;

public class EthereumType {
    public static final TypeReference<?> ADDRESS_INDEXED = new TypeReference<Address>(true) {};
    public static final TypeReference<?> UINT256_INDEXED = new TypeReference<Uint256>(true) {};
    public static final TypeReference<?> BYTES32_INDEXED = new TypeReference<Bytes32>(true) {};
    public static final TypeReference<?> ADDRESS = new TypeReference<Address>(false) {};
}
