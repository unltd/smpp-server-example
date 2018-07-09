# SMPP Server Example

SMPP Server that logs all the traffic to files.

Uses blocking IO and does not scale very well. The task is to rewrite it using Cloudhopper SMPP library (commented out in pom.xml).
Here are CouldHopper examples:
* https://github.com/twitter/cloudhopper-smpp/blob/master/src/test/java/com/cloudhopper/smpp/demo/SimulatorMain.java
* https://github.com/twitter/cloudhopper-smpp/blob/master/src/test/java/com/cloudhopper/smpp/demo/SlowServerMain.java

## Manual testing
```
# send unbind using netcat
echo -e '\x00\x00\x00\x10\x00\x00\x00\x06\x00\x00\x00\x00\x00\x00\x00\x01' | netcat 127.0.0.1 8080 -q -1
```