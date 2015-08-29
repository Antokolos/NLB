#!/bin/bash

# g++ -arch i386 -I/path/to/steam/includes -D_MACOSX -o luapassing.so -shared luapassing.cpp adapter.cpp libsteam_api.dylib -ldl -fPIC
g++ -arch i386 -D_MACOSX -o luapassing.so -shared luapassing.cpp adapter.cpp -ldl -fPIC