package com.chargeplatform.simulator;

import com.chargeplatform.common.dto.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {
    private final SimulatorTcpClient client;

    public SimulatorController(SimulatorTcpClient client) {
        this.client = client;
    }

    @GetMapping("/status")
    public ApiResponse<SimulatorTcpClient.FleetStatus> status() {
        return ApiResponse.success(client.status());
    }

    @PostMapping("/command")
    public ApiResponse<Void> command(@Valid @RequestBody CommandRequest request) {
        client.send(new SimulatorTcpClient.Command(request.command(), request.connectorId()));
        return ApiResponse.message("模拟桩命令已发送");
    }

    @PostMapping("/command/{deviceId}")
    public ApiResponse<Void> command(@org.springframework.web.bind.annotation.PathVariable String deviceId,
                                     @Valid @RequestBody CommandRequest request) {
        client.sendToDevice(deviceId, new SimulatorTcpClient.Command(request.command(), request.connectorId()));
        return ApiResponse.message("模拟桩命令已发送");
    }

    public record CommandRequest(@NotBlank String command, Integer connectorId) { }
}
