package com.infobip;

import org.smpp.*;
import org.smpp.pdu.PDU;

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

                    System.out.println("Got: " + pdu.debugString());

                    PDU resp = null;
                    switch (pdu.getCommandId()) {
                        case Data.ENQUIRE_LINK:
                            resp = PDU.createPDU(Data.ENQUIRE_LINK_RESP);
                            break;

                        case Data.SUBMIT_SM:
                            resp = PDU.createPDU(Data.SUBMIT_SM_RESP);
                            break;

                        case Data.UNBIND:
                            resp = PDU.createPDU(Data.UNBIND_RESP);
                            break;
                    }

                    if (resp != null) {
                        resp.setSequenceNumber(pdu.getSequenceNumber());
                        transmitter.send(pdu);
                        System.out.println("Responded: " + resp.debugString());
                    }
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
    }
}
