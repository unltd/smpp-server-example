package com.infobip;

import org.apache.commons.io.FileUtils;
import org.smpp.*;
import org.smpp.pdu.PDU;
import org.smpp.pdu.Request;
import org.smpp.pdu.Response;

import java.io.IOException;

public class Server {
    public static void main(String[] args) throws Exception {
        var serverConnection = new TCPIPConnection(8080);
        serverConnection.open();

        Connection conn;
        while ((conn = serverConnection.accept()) != null) {
            Connection client = conn;
            new Thread(() -> new ServerSession().run(client), "SMPP-ServerSession").run();
        }
    }

    private static class ServerSession {
        void run(Connection conn) {
            System.out.println("Client from " + conn.getAddress() + " bound.");

            var transmitter = new Transmitter(conn);
            var receiver = new Receiver(transmitter, conn);

            receiver.start();

            try {
                PDU pdu;
                do {
                    pdu = receiver.receive(100);
                    if (pdu == null)
                        continue;

                    Request req = Request.class.cast(pdu);
                    Response resp = req.getResponse();

                    handle(req, resp);

                    transmitter.send(resp);
                } while (pdu != null && pdu.getCommandId() != Data.UNBIND);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                System.out.println("Client from " + conn.getAddress() + " is gone.");
                receiver.stop();
                try {
                    conn.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void handle(Request req, Response resp) throws Exception {
            System.out.println("Req: " + req.debugString());
            System.out.println("Resp: " + resp.debugString());

            FileUtils.writeByteArrayToFile(FileUtils.getFile("logs/received.log"), req.getData().getBuffer(), true);
            FileUtils.writeByteArrayToFile(FileUtils.getFile("logs/sent.log"), resp.getData().getBuffer(), true);
        }
    }
}
