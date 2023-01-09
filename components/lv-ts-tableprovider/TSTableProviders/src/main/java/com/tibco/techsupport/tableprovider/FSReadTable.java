/*
* Copyright © 2019. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import com.streambase.sb.ByteArrayView;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.Schema;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.util.Msg;

/*
 * This is the FSReadTable implementation. It can handle some FS Read operations
 * 
 * select [ ls | read | readbin ] from FileSystemRead where path=<path> [ limit N ]
 * return is snapshot only with schema ReturnedOutput(string) for ls and read and BlobValue(blob) for readbin
 * 
 * See the parseQuery method for details 
 */
public class FSReadTable implements Table  {

    private Logger logger = LoggerFactory.getLogger(FSReadTable.class);
    private BlockingQueue<FSReadOperation> queue=new  LinkedBlockingQueue<FSReadOperation>();
    AtomicBoolean shouldRun=new AtomicBoolean(true);
    
    private String NAME_VALUE="ReturnedOutput";
    private String NAME_BLOB="BlobValue";
    private Schema.Field FIELD_VALUE = new Schema.Field(NAME_VALUE, CompleteDataType.forString());
    private List<String> valueFieldArray = Arrays.asList(NAME_VALUE);
    
	private Schema.Field FIELD_BLOB = new Schema.Field(NAME_BLOB, CompleteDataType.forBlob());
    private List<String> blobFieldArray = Arrays.asList(NAME_BLOB);
    private Schema readBinSchema = new Schema(null, FIELD_BLOB);

    private final String COMMAND_READ="read";
    private final String COMMAND_READBIN="readbin";
    private final String COMMAND_LS="ls";
    
	private Schema schema=null;
	private Schema keySchema=null;
	private CatalogedTable catalogTable=null;

	private final TableProviderControl helper;
	private final String tableName;
	
	FSOperationThread fsot=null;
	
    public FSReadTable(TableProviderControl helper, String tableName) {
		this.helper=helper;
		this.tableName=tableName;
		
		// The file system work is done in a separate thread 
		fsot = new FSOperationThread(this);
		fsot.start();
		
		catalogTable = new CatalogedTable(tableName);
		catalogTable.setStatus(TableStatus.ENABLED, "Ready to Read");
		catalogTable.setCapabilities(EnumSet.of(LiveViewTableCapability.SNAPSHOT));
		catalogTable.setQueryLanguages( EnumSet.of(LiveViewQueryLanguage.OTHER));
		catalogTable.setDescription("TableProvider that can read local system files.  V1.0");
		catalogTable.setGroup("TechnicalSupport");
        catalogTable.setCreateTime(Timestamp.now());
        schema = new Schema(null, FIELD_VALUE);
        keySchema = new Schema(null, FIELD_VALUE);;
        catalogTable.setSchema(schema);
       
        catalogTable.setKeyFields(valueFieldArray);
        catalogTable.setRuntimeTable(this);
        
        helper.insert(catalogTable);
    }
    
	@Override
	public void addListener(QueryEventListener listener, LiveViewQueryType arg1, QueryModel queryModel) throws LiveViewException {
		// Just add a new query to a work list
		logger.debug(String.format("Adding query: %s : %s", queryModel.getProjection(), queryModel.getPredicate()));
		queue.add(new FSReadOperation(listener, queryModel));
	}

	@Override
	public void removeListener(QueryEventListener arg0) throws LiveViewException {
		// Since this table provider only does snapshots, we don't have anything to clean up here.
	}
    
	@Override
	public TablePublisher createPublisher(String arg0) throws LiveViewException {
		throw LiveViewExceptionType.PUBLISHING_NOT_SUPPORTED.error();
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
	private class FSReadOperation {
		
		public final QueryEventListener listener;
		public final QueryModel queryModel;
		
		public FSReadOperation(QueryEventListener listener, QueryModel queryModel) {
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
		Pattern queryOK = Pattern.compile("select +(\\w+) +from +(\\w+) +where +path=(.*)");
		Matcher matcher=queryOK.matcher(lowerQuery);
		
		if (!matcher.matches()) {
			throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Couldn't parse: %s", lowerQuery));
		}
		
		// Some UIs always tack on a LIMIT. If present, honor it.
		Pattern gotLimit = Pattern.compile(".*( +limit) +(\\d+) *$");
		Matcher limitMatcher=gotLimit.matcher(matcher.group(3));

		Integer suppliedLimit=null;
		int lenPredicate=matcher.group(3).length();
		if (limitMatcher.matches()) {
			lenPredicate=limitMatcher.start(1);

			try {
				suppliedLimit=Integer.valueOf(limitMatcher.group(2));
			} catch (Exception e) {
				throw LiveViewExceptionType.INVALID_REQUEST.error(String.format("Couldn't parse limit: %s", limitMatcher.group(2)));
			}
		}
		
		// Need the case of the predicate
		int start=matcher.start(3);
		String finalPredicate=queryString.substring(start, start+lenPredicate);
		int queryLimit = (suppliedLimit == null) ? 0 : suppliedLimit;
		boolean hasLimit = (suppliedLimit == null) ? false : true;
		
		QueryModel queryModel = new QueryModel() {
			
			String projection=matcher.group(1);
			String table=matcher.group(2);
			String predicate=finalPredicate;
					
			boolean isReadBin=projection.equalsIgnoreCase(COMMAND_READBIN);
			
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
				if (isReadBin) {
				    return readBinSchema;
				} 
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
				return queryLimit;
			}
			
			@Override
			public Schema getKeySchema() {
				return keySchema;
			}
			
			@Override
			public List<String> getKeyFields() {
				if (isReadBin) {
					return blobFieldArray;
				}
				return valueFieldArray;
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
				if (isReadBin) {
				    return readBinSchema;
				}
				return schema;
			}
			
			@Override
			public List<String> getDataFields() {
				if (isReadBin) {
				    return blobFieldArray;
				}
				return valueFieldArray;
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
	 * The FSOperationThread does all the FS work.
	 */
	private class FSOperationThread extends Thread {
		
		private final FSReadTable fsTable;
		
		public FSOperationThread(FSReadTable et) {
			this.fsTable=et;
		}
		
		public void run() {	
			// Now we are ready to start processing queries.
			while (shouldRun.get()) {
				FSReadOperation fsop=null;
				try {
					fsop=null;
					fsop=queue.poll(500, TimeUnit.MILLISECONDS);
					
					if (fsop !=null) {
						new BeginSnapshotEvent(this).dispatch(fsop.listener);
						doReadOp(fsop.listener, fsop.queryModel);
					}
				} catch (LiveViewException le) {
					if (fsop != null) {
						new QueryExceptionEvent(this, le).dispatch(fsop.listener);
					}
				} catch (Exception e) {
					logger.warn(Msg.format("Unexpected exception: {0}", e.getMessage()));
				} finally {
					// Even if dumpRows blows up we need to send an EndSnapshotEvent
					if (fsop != null) {
						new EndSnapshotEvent(this).dispatch(fsop.listener);
					}
				}
			}
		}
		
		/*
		 * This is the method that figures out the read operation, executes it, and serializes it into tuples and sends it to clients.
		 * 
		 * The "query language" is very primitive:
		 * select [ls read readbin ] from FileSystemRead where path=<path to File> 
		 */
		private void doReadOp(QueryEventListener listener, QueryModel queryModel) throws LiveViewException {
	
			String verb=queryModel.getProjection();
			String pred=queryModel.getPredicate();
			Integer limit=queryModel.hasLimit() ? queryModel.getLimit() : null;
			
			long idKey=1;	
			
			switch (verb) {
			
			case COMMAND_LS: {
				
				File dir = new File(pred);
				if (!dir.isDirectory()) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Path is not a directory: %s", pred));
				}
				File[] filesList = dir.listFiles();
				
				for (File file : filesList) {
					StringBuilder sb=new StringBuilder();
					Tuple tuple = schema.createTuple();
				    if (file.isFile()) {
				    	sb.append(file.length());
				    } else {
				    	sb.append("d");
				    }
				    sb.append(" ").append(file.getName());
				    
					try {
						tuple.setString(NAME_VALUE, sb.toString());
					} catch (Exception e) {
						throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(e);
					}
					
					new TupleAddedEvent(this, idKey++, tuple, queryModel).dispatch(listener);
					if ((limit != null) && (idKey > limit)) {
						throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
					}
				}
				break;
			}
			case COMMAND_READ: { 
				File file = new File(pred);
				if (!file.isFile()) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Path is not a file: %s", pred));
				}
				
				try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				    String line;
				    while ((line = br.readLine()) != null) {
				    	Tuple tuple = schema.createTuple();
				    	tuple.setString(NAME_VALUE, line);
				    	new TupleAddedEvent(this, idKey++, tuple, queryModel).dispatch(listener);
						
				    	if ((limit != null) && (idKey > limit)) {
							throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
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
			
			case COMMAND_READBIN: { 
				File file = new File(pred);
				if (!file.isFile()) {
					throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Path is not a file: %s", pred));
				}
				
				Path path = Paths.get(pred);
				try  {
					byte[] fileContents =  Files.readAllBytes(path);
					logger.info(String.format("ReadBin: File: %s size %s", pred, fileContents.length));
					Tuple tuple = readBinSchema.createTuple();
					ByteArrayView bav= ByteArrayView.makeView(fileContents);
					tuple.setBlobBuffer(NAME_BLOB, bav);
					new TupleAddedEvent(this, idKey++, tuple, queryModel).dispatch(listener);
			    	if ((limit != null) && (idKey > limit)) {
						throw LiveViewExceptionType.SNAPSHOT_OVER_LIMIT.error();
					}
				} catch (LiveViewException e) {
					throw e;
				} catch (Exception e) {
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