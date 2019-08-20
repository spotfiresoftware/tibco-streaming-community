These adapters connect StreamBase to Redis (http://redis.io/)

The input adapter's single output port has a schema with two fields,
both strings:
  channel
  value

The RedisInput adapter always does a pattern subscribe (psubscribe)
using the value of its Redis subscription channel property.

The output adapter's single input port has a schema with two fields,
both strings:
  key
  value

The output adapter sets the Redis key with the value and also publishes
value on the key channel.

Both adapters maintain their own connection and in each is configured
using its own properties (including the Redis server to connect to).

This was developed against Redis 3.2.1, using Jedis 2.9.1 (a jar file 
is included for your convenience, from sources retrieved from
https://github.com/xetorthio/jedis).

The Apache Commons Pool 2 2.4.2 library is also used and provided.

The StreamBase applications in this project assume a Redis server is
running on port 6379 of the localhost. 

History:
1.1 SEB
 * Update to and test with StreamBase 7.6.7
 * Update to Redis 3.2.1 and Jedis 2.9.1 (https://github.com/xetorthio/jedis/archive/2.9.zip)
 * Update to Apache Commons Pool 2 2.4.2
 * Test with MSOpenTech Redis 3.2.100 (https://github.com/MicrosoftArchive/redis/releases/tag/win-3.2.100)
 * RedisInput: Close Jedis in Input Adapter shutdown()
 * RedisOutput: Use Jedis.close() in shutdown in RedisOutput (pool release deprecated)
 * RedisOutput: Use direct publish instead of PipelineBlock (deprecated)
 * RedisInputBeanInfo, RedisOutputBeanInfo: Make adapter properties optional since 
   they are defaulted in constructor; this makes the adapter instances typecheck
   cleanly when dropped from palette.
 * RedisInput: Don't recreate schema0 object with every typecheck(). Lazily create once.
 * RedisInput: use logger instead of System.err
 * remove commented out code
 * RedisAdapter.sbapp: Give some components more meaningful names
 * RedisInput: Use runtime schema at runtime
 * README.txt: Correct schema description for input adapter, update, add note about
   pattern subscribe
 * RedisInput: remove unused schema0 getter and setter
 * RedisOutput: remove unused run() method and Runnable
 * RedisInput, RedisOutput: demote all log info messages to debug
 * RedisAdapter.sbapp: reduce number of message published per second from
   4900 to 100
 * RedisAdapter.sbapp: remove concurrency from adapters
 * Add TestRedisAdapters.sbapp as suitable for automated unit tests
 * Add java-src/com/tibco/streambase/example/redis/test/TestRedisAdapters.java (testx1)
 1.0 John Drummond
 * Initial release
 

