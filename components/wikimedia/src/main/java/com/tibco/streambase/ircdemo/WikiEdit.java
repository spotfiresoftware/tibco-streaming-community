package com.tibco.streambase.ircdemo;

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ircclouds.irc.api.domain.messages.ChannelPrivMsg;

/**
 * Represents a wikipedia edit that this adapter handles. Some example of unmanaged edits:
 * <ul>
 * <li>flags (bot, minor, new...)
 * </ul>
 */
public class WikiEdit {
	
	public static class CannotParseAsWikiEditException extends Exception {
		private static final long serialVersionUID = -304085553044089820L;

		private final String input;
		
		private CannotParseAsWikiEditException(String input) {
			super(MessageFormat.format("for input: {0}", input));
			this.input = input;
		}
		
		public String getInput() {
			return input;
		}
	}
	
	private static final Logger logger = LoggerFactory.getLogger(WikiEdit.class);
	
	/**
	 * the page name that has been edited
	 */
	public String pageName;
	/**
	 * a complete URL string of the diff-page
	 */
	public String diffURL;
	/**
	 * the name of the person or thing that did the edit. a username, or an ip address for anonymous edits.
	 */
	public String editor;
	/**
	 * negative or positive number of lines changed; null when it cannot be determined
	 */
	public Integer lineDelta;
	/**
	 * free form comment of the edit, as entered by the editor
	 */
	public String comment;
	

	private WikiEdit(String pageName, String diffURL, String editor, int lineDelta, String comment) {
		this.pageName = pageName;
		this.diffURL = diffURL;
		this.editor = editor;
		this.lineDelta = lineDelta;
		this.comment = comment;
	}

	/**
	 * Extracts what we need from an IRC message about a wiki edit. See {@link #fromChannelMessage(ChannelPrivMsg)}
	 */
	final static Pattern EXTRACT = Pattern.compile(".*\\[\\[(.+)\\]\\]\\s+(\\S+)\\s+\\*(.+)\\s\\*\\s+\\(([+-]\\d+)\\)\\s(.*)"); //$NON-NLS-1$
	/**
	 * Java 7 parses integers with leading + signs, but to remain compatible with older Java, we do it this way
	 */
	final static DecimalFormat PLUS_MINUS_INTEGER = new DecimalFormat("+#;-#");
	
	/**
	 * Special, excluded prefixes for special pages
	 */
	final static List<String> EXCLUDE_PREFIXES = Arrays.asList(
			"Talk:",
			"User:",
			"User talk:",
			"Wikipedia:",
			"Wikipedia talk:",
			"File:",
			"File talk:",
			"MediaWiki:",
			"MediaWiki talk:",
			"Template:",
			"Template talk:",
			"Help:",
			"Help talk:",
			"Category:",
			"Category talk:",
			"Portal:",
			"Portal talk:",
			"Book:",
			"Book talk:",
			"Education Program:",
			"Education Program talk:",
			"TimedText:",
			"TimedText talk:",
			"Module:",
			"Module talk:",
			"Special:",
			"Media:",
			"Draft:"
			);
	/**
     * The format of the message includes caret-notation for coloring, via use of the control character U+0003 and
     * a number (0 to 15) indicating the color. As of writing, only foreground colors are used. All color information
     * is stripped off prior to actual segment parsing.
     * <p>
     * At time of writing, this is an example of a complete message string (ETX is the control character): 
     * ETX14 (color grey)
     * [[
     * ETX07 (color orange)
     * &lt;name of page&gt;
     * ETX14 (color grey)
     * ]]
     * ETX04 (color red)
     * &lt;flags: M (minor), create, delete, B (bot),...&gt; - when any are present, handling will be skipped
     * ETX10 (color teal)
     * &lt;??&gt;
     * ETX02 (color blue)
     * &lt;url&gt;
     * ETX
     * ETX
     * &lt;nothing or ??&gt;
     * ETX5 (color brown; incorrectly missing leading 0)
     * *
     * ETX
     * &lt;nothing or ??&gt;
     * ETX03
     * &lt;username or ip&gt;
     * ETX
     * &lt;nothing or ??&gt;
     * ETX5 (color brown; incorrectly missing leading 0)
     * *
     * ETX
     * &lt;nothing or ??&gt;
     * (&lt;line delta as negative or positive integer&gt;)
     * ETX10 (color teal)
     * &lt;freeform comment&gt;
     * ETX
     * &lt;nothing or ??&gt;
     * @param msg input string
     * @return a {@link WikiEdit} if able to parse enough; null to indicate an explicitly-excluded wiki edit from handling 
     * @throws CannotParseAsWikiEditException unable to parse
     */
	public static WikiEdit fromChannelMessage(ChannelPrivMsg msg)
			throws CannotParseAsWikiEditException {
		String text = msg.getText();
		
		// first remove all color code requests (note the optional leading first digit accounts for the lead 0 which sometimes is missing)
		text = text.replaceAll("\\u0003[0-1]?[0-9]", ""); //$NON-NLS-1$ //$NON-NLS-2$
		// next, remove all control characters remaining (they are probably clearing the color this way)
		text = text.replaceAll("\\u0003", ""); //$NON-NLS-1$ //$NON-NLS-2$ 
	
		// now, the parts can be extracted 
		Matcher matcher = EXTRACT.matcher(text);
		if (matcher.matches() && matcher.groupCount() == 5) {
			if (excludeByPage(matcher.group(1))) {
				return null;
			}
			String deltaString = matcher.group(4);
			Integer lineDelta = null;
			try {
				lineDelta = PLUS_MINUS_INTEGER.parse(deltaString).intValue();
			} catch (ParseException e) {
				logger.warn("line delta {} could not be parsed", deltaString, lineDelta);
			}
			
			return new WikiEdit(matcher.group(1).trim(), matcher.group(2).trim(), matcher.group(3).trim(), lineDelta, matcher.group(5));
		} else {
			throw new CannotParseAsWikiEditException(msg.getText());
		}
	}
	
	private static boolean excludeByPage(String page) {
		for (String prefix : EXCLUDE_PREFIXES) {
			if (page.startsWith(prefix)) return true;
		}
		return false;
	}
}