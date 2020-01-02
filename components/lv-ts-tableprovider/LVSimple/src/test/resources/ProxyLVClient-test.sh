# Copyright 2020 TIBCO Software Inc.
# Simple tests for ProxyLVClient table

# The ProxyLVClient table is intended for use with quick returning
# administrative commands such as killsession, sessioncontrol, etc.

set -x
LVCMD=lv-client.cmd

LVURI=lv://lvadmin:lvadmin@localhost
PROXY_SERVICE="LiveViewSimple.campbell;lvadmin:lvadmin"

# local
$LVCMD -c -u $LVURI select noop from ProxyLVClient where lvcmd="-u $LVURI listtables"
$LVCMD -c -u $LVURI select noop from ProxyLVClient where lvcmd="-u $LVURI status"

# URL proxied
$LVCMD -c -u $LVURI select noop from ProxyLVClient where lvuri=$LVURI lvcmd="-u $LVURI listtables"
$LVCMD -c -u $LVURI select noop from ProxyLVClient where lvuri=$LVURI lvcmd="-u $LVURI status"

# service name proxied
$LVCMD -c -u $LVURI select noop from ProxyLVClient where service=$PROXY_SERVICE lvcmd="-u $LVURI listtables"
$LVCMD -c -u $LVURI select noop from ProxyLVClient where service=$PROXY_SERVICE lvcmd="-u $LVURI status"
