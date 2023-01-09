# Copyright 2020 Cloud Software Group, Inc.
# Simple tests for TSTable

set -x
LVCMD=lv-client.cmd
TSCMD=ts-client.cmd

LVURI=lv://lvadmin:lvadmin@localhost
PROXY_SERVICE="LiveViewSimple.campbell;lvadmin:lvadmin"

# TSTable - local, text based
$LVCMD -c -u $LVURI select getversion from TSTable
$LVCMD -c -u $LVURI select dofullgc from TSTable
$LVCMD -c -u $LVURI select getstacktrace from TSTable > TSTable-stacktrace.txt
$LVCMD -c -u $LVURI select getlogs from TSTable > TSTable-getlogs.txt

# TSTable - lvuri proxied, text based
$LVCMD -c -u $LVURI select getversion from TSTable where "lvuri=$LVURI"
$LVCMD -c -u $LVURI select dofullgc from TSTable  where "lvuri=$LVURI"
$LVCMD -c -u $LVURI select getstacktrace from TSTable where "lvuri=$LVURI" > TSTable-proxy-uri-stacktrace.txt
$LVCMD -c -u $LVURI select getlogs from TSTable where "lvuri=$LVURI" > TSTable-proxy-uri-getlogs.txt

# TSTable - service name proxied, text based
$LVCMD -c -u $LVURI select getversion from TSTable where "service=$PROXY_SERVICE"
$LVCMD -c -u $LVURI select dofullgc from TSTable  where "service=$PROXY_SERVICE"
$LVCMD -c -u $LVURI select getstacktrace from TSTable where "service=$PROXY_SERVICE" > TSTable-proxy-service-stacktrace.txt
$LVCMD -c -u $LVURI select getlogs from TSTable where "service=$PROXY_SERVICE" > TSTable-proxy-service-getlogs.txt

# TSTable - local, binary data
$TSCMD -u $LVURI getsnapshot -d ./
$TSCMD -u $LVURI getprofiles -d ./
$TSCMD -u $LVURI getheapdump -d ./

# TSTable - service name proxied, binary data
$TSCMD -u $LVURI getsnapshot -d ./ -s $PROXY_SERVICE

# TSTable - LVURI proxied, binary data
$TSCMD -u $LVURI getsnapshot -d ./ -r $LVURI

