# Freenet SCGI Publisher

*Make freenet sites accessible in the general web via a whitelist.*

## How to build

- Adjust the paths to freenet.jar and freenet-ext.jar in build.xml  
  just use the ones in your freenet install folder
- run ant

## how to use

- Add the plugin path which ant gives you at the end as unofficial plugin on  
  <http://127.0.0.1:8888/plugins/>

- adjust (your freenet dir)/SCGIPublisher.ini  
  see <http://127.0.0.1:8888/config/de.saces.fnplugins.SCGIPublisher.SCGIPublisher?fproxyAdvancedMode=2>

- TODO: How to get it to work on a webserver.
