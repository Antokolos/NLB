#!/bin/bash

cd lua
make clean
make
rm lua.o luac.o
cd ..
g++ -arch i386 -I/Users/Antokolos/Downloads/=programm=/Steam/sdkv1p34/public/steam -c luapassing.cpp adapter.cpp
#gcc -dynamiclib -current_version 1.0 luapassing.o lua/*.o -o luapassing.dylib
g++ -arch i386 -o luapassing.so -shared luapassing.o adapter.o lua/*.o /Users/Antokolos/Downloads/=programm=/Steam/sdkv1p34/redistributable_bin/osx32/libsteam_api.dylib
rm *.o
cd lua
make clean