/*
* Copyright © 2019. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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
import com.streambase.liveview.server.event.query.BeginSnapshotEvent;
import com.streambase.liveview.server.event.query.EndSnapshotEvent;
import com.streambase.liveview.server.event.query.QueryExceptionEvent;
import com.streambase.liveview.server.event.query.listener.QueryEventListener;
import com.streambase.liveview.server.event.tuple.TupleAddedEvent;
import com.streambase.liveview.server.query.QueryModel;
import com.streambase.liveview.server.table.CatalogedTable;
import com.streambase.liveview.server.table.Table;
import com.streambase.liveview.server.table.plugin.TableProviderControl;
import com.streambase.liveview.server.table.publisher.TablePublisher;
import com.streambase.liveview.server.util.FunctionalCopier;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.Schema;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.util.Msg;
import com.streambase.sb.util.ProcessReader;
import com.streambase.sb.util.Util;

/*
 * This is the OSExecTable implementation. It executes commands in separate processes running as the user account
 * the LiveView server is running as. Obviously a very sharp instrument to be used very carefully. 
 * 
 * select command from OSExec where cmd=<command to execute>
 * return is snapshot only with schema OutputFromCommand(string)
 * 
 * See the parseQuery method for details 
 */
public class OSExecTable implements Table  {

    private Logger logger = LoggerFactory.getLogger(OSExecTable.class);
    private BlockingQueue<OSExecOperation> queue=new  LinkedBlockingQueue<OSExecOperation>();
    AtomicBoolean shouldRun=new AtomicBoolean(true);
    
    private String NAME_VALUE="OutputFromCommand";
    Schema.Field FIELD_VALUE = new Schema.Field(NAME_VALUE, CompleteDataType.forString());
    List<String> fieldArray = Arrays.asList(NAME_VALUE);
    // Some members for holding information about the OS exec object 
	private Schema schema=null;
	private Schema keySchema=null;
	private CatalogedTable catalogTable=null;

	private final TableProviderControl helper;
	private final String tableName;
	
	private final int processWaitTimeMS=Util.getIntSystemProperty("liveview.tstableprovider.process.wait.ms", 600000);
	
	OSExecThread execOSThread=null;
	
    public OSExecTable(TableProviderControl helper, String tableName) {
		this.helper=helper;
		this.tableName=tableName;
		
		// The execution work is done in a separate thread 
		
		execOSThread = new OSExecThread(this);
		execOSThread.start();
		
		catalogTable = new CatalogedTable(tableName);
		catalogTable.setStatus(TableStatus.ENABLED, "Ready to Execute");
		catalogTable.setCapabilities(EnumSet.of(LiveViewTableCapability.SNAPSHOT));
		catalogTable.setQueryLanguages( EnumSet.of(LiveViewQueryLanguage.OTHER));
		catalogTable.setDescription("TableProvider that can execute local commands.  V1.0");
		catalogTable.setGroup("TechnicalSupport");
        catalogTable.setCreateTime(Timestamp.now());
        schema = new Schema(null, FIELD_VALUE);
        keySchema = new Schema(null, FIELD_VALUE);;
        catalogTable.setSchema(schema);
       
        catalogTable.setKeyFields(fieldArray);
        catalogTable.setRuntimeTable(this);
        
        helper.insert(catalogTable);
    }
    
	@Override
	public void addListener(QueryEventListener listener, LiveViewQueryType arg1, QueryModel queryModel) throws LiveViewException {
		// Just add a new query to a work list
		logger.debug(String.format("Adding query: %s : %s", queryModel.getProjection(), queryModel.getPredicate()));
		queue.add(new OSExecOperation(listener, queryModel));
	}

	@Override
	public TablePublisher createPublisher(String arg0) throws LiveViewException {
		throw LiveViewExceptionType.PUBLISHING_NOT_SUPPORTED.error();
	}

	@Override
	public void removeListener(QueryEventListener arg0) throws LiveViewException {
		// Since this table provider only does snapshots, we don't have anything to clean up here.
	}

	public Schema getSchema() {
		return schema;
	}
	
	public String getTableName() {
		return tableName;
	}
	
	public void shutdown() {
		shouldRun.set(false);
	}
	
	/*
	 * Simple helper class to hold a query in the work list
	 */
	private class OSExecOperation {
		
		public final QueryEventListener listener;
		public final QueryModel queryModel;
		
		public OSExecOperation(QueryEventListener listener, QueryModel queryModel) {
			this.listener=listener;
			this.queryModel=queryModel;
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
		Pattern queryOK = Pattern.compile("select +(\\w+) +from +(\\w+) +where +cmd=(.*)");
		Matcher matcher=queryOK.matcher(lowerQuery);
		
		int lenPredicate;
		Integer limit=null;
		if (matcher.matches()) {
			// need to strip any LIMIT off
			Pattern gotLimit = Pattern.compile(".*( +limit) +(\\d+) *$");
			Matcher limitMatcher=gotLimit.matcher(matcher.group(3));

			lenPredicate=matcher.group(3).length();
			if (limitMatcher.matches()) {
				lenPredicate=limitMatcher.start(1);
				limit=Integer.valueOf(limitMatcher.group(2));
			}
		} else { 
			throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Failed OSExec parse: %s", lowerQuery));
		}

		int start=matcher.start(3);
		String finalPredicate=queryString.substring(start, start+lenPredicate);
		int queryLimit= (limit==null) ? 0 : limit;
		boolean hasLimit = (limit==null) ? false : true;
		
		QueryModel queryModel = new QueryModel() {
			
			String projection=matcher.group(1);
			String table=matcher.group(2);
			// Need the case of the predicate
			String predicate=finalPredicate;
			
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
				return schema;
			}
			
			@Override
			public LiveViewQueryType getQueryType() {
				return LiveViewQueryType.SNAPSHOT;
			}
			
			@Override
			public LiveViewQueryLanguage getQueryLanguage() {
				return LiveViewQueryLanguage.OTHER;
			}
			
			@Override
			public String getProjection() {
				return projection;
			}
			
			@Override
			public String getPredicate() {
				return predicate;
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
				// TODO Auto-generated method stub
				return queryLimit;
			}
			
			@Override
			public Schema getKeySchema() {
				return keySchema;
			}
			
			@Override
			public List<String> getKeyFields() {
				return fieldArray;
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
				return schema;
			}
			
			@Override
			public List<String> getDataFields() {
				return fieldArray;
			}
			
			@Override
			public CatalogedTable getCatalogedTable() {
				return catalogedTable;
			}

			@Override
			public String getDisplayablePredicate() {
				return predicate;
			}
		};
					
		return queryModel;
	}
	
	/*
	 * The OSExecThread does all the work.
	 */
	private class OSExecThread extends Thread {
		
		private final OSExecTable fsTable;
		
		public OSExecThread(OSExecTable et) {
			this.fsTable=et;
		}
		
		public void run() {	
			// Now we are ready to start processing queries.
			while (shouldRun.get()) {
				OSExecOperation execOp=null;
				try {
					execOp=null;
					execOp=queue.poll(500, TimeUnit.MILLISECONDS);
					
					if (execOp !=null) {
						new BeginSnapshotEvent(this).dispatch(execOp.listener);
						doExecOp(execOp.listener, execOp.queryModel);
					}
				} catch (LiveViewException le) {
					if (execOp != null) {
						new QueryExceptionEvent(this, le).dispatch(execOp.listener);
					}
				} catch (Exception e) {
					logger.warn(Msg.format("Unexpected exception: {0}", e.getMessage()));
				} finally {
					// Even if dumpRows blows up we need to send an EndSnapshotEvent
					if (execOp != null) {
						new EndSnapshotEvent(this).dispatch(execOp.listener);
					}
				}
			}
		}
		
		/*
		 * This is the method that figures out the OSExec operation, executes it, and sends both stdout and stderr to clients.
		 * 
		 */
		private void doExecOp(QueryEventListener listener, QueryModel queryModel) throws LiveViewException {
	
			String verb=queryModel.getProjection();
			String pred=queryModel.getPredicate();
			long idKey=1;	
			
			switch (verb.toLowerCase()) {
			
			case "command": {
				
				// splitting like this is very weak.
				String[] args=queryModel.getPredicate().split(" ");
				
				try {
					ProcessBuilder pb = new ProcessBuilder(args);
					
					ProcessReader pr = new ProcessReader(pb.start());
					pr.waitFor(processWaitTimeMS);
					
					if (pr.exitValue()!=0) {
						logger.warn("Error getting snapshot");
					}
	
					String[] stdout= pr.getStdOutLines();
					if ((stdout.length > 1) || !stdout[0].isEmpty()) {
						for (String l : stdout) {
					    	Tuple tuple = schema.createTuple();
					    	tuple.setString(NAME_VALUE, "stdout: " + l);
					    	new TupleAddedEvent(this, idKey++, tuple, queryModel).dispatch(listener);
							if (queryModel.hasLimit()) {
								if (queryModel.getLimit() < idKey) {
									throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
								}
							}
						}
					}
					String[] errout= pr.getStdErrLines();
					if ((errout.length > 1) || !errout[0].isEmpty()) {
						for (String l : errout) {
					    	Tuple tuple = schema.createTuple();
					    	tuple.setString(NAME_VALUE, "stderr: " + l);
					    	new TupleAddedEvent(this, idKey++, tuple, queryModel).dispatch(listener);
							if (queryModel.hasLimit()) {
								if (queryModel.getLimit() < idKey) {
									throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
								}
							}
						}
					}
				} catch (LiveViewException e) {
					throw e;
				}
				catch (Exception e) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
				}

				break;
			}
			
			default:
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Unsupported verb: %s", verb));
			
			}
		}
	}
}