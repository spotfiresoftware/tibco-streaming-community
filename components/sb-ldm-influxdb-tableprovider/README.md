# SB LDM InfluxDB Table Provider

This is a LDM Table Provider example that connects to InfluxData's InfluxDB open source offering (https://github.com/influxdata/influxdb).

This is a Liveview Project, intended to be loaded and run in StreamBase Studio.
The project was developed in Studio 10.2.1, and targets Live Datamart 10.2.1 or higher.
To run, the project will require setting up InfluxDB, and to have it running on some accessible server to the machine
running this sample.


## Implementation Notes

The focus of this project is to illustrate a working table provider, not for production use. 
InfluxDB schemas are flexible (may change during a query in various circumstances) but the sample will create the schema
only once, at startup. Additionally, to detect a series' schema, there must be at least one value already.

For simplicity, this table provider will delete/create a database called "LiveDataMartSample", and populate with sample data in
a table (measurement) called "cpu" on the target InfluxDB instance.


## Project Details

InfluxData.lvconf - declares the Table Provider to the Live Datamart server. A single parameter configures the connection URI
                    (out of the box this is set to http://localhost:8086, the default URI for an InfluxDB local instance.)
                    

## Running This Example

This Studio project is also a LiveView project complete with a single configuration file (InfluxData.lvconf) registering
this table provider. You should edit the .lvconf to ensure the target InfluxDB instance URL is correct (the default
is set to localhost:8086).

Once running and connected, use lv-client, LiveView Web or LiveView Desktop to query the "cpu" table, or any
 other table discovered in your target influxdb instance.
 
 
 
## Third Party Information

No third-party components are included in the original distribution. Dependencies to influxdb and its dependencies
are managed by Maven.


## Changelog

* 1.0    initial release
* 1.1    migrated to StreamBase 10 project
