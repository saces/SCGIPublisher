package de.saces.fnplugins.SCGIPublisher;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Vector;

import de.saces.fnplugins.SCGIPublisher.iniparser.IniParserException;
import de.saces.fnplugins.SCGIPublisher.iniparser.SimpleIniParser;


import freenet.config.InvalidConfigValueException;
import freenet.config.NodeNeedRestartException;
import freenet.config.SubConfig;
import freenet.keys.FreenetURI;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.l10n.PluginL10n;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginBaseL10n;
import freenet.pluginmanager.FredPluginConfigurable;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginRealVersioned;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Logger;
import freenet.support.api.BooleanCallback;
import freenet.support.api.StringCallback;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;

public class SCGIPublisher implements FredPlugin, FredPluginL10n, FredPluginThreadless, FredPluginVersioned, FredPluginRealVersioned, FredPluginConfigurable, FredPluginBaseL10n {

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
	private PluginL10n intl;

	private Vector<SCGIServer> servers;

	private String configFileName;
	private boolean allowConfigFproxy;
	private boolean allowConfigFCP;

	class ConfigFileNameOption extends StringCallback {
		@Override
		public String get() {
			return configFileName;
		}

		@Override
		public void set(String val) throws InvalidConfigValueException,
				NodeNeedRestartException {
			if (!val.equals(get())) {
				configFileName = val;
			}
		}
	}

	class AllowConfigFProxyOption extends BooleanCallback {
		@Override
		public Boolean get() {
			return allowConfigFproxy;
		}

		@Override
		public void set(Boolean val) throws InvalidConfigValueException,
				NodeNeedRestartException {
			if (!val.equals(get())) {
				allowConfigFproxy = val;
			}
		}

		@Override
		public boolean isReadOnly() {
			return true;
		}
	}

	class AllowConfigFCPOption extends BooleanCallback {
		@Override
		public Boolean get() {
			return allowConfigFCP;
		}

		@Override
		public void set(Boolean val) throws InvalidConfigValueException,
				NodeNeedRestartException {
			if (!val.equals(get())) {
				allowConfigFCP = val;
			}
		}

		@Override
		public boolean isReadOnly() {
			return true;
		}
	}

	public void setupConfig(SubConfig subconfig) {
		short sortOrder = 0;
		subconfig.register("configFileName", DEFAULT_CONFIGNAME, sortOrder, true, true, "Config.Filename", "Config.FilenameLong", new ConfigFileNameOption());
		subconfig.register("allowFProxyConfig", true, sortOrder++, true, false, "Config.AllowFProxy", "Config.AllowFProxyLong", new AllowConfigFProxyOption());
		subconfig.register("allowFCPConfig", false, sortOrder++, true, false, "Config.AllowFCP", "Config.AllowFCPLong", new AllowConfigFCPOption());
	}

	public void runPlugin(PluginRespirator pr) {
		if (intl == null) {
			intl = new PluginL10n(this);
		}
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
		String s = intl.getBase().getString(key, true);
		return s != null ? s : key;
	}

	public void setLanguage(LANGUAGE newLanguage) {
		if (intl == null) {
			intl = new PluginL10n(this, newLanguage);
			return;
		}
		intl.getBase().setLanguage(newLanguage);
	}

	public String getVersion() {
		return Version.getLongVersionString();
	}

	public long getRealVersion() {
		return Version.getRealVersion();
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
		String frontpage = conf.getValueAsString(section, "frontpage");

		SCGIServer server = new SCGIServer(pluginContext.hlsc, pluginContext.clientCore.getExecutor(), pluginContext.clientCore.tempBucketFactory);
		servers.add(server);
		server.setAdress(host, port, allowed, false);
		server.setServerpath(serverpath);
		server.setFrontPage(frontpage);

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

	public String getL10nFilesBasePath() {
		return "de/saces/fnplugins/SCGIPublisher/intl/";
	}

	public String getL10nFilesMask() {
		return "${lang}.txt";
	}

	public String getL10nOverrideFilesMask() {
		return "SCGIPublisher.${lang}.override.txt";
	}

	public ClassLoader getPluginClassLoader() {
		return this.getClass().getClassLoader();
	}

}
