package com.tibco.contrib.sb.ldm.influxdb;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Result;
import org.influxdb.dto.QueryResult.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.server.table.plugin.TableNameMapper;
import com.streambase.liveview.server.table.plugin.TableProvider;
import com.streambase.liveview.server.table.plugin.TableProviderControl;
import com.streambase.liveview.server.table.plugin.TableProviderParameters;

/**
 * A Live Datamart Table Provider to InfluxData's InfluxDB Time Series Database.
 * As a sample, many production features are missing. In particular, this class
 * deletes and creates (and generates data for) a database, and makes no attempt
 * to manage this if other instances of this table provider are used.
 */
public class InfluxDBTableProvider implements TableProvider {
	
	private static final Logger logger = LoggerFactory.getLogger(InfluxDBTableProvider.class);
	
	private static final String SAMPLE_DATABASE_NAME = "LiveDataMartSample"; //$NON-NLS-1$
	
	private static final String PARAM_CONNECTION_URL = "connection-url"; //$NON-NLS-1$
	private static final String DEFAULT_URL = "http://localhost:8086"; //$NON-NLS-1$
	
	private String providerId;
	private TableProviderControl helper;
	private String connectionURL;
	private List<InfluxDBTable> tableList = new ArrayList<InfluxDBTable>();
	private InfluxDB influxDB;
	
	@Override
	public void initialize(String id, TableProviderControl helper, TableProviderParameters parameters, TableNameMapper mapper) throws LiveViewException {
		this.providerId = id;
		this.helper = helper;
		this.connectionURL = parameters.getString(PARAM_CONNECTION_URL, DEFAULT_URL);
	}

	@Override
	public void start() throws LiveViewException, InterruptedException {
		try {
			influxDB = InfluxDBFactory.connect(connectionURL);
			Pong pong  = influxDB.ping();
			info(String.format("connection to %s reporting version %s, ping reponse time %d ms",
					connectionURL, pong.getVersion(), pong.getResponseTime()));
		} catch (Exception e) {
			e.printStackTrace();
			throw new LiveViewException(LiveViewExceptionType.COULD_NOT_CONNECT, e,
					String.format("Error connecting to %s", connectionURL));
		}
		
		resetSampleDatabase();
		
		getTableNamesThenCreateLiveviewTablesFromInfluxDB();
	}
	
	private void getTableNamesThenCreateLiveviewTablesFromInfluxDB(){
		List<String> databases = influxDB.describeDatabases();
		for (String dbName: databases){
			if (dbName.equals("_internal")) { //$NON-NLS-1$
				continue;
			}
			Set<String> tables = new HashSet<>();
			Query query = new Query("show measurements", dbName); //$NON-NLS-1$
			debug(String.format("requesting measurements for database '%s'", dbName));
			QueryResult queryResult = influxDB.query(query);
			if (!queryResult.hasError() && queryResult.getResults().size() > 0) {
				debug(String.format("%s", queryResult));
				for (Result result : queryResult.getResults()) {
					List<Series> resultSeries = result.getSeries();
					if (resultSeries != null) {
						for (Series series : resultSeries) {
							List<List<Object>> tableNames = series.getValues();
							if (tableNames != null) {
								for (List<Object> table : tableNames) {
									tables.add(table.get(0).toString());
								}
							}
						}
					}
				}
				
				createLiveviewTablesForInfluxDBTables(tables, dbName);	   
			}
		}
	}  

	private void debug(String msg) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("%s: %s", providerId, msg));
		}
	}
	
	private void info(String info) {
		if (logger.isInfoEnabled()) {
			logger.info(String.format("%s: %s", providerId, info));
		}
	}
	
	private void createLiveviewTablesForInfluxDBTables(Set<String> tableNames, String dbName) {
		// Note - this code does not handle tables with the same name in different databases
		//  (configuration settings could provide a name mapping if necessary) 
        Iterator<String> tableNamesIterator = tableNames.iterator();
        while (tableNamesIterator.hasNext()) {
        	String tableName = tableNamesIterator.next();
			InfluxDBTable influxTable = new InfluxDBTable(influxDB, dbName, tableName, helper);
			influxTable.createCatalogedTable();
			tableList.add(influxTable);
        }
	}
	
	private void resetSampleDatabase() {
		debug(String.format("deleting and re-creating sample database '%s'", SAMPLE_DATABASE_NAME));
		if (influxDB.databaseExists(SAMPLE_DATABASE_NAME)) {
			influxDB.deleteDatabase(SAMPLE_DATABASE_NAME);
		}
		influxDB.createDatabase(SAMPLE_DATABASE_NAME);

		// Threaded this so we get some 'interesting' load during LDM startup
		Thread dataThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();

				int numPoints = 5000;
				debug(String.format("data thread beginning to populate database '%s'", SAMPLE_DATABASE_NAME));

				while (numPoints-- > 0) {
					BatchPoints batchPoints = BatchPoints
							.database(SAMPLE_DATABASE_NAME)
							.tag("source", "java.os.mxbean") // which will appear as a field, a string
							.build();

					for (int i = 0; i < 10; ++i) { // batch of 10 points
						Point point = Point.measurement("cpu")
								.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
								.addField("loadAvg", operatingSystemMXBean.getSystemLoadAverage())
								.addField("availProcessors", operatingSystemMXBean.getAvailableProcessors())
								.build();		
						batchPoints.point(point);
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							return;
						}
					}
					influxDB.write(batchPoints);
				}
			}
		});
		dataThread.setDaemon(true);
		dataThread.start();
		// and block while we collect at least a few data points (so we have data to derive a schema for)
		try {
			dataThread.join(3000);
		} catch (InterruptedException e) {
			debug("data thread interrupted");
		}
	}

	@Override
	public void shutdown() {
		for(InfluxDBTable table: tableList){
			table.shutdown();
		}
		influxDB.close();
	}
	
}
