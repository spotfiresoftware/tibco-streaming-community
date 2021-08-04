# LiveView dynamic cluster monitor/scaler

This sample connects to a node in a LiveView cluster and will discover all the other LiveView nodes present.
It will connect to all the other LiveView nodes and establish performance monitoring on the LiveView Data Layers.
When the aggregate load on the cluster exceeds defined threshold, the application will generate a "scale up" signal.
Conversely, when the aggregate load on the cluster drops below a defined threshold, the application will generate a "scale down" signal.

Note that the cluster scaling being focused on here is adding/removing
DLs  not scaling SLs. DLs do the heavy lifting in a LV cluster,
while SLs are mostly thin proxies. We have scale tests where a single
SL can support up to ~50K queries.

Note also that in a single cluster there may be multiple TableGroups
and/or AlertGroups. The present sample assumes there will only be a
single TableGroup and AlertGroup, although its certainly possible to
extend the sample to track and signal for multiple groups.

The environments that LV can be run in are disparate  bare iron,
private cloud, k8s, etc. These different environments will likely use
different monitoring/metric/alerting systems and data access
points. They certainly will use different means to start/remove
servers.

To provide the most generic data access to the LV servers, the sample
will use the LV tables alone to determine scaling signals. It will be
a requirement that at least one LV server has a well-known URL, and
that a LV credential that can get to system tables is available for
all the LV servers. An obvious requirement is that the eventflow
application has routable access to all LV nodes. The output of the
sample will be a simple signal to indicate that the cluster should
be scaled up or down.

To support connecting to an arbitrary number of random URI LV servers,
a custom LV query adapter will be implemented. The LVNodeInfo table
from the well-known LV will provide the URLs to all the other LV
servers in the cluster. Tables that will be monitored are:

LVSessionQueries
LVNodeInfo
LiveViewStatistics

Statistics will be kept for all DL nodes currently in the cluster. These statistics are the X (120 by default) second average of:

Number of queries
Total Queued tuples
(LoadAvg/core count)
Old Gen collections

A scoring system will be used to determine when a cluster should be
scaled up or down. The score thresholds, as well as the scaling
factors for each calculated average will be dynamically settable. The
scaling factors are:

Name               default
QueryScale         (10 * CPUFactor)
QueuedScale        .01
CPUScale            V < 1.0=0 ; V > 1.0 200 (i.e. LoadAvg less than core count does not contribute to score)

The Query scale can be adjusted to take into account some notion of
Server performance. A 4 core VM should have a low (possibly
fractional) value, while a 64 core server blade should have a higher
value. The notion is that a 4 core VM might only be able to handle 100
queries, where a 64 core server might be able to handle 1000+.

CPUScale already has core count factored in. 

QueuedScale indicates the server, whatever its performance, is unable to keep up. 

For a default example with a 100 query average running:

Number of queries  * QueryScale = query score
100 * (10*1) = 1000

Each of the categories score is added for a given server to reach a
total for that server. Its assumed each of the servers in the cluster
will be of similar power and size, so each of the currently running
servers scores are added together to reach a cluster score. A cluster
score above the scale up threshold will cause a scale up
request. Similarly a cluster score below a scale down threshold will
cause a scale down request. Scale up and down requests will be rate
limited. i.e. scale up requests may only be issued every X minutes
(say 5) and scale down requests may only be issued every Y minutes
(say 10). One reason for this rate limiting is to avoid signaling
cluster changes faster than the cluster environment can accomplish.

Individual server scores can also be used to identify unusually busy
(or idle) DLs. The default SL query distribution policy is to
balance queries evenly among all the DLs, however different kinds of
queries have very different load characteristics, so uneven loading is
certainly possible. At some future time it may be possible to intervene
with busy servers to push off queries to other nodes.

So a high cluster score  say (Total/NumDL) of 1200 or greater - would
create a scale up request, while a low score of 700 or lower would
create a scale down request. Realistically, all of the thresholds and
scaling factors have to adjusted to the environment and use case.

While not exactly a scaling function, the type of monitoring being
done here lends itself to producing alerts for servers that are
identified as being under stress that is not necessarily related to
variable user load. This mostly has to do with heap usage. For DLs in
a cluster, this typically is related to LV tables growing too large
for the configured heap. Its expected that LV tables have their size
trimmed using appropriate metrics, but in the event that is not done
this monitoring tool can identify the problem. Some combination of old
gen collections and a time window average of total collection time
will be used for this alert. Note that under normal HA table situations, it
will be common that if one DL is experiencing heap exhaustion then all
of the DLs will be since all tables should have the same data.


