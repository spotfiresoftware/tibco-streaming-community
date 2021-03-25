This component provides two applications that read StreamBase profile
data files and summarize the data into top consumers by category.
One application reads profile data from StreamBase servers 7.1 and
later and the other reads profile data from 6.6 StreamBase servers.
The first application must be run on StreamBase 7.2.10 or later.

The categories available for all StreamBase versions are:

Top Operator CPU usage (milliseconds for 6.6 and microseconds for
                        7.1 and later)
    For individual cycles - help find spikes in operator CPU usage.
Aggregate Operator CPU usage (milliseconds)
    By adding each cycle's operator CPU use, lists the operators that are
    most consistently busy.
Top Operator Input rate (TPS)
    For individual cycles - help find spikes in input rates.
Top Operator Output rates (TPS)
    For individual cycles - help find spikes in output rates.
Top Operator Size (operator dependent - see StreamBase Operators documentation)
    For individual cycles - help find spikes in operator sizes.
Top Queues size
    For individual cycles - find queues that have size spikes.
Aggregate Queue size
    By adding each cycle's queue size, lists the queues that are most
    consistently busy.


For 7.1 and later, profile data:

Top Microseconds Per [Input | Output] Tuple Operators
    Find operators that take the most time per individual tuple.
Top Thread CPU usage (microseconds)
    For individual cycles - help find spikes in thread CPU usage.
Aggregate Thread usage (milliseconds)
    By adding each cycle's thread CPU usage, lists the threads that are most
    consistently busy.
Memory usage
    For individual cycles - find periods when JVM memory usage is at maximum.
Garbage Collectors
    Summarize the total, average, and maximum GC times for all running collectors.

For 7.2 and later, profile data:
Top Streams
    The highest average rate output/input streams

To collect profile data, you can connect sbprofile to a running server.
For example:

    sbprofile -u sb://hostname:portnumber -c profile-dd-mm-yy.log

There are two sample profile files included in this component (one from 7.1
and one from 6.6).

A bash script is provided that determines if the profile file provided
is 7.1 or later and runs the correct bundled application. To use the
bash script, do:

$ sum-profile profile-7.1.log.gz
# Target information: [sbd at campbell7:10000; pid=9192; version=7.1.7ruges_150842; name=campbell7; javahome=C:\Program Files (x86)\StreamBase Systems\StreamBase.7.1\jdk64\jre; memory=1629888kb; clients=1; leadership_status=LEADER]
Version mismatch. This is StreamBase version 7.1.7, but the bundle is built with version 7.0.12
sbd at campbell7:56789; pid=7472; version=7.1.7ruges_150842; Listening
Total Number of Operators=505. Zero Operators=71. Non-Zero Operators=434. Start time=2012-01-19 08:54:04.434-0500, profile duration 3 minutes.
*** Top Input TPS ***
Executions.IsMarker, InTPS=446,  OutTPS=446, Size=0, CPU=0uS, Type=filter,  2012-01-19 08:54:04.434-0500
Executions.SnapOutUnion, InTPS=446,  OutTPS=446, Size=0, CPU=0uS, Type=union,  2012-01-19 08:54:05.434-0500
.
.
.

A Window DOS script called sum-profile.bat is also provided, but this
script is only intended to run on StreamBase 7.1.6 and later.

The number of lines summarized in each category can be configured by
setting the environment variable MAX_COUNT=X, where X is the number
of lines you wish.

In addition to the summary data sent to the console, there are 6 files
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

You can get operator and module test coverage information if you
specify the "-z" option on sbprofile. This will include operators that
have zero input/output data in the profile data which is omitted by default.
The {profile-filename}-Operator-Zero.csv file will list the operators
that were never touched during a profile run, while
{profile-filename}-ModuleName-Zero.csv will list the modules that
contained no operators that had data flow.

Because the script uses sbbundles, it requires that the version of
profile data you are reading is the same as the StreamBase version on
your path.  The 6.6 apps are authored with 6.6, while the 7.* apps are
authored with 7.1. Note that StreamBase 7.1.6 and later have an override
option that will run a bundle built by a previous version.

A note about versions. StreamBase server profile data should be
collected from an sbprofile command with the same version as the
server.

Version History:

1.6     Fix exotic identifiers. Add file with streams summary.
1.5     Support bz2 format. Only emit unknown message types once.
		Performance improvements. Support of 7.0.x dropped.
1.4     Add windows sum-profile.bat script. Update profile sample data to 7.1.
        Add concurrent region and queue lists. Add module filtering.
        Fix time filters. Fix bugs related to calculating GC numbers.
1.3     Add top microseconds per tuple. Add Max and Ave GC times.
1.2     Add module summary files.
1.1     Add operator counts, gap detection, profile duration,
        minor bug fixes. Add latent support for future garbage
        collection data. Rename and improve bash script.

1.0     Initial release.
