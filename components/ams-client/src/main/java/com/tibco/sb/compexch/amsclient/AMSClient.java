package com.tibco.sb.compexch.amsclient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;

import com.alibaba.fastjson.JSON;
import com.streambase.sb.ByteArrayView;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.Schema;
import com.streambase.sb.Schema.Field;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleException;
import com.streambase.sb.adapter.common.AdapterUtil;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

/**
 * The AMSClient Java operator demonstrates using several of the key end points of the TIBCO Artifact Management Server's REST API.
 */
@SuppressWarnings("serial")
public class AMSClient extends Operator implements Parameterizable {

    // Constants
    private static final String DISPLAY = "AMS Client Example";
    private static final int INPUT_PORT_COUNT = 1;
    private static final int INPUT_PORT_NUMBER = 0;
    private static final String INPUT_PORT_NAME = "Control";
    private static final int OUTPUT_PORT_COUNT = 1;
    private static final int OUTPUT_PORT_NUMBER = 0;
    private static final String MISSING_AMS_PROPERTY_MESSAGE_TEMPLATE = "You must specify a %s to access AMS projects and artifacts";
    private static final int READ_DATA_BUFFER_SIZE = 1024;
    private static final long LATEST_ARTIFACT_VERSION = -1;
    private static final String ENCODING_BASE64 = "BASE64";
    private static final String ENCODING_NONE = "NONE";

    // Field names
    private static final String FIELD_NAME_COMMAND = "command";
    private static final String FIELD_NAME_STATUS = "status";
    private static final String FIELD_NAME_SUCCESS = "success";
    private static final String FIELD_NAME_REASON = "reason";
    private static final String FIELD_NAME_MESSAGE = "message";
    private static final String FIELD_NAME_PROJECTS = "projects";
    private static final String FIELD_NAME_PROJECT_NAME = "projectName";
    private static final String FIELD_NAME_PROJECT_ID = "projectID";
    private static final String FIELD_NAME_ARTIFACT_PATH = "artifactPath";
    private static final String FIELD_NAME_ARTIFACT_PATHS = "artifactPaths";
    private static final String FIELD_NAME_ARTIFACT = "artifact";
    private static final String FIELD_NAME_ARTIFACTS = "artifacts";
    private static final String FIELD_NAME_ARTIFACT_ID = "artifactID";
    private static final String FIELD_NAME_TYPE = "type";
    private static final String FIELD_NAME_DESCRIPTION = "description";
    private static final String FIELD_NAME_VERSION = "version";
    private static final String FIELD_NAME_CONTENT = "content";
    private static final String FIELD_NAME_BINARY_CONTENT = "binaryContent";
    private static final String FIELD_NAME_ENCODING = "encoding";
    private static final String FIELD_NAME_METADATA = "metadata";
    private static final String FIELD_NAME_INPUT_TUPLE = "inputTuple";
    private static final String FIELD_NAME_LOGIN_TOKEN = "loginToken";

    // Required input schema
    private static final Schema INPUT_SCHEMA = new Schema(null, new Field[] {
            new Field(FIELD_NAME_COMMAND, CompleteDataType.forString()),
            new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
            new Field(FIELD_NAME_ARTIFACT_PATH, CompleteDataType.forString()),
            new Field(FIELD_NAME_VERSION, CompleteDataType.forLong()),
            new Field(FIELD_NAME_TYPE, CompleteDataType.forString()),
            new Field(FIELD_NAME_DESCRIPTION, CompleteDataType.forString()),
            new Field(FIELD_NAME_CONTENT, CompleteDataType.forString()),
            new Field(FIELD_NAME_BINARY_CONTENT, CompleteDataType.forBlob()),
            new Field(FIELD_NAME_ENCODING, CompleteDataType.forString()),
            new Field(FIELD_NAME_METADATA, CompleteDataType.forString()),
            new Field(FIELD_NAME_ARTIFACT_PATHS, CompleteDataType.forList(CompleteDataType.forString())),
            new Field(FIELD_NAME_MESSAGE, CompleteDataType.forString()),
    });

    // Output schema
    private static final Schema OUTPUT_SCHEMA = new Schema(null, new Field[] {

            // Status of command execution
            new Field(FIELD_NAME_STATUS, CompleteDataType.forTuple(new Schema(null, new Field[] {
                    new Field(FIELD_NAME_SUCCESS, CompleteDataType.forBoolean()),
                    new Field(FIELD_NAME_REASON, CompleteDataType.forString()),
                    new Field(FIELD_NAME_MESSAGE, CompleteDataType.forString()),
            }))),

            // Copy of the input tuple
            new Field(FIELD_NAME_INPUT_TUPLE, CompleteDataType.forTuple(INPUT_SCHEMA)),

            // Login token when logging in
            new Field(FIELD_NAME_LOGIN_TOKEN, CompleteDataType.forString()),

            // Output from list projects
            new Field(FIELD_NAME_PROJECTS, CompleteDataType.forList(CompleteDataType.forTuple(new Schema(null, new Field[]{
                    new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
                    new Field(FIELD_NAME_PROJECT_ID, CompleteDataType.forString()),
            })))),

            // Output from list project artifacts
            new Field(FIELD_NAME_ARTIFACTS, CompleteDataType.forList(CompleteDataType.forTuple(new Schema(null, new Field[]{
                    new Field(FIELD_NAME_ARTIFACT_PATH, CompleteDataType.forString()),
                    new Field(FIELD_NAME_ARTIFACT_ID, CompleteDataType.forString()),
            })))),

            // Output from fetch artifact
            new Field(FIELD_NAME_ARTIFACT, CompleteDataType.forTuple(new Schema(null, new Field[] {
                    new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
                    new Field(FIELD_NAME_ARTIFACT_PATH, CompleteDataType.forString()),
                    new Field(FIELD_NAME_TYPE, CompleteDataType.forString()),
                    new Field(FIELD_NAME_ARTIFACT_ID, CompleteDataType.forString()),
                    new Field(FIELD_NAME_VERSION, CompleteDataType.forLong()),
                    new Field(FIELD_NAME_CONTENT, CompleteDataType.forString()),
                    new Field(FIELD_NAME_BINARY_CONTENT, CompleteDataType.forBlob()),
                    new Field(FIELD_NAME_ENCODING, CompleteDataType.forString()),
                    new Field(FIELD_NAME_METADATA, CompleteDataType.forString()),
            }))),
    });

    /**
     * Enumeration that specifies the REST end point(s) to invoke for each input tuple
     */
    enum Command {

        LOGIN("Login"),
        LOGOUT("Logout"),
        LIST_PROJECTS("ListProjects"),
        LIST_PROJECT_ARTIFACTS("ListProjectArtifacts"),
        FETCH_ARTIFACT("FetchArtifact"),
        ADD_ARTIFACT("AddArtifact"),
        UPDATE_ARTIFACT("UpdateArtifact"),
        ADD_OR_UPDATE_ARTIFACT("AddOrUpdateArtifact"),
        DELETE_ARTIFACT("DeleteArtifact"),
        COMMIT("Commit");

        private String humanName;
        private static final Set<String> validCommands = new LinkedHashSet<String>();
        private static final Map<String, Command> humanNameLowerCaseToCommandMap = new LinkedHashMap<String, Command>();
        static {
            for (Command command : values()) {
                validCommands.add(command.humanName);
                humanNameLowerCaseToCommandMap.put(command.humanName.toLowerCase(), command);
            }
        }
        Command(String humanName) {
            this.humanName = humanName;
        }
        public static List<String> getValidCommands() {
            return new LinkedList<String>(validCommands);
        }
        public static boolean isValidCommand(String humanString) {
            return humanString == null ? false : humanNameLowerCaseToCommandMap.containsKey(humanString.toLowerCase());
        }
        public static Command fromString(String humanString) {
            return humanNameLowerCaseToCommandMap.get(humanString.toLowerCase());
        }
        @Override
        public String toString() {
            return humanName;
        }
    }

    /**
     * Class that encapsulates AMS connection info
     */
    private static class AMSConnectionInfo {
        public AMSConnectionInfo(String host, int port, boolean secureChannel, String username, String password) {
            super();
            this.host = host;
            this.port = port;
            this.secureChannel = secureChannel;
            this.username = username;
            this.password = password;
        }

        private String getProtocolName() {
            return secureChannel ? "https" : "http";
        }

        private String buildURL(String path) {
            return buildURL(path, null);
        }
        private String buildURL(String path, String query) {
            return String.format("%s://%s:%d/ws/api/%s%s", getProtocolName(), host, port, path, isEmpty(query) ? "" : String.format("?%s", query));
        }

        private final String host;
        private final int port;
        private final boolean secureChannel;
        private final String username;
        private final String password;
        String loginToken;
    }

    // Local variables along with their default values
    private Logger logger;
    private boolean logInAtStartup = true;
    private String amsServerHostName = "localhost";
    private String amsServerPortNumber = "2185";
    private int amsServerPortNumberIntValue;
    private boolean amsServerSecureChannel = false;
    private String amsServerUsername = "admin";
    private String amsServerPassword = "admin";
    private boolean autoCommit = true;
    private String autoCommitMessage = "Auto-commit message";
    private AMSConnectionInfo amsConnectionInfo = null;

    // Cached schema fields
    private Schema outputSchema;
    private Schema projectsOutputSchema;
    private Schema artifactsOutputSchema;
    private Schema artifactOutputSchema;

    // Proxy not currently supported
    private boolean useProxy = false;
    private Proxy proxy = null;
    private boolean ignoreCertificateErrors = false;
    private int connectTimeout = 0;
    private int readTimeout = 0;
    private String proxyHost;
    private String proxyPort;
    private boolean useDefaultCharset = true;
    private String charset;
    private boolean basicAuthEnabled = false;

    /**
     * Operator constructor
     */
    public AMSClient() {
        super();
        logger = getLogger();
        setPortHints(INPUT_PORT_COUNT, OUTPUT_PORT_COUNT);
        setDisplayName(DISPLAY);
        setShortDisplayName(this.getClass().getSimpleName());
    }

    /**
     * Provide a set of input schemas that streamline the use of this operator within a StreamBase application.
     */
    @Override
    public Schema[] getProposedInputSchemas(String mainName) {
        return new Schema[]{

                // List project artifacts
                new Schema(String.format("%s%sInputSchema", mainName, Command.LIST_PROJECT_ARTIFACTS.humanName), new Field[]{
                        new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
                }),

                // Fetch artifact schema
                new Schema(String.format("%s%sInputSchema", mainName, Command.FETCH_ARTIFACT.humanName), new Field[]{
                        new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
                        new Field(FIELD_NAME_ARTIFACT_PATH, CompleteDataType.forString()),
                        new Field(FIELD_NAME_VERSION, CompleteDataType.forLong()),
                }),

                // Add artifact schema
                new Schema(String.format("%s%sInputSchema", mainName, Command.ADD_ARTIFACT.humanName), new Field[]{
                        new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
                        new Field(FIELD_NAME_ARTIFACT_PATH, CompleteDataType.forString()),
                        new Field(FIELD_NAME_TYPE, CompleteDataType.forString()),
                        new Field(FIELD_NAME_DESCRIPTION, CompleteDataType.forString()),
                        new Field(FIELD_NAME_CONTENT, CompleteDataType.forString()),
                        new Field(FIELD_NAME_BINARY_CONTENT, CompleteDataType.forBlob()),
                        new Field(FIELD_NAME_ENCODING, CompleteDataType.forString()),
                        new Field(FIELD_NAME_METADATA, CompleteDataType.forString()),
                }),

                // Update artifact schema
                new Schema(String.format("%s%sInputSchema", mainName, Command.UPDATE_ARTIFACT.humanName), new Field[]{
                        new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
                        new Field(FIELD_NAME_ARTIFACT_PATH, CompleteDataType.forString()),
                        new Field(FIELD_NAME_TYPE, CompleteDataType.forString()),
                        new Field(FIELD_NAME_DESCRIPTION, CompleteDataType.forString()),
                        new Field(FIELD_NAME_CONTENT, CompleteDataType.forString()),
                        new Field(FIELD_NAME_BINARY_CONTENT, CompleteDataType.forBlob()),
                        new Field(FIELD_NAME_ENCODING, CompleteDataType.forString()),
                        new Field(FIELD_NAME_METADATA, CompleteDataType.forString()),
                }),

                // Delete artifact schema
                new Schema(String.format("%s%sInputSchema", mainName, Command.DELETE_ARTIFACT.humanName), new Field[]{
                        new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
                        new Field(FIELD_NAME_ARTIFACT_PATH, CompleteDataType.forString()),
                }),

                // Commit schema
                new Schema(String.format("%s%sInputSchema", mainName, Command.COMMIT.humanName), new Field[]{
                        new Field(FIELD_NAME_PROJECT_NAME, CompleteDataType.forString()),
                        new Field(FIELD_NAME_ARTIFACT_PATHS, CompleteDataType.forList(CompleteDataType.forString())),
                        new Field(FIELD_NAME_MESSAGE, CompleteDataType.forString()),
                }),

                // Required operator input schema: union of all input schema fields
                new Schema(String.format("%sInputSchema", mainName), INPUT_SCHEMA.getFields()),
        };
    }

    /**
     * Typecheck: validate the operator's input port schema and properties, and set its output port schema
     */
    @Override
    public void typecheck() throws TypecheckException {

        // Validate the input port count
        requireInputPortCount(INPUT_PORT_COUNT);

        Schema requiredSchema = INPUT_SCHEMA;
        Schema actualInputSchema = getInputSchema(INPUT_PORT_NUMBER);
        boolean isInputPort = true;
        Set<String> optionalFields = null;
        boolean checkForUnexpectedFields = true;
        List<Field> fieldsToAdd = null;
        AdapterUtil.validateSchema(this, requiredSchema, actualInputSchema, isInputPort, INPUT_PORT_NUMBER, INPUT_PORT_NAME, optionalFields, checkForUnexpectedFields, fieldsToAdd);

        // Validate the presence of the required property values
        if (isEmpty(amsServerHostName)) {
            throw new PropertyTypecheckException(AMSClientBeanInfo.AMS_SERVER_HOST_NAME_PARAM_NAME, String.format(MISSING_AMS_PROPERTY_MESSAGE_TEMPLATE, "host name or IP address"));
        }
        if (isEmpty(amsServerPortNumber)) {
            throw new PropertyTypecheckException(AMSClientBeanInfo.AMS_SERVER_HOST_NAME_PARAM_NAME, String.format(MISSING_AMS_PROPERTY_MESSAGE_TEMPLATE, "port number"));
        }
        if (isEmpty(amsServerUsername)) {
            throw new PropertyTypecheckException(AMSClientBeanInfo.AMS_SERVER_HOST_NAME_PARAM_NAME, String.format(MISSING_AMS_PROPERTY_MESSAGE_TEMPLATE, "username"));
        }
        if (isEmpty(amsServerPassword)) {
            throw new PropertyTypecheckException(AMSClientBeanInfo.AMS_SERVER_HOST_NAME_PARAM_NAME, String.format(MISSING_AMS_PROPERTY_MESSAGE_TEMPLATE, "password"));
        }

        // Validate the AMS server port number and translate it to an integer
        try {
            amsServerPortNumberIntValue = Integer.parseInt(amsServerPortNumber);
        }
        catch (NumberFormatException e) {
            throw new PropertyTypecheckException(AMSClientBeanInfo.AMS_SERVER_HOST_NAME_PARAM_NAME, String.format("Invalid port number: %s", e.getMessage()));
        }

        // Set the operator's output schema
        setOutputSchema(OUTPUT_PORT_NUMBER, OUTPUT_SCHEMA);

    }

    /**
     * Init: cache copies of the various runtime output schemas
     */
    @Override
    public void init() throws StreamBaseException {
        super.init();

        // Cache the runtime output schema for better performance
        outputSchema = getRuntimeOutputSchema(OUTPUT_PORT_NUMBER);
        projectsOutputSchema = outputSchema.getField(FIELD_NAME_PROJECTS).getElementType().getSchema();
        artifactsOutputSchema = outputSchema.getField(FIELD_NAME_ARTIFACTS).getElementType().getSchema();
        artifactOutputSchema = outputSchema.getField(FIELD_NAME_ARTIFACT).getSchema();
    }

    /**
     * Resumed: log into the AMS server, if configured to do so
     */
    @Override
    public void resumed() throws StreamBaseException {

        if (amsConnectionInfo == null) {
            amsConnectionInfo = new AMSConnectionInfo(
                    amsServerHostName,
                    amsServerPortNumberIntValue,
                    amsServerSecureChannel,
                    amsServerUsername,
                    amsServerPassword);
        }

        // Log in to the AMS server if we haven't already done so
        if (logInAtStartup && isEmpty(amsConnectionInfo.loginToken)) {
            Tuple outputTuple = outputSchema.createTuple();
            try {
                amsConnectionInfo.loginToken = login();
                logger.info(String.format("Logged in to AMS server at '%s:%d', login token: '%s'", amsServerHostName, amsServerPortNumberIntValue, amsConnectionInfo.loginToken));
                outputTuple.setString(FIELD_NAME_LOGIN_TOKEN, amsConnectionInfo.loginToken);
                setSuccessStatus(outputTuple);
            }
            catch (Exception e) {
                String reason = getReason(e);
                String message = String.format("Error logging into AMS server %s:%d: %s ", amsServerHostName, amsServerPortNumberIntValue, e.getMessage());
                logger.error(message, e);
                setFailureStatus(outputTuple, reason, message);
            }
            finally {
                sendOutputTuple(outputTuple);
            }
        }
    }

    /**
     * Process input tuples by validating its command field and performing command-specific actions
     */
    @Override
    public void processTuple(int inputPort, Tuple tuple) {

        logger.info(String.format("Processing tuple '%s", tuple.toString(true)));

        Tuple outputTuple = outputSchema.createTuple();
        String commandString = "Unknown";
        Command command = null;
        try {
            outputTuple.setField(FIELD_NAME_INPUT_TUPLE, tuple);

            // Validate the command
            commandString = (String)tuple.getField(FIELD_NAME_COMMAND);
            if (!Command.isValidCommand(commandString)) {
                logger.warn(String.format("Invalid command '%s'. Valid commands are: %s", commandString, Command.getValidCommands()));
                return;
            }
            command = Command.fromString(commandString);

            // Send a login request if the command is a login request or we're not already logged in (because every other command requires that we be logged in)
            if (command == Command.LOGIN || isEmpty(amsConnectionInfo.loginToken)) {
                amsConnectionInfo.loginToken = login();
                if (command == Command.LOGIN) {
                    outputTuple.setString(FIELD_NAME_LOGIN_TOKEN, amsConnectionInfo.loginToken);
                }
            }

            switch (command) {

            case LOGIN: {

                // Already processed login request above
                break;
            }

            case LOGOUT: {

                // Logout requests are processed at the end
                break;
            }

            case LIST_PROJECTS: {

                // Fetch the available projects and populate the output tuple with a subtuple per project
                Map<String, String> projectNameToIdMap = listProjects();
                List<Tuple> projectTuples = new LinkedList<Tuple>();
                for (String projectName : projectNameToIdMap.keySet()) {
                    Tuple projectTuple = projectsOutputSchema.createTuple();
                    projectTuple.setString(FIELD_NAME_PROJECT_NAME, projectName);
                    projectTuple.setString(FIELD_NAME_PROJECT_ID, projectNameToIdMap.get(projectName));
                    projectTuples.add(projectTuple);
                }
                outputTuple.setList(FIELD_NAME_PROJECTS, projectTuples);
                break;
            }

            case LIST_PROJECT_ARTIFACTS: {

                // Get the project name and its ID
                String projectName = getProjectName(tuple);
                String projectId = getProjectId(projectName);

                // Fetch the project's artifact and populate the output tuple with a subtuple per artifact
                Map<String, String> artifactPathToIdMap = listProjectArtifacts(projectName, projectId);
                List<Tuple> artifactTuples = new LinkedList<Tuple>();
                for (String artifactPath : artifactPathToIdMap.keySet()) {
                        Tuple artifactTuple = artifactsOutputSchema.createTuple();
                        artifactTuple.setString(FIELD_NAME_ARTIFACT_PATH, artifactPath);
                        artifactTuple.setString(FIELD_NAME_ARTIFACT_ID, artifactPathToIdMap.get(artifactPath));
                        artifactTuples.add(artifactTuple);
                }
                outputTuple.setList(FIELD_NAME_ARTIFACTS, artifactTuples);
                break;
            }

            case FETCH_ARTIFACT: {

                // Get the project name and artifact path
                String projectName = getProjectName(tuple);
                String artifactPath = getArtifactPath(tuple);

                // By default, fetch the latest artifact version
                Long artifactVersion = (Long)tuple.getField(FIELD_NAME_VERSION);
                if (artifactVersion == null) artifactVersion = LATEST_ARTIFACT_VERSION;

                // Fetch the artifact and populate the output tuple with its content
                FetchArtifactResponse.ArtifactRevisionRecord artifactRecord = fetchArtifact(projectName, artifactPath, artifactVersion);
                Tuple artifactTuple = artifactOutputSchema.createTuple();
                artifactTuple.setString(FIELD_NAME_PROJECT_NAME, projectName);
                artifactTuple.setString(FIELD_NAME_ARTIFACT_PATH, artifactPath);
                artifactTuple.setString(FIELD_NAME_TYPE, artifactRecord.type);
                artifactTuple.setLong(FIELD_NAME_VERSION, artifactRecord.revisionNumber);
                artifactTuple.setString(FIELD_NAME_ARTIFACT_ID, artifactRecord.entityId);
                String content = artifactRecord.content;
                String encoding = artifactRecord.encoding;
                artifactTuple.setString(FIELD_NAME_CONTENT, artifactRecord.content);
                if (content != null) {
                    byte[] binaryContent = !isEmpty(encoding) && encoding.equals(ENCODING_BASE64) ? Base64.getDecoder().decode(content) : content.getBytes();
                    artifactTuple.setBlobBuffer(FIELD_NAME_BINARY_CONTENT, ByteArrayView.makeView(binaryContent));
                }
                artifactTuple.setString(FIELD_NAME_ENCODING, artifactRecord.encoding);
                artifactTuple.setString(FIELD_NAME_METADATA, artifactRecord.metadata);
                outputTuple.setTuple(FIELD_NAME_ARTIFACT, artifactTuple);
                break;
            }

            case ADD_ARTIFACT:
            case UPDATE_ARTIFACT:
            case ADD_OR_UPDATE_ARTIFACT: {

                // Get the project name and artifact path
                String projectName = getProjectName(tuple);
                String artifactPath = getArtifactPath(tuple);

                // Prepare to validate the command against the state of the artifact
                boolean existsInProject = artifactExistsInProject(projectName, artifactPath);
                boolean existsInCheckout = artifactExistsInCheckout(projectName, artifactPath);

                // Cannot add an artifact that already exists in the project or checkout
                if (command == Command.ADD_ARTIFACT) {
                    if (existsInProject) {
                        throw new Exception(String.format("Project '%s' already contains artifact '%s'", projectName, artifactPath));
                    }
                    if (existsInCheckout) {
                        throw new Exception(String.format("Your checkout of project '%s' already contains artifact '%s'", projectName, artifactPath));
                    }
                }

                // Cannot update an artifact that has been added but not approved or that hasn't yet been added
                else if (command == Command.UPDATE_ARTIFACT) {
                    if (!existsInProject) {
                        if (existsInCheckout) {
                            throw new Exception(String.format("You cannot update project '%s' artifact '%s' until the add of artifact '%s' has been approved", projectName, artifactPath, artifactPath));
                        }
                        throw new Exception(String.format("Project '%s' does not contain artifact '%s'", projectName, artifactPath));
                    }
                }

                // Now that we've made all the sanity checks, decide whether we should add or update the artifact
                boolean doAdd = !existsInProject;

                // Get the artifact's type, description, content, encoding, and metadata
                String type = (String)tuple.getField(FIELD_NAME_TYPE);
                String description = (String)tuple.getField(FIELD_NAME_DESCRIPTION);
                String content = (String)tuple.getField(FIELD_NAME_CONTENT);
                String encoding = (String)tuple.getField(FIELD_NAME_ENCODING);
                ByteArrayView binaryContent = (ByteArrayView)tuple.getField(FIELD_NAME_BINARY_CONTENT);
                String metadata = (String)tuple.getField(FIELD_NAME_METADATA);

                // Content need not be present, but if it is, it must come in through either the content of binary content field, but not both
                if (content != null && binaryContent != null) {
                    throw new Exception("Content and binary content cannot both be specified");
                }
                if (content == null) {
                    content = "";

                    // Base64 binary content
                    if (binaryContent != null) {
                        content = Base64.getEncoder().encodeToString(binaryContent.copyBytes());
                        encoding = ENCODING_BASE64;
                    }
                }

                // If the content isn't encoded, indicate so
                if (isEmpty(encoding)) {
                    encoding = ENCODING_NONE;
                }

                // Check out the parent project
                String checkoutId = checkoutProject(projectName);

                if (doAdd) {
                    // Add the artifact
                    addArtifact(checkoutId, artifactPath, type, description, content, encoding, metadata);

                    // If auto-commit is enabled, get the checked-out artifact ID of the added artifact and commit it
                    if (autoCommit) {
                        List<String> ditryCheckedOutArtifactIds = getCheckedOutArtifactIds(checkoutId, new HashSet<String>(Arrays.asList(artifactPath)), true);
                        commit(checkoutId, autoCommitMessage, ditryCheckedOutArtifactIds);
                    }

                }
                else {
                    // Check out and update the artifact
                    String checkedOutArtifactId = checkoutArtifact(projectName, artifactPath);
                    updateArtifact(checkedOutArtifactId, type, description, content, encoding, metadata);

                    // If auto-commit is enabled, get the checked-out artifact ID of the updated artifact and commit it if the update changed the artifact
                    if (autoCommit) {
                        List<String> dirtyCheckedOutArtifactIds = getCheckedOutArtifactIds(checkoutId, new HashSet<String>(Arrays.asList(artifactPath)), true);
                        if (dirtyCheckedOutArtifactIds.isEmpty()) {
                            throw new Exception(String.format("Artifact '%s' remains unchanged as the requested update matches its current state", artifactPath));
                        }
                        commit(checkoutId, autoCommitMessage, dirtyCheckedOutArtifactIds);
                    }
                }
                break;
            }

            case DELETE_ARTIFACT: {

                // Get the project name and artifact path
                String projectName = getProjectName(tuple);
                String artifactPath = getArtifactPath(tuple);

                // Check out the artifact to delete
                String checkedOutArtifactId = checkoutArtifact(projectName, artifactPath);

                // Delete the artifact
                deleteArtifact(checkedOutArtifactId);

                // If auto-commit is enabled, get the checked-out artifact ID of the deleted artifact and commit it
                if (autoCommit) {
                    String checkoutId = checkoutProject(projectName);
                    List<String> ditryCheckedOutArtifactIds = getCheckedOutArtifactIds(checkoutId, new HashSet<String>(Arrays.asList(artifactPath)), true);
                    commit(checkoutId, autoCommitMessage, ditryCheckedOutArtifactIds);
                }
                break;
            }

            case COMMIT: {

                // Get the project name and artifact paths to commit
                String projectName = getProjectName(tuple);
                @SuppressWarnings("unchecked")
                List<String> artifactPaths = (List<String>)tuple.getField(FIELD_NAME_ARTIFACT_PATHS);

                // Normalize the artifact paths by adding a leading "/"
                Set<String> normalizedArtifactPaths = new LinkedHashSet<String>();
                for (String path : artifactPaths) {
                    normalizedArtifactPaths.add(normalizeArtifactPath(path));
                }

                // Get the commit message
                String commitMessage = (String)tuple.getField(FIELD_NAME_MESSAGE);
                if (isEmpty(commitMessage)) {
                    throw new Exception("You must specify a commit message");
                }

                // Get the ID of the project checkout
                String checkoutId = getCheckoutId(projectName);
                if (checkoutId == null) {
                    throw new Exception(String.format("Project '%s' is not checked out by  user '%s'", projectName, amsConnectionInfo.username));
                }

                // Get the IDs of the dirty artifacts
                List<String> dirtyCheckedOutArtifactIds = getCheckedOutArtifactIds(checkoutId, normalizedArtifactPaths, true);
                if (dirtyCheckedOutArtifactIds.isEmpty()) {
                    throw new Exception(String.format("None of the specified artifacts in project '%s' have been modified by user '%s'", projectName, amsConnectionInfo.username));
                }

                // Commit the changes to the dirty artifacts
                commit(checkoutId, commitMessage, dirtyCheckedOutArtifactIds);
            }

            } // switch (command)

            // The command executed without incurring an exception. Indicate the command was executed successfully
            setSuccessStatus(outputTuple);
        }

        // Handle exceptions incurred in executing the command by logging a console message and conveying the error in a status tuple
        catch (Throwable t) {
            String reason = getReason(t);
            String message = String.format("Error processing input tuple '%s': %s", tuple.toString(true), t.getMessage());
            logger.warn(message);
            setFailureStatus(outputTuple, reason, message);
        }

        // All input tuples pass through here to log out and emit the output tuple
        finally {

            // Don't auto-logout if executing a login command
            if (command != null && command != Command.LOGIN) {
                try {
                    logout();
                }
                catch (Exception e) {
                    logger.warn(String.format("Error logging out: %s", e.getMessage()));
                }
                finally {
                    amsConnectionInfo.loginToken = null;
                }
            }

            // Emit the output tuple
            sendOutputTuple(outputTuple);
        }
    }

    /**
     * Retrieve the project name from the input tuple
     *
     * @param tuple the input tuple
     * @return the project name
     * @throws Exception if the input tuple's project name field is null or empty
     */
    private String getProjectName(Tuple tuple) throws Exception {

        String projectName = (String)tuple.getField(FIELD_NAME_PROJECT_NAME);
        if (isEmpty(projectName)) {
            throw new Exception("You must specify a project name");
        }
        return projectName;
    }

    /**
     * Retrieve the artifact path from the input tuple
     *
     * @param tuple the input tuple
     * @return the artifact path
     * @throws Exception if the input tuple's artifact path field is null or empty
     */
    private String getArtifactPath(Tuple tuple) throws Exception {
        String artifactPath = (String)tuple.getField(FIELD_NAME_ARTIFACT_PATH);
        if (isEmpty(artifactPath)) {
            throw new Exception("You must specify an artifact path");
        }
        return normalizeArtifactPath(artifactPath);
    }

    /**
     * Sets success status in the status tuple
     *
     * @param tuple the status tuple
     */
    private void setSuccessStatus(Tuple tuple) {
        setStatus(tuple, true, null, null);
    }

    /**
     * Sets failure status, the reason for the failure, and a message in the status tuple
     *
     * @param tuple the status tuple
     * @param reason the failure reason
     * @param message the failure message
     */
    private void setFailureStatus(Tuple tuple, String reason, String message) {
        setStatus(tuple, false, reason, message);
    }

    /**
     * Populates the status tuple
     *
     * @param tuple the status tuple
     * @param success true if the command succeeded and false otherwise
     * @param reason the failure reason, if applicable
     * @param message the failure message, if applicable
     */
    private void setStatus(Tuple tuple, boolean success, String reason, String message) {
        try {
            Tuple statusTuple = tuple.getSchema().getField(FIELD_NAME_STATUS).getSchema().createTuple();
            statusTuple.setBoolean(FIELD_NAME_SUCCESS, success);
            statusTuple.setString(FIELD_NAME_REASON, reason);
            statusTuple.setString(FIELD_NAME_MESSAGE, message);
            tuple.setTuple(FIELD_NAME_STATUS, statusTuple);
        }
        catch (TupleException e) {
            logger.warn(String.format("Error setting status in output tuple: %s", e.getMessage()), e);
        }
    }

    /**
     * Emits and output tuple and logs an error if the emission fails
     *
     * @param tuple the output tuple
     */
    private void sendOutputTuple(Tuple tuple) {
        int port = OUTPUT_PORT_NUMBER;
        try {
            sendOutput(port, tuple);
        }
        catch (Exception e) {
            logger.error(String.format("Error sending output tuple '%s' on port %d: %s", tuple.toString(true), port, e.getMessage()), e);
        }
    }

    //****************************************************************************************************************************
    //
    // Methods for accessing AMS
    //
    //****************************************************************************************************************************

    /**
     * Logs in to the AMS
     *
     * @return the login token
     * @throws Exception if an error occurs logging in
     */
    private String login() throws Exception {

        // Force the username/password to be sent in a basic authorization header
        amsConnectionInfo.loginToken = null;
        String response = sendGetRequest(amsConnectionInfo.buildURL("login", "force=true&readonly=false"));
        if (response != null) {
            LoginResponse loginResponse = fromJson(response, LoginResponse.class);
            if (loginResponse.status != 0) {
                throw new Exception(String.format("Error logging into AMS: %s", loginResponse.errorMessage));
            }
            if (loginResponse != null && loginResponse.record != null && loginResponse.record.length == 1) {
                return loginResponse.record[0].apiToken;
            }
        }
        return null;

    }

    /**
     * Logs out from the AMS
     *
     * @throws Exception if an error occurs logging out
     */
    private void logout() throws Exception {

        String response = sendGetRequest(amsConnectionInfo.buildURL("logout"));
        if (response != null) {
            Response logoutResponse = fromJson(response, Response.class);
            if (logoutResponse.status != 0) {
                throw new Exception(String.format("Error logging out of AMS: %s", logoutResponse.errorMessage));
            }
        }
    }

    /**
     * Retrieves the set of available projects
     *
     * @return a map of project name to project ID
     * @throws Exception if an error occurs retrieving the set of available projects
     */
    private Map<String, String> listProjects() throws Exception {

        Map<String, String> ret = new LinkedHashMap<String, String>();

        // First, get the available projects to get the ID of the requested project

        String response = sendGetRequest(amsConnectionInfo.buildURL("projects"));
        if (response == null) {
            throw new Exception("Error fetching projects");
        }
        FetchProjectsResponse fetchProjectsResponse = fromJson(response, FetchProjectsResponse.class);
        for (FetchProjectsResponse.ProjectRecord project : fetchProjectsResponse.record) {
            ret.put(project.projectName, project.entityId);
        }
        return ret;
    }

    /**
     * Retrieves a project's ID
     *
     * @param projectName the project's name
     * @return the project's ID
     * @throws Exception
     */
    private String getProjectId(String projectName) throws Exception {

        Map<String, String> projectNameToIdMap = listProjects();
        if (!projectNameToIdMap.containsKey(projectName)) {
            throw new Exception(String.format("Project '%s' does not exist", projectName));
        }
        return projectNameToIdMap.get(projectName);
    }

    /**
     * Retrieves an artifact's ID
     *
     * @param projectName the project's name
     * @param artifactPath the artifact's path
     * @return the artifact's ID
     * @throws Exception
     */
    private String getArtifactId(String projectName, String artifactPath) throws Exception {

        // Get the ID of the requested artifact
        artifactPath = normalizeArtifactPath(artifactPath);
        Map<String, String> artifactPathToIdMap = listProjectArtifacts(projectName, getProjectId(projectName));
        String artifactId = artifactPathToIdMap.get(artifactPath);
        if (artifactId == null) {
            throw new Exception(String.format("Error fetching artifact %s%s from AMS: No such artifact", projectName, artifactPath));
        }
        return artifactId;
    }

    /**
     * Determines whether an artifact exists in a project. For an artifact to exist in a project, it must have been added, committed, and approved.
     *
     * @param projectName the project's name
     * @param artifactPath the artifact's path
     * @return true if the artifact exists in the project and false otherwise
     * @throws Exception
     */
    private boolean artifactExistsInProject(String projectName, String artifactPath) throws Exception {

        // Get the ID of the requested artifact
        artifactPath = normalizeArtifactPath(artifactPath);
        Map<String, String> artifactPathToIdMap = listProjectArtifacts(projectName, getProjectId(projectName));
        return artifactPathToIdMap.containsKey(artifactPath);
    }

    /**
     * Determines whether an artifact exists in a checkout. For an artifact to exist in a checked, it must have been added, but it need not have been committed or approved.
     *
     * @param projectName the project's name
     * @param artifactPath the artifact's path
     * @return true if the artifact exists in the checkout and false otherwise
     * @throws Exception
     */
    private boolean artifactExistsInCheckout(String projectName, String artifactPath) throws Exception {

        String checkoutId = getCheckoutId(projectName);
        if (checkoutId == null) return false;
        return !getCheckedOutArtifactIds(checkoutId, new HashSet<String>(Arrays.asList(artifactPath)), false).isEmpty();
    }

    /**
     * Retrieves the artifacts in a project.
     *
     * @param projectName the projct's name
     * @param projectId the project's ID
     * @return a map of artifact path to artifact ID
     * @throws Exception
     */
    private Map<String, String> listProjectArtifacts(String projectName, String projectId) throws Exception {

        Map<String, String> ret = new LinkedHashMap<String, String>();

        // Next, retrieve the artifacts from the project
        String response = sendGetRequest(amsConnectionInfo.buildURL(String.format("projects/%s/artifacts", projectId)));
        if (response != null) {
            FetchProjectArtifactsResponse fetchProjectArtifactsResponse = fromJson(response, FetchProjectArtifactsResponse.class);
            if (fetchProjectArtifactsResponse.status != 0) {
                throw new Exception(String.format("Error fetching artifacts of project %s from AMS: %s", projectName, fetchProjectArtifactsResponse.errorMessage));
            }
            for (int i=0; i<fetchProjectArtifactsResponse.totalRows; i++) {
                FetchProjectArtifactsResponse.ArtifactRecord record = fetchProjectArtifactsResponse.record[i];
                ret.put(record.path, record.entityId);
            }
        }
        return ret;
    }

    /**
     * Fetches an artifact revision
     *
     * @param projectName the project's name
     * @param artifactPath the artifact's path
     * @param artifactVersion the version of the artifact fetch, or null to fetch the latest version
     * @return an artifact revision record containing the artifact type, version number, ID, content, encoding, and metadata
     * @throws Exception
     */
    private FetchArtifactResponse.ArtifactRevisionRecord fetchArtifact(String projectName, String artifactPath, long artifactVersion) throws Exception {

        // Get the ID of the requested artifact
        String artifactId = getArtifactId(projectName, artifactPath);

        // Fetch the artifact
        String response = sendGetRequest(amsConnectionInfo.buildURL(String.format("artifacts/%s/fetch/%d", artifactId, artifactVersion)));
        if (response == null) {
            throw new Exception(String.format("Error fetching artifact %s%s from AMS: %s", projectName, artifactPath, "No response returned from AMS server"));
        }
        FetchArtifactResponse fetchArtifactResponse = fromJson(response, FetchArtifactResponse.class);
        if (fetchArtifactResponse.status != 0) {
            throw new Exception(String.format("Error fetching artifact %s%s from AMS: %s", projectName, artifactPath, fetchArtifactResponse.errorMessage));
        }
        if (fetchArtifactResponse.record == null || fetchArtifactResponse.record.length != 1) {
            throw new Exception(String.format("Error fetching artifact %s%s from AMS: no artifact record in response", projectName, artifactPath));
        }
        return fetchArtifactResponse.record[0];
    }

    /**
     * Adds an artifact to a checkout, The added artifact is not visible to other AMS users until it is committed and approved.
     *
     * @param checkoutId the checkout ID
     * @param artifactPath the artifact's path
     * @param type the artifact's type
     * @param description the artifact's description
     * @param content the artifact's content
     * @param encoding the artifact's encoding
     * @param metadata the artifact's metadata
     * @return a response containing the status, error code, and message
     * @throws Exception
     */
    private AddArtifactResponse addArtifact(String checkoutId, String artifactPath, String type, String description, String content, String encoding, String metadata) throws Exception {

        AddArtifactRequest payload = new AddArtifactRequest();
        payload.artifact.path = artifactPath;
        payload.artifact.type = type;
        payload.artifact.description = description;
        payload.artifact.content = content;
        payload.artifact.encoding = encoding;
        payload.artifact.metadata = metadata;
        String response = sendPostRequest(amsConnectionInfo.buildURL(String.format("artifacts/%s/add", checkoutId)), toJson(payload));
        return fromJson(response, AddArtifactResponse.class);
    }

    /**
     * Updates a checked-out artifact. The update is not visible to other AMS users until it is committed and approved.
     *
     * @param checkedOutArtifactId the checked-out artifact ID
     * @param type the new artifact type or null to leave type type unchanged
     * @param description the new artifact description or null to leave the description unchanged
     * @param content the new artifact content or null to leave the content unchanged
     * @param encoding the new artifact encoding or null to leave the encoding unchanged
     * @param metadata the new artifact metadata or null to leave the metadata unchanged
     * @return a response containing the status, error code, and message
     */
    private UpdateArtifactResponse updateArtifact(String checkedOutArtifactId, String type, String description, String content, String encoding, String metadata) throws Exception {

        UpdateArtifactRequest payload = new UpdateArtifactRequest();
        payload.artifact.type = type;
        payload.artifact.description = description;
        payload.artifact.content = content;
        payload.artifact.encoding = encoding;
        payload.artifact.metadata = metadata;
        String response = sendPutRequest(amsConnectionInfo.buildURL(String.format("artifacts/%s/update", checkedOutArtifactId)), toJson(payload));
        return fromJson(response, UpdateArtifactResponse.class);
    }

    /**
     * Deletes an artifact from a checkout. The deletion is not visible to other AMS users until it is committed and approved.
     *
     * @param checkedOutArtifactId
     * @return
     * @throws Exception
     */
    private DeleteArtifactResponse deleteArtifact(String checkedOutArtifactId) throws Exception {

        String response = sendDeleteRequest(amsConnectionInfo.buildURL(String.format("artifacts/%s/delete", checkedOutArtifactId)));
        return fromJson(response, DeleteArtifactResponse.class);
    }

    /**
     * Checks out a project
     *
     * @param projectName the project's name
     * @return the checkout ID
     * @throws Exception
     */
    private String checkoutProject(String projectName) throws Exception {

        String projectId = getProjectId(projectName);
        CheckoutArtifactRequest payload = new CheckoutArtifactRequest();
        payload.projectId = projectId;
        String response = sendPutRequest(amsConnectionInfo.buildURL("artifacts/checkout"), toJson(payload));
        CheckoutResponse checkoutResponse = fromJson(response, CheckoutResponse.class);
        if (checkoutResponse.record == null || checkoutResponse.record.length != 1) {
            throw new Exception(String.format("Error checking out project '%s': no checkout record in response", projectName));
        }
        return checkoutResponse.record[0].entityId;
    }

    /**
     * Checks out an artifact
     *
     * @param projectName the project's name
     * @param artifactPath the artifact's path
     * @return the checked-out artifact ID
     * @throws Exception
     */
    private String checkoutArtifact(String projectName, String artifactPath) throws Exception {

        String projectId = getProjectId(projectName);
        String artifactId = getArtifactId(projectName, artifactPath);
        CheckoutArtifactRequest payload = new CheckoutArtifactRequest();
        payload.projectId = projectId;
        payload.artifactIds = new String[]{artifactId};
        String response = sendPutRequest(amsConnectionInfo.buildURL("artifacts/checkout"), toJson(payload));
        CheckoutResponse checkoutResponse = fromJson(response, CheckoutResponse.class);
        if (checkoutResponse.record == null || checkoutResponse.record.length != 1) {
            throw new Exception(String.format("Error checking out project '%s' artifact '%s': no checkout record in response", projectName, artifactPath));
        }
        CheckoutResponse.CheckoutRecord checkoutRecord = checkoutResponse.record[0];
        if (checkoutRecord.artifacts == null || checkoutRecord.artifacts.length != 1) {
            throw new Exception(String.format("Error checking out project '%s' artifact '%s': no checked-out artifact record in response", projectName, artifactPath));
        }
        return checkoutRecord.artifacts[0].entityId;
    }

    /**
     * Gets the ID of a user's checkout of a project
     *
     * @param projectName the project's name
     * @return the ID of a user's checkout of the project
     * @throws Exception
     */
    private String getCheckoutId(String projectName) throws Exception {

        String payload = sendGetRequest(amsConnectionInfo.buildURL("lifecycle/checkouts"));
        ListCheckoutsResponse response = fromJson(payload, ListCheckoutsResponse.class);
        for (ListCheckoutsResponse.CheckoutRecord checkout : response.record) {
            if (checkout.projectName.equals(projectName)) {
                return checkout.entityId;
            }
        }
        return null;
    }

    /**
     * Gets the checked-out artifact IDs of the checked-out artifacts in a user's checkout of a project
     *
     * @param checkoutId the ID of the project checkout
     * @param artifactPaths the set of artifact paths within the project
     * @param dirtyOnly true to return only the IDs of the dirty (added, updated, or deleted) artifacts within the project checkout and false otherwise
     * @return the checked-out artifact IDs of the checked-out artifacts in a user's checkout of a project
     * @throws Exception
     */
    private List<String> getCheckedOutArtifactIds(String checkoutId, Set<String> artifactPaths, boolean dirtyOnly) throws Exception {

        List<String> ret = new LinkedList<String>();
        String payload = sendGetRequest(amsConnectionInfo.buildURL(String.format("lifecycle/checkouts/%s", checkoutId)));
        CheckedOutArtifactsResponse response = fromJson(payload, CheckedOutArtifactsResponse.class);
        int recordCount = response.record == null ? 0 : response.record.length;
        if (recordCount != 1) {
            throw new Exception(String.format("Error retrieving checked-out artifact IDs: expected 1 record in response, got %d", recordCount));
        }
        CheckedOutArtifactsResponse.CheckoutRecord checkoutRecord = response.record[0];
        CheckedOutArtifactsResponse.CheckoutRecord.CheckedOutArtifactRecord[] checkedOutArtifactRecords = checkoutRecord.artifacts;
        for (CheckedOutArtifactsResponse.CheckoutRecord.CheckedOutArtifactRecord checkedOutArtifactRecord : checkedOutArtifactRecords) {
            if (!(dirtyOnly && checkedOutArtifactRecord.status.equals("CLEAN")) && artifactPaths.contains(checkedOutArtifactRecord.path)) {
                ret.add(checkedOutArtifactRecord.entityId);
            }
        }
        return ret;
    }

    /**
     * Commits changes to a user's checkout
     *
     * @param checkoutId the ID of the project checkout
     * @param commitMessage the commit message
     * @param checkedOutArtifactIds a list of the checked-out artifact IDs to commit
     * @throws Exception
     */
    private void commit(String checkoutId, String commitMessage, List<String> checkedOutArtifactIds) throws Exception {

        CommitRequest request = new CommitRequest();
        request.commitMessage = commitMessage;
        request.checkedOutArtifactIds = checkedOutArtifactIds;
        String payload = sendPostRequest(amsConnectionInfo.buildURL(String.format("lifecycle/checkouts/%s/commit", checkoutId)), toJson(request));
        CommitResponse response = fromJson(payload, CommitResponse.class);
        int recordCount = response.record == null ? 0 : response.record.length;
        if (recordCount != 1) {
            throw new Exception(String.format("Error committing artifacts: expected 1 record in response, got %d", recordCount));
        }
    }

    /**
     * Encapsulates an HTTP 401 (unauthorized) error
     */
    private static class UnauthorizedException extends Exception {
        public UnauthorizedException(String message) {super(message);}
    }

    /**
     * Sends an HTTP GET request to the AMS server
     *
     * @param urlString the AMS URL, including the path and any query parameters associated with the end point
     * @return the JSON response payload from the AMS
     * @throws Exception
     */
    private String sendGetRequest(String urlString) throws Exception {
        return sendRequest(urlString, "GET", null);
    }

    /**
     * Sends an HTTP POST request to the AMS server
     *
     * @param urlString the AMS URL, including the path and any query parameters associated with the end point
     * @param payload the JSON-encoded payload to send to the server
     * @return the JSON response payload from the AMS
     * @throws Exception
     */
    private String sendPostRequest(String urlString, String payload) throws Exception {
        return sendRequest(urlString, "POST", payload);
    }

    /**
     * Sends an HTTP PUT request to the AMS server
     *
     * @param urlString the AMS URL, including the path and any query parameters associated with the end point
     * @param payload the JSON-encoded payload to send to the server
     * @return the JSON response payload from the AMS
     * @throws Exception
     */
    private String sendPutRequest(String urlString, String payload) throws Exception {
        return sendRequest(urlString, "PUT", payload);
    }

    /**
     * Sends an HTTP DELETE request to the AMS server
     *
     * @param urlString the AMS URL, including the path and any query parameters associated with the end point
     * @return the JSON response payload from the AMS
     * @throws Exception
     */
    private String sendDeleteRequest(String urlString) throws Exception {
        return sendRequest(urlString, "DELETE", null);
    }

    /**
     * Sends an HTTP request to the AMS server
     *
     * @param urlString the AMS URL, including the path and any query parameters associated with the end point
     * @param requestMethod the HTTP request method: GET, POST, PUT, or DELETE
     * @param payload the JSON-encoded payload to send to the server (POST and PUT requests only)
     * @return the JSON response payload from the AMS
     * @throws Exception
     */
    private String sendRequest(String urlString, String requestMethod, String payload) throws Exception {

        try {
            URL url;
            HttpURLConnection connection;
            if (proxy == null && useProxy) {
                proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort.trim())));
            }

            // Connect to the AMS server
            url = new URL(urlString);
            connection = (HttpURLConnection) (useProxy ? url.openConnection(proxy) : url.openConnection());
            if (isIgnoreCertificateErrors()) {
                makeIgnoreCertificate((HttpsURLConnection) connection);
            }
            connection.setConnectTimeout(getConnectTimeout());
            connection.setReadTimeout(getReadTimeout());
            connection.setRequestMethod(requestMethod);
            if (!getUseDefaultCharset()) {
                connection.setRequestProperty("charset", charset);
            }
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            setAuthHeader(connection, amsConnectionInfo.username, amsConnectionInfo.password, amsConnectionInfo.loginToken);

            // Write the outgoing payload, if present
            if (!isEmpty(payload)) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                OutputStream out = connection.getOutputStream();
                out.write(payload.getBytes());
                out.flush();
                out.close();
            }

            // Read the response code
            int responseCode = connection.getResponseCode();
            boolean success = responseCode >= 200 && responseCode < 300;

            // Read the response payload
            String responsePayload = readResponsePayload(connection, success);

            // Special handling of 401 (unauthorized)
            if (responseCode == 401) {
                amsConnectionInfo.loginToken = null;
                throw new UnauthorizedException("Unauthorized");
            }

            // Handle errors
            if (!success) {

                // Retrieve the error message from the response payload, if available
                if (!isEmpty(responsePayload)) {
                    Response response = null;
                    try {
                        response = fromJson(responsePayload, Response.class);
                    }
                    catch (Exception e) {
                        // Ignore and throw a 'bad  HTTP status code' exception below
                    }
                    if (response != null && !isEmpty(response.errorMessage)) {
                        throw new Exception(response.errorMessage);
                    }
                }
                throw new Exception(String.format("Bad HTTP status code: %d", responseCode));
            }
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Response from %s request to HTTP URL '%s': %s", requestMethod, urlString, responsePayload));
            }
            return responsePayload;
        }
        catch (Exception e) {
            throw new Exception(String.format("Error sending HTTP %s request to URL '%s': %s", requestMethod, urlString, e.getMessage()), e);
        }
    }

    /**
     * Read the HTTP response payload from the AMS server
     *
     * @param connection the HTTP connection to the AMS server
     * @param success true of the request succeeded and false otherwise
     * @return the JSON-encoded response payload
     */
    private String readResponsePayload(HttpURLConnection connection, boolean success) {

        String ret = null;
        try {
            String encoding = connection.getContentEncoding();
            InputStream resultingInputStream = null;

            // If the request succeeded/failed, the response payload comes in on input/error stream
            InputStream in = success ? connection.getInputStream() : connection.getErrorStream();

            // Create the appropriate stream wrapper based on the encoding type
            if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
                resultingInputStream = new GZIPInputStream(in);
            }
            else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
                resultingInputStream = new InflaterInputStream(in, new Inflater(true));
            }
            else {
                resultingInputStream = in;
            }

            if (resultingInputStream == null) {
                return null;
            }

            // Log the response headers
            Iterator<Entry<String, List<String>>> headers = connection.getHeaderFields().entrySet().iterator();
            while (headers.hasNext()) {
                Entry<String, List<String>> entry = headers.next();
                if (entry != null && entry.getKey() != null && entry.getKey().length() > 0) {
                    if (logger.isTraceEnabled()) {
                        logger.trace(String.format("Response header: %s -> %s", entry.getKey(), entry.getValue()));
                    }
                }
            }

            // Accumulate the response payload in a ByteArrayOutputStream
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            int bytesRead = -1;
            byte[] buffer = new byte[READ_DATA_BUFFER_SIZE];
            while ((bytesRead = resultingInputStream.read(buffer)) != -1) {
                bs.write(buffer, 0, bytesRead);
            }
            ret = bs.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
            // Ignore
        }

        return ret;
    }

    /**
     * Ignore SSL server certificates when making secure connections
     *
     * @param connection the HRTTP connection to the AMS server
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    private void makeIgnoreCertificate(HttpsURLConnection connection) throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sc = SSLContext.getInstance("SSL");
        TrustManager[] tma = { new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }

        } };
        sc.init(null, tma, null);

        connection.setSSLSocketFactory(sc.getSocketFactory());
        connection.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true; // everything goes
            }
        });
    }

    /**
     * Sets the authorization header in outgoing HTTP requests to the AMS server
     *
     * @param connection the HTTP connection to the AMS server
     * @param username the AMS user name
     * @param password the AMS password
     * @param loginToken the login token once we've logged in to the AMS
     */
    private void setAuthHeader(HttpURLConnection connection, String username, String password, String loginToken) {

        String authHeaderValue = null;
        if (loginToken == null) {
            String base64EncodedUsernameAndPassword = new String(Base64.getEncoder().encode(String.format("%s:%s", username, password).getBytes()));
            authHeaderValue = String.format("Basic %s", base64EncodedUsernameAndPassword);
        }
        else {
            authHeaderValue = String.format("Token %s", loginToken);
        }
        connection.setRequestProperty("Authorization", authHeaderValue);
    }

    //****************************************************************************************************************************
    //
    // Utility methods
    //
    //****************************************************************************************************************************

    /**
     * Get the reason code from a Throwable
     *
     * @param t the Throwable
     * @return the Throwable's reason code
     */
    private static String getReason(Throwable t) {
        return t.getCause() == null ? t.getMessage() : getReason(t.getCause());
    }

    /**
     * Tests whether a string is null or empty
     *
     * @param s the string
     * @return true if the string is null or empty and false otherwise
     */
    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Returns a Java object populated from a JSON-encoded string
     *
     * @param json the JSON-encoded string
     * @param clazz the Java class of the object to create and populated
     * @return the populated Java object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    /**
     * Converts a Java object to a JSON-encoded string
     *
     * @param o the Java object
     * @return the JSON-encoded string
     */
    public static String toJson(Object o) {
        return JSON.toJSONString(o);
    }

    /**
     * Normalized an AMS artifact path by ensuring it starts with a forward slash but doesn't end with one
     *
     * @param path the AMS artifact path to normalize
     * @return the normalized AMS artifact path
     */
    public static String normalizeArtifactPath(String path) {
        if (!path.startsWith("/")) {
            path = String.format("/%s", path);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length()-1);
        }
        return path;
    }

    //****************************************************************************************************************************
    //
    // Java classes for building JSON payloads in outgoing REST requests
    //
    // Note: these classes may not contain all the available fields; they contain those fields necessary to support the sample
    //
    //****************************************************************************************************************************

    public static class CheckoutArtifactRequest {
        public String projectId;
        public String[] artifactIds;
        public boolean ignoreAlreadyCheckedOutArtifacts = true;
    }

    public static class AddArtifactRequest {
        public AddArtifactRecord artifact = new AddArtifactRecord();
        public static class AddArtifactRecord {
            public String path;
            public String type;
            public String description;
            public String content;
            public String encoding;
            public String metadata;
        }
    }

    public static class UpdateArtifactRequest {
        public UdpdateArtifactRecord artifact = new UdpdateArtifactRecord();
        public static class UdpdateArtifactRecord {
            public String path;
            public String type;
            public String description;
            public String content;
            public String encoding;
            public String metadata;
        }
    }

    public static class CommitRequest {
        public String commitMessage;
        public List<String> checkedOutArtifactIds;
    }

    //****************************************************************************************************************************
    //
    // Java classes for parsing JSON payloads in incoming REST responses
    //
    // Note: these classes may not contain all the available fields; they contain those fields necessary to support the sample
    //
    //****************************************************************************************************************************

    public static class Response {
        public int status;
        public int startRow;
        public int endRow;
        public int totalRows;
        public String errorCode;
        public String errorMessage;
    }

    public static class LoginResponse extends Response {
        public LoginRecord[] record;
        private static class LoginRecord {
            public String apiToken;
        }
    }

    private static class FetchProjectsResponse extends Response {
        public ProjectRecord[] record;
        private static class ProjectRecord {
            public String projectName;
            public String entityId;
        }
    }

    private static class FetchProjectArtifactsResponse extends Response {
        public ArtifactRecord[] record;
        private static class ArtifactRecord {
            public String path;
            public String entityId;
        }
    }

    public static class FetchArtifactResponse extends Response {
        public ArtifactRevisionRecord[] record;
        public static class ArtifactRevisionRecord {
            public String entityId;
            public String path;
            public String type;
            public String description;
            public String basePath;
            public String changeType;
            public String extension;
            public String imageSrc;
            public boolean locked;
            public String content;
            public String encoding;
            public String metadata;
            public Integer revisionNumber;
            public String state;
        }
    }

    public static class ListCheckoutsResponse extends Response {
        public CheckoutRecord[] record;
        public static class CheckoutRecord {
            public String projectName;
            public String entityId;
        }
    }

    public static class CheckoutResponse extends Response {
        public CheckoutRecord[] record;
        public static class CheckoutRecord {
            public static class CheckedOutArtifactRecord {
                public String entityId;
            }
            public String entityId;
            public CheckedOutArtifactRecord[] artifacts;
        }
    }

    private static class CheckedOutArtifactsResponse extends Response {
        public CheckoutRecord[] record;
        private static class CheckoutRecord {
            public CheckedOutArtifactRecord[] artifacts;
            private static class CheckedOutArtifactRecord {
                public String entityId;
                public String path;
                public String status;
            }
        }
    }

    public static class CommitResponse extends Response {
        public CommtRecord[] record;
        public static class CommtRecord {
            public String entityId;
            public CommitCandidateRecord[] changeList;
            public static class CommitCandidateRecord {
                public String entityId;
            }
        }
    }

    private static class EmptyResponse extends Response {
    }
    private static class AddArtifactResponse extends EmptyResponse {}
    private static class UpdateArtifactResponse extends EmptyResponse {}
    private static class DeleteArtifactResponse extends EmptyResponse {}

    //****************************************************************************************************************************
    //
    // Operator property getters and setters
    //
    //****************************************************************************************************************************

    public boolean isLogInAtStartup() {
        return logInAtStartup;
    }

    public void setLogInAtStartup(boolean logInAtStartup) {
        this.logInAtStartup = logInAtStartup;
    }

    public String getAmsServerHostName() {
        return amsServerHostName;
    }

    public void setAmsServerHostName(String amsServerHostName) {
        this.amsServerHostName = amsServerHostName;
    }

    public String getAmsServerPortNumber() {
        return amsServerPortNumber;
    }

    public void setAmsServerPortNumber(String amsServerPortNumber) {
        this.amsServerPortNumber = amsServerPortNumber;
    }

    public boolean isAmsServerSecureChannel() {
        return amsServerSecureChannel;
    }

    public void setAmsServerSecureChannel(boolean amsServerSecureChannel) {
        this.amsServerSecureChannel = amsServerSecureChannel;
    }

    public String getAmsServerUsername() {
        return amsServerUsername;
    }

    public void setAmsServerUsername(String amsServerUsername) {
        this.amsServerUsername = amsServerUsername;
    }

    public String getAmsServerPassword() {
        return amsServerPassword;
    }

    public void setAmsServerPassword(String amsServerPassword) {
        this.amsServerPassword = amsServerPassword;
    }

    public boolean isIgnoreCertificateErrors() {
        return ignoreCertificateErrors;
    }

    public void setIgnoreCertificateErrors(boolean ignoreCertificateErrors) {
        this.ignoreCertificateErrors = ignoreCertificateErrors;
    }

    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return this.readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public boolean getUseDefaultCharset() {
        return useDefaultCharset;
    }

    public void setUseDefaultCharset(boolean useDefault) {
        this.useDefaultCharset = useDefault;
    }

    public boolean getBasicAuthEnabled() {
        return this.basicAuthEnabled;
    }

    public void setBasicAuthEnabled(boolean basicAuthEnabled) {
        this.basicAuthEnabled = basicAuthEnabled;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public String getAutoCommitMessage() {
        return autoCommitMessage;
    }

    public void setAutoCommitMessage(String autoCommitMessage) {
        this.autoCommitMessage = autoCommitMessage;
    }

    public boolean shouldEnableAutoCommitMessage() {
        return isAutoCommit();
    }
}
