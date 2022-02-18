# Blockchain

This repository contains the Tendermint Core implementation of the Jynx Pro blockchain. This implementation is written in Java and depends heavily on Spring Boot and H2DB.

## Installation

You will need the following dependencies:

* `Java 11`
* `Maven 3.6`

Then you can install the dependencies and build the target JAR file with the command below:

* `mvn clean install -DskipTests`

## Testing

This project is mostly tested with Spring Boot integration tests. There are virtually no unit tests at this time, so running the entire test suite can be quite time consuming. In any case, if you wish to do so, you can execute the command below:

* `mvn clean test`

A code coverage report will be generated and be available at the following location:

* `target/xxx`

## License

* [MIT](https://choosealicense.com/licenses/mit)
