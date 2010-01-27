/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.SCGIPublisher.server;

import java.io.IOException;
import java.net.Socket;

/**
 * @author saces
 *
 */
public interface AbstractService {

	public void handle(Socket sock) throws IOException;

}
