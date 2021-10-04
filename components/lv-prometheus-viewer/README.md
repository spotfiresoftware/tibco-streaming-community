# LiveView Prometheus Metrics Integration

This LiveView sample can connect with a Prometheus server and will periodically download current select metric values.

There is a table called `MetricMetadata` which has a list of all current metric names on the
Prometheus server along with any available information about the metric such a description and/or unit definition.

A second table called `Metrics` has metric values, keyed by metric name and labels. By default the metric names included in this table
are configured by setting a regex expression, one per line, in a file called `MetricsWhiteListRegex.txt`.
Each regex line in the file is evaluated in turn to see if a particular metric is of interest.
The default regex is `".*"`, so all metrics are included.

For metrics that have Prometheus labels you can define column names in the Metrics table that match one or more of the labels.
A metric that contains a label that matches a column name will have that label value set in the field.
The label column can always be a string type, but you can also define it to be a data type that better matches the label data type.
This sample has a Metrics column called 'code' of type integer. Some of Prometheus's own metrics have a label called 'code' and if present
in the metric data being subscribed to, it will be populated with integer values from that label. 
All non-matching labels will be concatenated and set in the "OtherLabels" field, if it's present.

A third LiveView table called `MetricsSelector` will be initialized to have all the rows
present in `MetricsWhiteListRegex.txt`. By publishing to the `MetricsSelector` table you can dynamically add a new metric to be included in the `Metrics` table.
Similarly, you can delete an existing row(s) from the `MetricsSelector` table and the associated metrics will be removed from the `Metrics` table
and not appear again. You can use one of the 4 available APIs, or the lv-client command line, to publish or delete from a LiveView table. 
See LiveView documentation for more information. Note that metrics dynamically added/removed will not survive a node restart. On restart, the original
`MetricsWhiteListRegex.txt` RegExs will be present.

There is an alternative event flow application called GetAllMetrics.sbapp that does not query Prometheus for individual
metrics but just GETs all metrics from the /metrics endpoint. If you wish to use that method of retrieving metrics, you need to replace the
GetSelectMetrics module reference in PrometheusPub.sbapp with GetAllMetrics.sbapp.

You must provide a Prometheus server URL by setting the system property liveview.prometheus.server.url. By default the Prometheus URL defaults to http://localhost:9090.
If authentication is needed you must also configure two different
HTTP client adapters - one in the `PrometheusPub.sbapp` and one in `GetSelectMetrics.sbapp` (or GetAllMetrics.sbapp).

By default the Metrics table is trimmed to keep 60 minutes of metric history. You can change this to any length history that your
heap allocation will support by updating the "Trim Metrics" alert.

If you don't have ready access to a Promethus server, you can quickly download and setup one as described here:
[https://prometheus.io/docs/prometheus/latest/getting_started](https://prometheus.io/docs/prometheus/latest/getting_started). 
Running that server on the same host that is running this sample will populate the `MetricsMetadata` and `Metrics` table with the internal
Prometheus server metrics. This sample has been tested with Prometheus version 2.21.0.

Note that the Promethus data model is polling based, while the LiveView model is real time event driven. There are a number
of polling intervals you can control in both the Prometheus server and in this sample to improve real time resolution available in the `Metrics` table.
The default Prometheus `scrape_interval` is once per minute. You can decrease this value in the `--config.file` to improve the responsiveness of the `Metrics`. By default this sample will
poll the Prometheus server for its metadata once every 60 seconds. By default this sample will poll the metric data once every 2 seconds.

The polling and URL defaults can changed in the `engine.conf` or by using substitution variables when deploying the sample.
