# Copyright 2020 Cloud Software Group, Inc.
# Simple tests for FileSystemRead Table

set -x
LVCMD=lv-client.cmd
TSCMD=./ts-client.cmd

LVURI=lv://lvadmin:lvadmin@localhost
THIS_DIR=`pwd`

# local, text based
$LVCMD -c -u $LVURI select ls from FileSystemRead where path=$THIS_DIR > FSReadTable-ls.txt
$LVCMD -c -u $LVURI select read from FileSystemRead where path=$THIS_DIR/FSReadTable-ls.txt > FSReadTable-read.txt
