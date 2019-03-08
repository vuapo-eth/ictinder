# IcTinder

## About

IcTinder is a service maintaining your Ict neighbors. It will add and remove neighbors automatically depending on their
reliability to ensure that you are always well-connected. Sign up and worry less.

## Getting Started

### 1) Sign up

1) Visit the official IOTA Discord
2) Use the `!ictinder` command to start a dialog with the IcTinder bot
3) In the dialog, use `!register` to sign up
4) Keep your Discord ID and Password for later.

### 2) Install the IXI

1) Open your Ict Web GUI (hosted on port 2187 by default).
2) Navigate to **IXI Modules** > **Manage Modules**
3) Click on the **Install Third Party Module** button.
4) Enter `mikrohash/ictinder` and click on **Install**.
5) Refresh the page.
6) Navigate to **IXI Modules** > **IcTinder**
7) Configure IcTinder:
    * `static_neighbors`: In case you have static neighbors you want to keep all the time, put them here
    * `ict_gui_port` and `ict_gui_password`: Part of your Ict configuration.
    * `node_address`: The address your peers can use to connect to your node (format: `HOST:PORT`). The host should never be `localhost` or similar.
    * `discord_id` and `ictinder_password`: You received these values when you signed up with the IcTinder bot.
8) Press the **Save** button.

IcTinder should now be started. Give it a few minutes to find your first neighbor.

## FAQ

### How does it work?

How it works:
* IcTinder.ixi (installed on the Ict node) publishes stats about neighbors to the central API.
* The central API decides how to connect nodes based on the stats reported by their neighbors.
* IcTinder.ixi downloads the neighbors recommended by the central API and connects to them. Rinse repeat.

### Why is it centralized?

In the Internet-of-Things, auto-peering will be limited to devices nearby. IcTinder is only a temporary measure to
simplify the peering process while Ict is running over the cloud-based Internet. Therefore it does not make any sense to
spend too much resources on developing a decentralized peering layer which would not make it into the final version.
IcTinder can be considered an extension of the previous peer-finder bot with the difference that the whole process is
automatized and no manual neighbor adding/removing is required. Additionally, stats reported by neighbors are utilized
to improve the network.

### I'm getting an `SSLHandShakeException`

```
10:57:40.500 ERROR [org.iota.ictinder.IcTinder/IcTinder]   Unexpected issue occurred during syncing. - java.lang.RuntimeException: javax.net.ssl.SSLHandshakeException: Received fatal alert: handshake_failure
	at org.iota.ict.api.HttpGateway.sendRequest(HttpGateway.java:39)
```

If you encounter this message in your Ict log, your JRE or JDK is not supporting the encryption used by our central API.
This can be fixed by installing the [Java Cryptography Extension](https://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html).
Please report if you encountered this issue and whether you were able to fix it.

## Disclaimer

Run this software at your own cost. We are not liable for any damage caused by running this software.
In order to use IcTinder, you will have to sign up at the central IcTinder API. We are not responsible for data breaches.
However, we will give you a custom password instead of asking you for one to minimize any risks. We will only store a
bcrypt hash of your password in our database. Your node address will be exposed to your peers in order to allow them to
connect.