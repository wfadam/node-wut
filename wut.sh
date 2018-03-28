#!/bin/bash
readonly fname=`which $0`
readonly dname=`dirname $fname`
readonly jspath="$dname"/node-wut/wut.js
node "$jspath" $*

