# SKAS Pi Control

This repository holds the code needed to properly shut down the terminal Raspberry Pis for a SKAS[1] shooting range setup.
The Pis are shut down via SSH and the power is controlled via a Shelly Plug S[2].

# Build

To build the application simply execute `mvn package` what produces an executable `.jar` file in the target directory.
To execute this archive simply execute `java -jar <filename>.jar` to start the server.
Afterwards the requests are available as they are configured in the settings.

# Settings

In order for this application to run a `settings.json` file is needed in the same folder as the compiled jar file is saved.
In order to be compatible with as many setups as possible, the relevant parameters can be adjusted accordingly.
An example of such a file can be seen in this repo in the `settings Example.json` file.
All parameters are needed.

# Links

[1] https://kiermeier.solutions/skas.htm
[2] https://shelly.cloud/products/shelly-plug-s-smart-home-automation-device/
