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

This setup worked well on a small Gentoo GNU Linux box with port 80 forwarded to the box in my NAT.

I used lighttpd, because I just had an issue with Apache asking for PHP without threads on my Gentoo box and I did not want to resolve that right now. So the choice of lighttpd is 
mostly random. It's just the first thing which came after Apache.

### SCGIPublisher.ini

    [Server_test]
    bindTo=127.0.0.1
    port=1400
    allowedHosts=127.0.0.1
    keysets=saces,gpl,toadflog
    enabled=true
    serverpath=http://localhost/freenet
    ; without a slash, otherwise every link will get an additional / prepended and you get URLs like http://localhost/freenet//USK@...
    ; replace localhost with the server you want to use, for example your dyndns host. SCGIPublisher will use this to rewrite freenet-links.
    
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
    ## Put NO SLASH at the end of /freenet, otherwise the SCGIPublisher gateway throws the error "no @ in this key"
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

#### SSL in lighttpd

    mkdir -p /etc/lighttpd/certs
    cd /etc/lighttpd/certs
    openssl req -new -x509 -keyout lighttpd.pem -out lighttpd.pem -days 365 -nodes
    chmod 400 lighttpd.pem

/etc/lighttpd/lighttpd.conf

    $SERVER["socket"] == ":443" {
      ssl.engine = "enable" 
      ssl.pemfile = "/etc/lighttpd/certs/lighttpd.pem" 
    }



### Usage

- To to http://127.0.0.1:8888/config/de.saces.fnplugins.SCGIPublisher.SCGIPublisher?fproxyAdvancedMode=2
- Hit apply.
- Restart the plugin. If that does not suffice, restart Freenet. I had to do that to make it work!
- Whenever you change the SCGIPublisher.ini, you have to reload the SCGIPublisher plugin.  
  To do so, go to http://127.0.0.1:8888/plugins/ and click the "reload" button of the line showing the SCGIPublisher plugin.

Now I can access the allowed freesites via http://localhost/freenet/[key]

Links within freenet will automatically point to http://localhost/freenet/[link] - if you set a hostname in the serverpath for SCGIPublisher.ini, the links will go to the hostname instead.

If you want to make your gateway more useful with little effort, you can reuse SCGIPublisher.ini.example. It already provides a whitelist of safe sites. Just be sure to replace d6.or.gs with your own domain.
