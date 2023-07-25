# NYC Yellow Cabs Demo

**Visualize live data in LiveView Web using a LiveView project in StreamBase Studio**

This demo uses part of a data set provided by NYC Open Data. The data consists of all the yellow taxi trips in New York City that occurred on January 9th, 2015. Pickup and dropoff times, taxi position, and other trip information is recorded in a CSV file.

A Feed Simulation streams the contents of the CSV file to the input port of a StreamBase EventFlow module using the pickup and dropoff times.

The EventFlow module transforms the raw taxi trip data, geo-tagging and pre-processing some of the data prior to sending it to the LiveView server.

The LiveView server accepts the incoming data into various Tables, and makes the data available for querying by any connecting client.

## Visualizing LiveView Data

The second step of the demo is to open your web brower to Spotfire LiveView Web and have it connect to the LiveView server started earlier in this demo.

Once the feed simulation is started, data will begin to flow through the EventFlow module and the visualizations will begin to populate.

## LiveView Details

LiveView Web can visualize data available on a LiveView Server hundreds of ways, using the full power of LiveQL to issue continuous queries against LiveView tables in order to display simple grids to complex graphical transformations of the data on a map, chart, or even custom visualizations.

The LiveView Web dashboard contains various tabs (pages):

- "Welcome": Displays the simulation's current time and allows you to change the speed at which data is read. The top fares and tips, and the most recent trips are displayed in grids.
- "Aggregates Overview": Shows several aggregates such as total number of trips and passengers, total distance travelled by taxis, and total tolls and taxes collected.
- "Financial": This page show relevant financial information using single-value and line chart visualizations.
- "Taxi Activity": Find out which areas in NYC are most concurred by taxis, and whether pickups (or dropoffs) are predominant in specific areas.
- "Taxi Destinations": Discover where the most and least expensive trips have their final destination through geo-map and grid visualizations.
- "Trends": Learn what are the trends in the NYC area. Pie chart, column chart, and tree map visualizations are used to show when do people travel, for how long, and how much do they pay.