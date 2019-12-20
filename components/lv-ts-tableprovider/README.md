# LiveView Technical Support TableProvider

LiveView has a public TableProvider API that allows user to add
*foreign* backend data sources. Such data sources are queried using
whatever native language the backend implements. In the Technical
Support TableProvider we have define a very simple "query language"
that allows users to express things like "get all the system logs", or
"get a system snapshot".

In addition, there are several tables that support
"proxying" operations to other LiveView servers. These
tables are intended for use in clusters where the end user can only
connect to the services layer (SL), but the SL can connect
to other nodes in the cluster.

A LiveView project is included that has all the TS tables configured.
Authentication is enabled and configured to demonstrate some ways
to restrict access to capabilities. The project also has a LiveView
appender configured to publish the server log to the LVServerLog table.

There are eight different tables that may be independently added.
They are:

* FileSystemRead - can list directories and read text or binary files from remote system
* LVServerLog - A very limited capability faux table that is configured to accept publishing from the LiveView logger appender
* ProxyLVClient - Execute the supplied lv-client command on the proxied system
* ProxySBClient - Execute the supplied sbc/sbadmin command on the proxied system
* ProxyPublish - Supports publishing to proxied LiveView servers.
* ProxyQuery - Forwards queries to remote LiveView servers.
* OSExec - Executes commands on the node using a very primitive command parser
* TSTable - supports typical TS tasks like getting system logs, system snapshots, stack traces, etc. These requests can be forwarded to remote LiveView servers. 

The "query language" for each of the table providers is similar, but does differ. The parseQuery method in each of the TableProviders have the details, but a summary is:

 * select [ ls | read | readbin ] from FileSystemRead where path={path} [ limit N]<br/>
   return is snapshot only with schema ReturnedOutput(string) for ls and read and BlobValue(blob) for readbin

 * select {ignored} from LVClient where [ lvuri= {RemotelvURI} | service={My.Service.cluster[;username:password] ] lvcmd={lv-client command line to execute}<br/>
   return is snapshot only with schema OutputFromCommand(string)

 * select [ sbc | sbadmin ] from ProxySBClient where [ sburi= {RemoteSBURI} | service={My.Service.cluster[;username:password] ] sbcmd={sbc/sdadmin command line to execute}<br/>
   return is snapshot only with schema OutputFromCommand(string)

 * publish ProxyPublish<br/>
   The publish schema is: lvURI(string),Table(string),Delete(boolean),JSONTuple(string)

 * select [ snapshot | delete | continuous ] from ProxyQuery where lvuri={remote URI} query={LiveQL string}<br/>
   return is snapshot or snapshot_and_continuous where the schema is based on the supplied LiveQL projection

 * select command from OSExec where cmd={command to execute and args}<br/>
   return is snapshot only with schema OutputFromCommand(string)

 * select [ getsnapshot | getstacktrace | getlogs | getprofiles | getversion | getheapdump | dofullgc ] from TSTable [ where lvuri= {RemotelvURI} ]<br/>
   return is snapshot only with schema: index(long), Text(String), BlobValue(blob)

As with any LiveView table when authentication is enabled, access to these tables can be controlled through entitlements. With LiveViewTableList and
LiveViewTableQuery entitlements you can exercise all the query tables capabilities, excepting LiveViewTableDelete is needed for ProxyQuery delete operations.
The LiveViewTablePublish entitlement is needed for the ProxyPublish table.

Furthermore, any operation that touches a table will also require the user to have entitlements to do the operation on that table. For example, if a user has list
and read permissions to ProxyQuery and queries MyTable - they will also require list and query permissions to MyTable. All proxied operations are executed as the
user specified in remote LV URI.

The lv-client command included with LiveView can exercise most of the TableProvider capabilities. Included in this project is a tailored command line tool called TSClient that can
makes accessing the TS tables a little easier. A good example of where the TSClient is helpful is the TSTable getsnapshot operation. This operation returns
a few rows with Text status messages, and potentially many rows of binary data in the Blob field. These blob fields need to be reassembled into a file on the client
that is a copy of the snapshot ZIP file that was created on the LiveView server. Breaking the snapshot zip into multiple small tuples avoids issues with trying to transfer very
large binary files, but does require reassembly.

