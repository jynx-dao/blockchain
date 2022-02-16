cd ../ethereum-contracts
truffle compile
cd ../blockchain

solcjs ../ethereum-contracts/contracts/JYNX.sol --bin --abi --optimize -o ../ethereum-contracts/build/contracts
solcjs ../ethereum-contracts/contracts/JYNX_Distribution.sol --bin --abi --optimize -o ../ethereum-contracts/build/contracts
solcjs ../ethereum-contracts/contracts/JynxPro_Bridge.sol --bin --abi --optimize -o ../ethereum-contracts/build/contracts
solcjs ../ethereum-contracts/contracts/lib/ERC20.sol --bin --abi --optimize -o ../ethereum-contracts/build/contracts
solcjs ../ethereum-contracts/contracts/lib/ERC20Detailed.sol --bin --abi --optimize -o ../ethereum-contracts/build/contracts

mv ../ethereum-contracts/build/contracts/*contracts_JYNX_sol_JYNX.abi ../ethereum-contracts/build/contracts/JYNX.abi
mv ../ethereum-contracts/build/contracts/*contracts_JYNX_sol_JYNX.bin ../ethereum-contracts/build/contracts/JYNX.bin

mv ../ethereum-contracts/build/contracts/*contracts_JYNX_Distribution_sol_JYNX_Distribution.abi ../ethereum-contracts/build/contracts/JYNX_Distribution.abi
mv ../ethereum-contracts/build/contracts/*contracts_JYNX_Distribution_sol_JYNX_Distribution.bin ../ethereum-contracts/build/contracts/JYNX_Distribution.bin

mv ../ethereum-contracts/build/contracts/*contracts_JynxPro_Bridge_sol_JynxPro_Bridge.abi ../ethereum-contracts/build/contracts/JynxPro_Bridge.abi
mv ../ethereum-contracts/build/contracts/*contracts_JynxPro_Bridge_sol_JynxPro_Bridge.bin ../ethereum-contracts/build/contracts/JynxPro_Bridge.bin

mv ../ethereum-contracts/build/contracts/*contracts_lib_ERC20_sol_ERC20.abi ../ethereum-contracts/build/contracts/ERC20.abi
mv ../ethereum-contracts/build/contracts/*contracts_lib_ERC20_sol_ERC20.bin ../ethereum-contracts/build/contracts/ERC20.bin

mv ../ethereum-contracts/build/contracts/*contracts_lib_ERC20Detailed_sol_ERC20Detailed.abi ../ethereum-contracts/build/contracts/ERC20Detailed.abi
mv ../ethereum-contracts/build/contracts/*contracts_lib_ERC20Detailed_sol_ERC20Detailed.bin ../ethereum-contracts/build/contracts/ERC20Detailed.bin

web3j generate solidity -b ../ethereum-contracts/build/contracts/JYNX.bin -a ../ethereum-contracts/build/contracts/JYNX.abi -o src/main/java -p com.jynx.pro.ethereum
web3j generate solidity -b ../ethereum-contracts/build/contracts/ERC20.bin -a ../ethereum-contracts/build/contracts/ERC20.abi -o src/main/java -p com.jynx.pro.ethereum
web3j generate solidity -b ../ethereum-contracts/build/contracts/ERC20Detailed.bin -a ../ethereum-contracts/build/contracts/ERC20Detailed.abi -o src/main/java -p com.jynx.pro.ethereum
web3j generate solidity -b ../ethereum-contracts/build/contracts/JYNX_Distribution.bin -a ../ethereum-contracts/build/contracts/JYNX_Distribution.abi -o src/main/java -p com.jynx.pro.ethereum
web3j generate solidity -b ../ethereum-contracts/build/contracts/JynxPro_Bridge.bin -a ../ethereum-contracts/build/contracts/JynxPro_Bridge.abi -o src/main/java -p com.jynx.pro.ethereum
