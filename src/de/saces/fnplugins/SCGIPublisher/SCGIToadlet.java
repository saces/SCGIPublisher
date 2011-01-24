package de.saces.fnplugins.SCGIPublisher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageNode;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileUtil;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterfaceToadlet;

public class SCGIToadlet extends WebInterfaceToadlet {

	protected SCGIToadlet(PluginContext pluginContext2) {
		super(pluginContext2, SCGIPublisher.PLUGIN_URI, "");
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if (!normalizePath(request.getPath()).equals("/")) {
			sendErrorPage(ctx, 404, "Not found", "the path '"+uri+"' was not found");
			return;
		}
		makePage(ctx);
	}

	private void makePage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		PageNode pageNode = pluginContext.pageMaker.getPageNode("SCGI Publisher", ctx);
		HTMLNode outer = pageNode.outer;
		HTMLNode contentNode = pageNode.content;

		File conf = new File(SCGIPublisher.DEFAULT_CONFIGNAME);

		if (!conf.exists()) {
			contentNode.addChild(createInfoBox());
			contentNode.addChild(createConfigSampleBox());
		} else {
			contentNode.addChild(createConfigBox(conf));
		}

		writeHTMLReply(ctx, 200, "OK", outer.generate());
	}

	private HTMLNode createInfoBox() {
		InfoboxNode box = pluginContext.pageMaker.getInfobox("SCGI Publisher");
		HTMLNode outer = box.outer;
		HTMLNode content = box.content;

		content.addChild("#", "Expose white listed Freenet URIs via SCGI. Add 'SCGIMount /path/on/server/ 127.0.0.1:1234' to your webserver config for public acess.");
		return outer;
	}

	private HTMLNode createConfigSampleBox() {
		InfoboxNode box = pluginContext.pageMaker.getInfobox("Sample config");
		HTMLNode outer = box.outer;
		HTMLNode content = box.content;

		InputStream is = SCGIPublisher.class.getResourceAsStream('/'+SCGIPublisher.DEFAULT_CONFIGNAME);
		if (is == null) {
			content.addChild("pre", "ERROR: ressource '"+SCGIPublisher.DEFAULT_CONFIGNAME+"' not found.");
			return outer;
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		String s;
		try {
			FileUtil.copy(is, baos, -1);
			s = new String(baos.toByteArray(), "UTF-8");
		} catch (IOException e) {
			Logger.error(this, "Error while reading ressource '"+SCGIPublisher.DEFAULT_CONFIGNAME+"' not found.");
			s = "Error while reading ressource '"+SCGIPublisher.DEFAULT_CONFIGNAME+"' not found.";
		}
		content.addChild("pre", s);
		return outer;
	}

	private HTMLNode createConfigBox(File conf) {
		InfoboxNode box = pluginContext.pageMaker.getInfobox(SCGIPublisher.DEFAULT_CONFIGNAME);
		HTMLNode outer = box.outer;
		HTMLNode content = box.content;

		String s = "<error>";
		try {
			s = FileUtil.readUTF(conf);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		content.addChild("pre", s);
		return outer;
	}

}
