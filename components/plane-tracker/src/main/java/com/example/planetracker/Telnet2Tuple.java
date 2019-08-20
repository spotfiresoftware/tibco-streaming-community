package com.example.planetracker;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;

import org.apache.commons.net.telnet.TelnetClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleException;
import com.streambase.sb.adapter.InputAdapter;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

public class Telnet2Tuple extends InputAdapter implements Parameterizable, Runnable {
    public static final long serialVersionUID = 1562681142099L;
    // Properties
    private String telnetUri;
    private Schema planeSchema;
    private String Region;
    private String displayName = "telnetTransponderParser";
    private String server;
    private int port;
    private Tuple tuple;
    private Logger logger = LoggerFactory.getLogger(Telnet2Tuple.class);
    private TelnetClient telnet;
    private HashMap<String,Long> planes = new HashMap<>();
    //Fileds
    private static String ICAO_fieldname = "ICAO";
    private static String FlightId_fieldname = "FlightId";
    private static String Altitude_fieldname = "Altitude";
    private static String Speed_fieldname = "Speed";
    private static String Heading_fieldname = "Heading";
    private static String Latitude_fieldname = "Latitude";
    private static String Longitude_fieldname = "Longitude";
    private static String LastReceiveTime_fieldname = "LastReceiveTime";
    private static String StartReceiveTime_fieldname = "StartReceiveTime";
    private static String Region_fieldname = "Region";
	
    /**
     * This is a custom Java operator that listens to local telnet port which dump1090 posts plane data in SBS-1 format. 
     * It decodes and composes raw data from SBS-1 to tuples that will be published to LiveView Server. 
     */
    public Telnet2Tuple() {
        super();
        setPortHints(0, 1);
        setDisplayName(displayName);
        setShortDisplayName(this.getClass().getSimpleName());
        setTelnetUri("");
    }

    /**
     * The typecheck method is called after the adapter instance is constructed, and once input ports are connected. 
     * 
     */
    public void typecheck() throws TypecheckException {
        setOutputSchema(0, planeSchema);
        server = telnetUri.split(":")[0];
        port = Integer.parseInt(telnetUri.split(":")[1]);
        if (!server.equals("localhost") || port!=30003) {
            logger.warn("TelnetURI needs to be localhost:30003");
        }
    }
    
    /**
     * Initialize the adapter. If typecheck succeeds, the init method is called before
     * the StreamBase application is started. Note that your adapter is not required to
     * define the init method, unless you need to register a runnable or perform
     * initialization of a resource such as, for example, a JDBC pool.
     */
    public void init() throws StreamBaseException {
        super.init();
        telnet = new TelnetClient();
        tuple = getSchema0().createTuple();
        // Register the object so it will be run as a thread managed by StreamBase.
        registerRunnable(this, true);
        try {
            telnet.connect(server, port);
        } catch (IOException e) {
            logger.error("Failed to connect to " +server +":"+port);
            logger.info("Reconnecting in 30 seconds.");
            while(!telnet.isConnected()) {
                try {
                    Thread.sleep(30000);
                    try {
                        telnet.connect(server, port);
                        logger.info("Reconnection successful");
                    } catch (IOException reconnectionError) {
                        logger.info("Reconnection failed, try again in 30 seconds.");
                    }
                }catch (InterruptedException threadError) {
                    shutdown();
                    return;
                }
            }
        }	
    }

    /**
     * Shutdown adapter by cleaning up any resources used.
     */
    public void shutdown() {
        Thread.currentThread().interrupt();
        postShutdown();
    }

    /**
     * Main thread of the adapter. At this point, the application begins to run.
     * StreamBase will start threads for any managed runnables registered by
     * earlier calls to registerRunnable.
     */
    public void run() {
        while (shouldRun()&&telnet.isConnected()) {
            logger.info("Listening to telnet port: " + port );
            InputStream in = telnet.getInputStream();
            listen2Port(in);    	
        }
        logger.info("Disconnect from telnet port successfully");
        logger.info("Shutting down telnet connection...");
        shutdown();
    }
	
    /**
     * Listen to telnet port and read SBS-1 format plane data line by line
     * @param in is the InputStream from telnet connection
     */
    private void listen2Port(InputStream in) {
        try {
            StringBuffer sb = new StringBuffer();
            while (telnet.isConnected()) {
                char ch = (char) in.read();
                sb.append(ch);
                if(sb.toString().endsWith("\n")) {
                    String[] msg = sb.toString().split(",");
                    msg[msg.length-1]= msg[msg.length-1].replace("\n","").replace("\r", "");
                    parseTuple(msg);
                    cleanPlanes();
                    sb = new StringBuffer();
                }
            }
        } catch (Exception telnetConnection) {
            while(!telnet.isConnected()) {
                logger.warn("Connection break, reconnecting in 30 seconds...");
                try {
                    Thread.sleep(30000);
                    try {
                        telnet.connect(server, port);
                        logger.info("Reconnection successful");
                    } catch (IOException e) {
                        logger.warn("Reconnection failed, try again in 30 seconds");
                    }
                } catch (InterruptedException e) {
                }
            }	
        }
    }
	
    /**
     * Clean up planes that have not been updated for 3 minutes in Planes map so that 
     * the same plane will be recognized as new plane again in the future.
     */
    private void cleanPlanes() {
        HashMap<String,Long> tempPlanes = new HashMap<>();
        for(String currentPlane: planes.keySet()) {
            //delete plane from database if it has not been updated for 3 minutes
            long currentTime = System.currentTimeMillis();
            long lastHeardTime = planes.get(currentPlane);
            if(currentTime-lastHeardTime>180000) {
                logger.info("Stopped listening to "+currentPlane);
            }else {
                tempPlanes.put(currentPlane,lastHeardTime);
            }
        }
        planes = tempPlanes;
    }

    /**
     * Read and parse SBS-1 message into tuples that can be published to LiveView Server
     * @param msg is an array contains SBS-1 message
     */
    private void parseTuple(String[] msg) {
        //flight ICAO number cannot be 000000
        if(msg[4].length()!=0 && !msg[4].equals("000000")) {
            try {
                tuple.setString(ICAO_fieldname, msg[4]);
            } catch (TupleException e) {
                logger.warn("Failed to set ICAO field");
            }
        }else {
            return;
        }
        if(msg[10].length()!=0) {
            try {
                tuple.setString(FlightId_fieldname, msg[10]);
            } catch (TupleException e) {
               logger.warn("Failed to set flight_ID field");			
           }
        }
        if(msg[11].length()!=0) {
           try {
               tuple.setField(Altitude_fieldname,msg[11]);
           } catch (TupleException e) {
               logger.warn("Failed to set altitude field");
           }
        }
        if(msg[12].length()!=0) {
            try {
                tuple.setField(Speed_fieldname, msg[12]);
            } catch (TupleException e) {
                logger.warn("Failed to set speed field");
            }
        }
        if(msg[13].length()!=0) {
            try {
                tuple.setField(Heading_fieldname, msg[13]);
            } catch (TupleException e) {
                logger.warn("Failed to set heading field");
            }
        }
        if(msg[14].length()!=0) {
            try {
                tuple.setField(Latitude_fieldname, msg[14]);
            } catch (TupleException e) {
                logger.warn("Failed to set latitude field");
            }
        }
        if(msg[15].length()!=0) {
            try {
                tuple.setField(Longitude_fieldname, msg[15]);
            } catch (TupleException e) {
                logger.warn("Failed to set longitude field");
            }
        }
        Date date = new Date();
        Timestamp timestamp = new Timestamp(date);
        if(!planes.containsKey(msg[4])) {
            try {
                tuple.setTimestamp(StartReceiveTime_fieldname, timestamp);
            } catch (TupleException e) {
                logger.warn("Failed to set StartReceiveTime field");
            }
        }
        try {
            tuple.setTimestamp(LastReceiveTime_fieldname, timestamp);
            planes.put(msg[4], System.currentTimeMillis());
        } catch (TupleException e) {
            logger.warn("Failed to set LastReceiveTime field");
        }
        try {
            tuple.setString(Region_fieldname,Region);
        } catch (TupleException e) {
            logger.warn("Failed to set Region field");
        }
        publishTuple(); 
    }

    /**
     * Publish composed tuples to LiveView Server
     */
    protected void publishTuple() {
        if (getLogger().isInfoEnabled()) {
            try {
                getLogger().debug("Flight {} has been updated", tuple.getField(ICAO_fieldname));
            } catch (TupleException e) {
                logger.warn("Tuple does not have correct ICAO");
            }
        }
        try {
            int portCount = getOutputPortCount();
            for (int i = 0; i < portCount; i++) {
                try {
                    // Output the tuple
                    sendOutput(i, tuple);
                } catch (StreamBaseException e) {
                    logger.warn("Exception sending output to port {}",portCount);
                    return;
                }
            }
        } finally {
            try {
                tuple.clear();
            } catch (Exception e) {
            }
        }
    }

    /***************************************************************************************
     * The getter and setter methods provided by the Parameterizable object.               *
     * StreamBase Studio uses them to determine the name and type of each property         *
     * and obviously, to set and get the property values.                                  *
     ***************************************************************************************/
    
    public void setTelnetUri(String TelnetUri) {
        this.telnetUri = TelnetUri;
    }

    public String getTelnetUri() {
        return this.telnetUri;
    }

    public Schema getSchema0() {
        return this.planeSchema;
    }

    public void setSchema0(Schema planeSchema) {
        this.planeSchema = planeSchema;
    }
	
    public String getRegion() {
        return this.Region;
    }

    public void setRegion(String Region) {
        this.Region = Region;
    }

    public boolean shouldEnableTelnetUri() {
        return true;
    }
}
