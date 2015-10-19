#!/bin/bash

g++ -I/home/apkolosov/work/Steam/sdk/public/steam -D_LINUX -o luapassing.so -shared luapassing.cpp adapter.cpp libsteam_api.so -fPIC