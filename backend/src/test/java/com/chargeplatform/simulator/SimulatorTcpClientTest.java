package com.chargeplatform.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class SimulatorTcpClientTest {

    @Test
    void confirmsStartAndUsesStoppedDeviceReading() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            var executor = Executors.newSingleThreadExecutor();
            var serverTask = executor.submit(() -> {
                try (var socket = server.accept();
                     var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
                    sendStatus(writer, "IDLE", "0.000000");
                    assertEquals("START", new ObjectMapper().readTree(reader.readLine()).get("command").asText());
                    sendStatus(writer, "IDLE", "0.000000");
                    Thread.sleep(100);
                    sendStatus(writer, "CHARGING", "0.002000");
                    assertEquals("STOP", new ObjectMapper().readTree(reader.readLine()).get("command").asText());
                    sendStatus(writer, "IDLE", "0.004000");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            SimulatorTcpClient client = new SimulatorTcpClient(new ObjectMapper(), true, "127.0.0.1",
                    server.getLocalPort(), "SIM-PILE-001", "", 100, false, 30000,
                    mock(com.chargeplatform.device.mapper.ConnectorMapper.class),
                    mock(com.chargeplatform.order.mapper.ChargeOrderMapper.class),
                    mock(SimulatorBindingResolver.class));
            try {
                client.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (client.status().devices().get(0).connectors().isEmpty() && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertFalse(client.status().devices().get(0).connectors().isEmpty());
                var started = client.sendAndConfirm("SIM-PILE-001", new SimulatorTcpClient.Command("START", 1), "CHARGING");
                assertEquals("CHARGING", started.status());
                var stopped = client.sendAndConfirm("SIM-PILE-001", new SimulatorTcpClient.Command("STOP", 1), "IDLE");
                assertEquals("0.004000", stopped.energyKwh().toPlainString());
                serverTask.get(3, TimeUnit.SECONDS);
            } finally {
                client.stop();
                executor.shutdownNow();
            }
        }
    }

    private void sendStatus(BufferedWriter writer, String status, String energy) throws Exception {
        writer.write("{\"type\":\"STATUS\",\"deviceId\":\"SIM-PILE-001\",\"connectorId\":1,\"status\":\""
                + status + "\",\"energyKwh\":" + energy + ",\"powerKw\":7.2}");
        writer.newLine();
        writer.flush();
    }
}
