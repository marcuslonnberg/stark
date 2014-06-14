Stark proxy
===========

WIP - Not ready yet

Stark proxy is a reverse proxy that is configurable at runtime and handles authentication of users. 
No requests are sent through before a user have authenticated.
The authentication can be done with OAuth and works seamlessly between different proxies on different domains, 
the user never needs to authenticate more than once.
It provides a simple REST API to configure the proxies at runtime.

API
---

REST API to access proxy configurations.

Configuration key `server.apiHost` is the domain that the API can be accessed on.

GET `api.domain.tld/proxies` - returns all proxies
