package de.saces.fnplugins.SCGIPublisher;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Vector;

import de.saces.fnplugins.SCGIPublisher.iniparser.IniParserException;
import de.saces.fnplugins.SCGIPublisher.iniparser.SimpleIniParser;


import freenet.keys.FreenetURI;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;

public class SCGIPublisher implements FredPlugin, FredPluginL10n, FredPluginThreadless, FredPluginVersioned, FredPluginRealVersioned {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(SCGIPublisher.class);
	}

	public static final String PLUGIN_URI = "/SCGIPublisher";

	public static final String DEFAULT_CONFIGNAME = "SCGIPublisher.ini";
	public static final String CONFIG_SERVERPREFIX = "Server_";
	public static final String CONFIG_KEYSETPREFIX = "Keyset_";
	

	private WebInterface webInterface;
	private PluginContext pluginContext;

	private Vector<SCGIServer> servers;

	public void runPlugin(PluginRespirator pr) {
		pluginContext = new PluginContext(pr);
		webInterface = new WebInterface(pluginContext);
		servers = new Vector<SCGIServer>();

		maybeLoadConfigAndStartServers();

		SCGIToadlet scgiToadlet = new SCGIToadlet(pluginContext);
		webInterface.registerInvisible(scgiToadlet);
	}

	public void terminate() {
		if (logDEBUG) Logger.debug(this, "Terminating SCGIPublischer");
		webInterface.kill();
		webInterface = null;
		for (SCGIServer server:servers) {
			server.kill();
		}
		servers = null;
		if (logDEBUG) Logger.debug(this, "Terminated SCGIPublischer");
	}

	public String getString(String key) {
		return key;
	}

	public void setLanguage(LANGUAGE newLanguage) {
		// TODO Auto-generated method stub
	}

	public String getVersion() {
		return Version.longVersionString;
	}

	public long getRealVersion() {
		return Version.version;
	}

	private void maybeLoadConfigAndStartServers() {
		File configFile = new File(SCGIPublisher.DEFAULT_CONFIGNAME);
		if (!configFile.exists()) {
			String errorText = "Config file '"+SCGIPublisher.DEFAULT_CONFIGNAME+"' not found in freenet dir: nothing started.\n" +
				"Please create one. Visit /SCGIPublisher/ for info/sample.";
			Logger.error(this, errorText);
			System.out.println(errorText);
			return;
		}

		SimpleIniParser conf = new SimpleIniParser();
		try {
			conf.parse(configFile);
		} catch (IOException e) {
			Logger.error(this, "Error while parsing config. Nothing started.", e);
			return;
		} catch (IniParserException e) {
			Logger.error(this, "Error while parsing config. Nothing started.", e);
			return;
		}

		String[] sections = conf.getSectionNames();
		for (String section:sections) {
			if (section.startsWith(CONFIG_SERVERPREFIX)) {
				configureServer(conf, section);
			}
		}
	}

	private void configureServer(SimpleIniParser conf, String section) {
		String host = conf.getValueAsString(section, "bindTo");
		String allowed = conf.getValueAsString(section, "allowedHosts");
		int port = conf.getValueAsInt(section, "port", -1);
		String[] keySets = conf.getValueAsString(section, "keysets").split(",");
		String serverpath = conf.getValueAsString(section, "serverpath");

		SCGIServer server = new SCGIServer(pluginContext.hlsc, pluginContext.clientCore.getExecutor(), pluginContext.clientCore.tempBucketFactory);
		servers.add(server);
		server.setAdress(host, port, allowed, false);
		server.setServerpath(serverpath);

		boolean error = false;
		for (String keyset: keySets) {
			String keysetname = CONFIG_KEYSETPREFIX+keyset;
			String[] keys = conf.getItemNames(keysetname);
			for (String key:keys) {

				String value = conf.getValueAsString(keysetname, key);
				FreenetURI uri;
				try {
					uri = new FreenetURI(value);
				} catch (MalformedURLException e) {
					Logger.error(this, "Invalid URI '"+value+"'in Keyset '"+keysetname+"' for "+section+"'. Not starting.", e);
					error = true;
					continue;
				}

				if ("strict".equals(key)) {
					server.addStrictFilter(uri);
				} else if ("begin".equals(key)) {
					server.addBeginFilter(uri);
				} else if ("derived".equals(key)) {
					server.addDerivedFilter(uri);
				} else {
					Logger.error(this, "Invalid type '"+key+"'in Keyset '"+keysetname+"' for "+section+"'. Not starting.");
					error = true;
				}
			}
		}
		if (!error && conf.getValueAsBoolean(section, "enabled", false))
			server.start();
	}

}
