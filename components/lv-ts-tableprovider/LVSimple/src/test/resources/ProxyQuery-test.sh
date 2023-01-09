# Copyright 2020 Cloud Software Group, Inc.
# Simple tests for ProxyPublish Table

set -x
LVCMD=lv-client.cmd
LVURI=lv://lvadmin:lvadmin@localhost
PROXY_SERVICE="LiveViewSimple.campbell;lvadmin:lvadmin"

# URL proxied
$LVCMD -c -u $LVURI select snapshot from ProxyQuery where lvuri=$LVURI query="select * from LVNodeInfo"

# service name proxied
$LVCMD -c -u $LVURI select snapshot from ProxyQuery where service=$PROXY_SERVICE query="select * from LVNodeInfo"

# ********
# NOTE: just hit CR to end the continous query
# ********
$LVCMD -c -u $LVURI live select continuous from ProxyQuery where service=$PROXY_SERVICE query="select * from LVNodeInfo"
 
