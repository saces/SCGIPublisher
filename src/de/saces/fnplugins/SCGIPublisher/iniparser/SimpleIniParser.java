/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.SCGIPublisher.iniparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import freenet.support.Logger;
import freenet.support.SimpleFieldSet;

/**
 * Really simple parser implementation:
 * <li>leading whitespace not allowed
 * <li>comment sign must be the first character in line
 * <li>duplicate section names not allowed
 * <li>duplicate items in a section are not allowed
 * 
 * @author saces
 *
 */
public class SimpleIniParser extends AbstractIniParser {

	private SimpleFieldSet mainSection;
	private HashMap<String, SimpleFieldSet> sections;
	private String currentSection;

	public SimpleIniParser() {
		super(true, false);
		mainSection = null;
		sections = new HashMap<String, SimpleFieldSet>();
		currentSection = null;
	}

	@Override
	protected void onSection(String sectionname) throws IniParserException {
		Logger.debug(this, "New section: " + sectionname);
		if (sectionname.trim().length() == 0) throw new IniParserException("Empty section name not allowed.");
		if (sections.containsKey(sectionname)) throw new IniParserException("Duplicate section name: '"+sectionname+"'.");
		sections.put(sectionname, new SimpleFieldSet(true));
		currentSection = sectionname;
	}

	@Override
	protected boolean onItem(String name, String value) throws IniParserException {
		Logger.debug(this, "New item key='"+name+"' value='"+value+"'");
		SimpleFieldSet section = getCurrentSection(currentSection);
		if (section.get(name) != null) throw new IniParserException("Duplicate item '"+name+"' in "+((currentSection == null)?"main section.":"section '"+name+"'."));
		section.putSingle(name, value);
		return false;
	}

	@Override
	protected void processLine(String line) {}

	private SimpleFieldSet getCurrentSection(String sectionname) {
		if (sectionname == null) {
			if (mainSection == null) {
				mainSection = new SimpleFieldSet(true);
			}
			return mainSection;
		}
		SimpleFieldSet section = sections.get(sectionname);
		if (section != null) return section;
		section = new SimpleFieldSet(true);
		sections.put(sectionname, section);
		return section;
	}

	public String[] getSectionNames() {
		if (sections.isEmpty()) return new String[] {};
		return sections.keySet().toArray(new String[sections.size()]);
	}

	public String[] getItemNames(String sectionname) {
		SimpleFieldSet section = getSection(sectionname);
		if (section == null) return null;
		if (section.isEmpty()) return new String[] {};
		ArrayList<String> result = new ArrayList<String>();
		Iterator<String> ki = section.keyIterator();
		while (ki.hasNext()) {
			result.add(ki.next());
		}
		return result.toArray(new String[result.size()]);
	}

	private SimpleFieldSet getSection(String sectionname) {
		if (sectionname == null)
			return mainSection;
		else
			return sections.get(sectionname);
	}

	public String getValueAsString(String sectionname, String item) {
		SimpleFieldSet section = getSection(sectionname);
		if (section == null) return null;
		return section.get(item);
	}

	public boolean getValueAsBoolean(String sectionname, String item, boolean def) {
		SimpleFieldSet section = getSection(sectionname);
		if (section == null) return def;
		return section.getBoolean(item, def);
	}

	public int getValueAsInt(String sectionname, String item, int def) {
		SimpleFieldSet section = getSection(sectionname);
		if (section == null) return def;
		return section.getInt(item, def);
	}

	void debug() {
		System.out.println("Debug content of: "+this);
		if (mainSection == null)
			System.out.println("No main section.");
		else {
			System.out.println("Main section content:");
			System.out.println(mainSection.toOrderedString());
		}
		Iterator<String> i = sections.keySet().iterator();
		while (i.hasNext()) {
			String key = i.next();
			SimpleFieldSet section = sections.get(key);
			System.out.println("Section: "+key);
			System.out.println(section.toOrderedString());
		}
		System.out.println("End Debug: "+this);
	}

}
