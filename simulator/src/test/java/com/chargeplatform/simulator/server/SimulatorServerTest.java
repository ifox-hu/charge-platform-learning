package com.chargeplatform.simulator.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

class SimulatorServerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendsHelloAndStatusAfterStartCommand() throws Exception {
        try (SimulatorServer server = new SimulatorServer(0, "SIM-TEST", 1)) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1", server.localPort());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                JsonNode hello = mapper.readTree(reader.readLine());
                assertEquals("HELLO", hello.get("type").asText());
                assertEquals("SIM-TEST", hello.get("deviceId").asText());

                writer.write("{\"command\":\"START\",\"connectorId\":1}");
                writer.newLine();
                writer.flush();

                JsonNode status = mapper.readTree(reader.readLine());
                assertEquals("STATUS", status.get("type").asText());
                assertEquals("CHARGING", status.get("status").asText());
                assertEquals(1, status.get("connectorId").asInt());
            }
        }
    }
}
