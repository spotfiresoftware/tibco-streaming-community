# Simple tests for OSExec Table

set -x
LVCMD=lv-client.cmd
LVURI=lv://lvadmin:lvadmin@localhost

# local command
$LVCMD -c -u $LVURI select command from OSExec where cmd=ls /tmp
