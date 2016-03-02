#!/bin/bash
SELF=`readlink -e -- "$0"`
DIR=`dirname -- "$DIR"`
cd "$DIR"

tar -czvf perfci-nmon-monitor-upload.tar.gz jenkins-perfci

