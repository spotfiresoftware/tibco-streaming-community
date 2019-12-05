This component represents an implementation of the Fast Flower Delivery
use case, conceived by members of the Event Processing Technical Society,
and described in details in the Event Processing in Action book by
by Opher Etzion and Peter Niblett (ISBN 9781935182214). The specification
is available in Appendix B. For more information on the book or the use
case, visit the book publisher's website at http://www.manning.com/etzion/

The main application, com.tibco.sb.compexch.flowershop.FlowerShop, has the following
assumptions and simplifications:
 - Driver GPS events are expected to send locations in degrees (-90 to +90).
 - A mapping takes place that maps these coordinates to "a grid".
 - The city grid is assumed to start at 0,0.
 - The assignment process timeout (described in Phase 2) is set to 20 seconds,
    instead of 2 minutes to help testing. It is defined as a module parameter
    for easy modification.
 - In Phase 5, assignments are counted on a hourly basis and reports are generated
    every day.

 Modules:
  - com.tibco.sb.compexch.flowershop.SharedSchemas contains definition of global
    events that are reused by the main application.
  - com.tibco.sb.compexch.flowershop.RawGPS2CityRegion defines the simple
    GPS-to-City grid mapping. Other mappings could be authored here.

 Additional files:
  - drivers.csv is loaded by the application on startup and loads an
    in-memory table (driver_id, driver_rank).
  - stores.csv does a similar task for stores, loading their location (in grid
     coordinates) and minimum-ranking.
  - drivers.sbfs is a feed simulation that can be used to generate driver GPS
    events.

Usage notes:
1. To define stores and drivers manually, use the InsertStore and InsertDriver
   streams.
2. drivers.sbfs is a convenience feed simulation provided that matches the
   drivers defined in drivers.csv. If you change one, update the other file
   to match. You can also send driver events manually or programmatically by
   sending tuples to the GPSLocationEvent input stream.

Version History
  1.0 - upgraded from SB7
  1.1 - updated email address for this component and removed spec sheet
  2.0 - updated for SB10
  2.1 - updated for SB 10.5 and GitHub release; new maven ID and versioning
  