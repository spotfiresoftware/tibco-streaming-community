# Blocker and Releaser adapter set

The Blocker and Releaser adapter set allows event flow authors to control locking in a concurrent region.
This is useful when you want to lock a region while other asynchronous activity runs.

The generic use case is this:

An event flow region has a work flow that must pause while other asynchronous work is done. The Blocker adapter takes a tuple in
and emits that tuple out. However, even when the processing of that tuple is completed in that region the Blocker adapter does not return as would normally be
expected. Eventually when the down stream asynchronous work is complete, a tuple is sent to the Releaser adapter. This signals the Blocker to
return and allow processing to continue in the region it is in.

It's important that the Blocker adapter always has a tuple sent first, followed some time later by a tuple being sent to the Releaser adapter. There
may be any number of Releaser adapters in different paths - but only one may be sent a tuple for a given Blocker tuple. 

It's also important to note that the processing model of StreamBase is that only exactly one tuple flow is active in a given region. So Blocker/Releaser adapters
are not needed, and should not be used, unless there's asynchronous work being done that needs to be synchronized with a different region.

Version History

**0.1:** Initial release.
