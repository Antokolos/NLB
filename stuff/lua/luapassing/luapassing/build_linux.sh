#!/bin/bash

# g++ -I/path/to/steam/includes -D_LINUX -o luapassing.so -shared luapassing.cpp adapter.cpp libsteam_api.so -fPIC
g++ -D_LINUX -o luapassing.so -shared luapassing.cpp adapter.cpp -fPIC