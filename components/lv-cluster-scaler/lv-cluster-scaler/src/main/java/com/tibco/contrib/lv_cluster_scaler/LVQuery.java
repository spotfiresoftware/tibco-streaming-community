package com.tibco.contrib.lv_cluster_scaler;

import com.streambase.sb.*;
import com.streambase.sb.TupleJSONUtil.Options;
import com.streambase.sb.adapter.*;
import com.streambase.sb.operator.*;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.BeginDeleteEvent;
import com.streambase.liveview.client.BeginSnapshotEvent;
import com.streambase.liveview.client.EndDeleteEvent;
import com.streambase.liveview.client.EndSnapshotEvent;
import com.streambase.liveview.client.LiveResult;
import com.streambase.liveview.client.LiveViewConnection;
import com.streambase.liveview.client.LiveViewConnectionFactory;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.Query;
import com.streambase.liveview.client.QueryClosedEvent;
import com.streambase.liveview.client.QueryConfig;
import com.streambase.liveview.client.QueryExceptionEvent;
import com.streambase.liveview.client.QueryListener;
import com.streambase.liveview.client.TupleAddedEvent;
import com.streambase.liveview.client.TupleRemovedEvent;
import com.streambase.liveview.client.TupleUpdatedEvent;

/**
* Generated by JDT StreamBase Client Templates (Version: 10.6.2.2107121246).
*
* <p>
*/
public class LVQuery extends InputAdapter implements Parameterizable, Runnable {

	public static final long serialVersionUID = 1626970168723L;
	private Schema inputSchema;
	private Schema statusSchema;
	private Schema dataschema;
	private String displayName = "LiveView Multiple Server Query";
	
	private final String LVNODEINFO="LVNodeInfo";
	private final String LVSESSIONQUERIES="LVSessionQueries";
	private final String LIVEVIEWSTATISTICS="LiveViewStatistics";
	
	// Status field names
    private final String TYPE_FIELD = "Type";
    private final String OBJECT_FIELD = "Object";
    private final String ACTION_FIELD = "Action";
    private final String MESSAGE_FIELD = "Message";
    private final String TUPLE_FIELD = "Tuple";
    private final String TIME_FIELD = "Time";
	
    // Input field names
    private final String ADD_FIELD = "IsAdd";
    
    // Data field names
    private final String URI_FIELD = "URI";
    private final String TABLENAME_FIELD = "TableName";
    private final String CQS_INTERNALID_FIELD = "CQSInternalID";
    private final String CQS_SCOPE_TRANSITION_FIELD = "CQSScopeTransition";
    private final String SNAPSHOT_FIELD = "Snapshot";
    
    private final String NEW = "New";
    private final String OLD = "Old";

    private final String NEW_JSON = "NewJSON";
    private final String OLD_JSON = "OldJSON";
    private final String SELECT = "Select";
    private final String QUERY = "Query";
    private final String SNAPSHOT = "Snapshot";
    private final String SNAPSHOT_ONLY = "SnapshotOnly";
    private final String UNREGISTER_QUERY = "Unregister";
    private final String TABLE_NAME = "TableName";
    
    private final int SCOPE_ADD=0;
    private final int SCOPE_DELETE=1;
    private final int SCOPE_UPDATE=2;
	
	private Map<String, LDMServer> ldmMap = new ConcurrentHashMap<String, LDMServer>();
	
	private Logger logger=LoggerFactory.getLogger(LVQuery.class);

	/**
	* The constructor is called when the Adapter instance is created, but before the Adapter 
	* is connected to the StreamBase application.
	*/
	public LVQuery() {
		super();
		setPortHints(1, 2);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());

	}

	/**
	* Typecheck this adapter. 
	*/
	public void typecheck() throws TypecheckException {
		
		inputSchema = getInputSchema(0);
		if (!inputSchema.hasField(URI_FIELD)) {
			throw new TypecheckException("Input needs the string field URI");
		}
		if (!inputSchema.hasField(ADD_FIELD)) {
			throw new TypecheckException("Input needs the boolean field IsAdd");
		}
		
		try {
			if (inputSchema.getField(URI_FIELD).getDataType() != DataType.STRING) {
				throw new TypecheckException("The input field URI must be a string");
			}
			if (inputSchema.getField(ADD_FIELD).getDataType() != DataType.BOOL) {
				throw new TypecheckException("The input field IsAdd must be a boolean");
			}
		} catch (TupleException e) {
			throw new TypecheckException(String.format("Unexpected error typechecking input: %s", e.getMessage()));
		}
		
        Schema.Field[] STATUS_FIELDS = {
                new Schema.Field(TYPE_FIELD, CompleteDataType.forString()),
                new Schema.Field(OBJECT_FIELD, CompleteDataType.forString()),
                new Schema.Field(ACTION_FIELD, CompleteDataType.forString()),
                new Schema.Field(MESSAGE_FIELD, CompleteDataType.forString()),
                new Schema.Field(TIME_FIELD, CompleteDataType.forTimestamp()),
            };
            statusSchema=new Schema(null, STATUS_FIELDS);
		setOutputSchema(0, statusSchema);
		
        Schema.Field[] DATA_FIELDS = {
                new Schema.Field(URI_FIELD, CompleteDataType.forString()),
                new Schema.Field(TABLENAME_FIELD, CompleteDataType.forString()),
                new Schema.Field(CQS_INTERNALID_FIELD, CompleteDataType.forLong()),
                new Schema.Field(CQS_SCOPE_TRANSITION_FIELD, CompleteDataType.forInt()),
                new Schema.Field(SNAPSHOT_FIELD, CompleteDataType.forBoolean()),
                new Schema.Field(NEW_JSON, CompleteDataType.forString()),
                new Schema.Field(OLD_JSON, CompleteDataType.forString()),
            };
            dataschema=new Schema(null, DATA_FIELDS);    
    	setOutputSchema(1, dataschema);
	}

	/**
	* Initialize the adapter.
	*/
	public void init() throws StreamBaseException {
		super.init();

		// Register the object so it will be run as a thread managed by StreamBase.
		registerRunnable(this, true);
	}

	/**
	* Shutdown adapter
	*/
	public void shutdown() {

	}

	@Override
	public void processTuple(int port, Tuple tuple) {
		try {
			String uri=tuple.getString(URI_FIELD);
			boolean add=tuple.getBoolean(ADD_FIELD);
			
			LDMServer ls;
			if (add) {
				ls=ldmMap.get(uri);
				if (ls != null) {
					logger.info(String.format("Add of %s failed, already known", uri));
					return;
				}
			} else {
				ls=ldmMap.remove(uri);
				if (ls == null) {
					logger.info(String.format("Remove of %s failed, not known", uri));
					return;
				}
				ls.getLVCon().close();
				return;
			}
			
			LiveViewConnection tmpCon=null;
			try {
				tmpCon = LiveViewConnectionFactory.getConnection(uri);
			} catch (Exception conE) {
				// BUGBUG status tuple
				logger.warn(String.format("Could not connect to URI: %s Error: %s", uri, conE.getMessage()));
				return;
			}
			
			ls = new LDMServer(uri);
			ldmMap.put(uri, ls);
			
			ls.setLVCon(tmpCon);
			QueryConfig qc = new QueryConfig().setQuery(LVNODEINFO, "true").setQueryType(LiveViewQueryType.SNAPSHOT_AND_CONTINUOUS);
			ls.getLVCon().registerQuery(qc, new LiveResult(new LVListener(ls, LVNODEINFO)));

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/**
	* Main thread of the adapter.
	*/
	public void run() {
		while (shouldRun()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
	}

	
//	private class LVListener implements QueryListener {
	private class LVListener implements QueryListener {
		private final LDMServer ldmServer;
		private final String tableName;
		private Tuple outputTuple;
		private boolean isSnap=true;
		
		protected LVListener (LDMServer ldmServer, String tableName) {
			this.ldmServer=ldmServer;
			this.tableName=tableName;
			outputTuple = dataschema.createTuple();
		}

		@Override
		public void deleteBegin(BeginDeleteEvent event) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void deleteEnd(EndDeleteEvent event) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void exceptionRaised(QueryExceptionEvent event) {
			// if one table fails, this will take the entire connection down.
			ldmMap.remove(ldmServer.getURL());
			ldmServer.getLVCon().close();
		}

		@Override
		public void queryClosed(QueryClosedEvent event) {
			// if one table fails, this will take the entire connection down.
			ldmMap.remove(ldmServer.getURL());
			ldmServer.getLVCon().close();
		}

		@Override
		public void snapshotBegin(BeginSnapshotEvent event) {
			isSnap=true;
		}

		@Override
		public void snapshotEnd(EndSnapshotEvent event) {
			isSnap=false;
		}

		@Override
		public void tupleAdded(TupleAddedEvent event) {
			sendResult(event.getKey(), SCOPE_ADD, event.getTuple(), null);
			
			if (tableName.equals(LVNODEINFO)) {
				// special case here to add the other tables if the node is a DATA_LAYER
				Tuple t=event.getTuple();
				try {
					if (t.getString("Category").equals("Cluster") && t.getString("Name").equals("Self") && t.getString("Value").equals("DATA_LAYER")) {
						// This add triggers two more queries 
						QueryConfig qc = new QueryConfig().setQuery(LVSESSIONQUERIES, "true").setQueryType(LiveViewQueryType.SNAPSHOT_AND_CONTINUOUS);
						Query q=ldmServer.getLVCon().registerQuery(qc, new LiveResult(new LVListener(ldmServer, LVSESSIONQUERIES)));
						
						qc = new QueryConfig().setQuery(LIVEVIEWSTATISTICS, "true").setQueryType(LiveViewQueryType.SNAPSHOT_AND_CONTINUOUS);
						q=ldmServer.getLVCon().registerQuery(qc, new LiveResult(new LVListener(ldmServer, LIVEVIEWSTATISTICS)));

					}
				} catch (Exception e) {
					logger.warn(String.format("Error issueing queries to %s", ldmServer.getURL()));
				}
			}
		}

		@Override
		public void tupleRemoved(TupleRemovedEvent event) {
			sendResult(event.getKey(), SCOPE_DELETE, null, event.getTuple());
			
		}

		@Override
		public void tupleUpdated(TupleUpdatedEvent event) {
			sendResult(event.getKey(), SCOPE_UPDATE, event.getTuple(), event.getOld());
			
		}
		
		private void sendResult(long CQSInternalID, int scope, Tuple newTuple, Tuple oldTuple) {
			try {
				outputTuple.clear();
		        outputTuple.setString(URI_FIELD, ldmServer.getURL());
		        outputTuple.setString(TABLENAME_FIELD, tableName);
		        outputTuple.setBoolean(SNAPSHOT_FIELD, isSnap);
		        outputTuple.setLong(CQS_INTERNALID_FIELD, CQSInternalID);
		        outputTuple.setInt(CQS_SCOPE_TRANSITION_FIELD, scope);
		
		        if (newTuple != null) {
		            outputTuple.setString(NEW_JSON, TupleJSONUtil.toJSONString(newTuple, EnumSet.of(Options.PREFER_MAP)));
		        }
		        
		        if (oldTuple != null) {
		            outputTuple.setString(OLD_JSON, TupleJSONUtil.toJSONString(oldTuple, EnumSet.of(Options.PREFER_MAP)));
		        }
		        sendOutput(1, outputTuple);
			} catch (Exception e) {
				logger.warn(String.format("Failed to send data for URI: %s Table: %s error:%s", ldmServer.getURL(), tableName, e.getMessage() ), e);
			}
		}
	
	}
	
	/***************************************************************************************
	 * The getter and setter methods provided by the Parameterizable object.               *
	 * StreamBase Studio uses them to determine the name and type of each property         *
	 * and obviously, to set and get the property values.                                  *
	 ***************************************************************************************/

//	public Schema getSchema0() {
//		return this.statusSchema;
//	}
//
//	public void setSchema0(Schema schema0) {
//		this.statusSchema = schema0;
//	}
//
//	public Schema getSchema1() {
//		return this.dataschema;
//	}
//
//	public void setSchema1(Schema schema1) {
//		this.dataschema = schema1;
//	}

}