curl -OL https://github.com/protocolbuffers/protobuf/releases/download/v3.19.4/protoc-3.19.4-linux-x86_64.zip
unzip protoc-3.19.4-linux-x86_64.zip -d protoc3
mv protoc3/include/* src/main/proto/
./$(dirname $0)/travis-build.sh
