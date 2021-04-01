# StreamBase profile data summary/analysis tool

This eventflow sample provides an application that reads StreamBase profile
data files and summarizes the data into top consumers by category.

The categories available are:

- Top Operator CPU usage  
    For individual cycles - help find spikes in operator CPU usage.
- Aggregate Operator CPU usage (milliseconds)  
    By adding each cycle's operator CPU use, lists the operators that are
    most consistently busy.
- Top Operator Input rate (TPS)  
    For individual cycles - help find spikes in input rates.
- Top Operator Output rates (TPS)  
    For individual cycles - help find spikes in output rates.
- Top Operator Size (operator dependent - see StreamBase Operators documentation)  
    For individual cycles - help find spikes in operator sizes.
- Top Queues size  
    For individual cycles - find queues that have size spikes.
- Aggregate Queue size  
    By adding each cycle's queue size, lists the queues that are most
    consistently busy.
- Top Microseconds Per [Input | Output] Tuple Operators  
    Find operators that take the most time per individual tuple.
- Top Thread CPU usage (microseconds)  
    For individual cycles - help find spikes in thread CPU usage.
- Aggregate Thread usage (milliseconds)  
    By adding each cycle's thread CPU usage, lists the threads that are most
    consistently busy.
- Memory usage  
    For individual cycles - find periods when JVM memory usage is at maximum.
- Garbage Collectors  
    Summarize the total, average, and maximum GC times for all running collectors.
- Top Streams  
    The highest average rate output/input streams

There are a number of things you may wish to configure in the engine.conf file, see that file for details.
The number of lines summarized in each category, the path name for the summary
report to be written, etc. 

## Output files 

In addition to the summary data sent to the console and summary file, there are 9 files
written to the same directory the profile data source is in. These
files contain the following lists of data and their base names are the
original profile name postpend with descriptive tag. These files are:

{profile-filename}-Operator-NonZero.csv - A list of input, output
    and CPU for all operators that have non-zero values.  
{profile-filename}-Operator-Zero.csv - A list of all operators
    that have a zero input, output, and CPU.  
{profile-filename}-ModuleFullPath-CPU.csv - A list of operators
    input, output, and CPU in the given full module path.  
{profile-filename}-ModuleFullPath-Zero.csv - A list of full module
    paths that have zero input/output/CPU in any operators  
{profile-filename}-ModuleName-CPU.csv - A list of operators
    input, output, and CPU for a module across all of its
    path references.  
{profile-filename}-ModuleName-Zero.csv - A list of modules
    that have zero input/output/CPU operators across all of
    its path references.  
{profile-filename}-Regions.csv - A list of application concurrent
    regions with aggregated input queue sizes.  
{profile-filename}-Queues.csv - A list of application 
    queues with aggregated queue sizes.  
{profile-filename}-Streams.csv - max, avg, and total tuples for each stream

## How to collect profile data

To collect profile data, you can connect sbprofile to a running server.
For example:

    sbprofile -u sb://hostname:portnumber -c profile-dd-mm-yy.log

You can get operator and module test coverage information if you
specify the "-z" option on sbprofile. This will include operators that
have zero input/output data in the profile data which is omitted by default.
The {profile-filename}-Operator-Zero.csv file will list the operators
that were never touched during a profile run, while
{profile-filename}-ModuleName-Zero.csv will list the modules that
contained no operators that had data flow.

## How to run the profile analysis tool

The analysis tool can be run in Studio in a number of ways, or as a deployed application.
You may wish to configure the summary report file location, no mater how you run the application.
The default output is /tmp/ProfileSummary.txt.

To run in studio, navigate to com.sb_profileanalysis.ProfilePerf.sbapp and right click on that application,
then Run as -> Eventflow Fragment. Once the application is started
go to the SB Test/Debug perspective and select the Manual input tab. Select the 'filename' input
and type the fully qualified path name to the profile data file. There is a sample profile data file
at src/main/resources/helloliveview.prof.gz.
 
The included deploy project can be used to build a application zip. You will need to rename the src/main/configurations/EventFlowDeploy.conf-hide
to EventFlowDeploy.conf to identify what application to run. See StreamBase documentation for the
ways in which you can deploy the application zip. Before installing the node you will run the
application on, you should set the environment variable PROFILE_DATA_PATH to the absolute path
name of the profile data file.
