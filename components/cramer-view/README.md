# Cramer View

## Requirements

This project was written against TIBCO Streaming (or Spotfire Data Streams) version 10.5 or higher (but should be compatible
with 10.4 or higher if the pom is updated).

## Usage

This project is intended to run as a LiveView Project and includes a LiveView Publisher that will continuously loop a data file, all
the while streaming the included sensor data through a Correlation operator, discovering in real-time which sensor readings are
correlated to the _failure_ (FailureCode) flag.
 
Once running, either use your browser and open TIBCO LiveView Web at http://localhost:11080 or open CramerView.dxp using Spotfire
10.5 or higher.
 