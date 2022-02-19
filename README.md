# Blockchain

[![Build Status](https://app.travis-ci.com/jynx-dao/blockchain.svg?branch=main)](https://app.travis-ci.com/jynx-dao/blockchain)

This repository contains the Tendermint Core implementation of the Jynx Pro blockchain. This implementation is written in Java and depends heavily on Spring Boot and H2DB.

## Installation

You will need the following dependencies:

* `Java 11`
* `Maven 3.6`

Before you can build this project, you need to check out the [Ethereum smart contracts](https://github.com/jynx-dao/ethereum-contracts). Once you have done that, you can build the latest Java classes from the smart contracts using [this script](https://github.com/jynx-dao/blockchain/blob/main/scripts/gen-contracts.sh).

Now you can install the dependencies and build the target JAR file using the command below:

* `mvn clean install -DskipTests`

## Testing

This project is mostly tested with Spring Boot integration tests. There are virtually no unit tests at this time, so running the entire test suite can be quite time-consuming. In any case, if you wish to do so, you can execute the command below:

* `mvn clean install`

A code coverage report will be generated and be available at the following location:

* `target/site/jacoco/index.html`

## License

* [MIT](https://choosealicense.com/licenses/mit)
