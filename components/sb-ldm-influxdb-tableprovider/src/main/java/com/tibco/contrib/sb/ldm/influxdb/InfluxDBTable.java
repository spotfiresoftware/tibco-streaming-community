package com.tibco.contrib.sb.ldm.influxdb;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.influxdb.InfluxDB;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.client.LiveViewQueryLanguage;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.LiveViewTableCapability;
import com.streambase.liveview.client.Table.TableStatus;
import com.streambase.liveview.server.event.query.EndSnapshotEvent;
import com.streambase.liveview.server.event.query.QueryExceptionEvent;
import com.streambase.liveview.server.event.query.listener.QueryEventListener;
import com.streambase.liveview.server.event.tuple.TupleAddedEvent;
import com.streambase.liveview.server.query.QueryModel;
import com.streambase.liveview.server.table.CatalogedTable;
import com.streambase.liveview.server.table.Table;
import com.streambase.liveview.server.table.plugin.TableProviderControl;
import com.streambase.sb.DataType;
import com.streambase.sb.Schema;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleException;
import com.streambase.sb.util.Util;

/**
 * Represents a table in an InfluxDB database.
 */
public class InfluxDBTable implements Table {
	
	private static final Logger logger = LoggerFactory.getLogger(InfluxDBTable.class);
	
	private static final String TIME_FIELD_NAME = "time"; //$NON-NLS-1$
	
	DateTimeFormatter timeFormatter = new DateTimeFormatterBuilder().append(DateTimeFormatter.ISO_INSTANT).toFormatter();
	
	private final String tableName;
	private final InfluxDB influxDB;
	private final String dbName;
	private final TableProviderControl helper;
	private CatalogedTable catalogTable;
    private Schema schema = Schema.EMPTY_SCHEMA;
    
    private final List<String> pkeys;
    
	public InfluxDBTable(InfluxDB influxDB, String dbName, String tableName, TableProviderControl helper) {
		this.influxDB = influxDB;
		this.dbName = dbName;
		this.tableName = tableName;
		this.helper = helper;
		
		this.pkeys = new ArrayList<String>();
		this.pkeys.add(TIME_FIELD_NAME);
	}
	
	public CatalogedTable createCatalogedTable() {
		catalogTable = new CatalogedTable(tableName);
		catalogTable.setKeyFields(pkeys);
		catalogTable.setIsSystemTable(false);
		
		catalogTable.setCapabilities(EnumSet.of(LiveViewTableCapability.SNAPSHOT));
		catalogTable.setQueryLanguages(EnumSet.of(LiveViewQueryLanguage.OTHER));
		
		catalogTable.setShortDescription(String.format("InfluxDB Table %s", tableName)); //$NON-NLS-1$
		catalogTable.setDescription(String.format("InfluxDB Database %s, Table %s", dbName, tableName)); //$NON-NLS-1$
		catalogTable.setGroup("user"); //$NON-NLS-1$
        catalogTable.setCreateTime(Timestamp.now());
        
       
        // Setting the schema after receiving data types from influxDB database
	    List<Schema.Field> fields = new ArrayList<Schema.Field>();
	    // An alternative would be to use "SHOW FIELD KEYS FROM", which returns
	    // field names and field types as strings. Note that in either case, if the table is empty,
	    // there is no schema to retrieve
	    Query query = new Query(String.format("SELECT * FROM %s ORDER BY %s DESC LIMIT 1", //$NON-NLS-1$
	    		Util.quote(tableName), TIME_FIELD_NAME), dbName);
	    logger.debug("Executing schema discover query [{}]", query.getCommand());
	    
	    QueryResult qresult = influxDB.query(query);
    	
	    if (!Util.isEmpty(qresult.getResults())) {
	    	if (logger.isDebugEnabled()) {
	    		logger.debug("{}", Arrays.deepToString(qresult.getResults().toArray()));
	    	}
	    	
	    	for (Result result : qresult.getResults()) {
	    		if (result != null && !Util.isEmpty(result.getSeries())) {
	    			for (Series rseries : result.getSeries()) {
	    				if (rseries != null && !Util.isEmpty(rseries.getValues()) && !Util.isEmpty(rseries.getColumns())) {
	    					// series name: "edit"
	    					// series tags (if any)
	    					// series columns (their names)
	    					// series values (rows)
	    					List<Object> values = rseries.getValues().get(0); // by design, handling only one row (the only one -- see LIMIT)
	    					for (int col = 0; col < rseries.getColumns().size(); ++col) {
	    						String columnName = rseries.getColumns().get(col);
	    						Object value = values.get(col);
	    						
	    						logger.debug(String.format("Column [%s] value class (%s) sample value:%s", columnName, value.getClass(), value.toString()));
	    						
	    						// the time field is recognizable by name
	    						if (TIME_FIELD_NAME.equals(columnName)) {
	    							fields.add(Schema.createField(DataType.TIMESTAMP, columnName));
	    						} else {
	    							// Field values are your data; they can be strings, floats, integers, or booleans,
	    							if (value instanceof Float || value instanceof Double) {
	    								fields.add(Schema.createField(DataType.DOUBLE, columnName));
	    							} else if (value instanceof Long) {
	    								fields.add(Schema.createField(DataType.LONG, columnName));
	    							} else if (value instanceof Integer) {
	    								fields.add(Schema.createField(DataType.INT, columnName));
	    							} else if (value instanceof Boolean) {
	    								fields.add(Schema.createField(DataType.BOOL, columnName));
	    							} else if (value instanceof String) {
	    								fields.add(Schema.createField(DataType.STRING, columnName));
	    							} else {
	    								throw new IllegalStateException("cannot handle type " + value.getClass());
	    							}
	    						}
	    					}
	    				}
	    			}
	    		}
	    	}
	    }
	    logger.debug("resulting table schema: " + fields);

	    schema = new Schema(null, fields);
        catalogTable.setSchema(schema);
        catalogTable.setRuntimeTable(this);
        helper.insert(catalogTable);
        catalogTable.setStatus(TableStatus.ENABLED, "OK"); //$NON-NLS-1$
        helper.upsert(catalogTable);

        return catalogTable;
	}

	@Override
	public QueryModel parseQuery(CatalogedTable catalogedTable, String queryString, LiveViewQueryType type,
			boolean includeInternal, String additionalPredicate) throws LiveViewException {
		return new InfluxDBQueryModel(catalogedTable, this, queryString, type, additionalPredicate);
	}

	@Override
	public void addListener(QueryEventListener lvListener, LiveViewQueryType type, QueryModel query) {
        if (!(query instanceof InfluxDBQueryModel)) {
            throw new IllegalStateException("InfluxDBTable provider handed an unexpected query model: " + query);
        }
        
	    Query influxQuery = new Query(((InfluxDBQueryModel)query).getQueryString(), dbName);
	    logger.debug("Executing query [{}]", influxQuery.getCommand());
	    QueryResult qresult = influxDB.query(influxQuery);
	    if (qresult.hasError()) {
	    	new QueryExceptionEvent(this, LiveViewExceptionType.INVALID_QUERY.error(qresult.getError())).dispatch(lvListener);
	    	return;
	    }
	    
		if (logger.isTraceEnabled()) {
    		logger.trace("{}", Arrays.deepToString(qresult.getResults().toArray()));
    	}
	    
	    if (type == LiveViewQueryType.SNAPSHOT || type == LiveViewQueryType.SNAPSHOT_AND_CONTINUOUS) {
	    	new com.streambase.liveview.server.event.query.BeginSnapshotEvent(this).dispatch(lvListener);
	    	for (Result result : qresult.getResults()) {
	    		if (result != null && !Util.isEmpty(result.getSeries())) {
	    			for (Series rseries : result.getSeries()) {
	    				List<String> columnNames = rseries.getColumns();
						if (rseries != null && !Util.isEmpty(columnNames)) {

							List<List<Object>> allrows = rseries.getValues();
							for (List<Object> row : allrows) {

								Tuple tuple = schema.createTuple();
								long key = -1;
								for (int col = 0; col < columnNames.size(); ++col) {
									Object value = row.get(col);
									String columnName = columnNames.get(col);

									try {
										if (TIME_FIELD_NAME.equals(columnName)) {
											// special timestamp handling due to format; in terms of type, influx gives us a String
											String timestampString = value.toString();
											try {
												key = Instant.from(timeFormatter.parse(timestampString)).toEpochMilli();
												tuple.setTimestamp(TIME_FIELD_NAME, Timestamp.msecs(Timestamp.TIMESTAMP, key));
											} catch (DateTimeParseException e) {
												logger.warn("timestamp field did not parse properly", e);
											}	

										} else {
											// assign the value using our type matching system
											tuple.setField(columnName, value);
										}
									} catch (TupleException e) {
										logger.warn(String.format("could not assign field value [%s] to [%s]", value, columnName), e);
									} 
								}
								if (key == -1) {
									throw new IllegalStateException("time field not available");
								}
	    						// send it as a new row. the key is the time field (in influxdb its the primary key)
	    						new TupleAddedEvent(this, key, tuple, query).dispatch(lvListener);
	    					}
	    				}
	    			}
	    		}
	    	}
	    	new EndSnapshotEvent(this).dispatch(lvListener);

	    } else {
	    	// UNHANDLED TYPE
	    	logger.warn("unhandled query type:{}", type);
	    }
	}
    
	@Override
	public void removeListener(QueryEventListener listener) throws LiveViewException {
		// used for continuous queries, and handling of long-running queries. this sample implements neither 
	}

	@Override
	public com.streambase.liveview.server.table.publisher.TablePublisher createPublisher(final String publisherName) throws LiveViewException {
		throw LiveViewExceptionType.PUBLISHING_NOT_SUPPORTED.error();
	}

	protected String getTableName() {
		return this.tableName;
	}
	
	protected Schema getSchema() {
		return this.schema;
	}
	
	public void shutdown() {
		if (catalogTable.getRuntimeTable() instanceof InfluxDBTable) {
			helper.delete(tableName);
		}
	}
}
