# Generating Protobuf Files

Android requires protobuf `2.5.0` to auto generate the files.
To generate the files just run `make`.

**Note: Protobuf `2.5.0` will have to be aliased to `protoc25`.**

## Mac Installation Instructions

Protobuf can be installed using brew but this will only get versions > 3.
To install protobuf `2.5.0` follow these steps:

```sh
wget https://github.com/google/protobuf/releases/download/v2.5.0/protobuf-2.5.0.tar.bz2
tar xvf protobuf-2.5.0.tar.bz2
cd protobuf-2.5.0
./configure CC=clang CXX=clang++ CXXFLAGS='-std=c++11 -stdlib=libc++ -O3 -g' LDFLAGS='-stdlib=libc++' LIBS="-lc++ -lc++abi" --disable-shared --prefix='<PATH TO A DIRECTORY>'
make -j4
make install
```

This will compile and build the binary at `PATH TO A DIRECTORY` which you specified in the `./configure` command.
Next you need to move it to your local bin:

```
cd <PATH WHERE YOU INSTALLED PROTOBUF 2.5>/bin
chmod +x ./protoc
mv ./protoc /usr/local/bin/protoc25
```
