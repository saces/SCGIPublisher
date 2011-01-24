/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.SCGIPublisher.iniparser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import freenet.support.Logger;
import freenet.support.io.Closer;

/**
 * @author saces
 *
 */
public abstract class AbstractIniParser {

	private boolean _strict;
	private boolean _costum;

	/**
	 * @param strict
	 * @param costum
	 */
	AbstractIniParser(boolean strict, boolean costum) {
		_strict = strict;
		_costum = costum;
	}
	
	/**
	 * begin a new section
	 * @param sectionname
	 */
	protected abstract void onSection(String sectionname) throws IniParserException;

	/**
	 * process a 'item=value' line
	 * @return
	 */
	protected abstract boolean onItem(String name, String value) throws IniParserException;

	/**
	 * process a line
	 * @param line
	 * @throws IOException 
	 */
	protected abstract void processLine(String line) throws IniParserException;

	public void parse(String inifilename) throws IOException, IniParserException {
		parse(new File(inifilename));
	}

	public void parse(File inifile) throws IOException, IniParserException {
		FileReader fr = null;
		BufferedReader br = null;
		try {
			fr = new FileReader(inifile);
			br = new BufferedReader(fr);
			parse(br);
		} finally {
			Closer.close(br);
			Closer.close(fr);
		}
	}

	public void parse(BufferedReader inireader) throws IOException, IniParserException {
		String line;
		boolean goToNextSection = false;
		while ((line = inireader.readLine()) != null) {

			Logger.debug(this, "Recived line: "+line);
			// jump over empty lines
			if (line.length() < 1) continue;

			char first = line.charAt(0);

			// ignore lines beginning with ';', '#'
			if (first == ';' || first == '#') continue;

			if (first == '[') {
				String sectionname = line.substring(1, line.length() -1);
				onSection(sectionname);
			} else {
				if (_costum) {
					processLine(line);
				} else {
					if (goToNextSection) continue;
					int index = line.indexOf('=');
					// no '='
					if (index == -1) {
						if (_strict) 
							throw new IniParserException("No '=' in line: "+line);
						else
							continue;
					}
					goToNextSection = onItem(line.substring(0, index), line.substring(index+1));
				}
			}
			Logger.debug(this, "Processed line: "+line);
		}
	}
}
