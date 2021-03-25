# StreamBase profile data summary/analysis tool

This eventflow sample provides an application that reads StreamBase profile
data files and summarizes the data into top consumers by category.

The categories available for all StreamBase versions are:

Top Operator CPU usage
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
Top Streams
    The highest average rate output/input streams

To collect profile data, you can connect sbprofile to a running server.
For example:

    sbprofile -u sb://hostname:portnumber -c profile-dd-mm-yy.log

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
