package com.tibco.streambase.ircdemo;


import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.ircclouds.irc.api.Callback;
import com.ircclouds.irc.api.IRCApi;
import com.ircclouds.irc.api.IRCApiImpl;
import com.ircclouds.irc.api.IServerParameters;
import com.ircclouds.irc.api.domain.IRCServer;
import com.ircclouds.irc.api.domain.messages.ChannelPrivMsg;
import com.ircclouds.irc.api.domain.messages.interfaces.IMessage;
import com.ircclouds.irc.api.listeners.IMessageListener;
import com.ircclouds.irc.api.state.IIRCState;
import com.streambase.sb.DataType;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.adapter.InputAdapter;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;
import com.tibco.streambase.ircdemo.WikiEdit.CannotParseAsWikiEditException;

/**
 * Connects to wikimedia's IRC servers for real-time wikipedia updates. Implements
 * basic conenction management (no reconnect). Has hard coded assumptions about
 * the message protocol and format, and is not a suitable general-purpose IRC adapter.
 * <p>
 * See {@link WikiEdit} for details on handling of messages
 * </p>
 */
public class WikimediaEditsAdapter extends InputAdapter implements
		Parameterizable, IMessageListener {

	private static Schema.Field FIELD_PAGENAME = Schema.createField(DataType.STRING, "pageName");
	private static Schema.Field FIELD_DIFFURL = Schema.createField(DataType.STRING, "diffURL");
	private static Schema.Field FIELD_EDITOR  = Schema.createField(DataType.STRING, "editor");
	private static Schema.Field FIELD_LINEDELTA = Schema.createField(DataType.INT, "lineDelta");
	private static Schema.Field FIELD_COMMENT = Schema.createField(DataType.STRING, "comment");
	
	
	public static final long serialVersionUID = 1407772137213L;
	private static final String displayName = "Wikimedia Edits Input Adapter";
	
	private Logger logger;

	private String server;
	private int port;
	private String realName;
	private String nick;
	private String channel;
	private IRCApi irc;
	
	private int connectTimeoutSecs;
	protected IIRCState ircState;

	private Schema outputSchema;
	
	public WikimediaEditsAdapter() {
		super();
		logger = getLogger();
		setPortHints(0, 1);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
		setServer("irc.wikimedia.org");
		setPort(6667);
		setConnectTimeoutSecs(20);
		setRealName("SB Wikimedia Edits Input Adapter (Bot)");
		setNick("sbeditsbot");
		setChannel("#en.wikipedia");

	}

	public void typecheck() throws TypecheckException {
		if (server.isEmpty()) {
			throw new PropertyTypecheckException("server", "IRC Server may not be empty");
		}
		if (channel.isEmpty()) {
			throw new PropertyTypecheckException("channel", "Channel may not be empty");
		}
		if (nick.isEmpty()) {
			throw new PropertyTypecheckException("nick", "IRC Nickname may not be empty");
		}
		Schema output = new Schema(null,
				FIELD_PAGENAME,
				FIELD_DIFFURL,
				FIELD_EDITOR,
				FIELD_LINEDELTA,
				FIELD_COMMENT);
		setOutputSchema(0, output);
	}

	/**
	 * Initialize the adapter. If typecheck succeeds, the init method is called before
	 * the StreamBase application is started. Note that your adapter is not required to
	 * define the init method, unless you need to register a runnable or perform
	 * initialization of a resource such as, for example, a JDBC pool.
	 */
	public void init() throws StreamBaseException {
		super.init();
		
		irc = new IRCApiImpl(false);
		// update output schema and field references now
		outputSchema = getOutputSchema(0);
		FIELD_PAGENAME = outputSchema.getField(FIELD_PAGENAME.getName());
		FIELD_DIFFURL = outputSchema.getField(FIELD_DIFFURL.getName());
		FIELD_EDITOR = outputSchema.getField(FIELD_EDITOR.getName());
		FIELD_LINEDELTA = outputSchema.getField(FIELD_LINEDELTA.getName());
		FIELD_COMMENT = outputSchema.getField(FIELD_COMMENT.getName());
	}

	public void shutdown() {
		if (irc != null) {
			irc.disconnect();
		}
	}

	private void sendAsync(WikiEdit edit) throws StreamBaseException {
		if (edit != null) {
			Tuple t = outputSchema.createTuple();
			t.setField(FIELD_PAGENAME, edit.pageName);
			t.setField(FIELD_DIFFURL, edit.diffURL);
			t.setField(FIELD_EDITOR, edit.editor);
			t.setField(FIELD_LINEDELTA, edit.lineDelta);
			t.setField(FIELD_COMMENT, edit.comment);
			sendOutputAsync(0, t);
		}
	}

	/**
	 * This method will be called when an adapter starts or resumes execution,
	 * before any registered runnables are started or resumed.  Note that if the
	 * application is shutdown directly from a suspended state, this will
	 * <b>not</b> be called; instead {@link #shutdown()} will be called.
	 *
	 * resume() is a callback that is called by the StreamBase runtime.
	 */
	public void resume() {
		if (ircState != null && ircState.isConnected()) {
			logger.debug("Ignoring resume(); IRC state already established and connected");
			return;
		}
		
        final CountDownLatch latch = new CountDownLatch(1); 
        final Exception[] failureException = new Exception[1];
		irc.connect(new IServerParameters() {
			
			@Override
			public IRCServer getServer() {
				return new IRCServer(server, port);
			}
			
			@Override
			public String getRealname() {
				return realName;
			}
			
			@Override
			public String getNickname() {
				return nick;
			}
			
			@Override
			public String getIdent() {
				return System.getProperty("user.name"); //$NON-NLS-1$
			}
			
			@Override
			public List<String> getAlternativeNicknames() {
				List<String> result = new ArrayList<String>();
				for (int i = 2; i < 10; ++i) {
					result.add(getNickname() + i);
				}
				return result;
			}
		}, new Callback<IIRCState>() {

			@Override
			public void onSuccess(IIRCState aObject) {
				ircState = aObject;
				latch.countDown();
			}

			@Override
			public void onFailure(Exception aExc) {
				failureException[0] = aExc;
			}
			
		});
		// wait until connected
        try {
        	logger.debug("Connecting to IRC server {}...", server);
            latch.await(getConnectTimeoutSecs(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        	logger.debug("Interrupted awaiting connection to IRC server", e);
        }
        if (failureException[0] != null) {
        	logger.error("Could not connect to IRC server", failureException[0]);
        	sendErrorOutput(failureException[0]);
        }
        if (ircState != null && ircState.isConnected()) {
        	logger.info("Connected to {}, registering listener and joining channel {}", ircState.getServer().getHostname(), channel);
        	irc.addListener(this);
        	irc.joinChannel(channel); // TODO async to guard against blocks here
        	logger.debug("Channel joined");
        } else {
        	logger.warn("No connection established; resume adapter to try again");
        }
        
	}

	

	@Override
	public void onMessage(IMessage aMessage) {
		logger.trace("received {}", aMessage);
		if (isSuspended()) {
			logger.trace("suspended; message dropped");
			return;
		}
		if (aMessage instanceof ChannelPrivMsg) {
			ChannelPrivMsg msg = (ChannelPrivMsg) aMessage;
			try {
				sendAsync(WikiEdit.fromChannelMessage(msg));
			} catch (CannotParseAsWikiEditException e) {
				// given that we do not handle many kinds of messages, low level debug
				logger.trace("Could not parse channel message as Wiki edit", e);
			} catch (StreamBaseException e) {
				logger.warn("Error sending Wiki Edit tuple downstream", e);
			}
		}
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getServer() {
		return this.server;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public int getPort() {
		return port;
	}

	public void setRealName(String realName) {
		this.realName = realName;
	}

	public String getRealName() {
		return this.realName;
	}

	public void setNick(String nick) {
		this.nick = nick;
	}

	public String getNick() {
		return this.nick;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getChannel() {
		return this.channel;
	}

	public int getConnectTimeoutSecs() {
		return connectTimeoutSecs;
	}

	public void setConnectTimeoutSecs(int connectTimeout) {
		this.connectTimeoutSecs = connectTimeout;
	}
	
	@Override
	public URL getIconResource(IconKind iconType) {
		switch (iconType) {
		case CANVAS_OVERLAY_ICON:
			return getClass().getResource("64px-Wikimedia_Community_Logo.svg.png");
		case PALETTE_ICON_LARGE:
			return getClass().getResource("32px-Wikimedia_Community_Logo.svg.png");
		case PALETTE_ICON_SMALL:
		default:
			return super.getIconResource(iconType);
		}
	}
	

}
