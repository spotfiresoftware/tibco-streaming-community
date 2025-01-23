# Wikipedia Monitoring

**Visualize live data in TIBCO LiveView&trade; Web using a LiveView project in TIBCO StreamBase&reg; Studio**

This demo uses a recording of the live data feed from the English language Wikipedia. The feed emits a record every time a page is edited, and about 20 minutes of this feed were captured into a CSV file.

A Feed Simulation streams the contents of the CSV file to the input port of a TIBCO StreamBase EventFlow&trade; module.

The EventFlow module transforms the raw Wikipedia data, geo-tagging data when able and pre-processing some of the data prior to sending it to the LiveView server.

The LiveView server accepts the incoming data into various Tables, and makes the data available for querying by any connecting client.

## Visualizing LiveView Data

The second step of the demo is to open your web browser to LiveView Web, or start TIBCO LiveView&trade; Desktop (if you have it installed) and have it connect to the LiveView server started earlier in this demo.

Once the feed simulation is started, data will begin to flow through the EventFlow module and the visualizations will begin to populate.

## LiveView Details

LiveView Web and LiveView Desktop can both visualize data available on a LiveView server hundreds of ways, using the full power of LiveQL to issue continuous queries against LiveView tables in order to display simple grids to complex graphical transformations of the data on a map, chart, or even custom visualizations.

The LiveView Web dashboard contains various tabs (pages):

- "Live Edit Stream": displays the edit stream with the latest edits appearing on the top of the grid.
- "Where Edits Come From": Summarizes geo-locatable edits in a pie chart, bar chart and if you select a continent in the bar chart, the grid and the rightmost pie chart will display details.
- "Largest Anonymous Edits" and "Anonymous Edits from the United States": These pages use maps to visualize where various edits of interest originate from.
- "Hot Pages" and "Active Editors": Discover which pages are being most edited, or look at who is making the most edits.
Other Pages: the remaining pages display raw data samples from all tables, or tables pertaining to the state of the system for monitoring.