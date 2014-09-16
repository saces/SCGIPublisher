/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package de.saces.fnplugins.SCGIPublisher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;

import de.saces.fnplugins.SCGIPublisher.server.AbstractServer;
import de.saces.fnplugins.SCGIPublisher.server.AbstractService;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.filter.ContentFilter;
import freenet.client.filter.UnsafeContentTypeException;
import freenet.client.filter.ContentFilter.FilterStatus;
import freenet.keys.FreenetURI;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.api.Bucket;
import freenet.support.api.BucketFactory;
import freenet.support.io.Closer;
import freenet.support.io.FileUtil;

/**
 * @author saces
 *
 */
public class SCGIServer extends AbstractServer implements AbstractService {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(SCGIServer.class);
	}

	private final HighLevelSimpleClient hlsc;

	private final Vector<Filter> filters;

	private final BucketFactory bucketFactory;

	private PageMaker pageMaker;

	private String mServerPath;

	private interface Filter {
		boolean isListed(FreenetURI uri);
	}

	private class StrictFilter implements Filter {
		final FreenetURI strictURI;
		StrictFilter(FreenetURI uri) {
			strictURI = uri;
		}
		public boolean isListed(FreenetURI uri) {
			return strictURI.equals(uri);
		}
	}

	private class BeginFilter implements Filter {
		final FreenetURI beginURI;
		BeginFilter(FreenetURI uri) {
			beginURI = uri;
		}
		public boolean isListed(FreenetURI uri) {
			if(!beginURI.getKeyType().equals(uri.getKeyType()))
				return false;
			if(!beginURI.isKSK()) {
				if(!Arrays.equals(beginURI.getRoutingKey(), uri.getRoutingKey()))
					return false;
				if(!Arrays.equals(beginURI.getCryptoKey(), uri.getCryptoKey()))
					return false;
			}
			if(!beginURI.getDocName().equals(uri.getDocName()))
				return false;
			return true;
		}
	}

	private class DerivedFilter implements Filter {
		final FreenetURI derivedURI;
		DerivedFilter(FreenetURI uri) {
			derivedURI = uri;
		}
		public boolean isListed(FreenetURI uri) {
			if(!(uri.isSSK() || uri.isUSK()))
				return false;
			if(!Arrays.equals(derivedURI.getRoutingKey(), uri.getRoutingKey()))
				return false;
			if(!Arrays.equals(derivedURI.getCryptoKey(), uri.getCryptoKey()))
				return false;
			return true;
		}
	}

	private class PageMaker {
		private final boolean initOK;
		private final String pageHeader;
		private final String pageBody;
		private final String pageFooter;

		public PageMaker(String filename) {
			if (filename == null || filename.isEmpty()) {
				initOK = false;
				pageHeader = null;
				pageBody = null;
				pageFooter = null;
				return;
			}
			String s;
			try {
				s = FileUtil.readUTF(new File(filename)).toString();
			} catch (FileNotFoundException e) {
				Logger.error(this, "Template file '" + filename + "' not found.", e);
				initOK = false;
				pageHeader = null;
				pageBody = null;
				pageFooter = null;
				return;
			} catch (IOException e) {
				Logger.error(this, "Error while reading template file '" + filename + "'.", e);
				initOK = false;
				pageHeader = null;
				pageBody = null;
				pageFooter = null;
				return;
			}
			String[] sa = s.split("<!-- ERROR_MESSAGE -->", 2);
			pageHeader = sa[0];
			s = sa[1];
			sa = s.split("<!-- KEY_LIST -->", 2);
			pageBody = sa[0];
			pageFooter = sa[1];
			initOK = true;
		}

		private void makeURIList(StringBuilder sb, HashMap<String, String> env) {
			for (Filter filter: filters) {
				if (filter instanceof StrictFilter) {
					sb.append("<li><a href=\"");
					getServerPath(env, sb);
					sb.append(((StrictFilter) filter).strictURI.toString(false, true));
					sb.append("\">");
					sb.append(((StrictFilter) filter).strictURI.toString(false, false));
					sb.append("</a></li>\n");
				}
				if (filter instanceof BeginFilter) {
					sb.append("<li>URIs that begin with ");
					sb.append("<a href=\"");
					getServerPath(env, sb);
					sb.append(((BeginFilter) filter).beginURI.toString(false, true));
					sb.append("\">");
					sb.append(((BeginFilter) filter).beginURI.toString(false, false));
					sb.append("</a></li>\n");
				}
				if (filter instanceof DerivedFilter) {
					sb.append("<li>URIs which are derived from ");
					sb.append(((DerivedFilter) filter).derivedURI.toString(false, false));
					sb.append("</li>\n");
				}
			}
		}

		private void makeInternalPage(OutputStream out, String errorTitle, String errorText, HashMap<String, String> env) throws IOException {
			StringBuilder header = new StringBuilder(256);
			header.append("Status: 200 OK");
			header.append("\r\n");
			header.append("Content-Type: text/html");
			header.append("\r\n");
			header.append("\r\n");
			out.write(header.toString().getBytes("US-ASCII"));
			StringBuilder body = new StringBuilder(4096);
			body.append("<html><head><title>Freenet Gateway</title></head><body>");
			body.append("<h3>");
			if (errorTitle != null)
				body.append(errorTitle);
			else
				body.append("Freenet gateway");
			body.append("</h3><p>");
			if (errorText != null) {
				body.append(errorText);
				body.append("</p><p>");
			}
			body.append("This is a restricted gate into Freenet. You can only request the Freenet URIs listed below.");
			body.append("</p><p>");
			body.append("To get the full satisfaction install <a href=\"http://freenetproject.org\">Freenet</a> and request the Freenet URIs on your own node without the limits here.");
			body.append("</p><p>");
			body.append("This proxy is created by the <a href=\"https://github.com/saces/SCGIPublisher/\">SCGIPublisher-plugin</a>.");
			body.append("</p><p>Allowed Freenet URIs:<ul>\n");

			makeURIList(body, env);

			body.append("</ul></p>");
			body.append("</body></html>");
			out.write(body.toString().getBytes("UTF-8"));
		}

		private void makeTemplatePage(OutputStream out, String errorTitle, String errorText, HashMap<String, String> env) throws IOException {
			StringBuilder header = new StringBuilder(256);
			header.append("Status: 200 OK");
			header.append("\r\n");
			header.append("Content-Type: text/html");
			header.append("\r\n");
			header.append("\r\n");
			out.write(header.toString().getBytes("US-ASCII"));

			StringBuilder html = new StringBuilder(4096);
			html.append(pageHeader);
			
			if (errorTitle != null) {
				html.append(errorTitle);
			}
			if (errorText != null) {
				html.append(errorText);
			}

			html.append(pageBody);

			html.append("<ul>\n");

			makeURIList(html, env);

			html.append("</ul>\n");

			html.append(pageFooter);
			out.write(html.toString().getBytes("UTF-8"));
		}

		private void makePage(OutputStream out, String errorTitle, String errorText, HashMap<String, String> env) throws IOException {
			if (initOK) {
				makeTemplatePage(out, errorTitle, errorText, env);
			} else {
				makeInternalPage(out, errorTitle, errorText, env);
			}
		}

	}

	public SCGIServer(HighLevelSimpleClient client, Executor executor, BucketFactory bf) {
		super("SCGIServer", executor);
		hlsc = client;
		filters = new Vector<Filter>();
		bucketFactory = bf;
	}

	public void setServerpath(String serverpath) {
		if (isRunning())
			throw new IllegalStateException("you cant set serverpath on a running server.");
		mServerPath = serverpath;
	}

	public void setFrontPage(String frontpage) {
		pageMaker = new PageMaker(frontpage);
	}

	public void addStrictFilter(FreenetURI uri) {
		StrictFilter filter = new StrictFilter(uri);
		filters.add(filter);
	}

	public void addBeginFilter(FreenetURI uri) {
		BeginFilter filter = new BeginFilter(uri);
		filters.add(filter);
	}

	public void addDerivedFilter(FreenetURI uri) {
		DerivedFilter filter = new DerivedFilter(uri);
		filters.add(filter);
	}

	@Override
	protected AbstractService getService() {
		return this;
	}

	public void handle(Socket socket) throws IOException {
		if (logMINOR) {
			Logger.minor(this, "Handel socke");
		}
		BufferedInputStream in = new BufferedInputStream(socket.getInputStream(), 4096);
		OutputStream out = socket.getOutputStream();

		HashMap<String, String> env = SCGI.parse(in);

		if (logDEBUG) {
			StringBuilder b = new StringBuilder(100);
			b.append("Got SCGI resquest: \n");
			for (String key : env.keySet()) {
				b.append(key);
				b.append(": ");
				b.append(env.get(key));
				b.append('\n');
			}
			Logger.debug(this, b.toString());
		}

		int length = Integer.parseInt(env.get("CONTENT_LENGTH"));
		if (length > 0)
			FileUtil.skipFully(in, length);

		String method = env.get("REQUEST_METHOD");
		if (!"GET".equalsIgnoreCase(method)) {
			StringBuilder buf = new StringBuilder(1024);
			buf.append("Status: 400 Bad request");
			buf.append("\r\n");
			buf.append("Content-Type: text/plain");
			buf.append("\r\n");
			buf.append("\r\n");
			buf.append("Bad request. Only Method 'GET' allowed.");
			out.write(buf.toString().getBytes("US-ASCII"));
			return;
		}

		String path = env.get("PATH_INFO");
		if (path == null) {
			pageMaker.makePage(out, "Server configuration error", "This server is not properly configured. See readme for details!", env);
			return;
		}
		if ((path.length() == 0) || ("/".equals(path))) {
			welcomePage(out, env);
			return;
		}
		path = path.substring(1);

		if ("test".equals(path)) {
			StringBuilder buf = new StringBuilder(1024);
			buf.append("Status: 200 OK");
			buf.append("\r\n");
			buf.append("Content-Type: text/plain");
			buf.append("\r\n");
			buf.append("\r\n");
			for (String key : env.keySet()) {
				buf.append(key);
				buf.append(": ");
				buf.append(env.get(key));
				buf.append('\n');
			}
			out.write(buf.toString().getBytes("US-ASCII"));
			return;
		}

		FreenetURI furi;
		try {
			furi = new FreenetURI(path);
		} catch (MalformedURLException e) {
			Logger.debug(this, "Malformet key", e);
			invalidURIPage(out, e, env);
			return;
		}

		if (!isWhiteListed(furi)) {
			notWhiteListedPage(out, env);
			return;
		}

		FetchResult fr;
		try {
			fr = hlsc.fetch(furi);
		} catch (FetchException e) {
			Logger.debug(this, "Fetch error: "+e.getLocalizedMessage(), e);
			if (e.mode == FetchException.PERMANENT_REDIRECT || e.mode == FetchException.NOT_ENOUGH_PATH_COMPONENTS) {
				StringBuilder buf = new StringBuilder(1024);
				buf.append("Status: 301 Moved Permanently");
				buf.append("\r\n");
				buf.append("Location: ");
				getServerPath(env, buf);
				buf.append(e.newURI.toString(false, true));
				buf.append("\r\n");
				buf.append("\r\n");
				out.write(buf.toString().getBytes("US-ASCII"));
				return;
			}

			Logger.debug(this, "Fetch error: "+e.getLocalizedMessage() + " "+((e.newURI == null)?"<null>": e.newURI.toString(false, false)), e);
			fetchErrorPage(out, e, env);
			return;
		}

		URI baseURI;
		try {
			//baseURI = new URI(uriMaker(env) + path);
			baseURI = new URI(path);
			//baseURI = new URI("/blahblub");
		} catch (URISyntaxException e) {
			if (logDEBUG) Logger.debug(this, "Error: "+e.getLocalizedMessage(), e);
			errorPage(out, e, env);
			return;
		}

		FilterStatus fs;
		Bucket fOut = bucketFactory.makeBucket(-1);
		try {
			ReadFilterCallback rfc = new ReadFilterCallback(baseURI, null, getServerPath(env));
			fs = ContentFilter.filter(fr.asBucket().getInputStream(), fOut.getOutputStream(), fr.getMimeType(), null, rfc);
		} catch (UnsafeContentTypeException e) {
			if (logDEBUG) Logger.debug(this, "Error: "+e.getLocalizedMessage(), e);
			StringBuilder buf = new StringBuilder(1024);
			buf.append("Status: 200 OK");
			buf.append("\r\n");
			buf.append("Content-Type: application/force-download");
			buf.append("\r\n");
			buf.append("Content-Disposition: attachment; filename=\"" + furi.getPreferredFilename() + '"');
			buf.append("\r\n");
			buf.append("Cache-Control: private");
			buf.append("\r\n");
			buf.append("Content-Transfer-Encoding: binary");
			buf.append("\r\n");
			buf.append("\r\n");
			out.write(buf.toString().getBytes("US-ASCII"));
			FileUtil.copy(fr.asBucket().getInputStream(), out, -1);
			Closer.close(fOut);
			return;
		}

		StringBuilder buf = new StringBuilder(1024);
		buf.append("Status: 200 OK");
		buf.append("\r\n");
		buf.append("Content-Type: ");
		buf.append(fs.mimeType);
		buf.append("\r\n");
		buf.append("\r\n");
		out.write(buf.toString().getBytes("US-ASCII"));
		FileUtil.copy(fOut.getInputStream(), out, -1);
		Closer.close(fOut);
	}

	private String getServerPath(HashMap<String, String> env) {
		if (mServerPath != null) {
			return mServerPath;
		}
		StringBuilder sb = new StringBuilder();
		uriMaker(env, sb);
		return sb.toString();
	}

	private void getServerPath(HashMap<String, String> env, StringBuilder sb) {
		if (mServerPath != null) {
			sb.append(mServerPath);
			return;
		}
		uriMaker(env, sb);
	}

	private void uriMaker(HashMap<String, String> env, StringBuilder sb) {
		sb.append("http");
		if ("on".equals(env.get("HTTPS")))
			sb.append('s');
		sb.append("://");
		sb.append(env.get("HTTP_HOST"));
		sb.append(':');
		sb.append(env.get("SERVER_PORT"));
		sb.append(env.get("SCRIPT_NAME"));
		sb.append('/');
	}

	private boolean isWhiteListed(FreenetURI furi) {
		for (Filter filter:filters) {
			if (filter.isListed(furi)) return true;
		}
		return false;
	}

	private void errorPage(OutputStream out, Exception e, HashMap<String, String> env) throws IOException {
		pageMaker.makePage(out, "Error while prcessing", "An Error occured while fullfilling your request: "+e.getLocalizedMessage(), env);
	}

	
	private void invalidURIPage(OutputStream out, Exception e, HashMap<String, String> env) throws IOException {
		pageMaker.makePage(out, "Not a valid Freenet URI", "The path you requested is not a valid Freenet URI: "+e.getLocalizedMessage(), env);
	}

	private void fetchErrorPage(OutputStream out, FetchException e, HashMap<String, String> env) throws IOException {
		pageMaker.makePage(out, "Error while fetching from Freenet", "An Error occured while fetching the Freenet URI you requested from Freenet network: ("+e.mode+") "+e.getLocalizedMessage(), env);
	}

	
	private void notWhiteListedPage(OutputStream out, HashMap<String, String> env) throws IOException {
		pageMaker.makePage(out, "Not an allowed Freenet URI", "The Freenet URI you requested is not white listed!", env);
	}

	private void welcomePage(OutputStream out, HashMap<String, String> env) throws IOException {
		pageMaker.makePage(out, null, null, env);
	}



}
