package com.infobip;

import com.cloudhopper.smpp.SmppServerConfiguration;
import com.cloudhopper.smpp.SmppServerHandler;
import com.cloudhopper.smpp.SmppServerSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.impl.DefaultSmppServerCounters;
import com.cloudhopper.smpp.impl.DefaultSmppSession;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.pdu.Pdu;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.PduResponse;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoder;
import com.cloudhopper.smpp.transcoder.DefaultPduTranscoderContext;
import com.cloudhopper.smpp.transcoder.PduTranscoder;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.io.FileUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/*

Start by answering the following questions:
* How does the service scale with the number of clients?
* What exactly is scaling bottleneck for the service with 10k+ clients?
* How should the service be rewritten in order to solve the scaling issues?

Rewrite the service to scale better, using Cloudhopper library (commented out in pom.xml).

 */

public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    // binary log files as in the given example
    private static final File RECEIVED_LOG_FILE = FileUtils.getFile("logs/received.log");
    private static final File SENT_LOG_FILE = FileUtils.getFile("logs/sent.log");

    private final DefaultSmppServer smppServer;

    // logs pdu in the binary format
    private final BiConsumer<PduRequest, PduResponse> pduLogger;
    // helps to encode PDU into binary format
    private static final PduTranscoder TRANSCODER = new DefaultPduTranscoder(new DefaultPduTranscoderContext());

    public Server(BiConsumer<PduRequest, PduResponse> pduLogger) {
        this.pduLogger = pduLogger;
        // create a server configuration
        SmppServerConfiguration configuration = new SmppServerConfiguration();
        configuration.setPort(2776);
        configuration.setNonBlockingSocketsEnabled(true);
        configuration.setDefaultSessionCountersEnabled(true);

        DefaultSmppServerHandler serverHandler = new DefaultSmppServerHandler();
        // create a server, start it up
        smppServer = new DefaultSmppServer(configuration, serverHandler);
    }

    public void start() throws SmppChannelException {
        this.smppServer.start();
    }

    public void stop() {
        this.smppServer.destroy();
    }

    public DefaultSmppServerCounters getCounters() {
        return this.smppServer.getCounters();
    }

    public static void main(String[] args) throws Exception {
        // ensure files can be written and all necessary directories created
        FileUtils.touch(RECEIVED_LOG_FILE);
        FileUtils.touch(SENT_LOG_FILE);

        // a single-thread executor handles binary logging
        ExecutorService pduLoggerExecutor = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("PduLogger")
                        .setDaemon(true)
                        .build()
        );
        try (
                // streams for logging bytes of request and response
                BufferedOutputStream received = new BufferedOutputStream(new FileOutputStream(RECEIVED_LOG_FILE));
                BufferedOutputStream sent = new BufferedOutputStream(new FileOutputStream(SENT_LOG_FILE))
        ) {

            // logs PDU request and response encoded
            BiConsumer<PduRequest, PduResponse> pduLogger = (pduRequest, pduResponse) ->
                    pduLoggerExecutor.execute(() -> {
                        logPdu(received, pduRequest);
                        logPdu(sent, pduResponse);
                    });

            // start server
            Server server = new Server(pduLogger);
            log.info("Starting SMPP server...");
            server.start();
            log.info("SMPP server started");

            System.out.println("Press any key to stop server");
            System.in.read();

            log.info("Stopping SMPP server...");
            server.stop();
            log.info("SMPP server stopped");

            log.info("Server counters: {}", server.getCounters());
        }
    }

    private class DefaultSmppServerHandler implements SmppServerHandler {
        @Override
        public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, BaseBind bindRequest) {
            sessionConfiguration.setName("SMPP-ServerSession-" + sessionConfiguration.getSystemId());
        }

        @Override
        public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) {
            log.info("Session created: {}", sessionId);
            DefaultSmppSession defaultSmppSession = (DefaultSmppSession) session;
            log.info("Client from {} bound.", defaultSmppSession.getChannel().getRemoteAddress());
            session.serverReady(new TestSmppSessionHandler());
        }

        @Override
        public void sessionDestroyed(Long sessionId, SmppServerSession session) {
            log.info("Session destroyed: {}", sessionId);
            DefaultSmppSession defaultSmppSession = (DefaultSmppSession) session;
            log.info("Client from {} is gone.", defaultSmppSession.getChannel().getRemoteAddress());
            // make sure it's really shutdown
            session.destroy();
        }

    }

    private class TestSmppSessionHandler extends DefaultSmppSessionHandler {

        @Override
        public PduResponse firePduRequestReceived(PduRequest pduRequest) {
            PduResponse response = pduRequest.createResponse();
            handle(pduRequest, response);
            return response;
        }
    }

    private void handle(PduRequest request, PduResponse response) {
        pduLogger.accept(request, response);
    }

    private static void logPdu(OutputStream out, Pdu pdu) {
        try {
            out.write(getBytes(pdu));
        } catch (Exception e) {
            log.error("Failed to log pdu {}", pdu, e);
        }
    }

    private static byte[] getBytes(Pdu pdu) throws UnrecoverablePduException, RecoverablePduException {
        ChannelBuffer buffer = TRANSCODER.encode(pdu);
        return buffer.array();
    }
}
