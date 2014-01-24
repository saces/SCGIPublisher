# Freenet SCGI Publisher

*Make freenet sites accessible in the general web via a whitelist.*

## How to build

- Adjust the paths to freenet.jar and freenet-ext.jar in build.xml  
  just use the ones in your freenet install folder
- run ant

## how to use

- Add the plugin path which ant gives you at the end as unofficial plugin on  
  <http://127.0.0.1:8888/plugins/>

- adjust SCGIPublisher.ini. Copy it to (your freenet dir)/SCGIPublisher.ini  
  also see <http://127.0.0.1:8888/config/de.saces.fnplugins.SCGIPublisher.SCGIPublisher?fproxyAdvancedMode=2>

- See http://www.jsoftware.com/jwiki/Guides/SCGI for a guide how to get it to work on a webserver. 
  The port is defined in SCGIPublisher.ini

## lighttpd setup which worked for me

### SCGIPublisher.ini

    [Server_test]
    bindTo=127.0.0.1
    port=1400
    allowedHosts=127.0.0.1
    keysets=saces,gpl,toadflog
    enabled=true
    serverpath=http://localhost/freenet
    ; replace localhost with the server you want to use, for example your dyndns host
    
    [Keyset_gpl]  
    strict=KSK@gpl.txt
    
    [Keyset_toadflog]
    ; edition numbers are ignored
    begin=USK@yGvITGZzrY1vUZK-4AaYLgcjZ7ysRqNTMfdcO8gS-LY,-ab5bJVD3Lp-LXEQqBAhJpMKrKJ19RnNaZMIkusU79s,AQACAAE/toad/19/
    
    [Keyset_saces]
    ; any [S|U]SK with this routing and crypto key
    derived=SSK@MYLAnId-ZEyXhDGGbYOa1gOtkZZrFNTXjFl1dibLj9E,Xpu27DoAKKc8b0718E-ZteFrGqCYROe7XBBJI57pB4M,AQACAAE/


### /etc/lighttpd/lighttpd.conf

    # ... stuff I did not touch
    
    server.modules = (
    #    "mod_rewrite",
    #    "mod_redirect",
    #    "mod_alias",
        "mod_access",
    #    "mod_cml",
    #    "mod_trigger_b4_dl",
    #    "mod_auth",
    #    "mod_status",
    #    "mod_setenv",
    #    "mod_proxy",
    #    "mod_simple_vhost",
    #    "mod_evhost",
    #    "mod_userdir",
         "mod_scgi",
    #    "mod_compress",
    #    "mod_ssi",
    #    "mod_usertrack",
    #    "mod_expire",
    #    "mod_secdownload",
    #    "mod_rrdtool",
    #    "mod_webdav",
        "mod_accesslog"
    )
    
    
    # ... stuff I did not touch
    
    #### scgi module
    ## read scgi.txt for more info
    ## Put NO SLASH at the end of /freenet, otherwise it breaks.
        scgi.server = ( "/freenet" =>
          ( "localhost" =>
           ( "host" => "127.0.0.1",
             "port" => 1400,
             "check-local" => "disable",
             "docroot" => "/" # remote server may use 
                              # it's own docroot
           )
          )
        )


### Usage

- To to http://127.0.0.1:8888/config/de.saces.fnplugins.SCGIPublisher.SCGIPublisher?fproxyAdvancedMode=2
- Hit apply.
- Restart Freenet. I had to do that to make it work!

Now I can access the allowed freesites via http://localhost/freenet/[key]
