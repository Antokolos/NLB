#!/bin/bash

g++ -o luapassing.so -shared luapassing.cpp adapter.cpp -ldl -fPIC