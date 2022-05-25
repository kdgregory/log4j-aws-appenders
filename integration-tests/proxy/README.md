# Setting up a Squid proxy for running integration tests

This directory contains everything that you need to run a Squid proxy to
relay connections to AWS. To use it, you'll need to have Docker installed.


## Building the proxy

```
docker build -t squid-local:latest .
```


## Running the proxy

```
docker run -it --rm -p 3128:3128 squid-local:latest
```


## Verifying the tests

If the tests run, they're able to use the proxy. Looking at the output of the
Squid container, you should see lines like the following:

```
1650370313.309  16084 172.17.0.1 TCP_TUNNEL/200 13653 CONNECT logs.us-east-1.amazonaws.com:443 - HIER_DIRECT/3.236.94.218 -
```


## Shutting down the proxy

Ctrl-C from the window where the proxy is running will suffice. Otherwise use
`docker ps` to identify the container, and `docker kill` to stop it.
