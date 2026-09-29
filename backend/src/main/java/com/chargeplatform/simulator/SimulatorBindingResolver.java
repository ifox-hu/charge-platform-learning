package com.chargeplatform.simulator;

import com.chargeplatform.common.exception.BusinessException;
import com.chargeplatform.device.domain.Charger;
import com.chargeplatform.device.domain.Connector;
import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.device.mapper.ConnectorMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SimulatorBindingResolver {
    private static final Pattern LOCAL_CONNECTOR_SUFFIX = Pattern.compile("(?:-|_)([A-Z]|\\d+)$", Pattern.CASE_INSENSITIVE);

    private final ChargerMapper chargers;
    private final ConnectorMapper connectors;

    public SimulatorBindingResolver(ChargerMapper chargers, ConnectorMapper connectors) {
        this.chargers = chargers;
        this.connectors = connectors;
    }

    public Binding resolve(Long connectorId) {
        Connector connector = connectors.selectById(connectorId);
        if (connector == null) throw new BusinessException(404, "充电枪不存在");
        Charger charger = chargers.selectById(connector.getChargerId());
        if (charger == null) throw new BusinessException(409, "充电枪未关联有效充电桩");
        return new Binding(toDeviceId(charger.getCode()), localConnectorId(connector, connectors.selectByChargerId(charger.getId())), connector.getId());
    }

    public Connector resolveDatabaseConnector(String deviceId, int localConnectorId) {
        Charger charger = findCharger(deviceId);
        if (charger == null) return null;
        List<Connector> rows = connectors.selectByChargerId(charger.getId());
        return rows.stream()
                .filter(connector -> parsedLocalId(connector.getCode()) == localConnectorId)
                .findFirst()
                .orElse(localConnectorId > 0 && localConnectorId <= rows.size() ? rows.get(localConnectorId - 1) : null);
    }

    private Charger findCharger(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return null;
        Charger charger = chargers.selectByCode(deviceId);
        if (charger != null) return charger;
        String code = deviceId.toUpperCase(Locale.ROOT).startsWith("SIM-") ? deviceId.substring(4) : deviceId;
        return chargers.selectByCode(code);
    }

    private int localConnectorId(Connector connector, List<Connector> rows) {
        int parsed = parsedLocalId(connector.getCode());
        if (parsed > 0) return parsed;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).getId().equals(connector.getId())) return index + 1;
        }
        throw new BusinessException(409, "无法确定充电枪在模拟桩中的编号");
    }

    private int parsedLocalId(String code) {
        if (code == null) return -1;
        Matcher matcher = LOCAL_CONNECTOR_SUFFIX.matcher(code.trim());
        if (!matcher.find()) return -1;
        String suffix = matcher.group(1).toUpperCase(Locale.ROOT);
        return Character.isLetter(suffix.charAt(0)) ? suffix.charAt(0) - 'A' + 1 : Integer.parseInt(suffix);
    }

    private String toDeviceId(String chargerCode) {
        if (chargerCode == null || chargerCode.isBlank()) throw new BusinessException(409, "充电桩缺少设备编码");
        String normalized = chargerCode.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("SIM-") ? normalized : "SIM-" + normalized;
    }

    public record Binding(String deviceId, int localConnectorId, Long databaseConnectorId) { }
}
