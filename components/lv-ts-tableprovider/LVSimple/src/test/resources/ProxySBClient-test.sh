# Copyright 2020 Cloud Software Group, Inc.
# Simple tests for ProxyLVClient table

# The ProxyLVClient table is intended for use with quick returning
# administrative commands such as killsession, sessioncontrol, etc.

set -x
LVCMD=lv-client.cmd
LVURI=lv://lvadmin:lvadmin@localhost
SBURI=sb://localhost
PROXY_SERVICE="LiveViewSimple.campbell;lvadmin:lvadmin"

# local
$LVCMD -c -u $LVURI select sbc from ProxySBClient where sbcmd="-u$SBURI status"
$LVCMD -c -u $LVURI select sbadmin from ProxySBClient where sbcmd="-u$SBURI cmd gc"

# URL proxied
$LVCMD -c -u $LVURI select sbc from ProxySBClient where lvuri=$LVURI sbcmd="-u$SBURI status"
$LVCMD -c -u $LVURI select sbadmin from ProxySBClient where lvuri=$LVURI sbcmd="-u$SBURI cmd gc"

# service name proxied
$LVCMD -c -u $LVURI select sbc from ProxySBClient where service=$PROXY_SERVICE sbcmd="-u$SBURI status"
$LVCMD -c -u $LVURI select sbadmin from ProxySBClient where service=$PROXY_SERVICE sbcmd="-u$SBURI cmd gc"
