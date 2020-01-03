/*
* Copyright © 2019. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.client.LiveViewQueryLanguage;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.LiveViewTableCapability;
import com.streambase.liveview.client.OrderDefinition;
import com.streambase.liveview.client.Table.TableStatus;
import com.streambase.liveview.client.internal.LiveViewConstants;
import com.streambase.liveview.server.event.query.BeginSnapshotEvent;
import com.streambase.liveview.server.event.query.EndSnapshotEvent;
import com.streambase.liveview.server.event.query.QueryExceptionEvent;
import com.streambase.liveview.server.event.query.listener.QueryEventListener;
import com.streambase.liveview.server.event.tuple.TupleAddedEvent;
import com.streambase.liveview.server.event.tuple.TupleRemovedEvent;
import com.streambase.liveview.server.event.tuple.TupleUpdatedEvent;
import com.streambase.liveview.server.query.QueryModel;
import com.streambase.liveview.server.table.CatalogedTable;
import com.streambase.liveview.server.table.Table;
import com.streambase.liveview.server.table.plugin.TableProviderControl;
import com.streambase.liveview.server.table.publisher.TablePublisher;
import com.streambase.liveview.server.util.FunctionalCopier;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.NullValueException;
import com.streambase.sb.Schema;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleException;
import com.streambase.sb.util.Msg;
import com.streambase.sb.util.Util;

/*
 * This is the LVServerLog table implementation.
 * 
 * This is a very limited implementation of a table. You can publish to it, and you can make snapshot
 * or snapshot_and_continuous queries to it. But it does not support any predicates, nor projections.
 * It's intended for a time series table to be used a means for services only servers to present their
 * server logs to an LV client basically as a streaming file.
 * 
 * select snapshot from LVServerLog
 * select continuous from LVServerLog
 * select clear from LVServerLog
 */
public class ServerLogTable implements Table  {

    private Logger logger = LoggerFactory.getLogger(ServerLogTable.class);
    AtomicBoolean shouldRun=new AtomicBoolean(true);
    
    private final String COMMAND_SNAPSHOT = "snapshot"; 
    private final String COMMAND_CONTINUOUS = "continuous";
    private final String COMMAND_CLEAR = "clear"; 
    
    // These are the table columns
    private String NAME_APPENDER="AppenderName";
    private String NAME_EVENTINDEX="EventIndex";
    private String NAME_EVENTTIME ="EventTime";
    private String NAME_LOGGERNAME="LoggerName";
    private String NAME_LEVEL="Level";
    private String NAME_TEXT="Text";
    private String NAME_EXCEPTION="Exception";
    private String NAME_THREADNAME="Threadname";
    
    private Schema.Field FIELD_APPENDER=new Schema.Field(NAME_APPENDER, CompleteDataType.forString());
    private Schema.Field FIELD_EVENTINDEX=new Schema.Field(NAME_EVENTINDEX, CompleteDataType.forLong());
    private Schema.Field FIELD_EVENTTIME=new Schema.Field(NAME_EVENTTIME, CompleteDataType.forTimestamp());
    
    private final Schema.Field[] SERVERLOG_FIELDS = {
    		FIELD_APPENDER,
    		FIELD_EVENTINDEX,
    		FIELD_EVENTTIME,
            new Schema.Field(NAME_LOGGERNAME, CompleteDataType.forString()),
            new Schema.Field(NAME_LEVEL, CompleteDataType.forString()),
            new Schema.Field(NAME_TEXT, CompleteDataType.forString()),
            new Schema.Field(NAME_EXCEPTION, CompleteDataType.forString()),
            new Schema.Field(NAME_THREADNAME, CompleteDataType.forString()),
    };
    private final Schema tableSchema = new Schema(null, SERVERLOG_FIELDS);
    private List<String> dataFields = new ArrayList<String>(Arrays.asList(NAME_APPENDER, NAME_EVENTINDEX, NAME_EVENTTIME, NAME_LOGGERNAME, NAME_LEVEL, NAME_TEXT, NAME_EXCEPTION, NAME_THREADNAME));
    
    private final Schema.Field[] KEY_FIELDS = {FIELD_APPENDER, FIELD_EVENTINDEX, FIELD_EVENTTIME};
    private Schema keySchema = new Schema(null, KEY_FIELDS);
    private Tuple keyTuple=keySchema.createTuple();
    
    private List<String> keyFields = new ArrayList<String>(Arrays.asList(NAME_APPENDER, NAME_EVENTINDEX, NAME_EVENTTIME));
    private final List<String> queryKeyList;
    
    // right now just say all fields have changed.
    private List<Integer> changedFields = new ArrayList<Integer>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7));
    
	private CatalogedTable catalogTable=null;
	private final TableProviderControl helper;
	private final String tableName;
	
	// helper method to extract just the primary key from a tuple
	private Tuple getKey(Tuple tuple) throws NullValueException, TupleException {
		keyTuple.setString(0, (tuple.isNull(0)) ? null : tuple.getString(0));
		keyTuple.setLong(1, (tuple.isNull(1)) ? null : tuple.getLong(1));
		keyTuple.setTimestamp(2, (tuple.isNull(2)) ? null : tuple.getTimestamp(2));
		return keyTuple;
	}
	
	// this map holds the table rows, mapped by a long id 
	private Map<Long, Tuple> idToRowMap = new ConcurrentHashMap<Long, Tuple>();
	
	// this maps primary key values to an ID 
	private Map<Tuple, Long> keyToIDMap = new ConcurrentHashMap<Tuple, Long>();
	// this table uses a global ID that increases indefinitely
	private long globalID=1;
	
	// Queue that holds listener adds briefly, they are taken off and process by the run thread
	private BlockingQueue<TableListener> listenerQueue = new LinkedBlockingQueue<TableListener>();
	
	// The map hold continuous query listeners. It's use send upserts to all registered listeners.
	private Map<QueryEventListener, TableListener> continuousQueryMap = new ConcurrentHashMap<QueryEventListener, TableListener>();
	
	// this is the size the ServerLog table is allowed to grow to. Once this limit is reached,
	// each newly arriving row will evict the oldest row in the table 
	private int limit=Util.getIntSystemProperty("liveview.tstableprovider.lvserverLog.limit", 2000);
	
	// A shared server publisher
	private ServerLogPublisher serverLogPub=new ServerLogPublisher();
	
	// Thread that runs/setups queries
	SystemTableThread stt=null;
	
    public ServerLogTable(TableProviderControl helper, String tableName) {
		this.helper=helper;
		this.tableName=tableName;
				
		stt = new SystemTableThread();
		stt.start();
		
		catalogTable = new CatalogedTable(tableName);
		catalogTable.setStatus(TableStatus.ENABLED, "Ready");
		catalogTable.setCapabilities(EnumSet.of(LiveViewTableCapability.PUBLISH, LiveViewTableCapability.SNAPSHOT, LiveViewTableCapability.CONTINUOUS));
		catalogTable.setQueryLanguages( EnumSet.of(LiveViewQueryLanguage.OTHER));
		catalogTable.setDescription("TableProvider that can serve as a primative local table to publish server log messages.  V1.0");
		catalogTable.setGroup("TechnicalSupport");
        catalogTable.setCreateTime(Timestamp.now());
        catalogTable.setSchema(tableSchema);
       
        catalogTable.setKeyFields(keyFields);
        catalogTable.setRuntimeTable(this);
        
        // the key used by clients
		queryKeyList = new ArrayList<String>();
		queryKeyList.add(LiveViewConstants.CQS_INTERNAL_ID);

        helper.insert(catalogTable);
    }
    
	@Override
	public void addListener(QueryEventListener listener, LiveViewQueryType arg1, QueryModel queryModel) throws LiveViewException {
		// Just add a new query to a work list
		logger.debug(String.format("Adding query: %s : %s", queryModel.getProjection(), queryModel.getPredicate()));
		listenerQueue.add(new TableListener(listener, queryModel, null));
	}

	@Override
	public void removeListener(QueryEventListener listener) throws LiveViewException {
		continuousQueryMap.remove(listener);
	}
    
	@Override
	public TablePublisher createPublisher(String arg0) throws LiveViewException {
		return serverLogPub;
	}

	public Schema getSchema() {
		return tableSchema;
	}
	
	public String getTableName() {
		return tableName;
	}
	
	public void shutdown() {
		shouldRun.set(false);
		// shutdown all the queries to this table
		for (TableListener tl : continuousQueryMap.values()) {
			new QueryExceptionEvent(this, LiveViewExceptionType.KILL_QUERY_TABLE_DROPPED_KILL.error()).dispatch(tl.listener);
		}
	}
	
	/*
	 * Simple helper class to hold a query in the work list
	 */
	private class TableListener {
		
		protected final QueryEventListener listener;
		protected final QueryModel queryModel;
		protected final Object source;	
		
		public TableListener(QueryEventListener listener, QueryModel queryModel, Object source) {
			this.listener=listener;
			this.queryModel=queryModel;
			this.source=source;
		}
	}
	
	/*
	 * The parseQuery method basically defines the query language for the TableProvider 
	 */
	@Override
	public QueryModel parseQuery(CatalogedTable catalogedTable, String queryString, LiveViewQueryType type, boolean includeInternal, String additionalPredicate) throws LiveViewException {
	
		logger.debug("parseQuery: " + queryString);
			
		if ((queryString==null) || queryString.isEmpty()) {
			throw LiveViewExceptionType.INVALID_REQUEST.error("No Query string found");
		}
		
		String lowerQuery = queryString.toLowerCase();
		Pattern queryOK = Pattern.compile("select +(\\w+) +from +(\\w+)(.*)");
		Matcher matcher=queryOK.matcher(lowerQuery);
		if (!matcher.matches()) {
			throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Failed basic LVServerLogTable parse: %s", queryString));
		}

		String command= matcher.group(1);
		// Some UIs always tack on a LIMIT. If present, honor it.
		Pattern gotLimit = Pattern.compile(".*( +limit) +(\\d+) *$");
		Matcher limitMatcher=gotLimit.matcher(matcher.group(3));
		
		Integer suppliedLimit=null;
		if (limitMatcher.matches()) {
			try {
				suppliedLimit=Integer.valueOf(limitMatcher.group(2));
			} catch (Exception e) {
				throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Couldn't parse limit: %s", limitMatcher.group(2)));
			}
		}
		int queryLimit = (suppliedLimit == null) ? 0 : suppliedLimit;
		boolean hasLimit = (suppliedLimit == null) ? false : true;
		
		QueryModel queryModel = new QueryModel() {
			
			@Override
			public void validate(CatalogedTable catalogedTable, boolean includeInternalFields) throws LiveViewException {
				// TODO Auto-generated method stub
			}
			
			@Override
			public void setTupleCopier(Schema schema, Schema schema2, FunctionalCopier tupleCopier) {
				// TODO Auto-generated method stub
			}
			
			@Override
			public boolean isPivotQuery() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean isCalcColumnQuery() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean isAggQuery() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasTimeWindowedPredicate() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasTemporalPredicate() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasPredicate() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasPivot() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasOrderBy() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasLimit() {
				return hasLimit;
			}
			
			@Override
			public boolean hasHaving() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public boolean hasGroupBy() {
				// TODO Auto-generated method stub
				return false;
			}
			
			@Override
			public String getWindowStartExpr() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getWindowEndExpr() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public FunctionalCopier getTupleCopier(Schema schema, Schema schema2) {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getTimestampField() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public long getTemporalPredicateComponent() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return 0;
			}
			
			@Override
			public String getTable() {
				return tableName;
			}
			
			@Override
			public Schema getSchema() {
				return tableSchema;
			}
			
			@Override
			public LiveViewQueryType getQueryType() {
				if ("continuous".equals(command)) {
					return LiveViewQueryType.SNAPSHOT_AND_CONTINUOUS;
				} else {
					return LiveViewQueryType.SNAPSHOT;
				}	
			}
			
			@Override
			public LiveViewQueryLanguage getQueryLanguage() {
				return LiveViewQueryLanguage.OTHER;
			}
			
			@Override
			public String getProjection() {
				return command;
			}
			
			@Override
			public String getPredicate() {
				return "true";
			}
			
			@Override
			public String getPivotValues() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getPivotField() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public String getPivotAggExpression() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public OrderDefinition getOrderDefinition() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public int getLimit() throws UnsupportedOperationException {
				return queryLimit;
			}
			
			@Override
			public Schema getKeySchema() {
				return keySchema;
			}
			
			@Override
			public List<String> getKeyFields() {
				// Just use Long keys
				return queryKeyList;
			}
			
			@Override
			public String getHaving() {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public List<String> getGroupBy() throws UnsupportedOperationException {
				// TODO Auto-generated method stub
				return null;
			}
			
			@Override
			public Schema getFullSchema() {
				return tableSchema;
			}
			
			@Override
			public List<String> getDataFields() {
				return dataFields;
			}
			
			@Override
			public CatalogedTable getCatalogedTable() {
				return catalogedTable;
			}

			@Override
			public String getDisplayablePredicate() {
				return "true";
			}
		};
					
		return queryModel;
	}
	
	/*
	 * The SystemTableThread does all the work.
	 */
	private class SystemTableThread extends Thread {
		
		public SystemTableThread() {}
		
		public void run() {	
			// Now we are ready to start processing queries.
			while (shouldRun.get()) {
				TableListener sttOp=null;
				try {
					sttOp=null;
					sttOp=listenerQueue.poll(500, TimeUnit.MILLISECONDS);
					
					if (sttOp !=null) {
						doServerLogOp(sttOp.listener, sttOp.queryModel);
					}
				} catch (LiveViewException le) {
					if (sttOp != null) {
						new QueryExceptionEvent(this, le).dispatch(sttOp.listener);
					}
				} catch (Exception e) {
					logger.warn(Msg.format("Unexpected exception: {0}", e.getMessage()));
					if (sttOp != null) {
						new QueryExceptionEvent(this, LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("LVServerLog exception: %s", e.getMessage()))).dispatch(sttOp.listener);
					}
				}
			}
		}
		
		/*
		 * This is the method that identifies the command and executes it.
		 */
		private void doServerLogOp(QueryEventListener listener, QueryModel queryModel) throws LiveViewException {
	
			String verb=queryModel.getProjection();
			switch (verb) {
			
			case COMMAND_SNAPSHOT: {
				
				new BeginSnapshotEvent(this).dispatch(listener);
				logger.info(String.format("Doing LVServerLog Snapshot"));

				int resultSize=0;
				synchronized (idToRowMap) {
					for (Long id : idToRowMap.keySet()) {
						new TupleAddedEvent(this, id, idToRowMap.get(id), queryModel).dispatch(listener);
						if (queryModel.hasLimit()) {
							resultSize++;
							if (resultSize >= queryModel.getLimit()) {
								throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
							}
						}
					}
				}
				new EndSnapshotEvent(this).dispatch(listener);
				break;
			}
				
			case COMMAND_CONTINUOUS: {

				new BeginSnapshotEvent(this).dispatch(listener);
				logger.info(String.format("Doing LVServerLog continuous"));

				synchronized (idToRowMap) {
					int resultSize=0;
					TableListener tl=new TableListener(listener, queryModel, this);
					continuousQueryMap.put(listener, tl);
					
					for (Long id : idToRowMap.keySet()) {
						new TupleAddedEvent(this, id, idToRowMap.get(id), queryModel).dispatch(listener);
						if (queryModel.hasLimit()) {
							resultSize++;
							if (resultSize >= queryModel.getLimit()) {
								throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
							}
						}
					}
				}
				new EndSnapshotEvent(this).dispatch(listener);
				break;
			}
			
			case COMMAND_CLEAR : {

				new BeginSnapshotEvent(this).dispatch(listener);
				logger.info(String.format("Clearing LVServerLog"));

				synchronized (idToRowMap) {
					// go through all listeners					
					for (TableListener tl : continuousQueryMap.values()) {
						// and delete all rows
						for (Long id : idToRowMap.keySet()) {
							new TupleRemovedEvent(tl.source, id, idToRowMap.get(id), tl.queryModel).dispatch(tl.listener);
						}
					}
					
					keyToIDMap.clear();
					idToRowMap.clear();
				}
				new EndSnapshotEvent(this).dispatch(listener);
				break;
			}
			
			default:
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Unsupported command: %s", verb));
			}
		}
	}
	
	public class ServerLogPublisher  implements TablePublisher  {
		
		@Override
		public void close() throws LiveViewException {
			// nothing we can do here
		}

		@Override
		public Long getLastPublishedId() throws LiveViewException {
			return null;
		}

		@Override
		public String getName() {
			return "LVServerLogPublisher";
		}

		@Override
		public void upsert(Long id, String CQSDataUpdatePredicate, Boolean CQSDelete, Tuple tuple) throws LiveViewException {
			Long existingID=null;
			Long removedID=null;
			boolean wasAdd=false;
			boolean wasRemoved=false;
			
			Tuple trimmedTuple=null;
			Long trimmedID=null;
			
			Tuple key;
			try {
				key=getKey(tuple);
			} catch (Exception e) {				
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error("Failed to get tuple key in upsert", e);
			}
		
			synchronized (idToRowMap) {
				
				if (CQSDelete!=null && CQSDelete) {
					removedID=keyToIDMap.remove(key);					
					if (removedID != null) {
						Tuple removedT=idToRowMap.remove(removedID);
						if (removedT==null) {
							logger.warn(String.format("Remove found ID: %s , but not a row for %s", removedID, tuple));
						}
						wasRemoved=true;
					}
				} else {
					existingID=keyToIDMap.get(key);
					
					if (existingID!=null) {
						// BUGBUG - need to add delta publish here
						idToRowMap.put(existingID, tuple);
					} else {
						wasAdd=true;
						keyToIDMap.put(key, globalID);
						idToRowMap.put(globalID, tuple);
					}
					
					int missingIDs=0;
					while (idToRowMap.size() > limit) {
						trimmedID=globalID-limit+missingIDs;
						trimmedTuple=idToRowMap.remove(trimmedID);
						// Although the intent is to support time series data, since deletes work it's
						// possible the trimmedID row was previously deleted.
						if (trimmedTuple==null) {
							// keep looking for the one to remove
							missingIDs++;
							continue;
						}
						try {
							keyToIDMap.remove(getKey(trimmedTuple));
						} catch (Exception e) {
							logger.warn(String.format("Failed to trim old rows: %s", e.getMessage()));
						}
					}
				}
			}
			
			if (removedID!=null && !wasRemoved) {
				// remove of something that didn't exist.
				return;
			}
			
			Iterator<QueryEventListener> it= continuousQueryMap.keySet().iterator();
			while (it.hasNext()) {
				TableListener tl=continuousQueryMap.get(it.next());
				
				if (removedID != null) {
					new TupleRemovedEvent(tl.source, removedID, tuple, tl.queryModel).dispatch(tl.listener);
				} else {
					if (existingID != null) {
						new TupleUpdatedEvent(tl.source, existingID, tuple, changedFields, tl.queryModel).dispatch(tl.listener);
					} else {
						new TupleAddedEvent(tl.source, globalID, tuple, tl.queryModel).dispatch(tl.listener);
						if (tl.queryModel.hasLimit()) {
							if (keyToIDMap.size() >= tl.queryModel.getLimit()) {
								new QueryExceptionEvent(this, LiveViewExceptionType.UPDATE_OVER_LIMIT.error()).dispatch(tl.listener);
								it.remove();
							}
						}
					}
				}
				
				if (trimmedID !=null) {
					new TupleRemovedEvent(tl.source, trimmedID, trimmedTuple, tl.queryModel).dispatch(tl.listener);
				}
			}

			if (wasAdd) {
				globalID++;
			}
		}
	}
}