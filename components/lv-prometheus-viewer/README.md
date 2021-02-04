# LiveView Prometheus Metrics Integration

This LiveView sample can connect with a Prometheus server and will periodically download current select metric values.

There is a table called `MetricMetadata` which has a list of all current metric names on the
Prometheus server along with any available information about the metric such a description and/or unit definition.

A second table called `Metrics` has metric values, keyed by metric name and labels. The metric names included in this table
are configured by setting a regex expression, one per line, in a file called `MetricsWhiteListRegex.txt`.
Each regex line in the file is evaluated in turn to see if a particular metric is of interest.
The default regex is `".*"`, so all metrics are included. By default the 
table is trimmed to keep 60 minutes of metric history. You can change this to any length history that your
heap allocation will support by updating the "Trim Metrics" alert. 

You must provide a Prometheus server URL, and if authentication is needed you must also configure two differenet
HTTP client adapters - one in the `PrometheusPub.sbapp` and one in `GetSelectMetrics.sbapp`.

If you don't have ready access to a Promethus server, you can quickly download and setup one as described here:
[https://prometheus.io/docs/prometheus/latest/getting_started](https://prometheus.io/docs/prometheus/latest/getting_started). Running that server on the same host that is running this sample will populate the `MetricsMetadata` and `Metrics` table with the internal Prometheus server metrics. This sample has been tested with Prometheus version 2.21.0.

Note that the Promethus data model is polling based, while the LiveView model is real time event driven. There are a number
of polling intervals you can control in both the Prometheus server and in this sample to improve real time resolution available in the `Metrics` table.
The default Prometheus `scrape_interval` is once per minute. You can decrease this value in the `--config.file` to improve the responsiveness of the `Metrics`. By default this sample will
poll the Prometheus server for its metadata once every 60 seconds. By default this sample will poll the metric data once every 2 seconds.

Finally, the Prometheus server URL is defaulted to `http://localhost:9090`.
The polling and URL defaults can changed in the `engine.conf` or by using substitution variables when deploying the sample.
