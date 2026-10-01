package com.chargeplatform.order.service;

import com.chargeplatform.common.exception.BusinessException;
import com.chargeplatform.device.domain.Connector;
import com.chargeplatform.device.mapper.ConnectorMapper;
import com.chargeplatform.device.mapper.ChargerMapper;
import com.chargeplatform.order.domain.ChargeOrder;
import com.chargeplatform.order.dto.OrderRequests;
import com.chargeplatform.order.mapper.ChargeOrderMapper;
import com.chargeplatform.price.domain.PricePeriod;
import com.chargeplatform.price.mapper.PricePeriodMapper;
import com.chargeplatform.station.mapper.StationMapper;
import com.chargeplatform.order.event.OrderCompletedEvent;
import com.chargeplatform.order.event.OrderEventPublisher;
import com.chargeplatform.simulator.SimulatorTcpClient;
import com.chargeplatform.simulator.SimulatorBindingResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Service
public class ChargeOrderService {
    private static final Logger log = LoggerFactory.getLogger(ChargeOrderService.class);
    private final ChargeOrderMapper orders; private final ConnectorMapper connectors; private final ChargerMapper chargers; private final StationMapper stations; private final PricePeriodMapper prices; private final ChargeBillingService billing; private final OrderEventPublisher events; private final SimulatorTcpClient simulator; private final SimulatorBindingResolver bindings;
    @Autowired
    public ChargeOrderService(ChargeOrderMapper orders,ConnectorMapper connectors,ChargerMapper chargers,StationMapper stations,PricePeriodMapper prices,ChargeBillingService billing,OrderEventPublisher events, SimulatorTcpClient simulator, SimulatorBindingResolver bindings){this.orders=orders;this.connectors=connectors;this.chargers=chargers;this.stations=stations;this.prices=prices;this.billing=billing;this.events=events;this.simulator=simulator;this.bindings=bindings;}
    public ChargeOrderService(ChargeOrderMapper orders,ConnectorMapper connectors,ChargerMapper chargers,StationMapper stations,PricePeriodMapper prices,ChargeBillingService billing,OrderEventPublisher events){this(orders, connectors, chargers, stations, prices, billing, events, null, null);}
    @Transactional
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public ChargeOrder start(OrderRequests.Start request){
        return start(request, null);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public ChargeOrder start(OrderRequests.Start request, String ownerUsername){
        var station = stations.selectById(request.stationId());
        if(station == null) throw new BusinessException(404,"充电站不存在");
        if(!"OPERATING".equals(station.getStatus())) throw new BusinessException(409,"充电站当前未运营");
        Connector connector=connectors.selectByIdForUpdate(request.connectorId());
        if(connector == null) throw new BusinessException(404,"充电枪不存在");
        var charger=chargers.selectById(connector.getChargerId());
        if(charger == null) throw new BusinessException(404,"所属充电桩不存在");
        if(!charger.getStationId().equals(request.stationId())) throw new BusinessException(400,"充电枪不属于所选站点");
        if(!"ONLINE".equals(charger.getStatus())) throw new BusinessException(409,"所属充电桩当前不在线");
        if(!"IDLE".equals(connector.getStatus())||orders.existsByConnectorIdAndStatus(connector.getId(),"CHARGING")) throw new BusinessException(409,"该充电枪当前不可用");
        connector.changeStatus("CHARGING"); connectors.updateById(connector);
        String no="CO"+java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))+ThreadLocalRandom.current().nextInt(100,1000);
        ChargeOrder order = new ChargeOrder(no,request.stationId(),request.connectorId(),request.plateNumber().trim(), Boolean.TRUE.equals(request.testOrder()));
        if (ownerUsername != null) order.assignCustomer(ownerUsername);
        orders.insert(order);
        try {
            confirmSimulatorCommand("START", order.getConnectorId(), "CHARGING");
        } catch (RuntimeException error) {
            // START may have reached the device before its confirmation was lost. Best-effort STOP prevents a ghost session.
            cancelSimulatorStart(order.getConnectorId());
            throw error;
        }
        return order;
    }

    private void cancelSimulatorStart(Long connectorId) {
        if (simulator == null || !simulator.enabled() || bindings == null) return;
        try {
            SimulatorBindingResolver.Binding binding = bindings.resolve(connectorId);
            simulator.sendToDevice(binding.deviceId(), new SimulatorTcpClient.Command("STOP", binding.localConnectorId()));
        } catch (RuntimeException error) {
            log.warn("模拟桩启动失败后的补偿 STOP 未发送成功: connectorId={}", connectorId, error);
        }
    }
    @Transactional
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public ChargeOrder stop(Long id,OrderRequests.Stop request){
        ChargeOrder order=orders.selectByIdForUpdate(id);
        if(order == null || !"CHARGING".equals(order.getStatus())) throw new BusinessException(409,"订单不存在或已经结束");
        Connector connector=connectors.selectById(order.getConnectorId());
        if(connector == null) throw new BusinessException(500,"订单关联的充电枪不存在");
        List<PricePeriod> periods = prices.selectByStationId(order.getStationId());
        LocalDateTime validationTime = LocalDateTime.now();
        if (simulator != null && simulator.enabled()) billing.calculate(order.getStartTime(), validationTime, BigDecimal.ONE, periods);
        BigDecimal energyKwh = settlementEnergy(order, request);
        LocalDateTime endTime = LocalDateTime.now();
        var result = billing.calculate(order.getStartTime(), endTime, energyKwh, periods);
        order.complete(energyKwh.setScale(3,RoundingMode.HALF_UP),result.electricityFee(),result.serviceFee(),endTime);
        connector.changeStatus("IDLE"); connectors.updateById(connector);
        orders.updateById(order);
        events.orderCompleted(new OrderCompletedEvent(order.getId(), order.getStationId(), order.getTotalAmount()));
        return order;
    }
    private BigDecimal settlementEnergy(ChargeOrder order, OrderRequests.Stop request) {
        if (simulator != null && simulator.enabled()) {
            SimulatorBindingResolver.Binding binding = requireBinding(order.getConnectorId());
            SimulatorTcpClient.LiveConnector live = simulator.liveConnector(binding.deviceId(), binding.localConnectorId());
            BigDecimal measured = live.connector().energyKwh();
            if (measured == null || measured.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(409,"模拟桩暂未上报有效充电电量");
            BigDecimal energy = measured;
            if ("CHARGING".equals(live.connector().status())) {
                SimulatorTcpClient.ConnectorSnapshot stopped = confirmSimulatorCommand("STOP", order.getConnectorId(), "IDLE");
                energy = stopped.energyKwh();
            }
            if (energy == null || energy.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(409,"模拟桩停止后未返回有效充电电量");
            return energy;
        }
        if (request == null || request.energyKwh() == null) throw new BusinessException(400,"未启用模拟桩时必须提供结算电量");
        return request.energyKwh();
    }
    private SimulatorTcpClient.ConnectorSnapshot confirmSimulatorCommand(String command, Long connectorId, String expectedStatus) {
        if (simulator == null || !simulator.enabled()) return null;
        SimulatorBindingResolver.Binding binding = requireBinding(connectorId);
        try { return simulator.sendAndConfirm(binding.deviceId(), new SimulatorTcpClient.Command(command, binding.localConnectorId()), expectedStatus); }
        catch (BusinessException error) { throw error; }
        catch (RuntimeException error) {
            log.warn("虚拟桩命令发送失败: command={}, deviceId={}, connectorId={}", command, binding.deviceId(), binding.localConnectorId(), error);
            throw new BusinessException(409,"模拟桩未连接或命令未确认");
        }
    }
    private SimulatorBindingResolver.Binding requireBinding(Long connectorId) {
        if (bindings == null) throw new BusinessException(409,"模拟桩映射服务不可用");
        return bindings.resolve(connectorId);
    }
    public OrderLiveStatus live(Long id) {
        ChargeOrder order = get(id);
        if (simulator == null || !simulator.enabled()) throw new BusinessException(409,"模拟桩适配器未启用");
        SimulatorBindingResolver.Binding binding = requireBinding(order.getConnectorId());
        SimulatorTcpClient.LiveConnector live = simulator.liveConnector(binding.deviceId(), binding.localConnectorId());
        SimulatorTcpClient.ConnectorSnapshot snapshot = live.connector();
        return new OrderLiveStatus(order.getId(), binding.deviceId(), binding.localConnectorId(), snapshot.status(), snapshot.energyKwh(), snapshot.powerKw(), snapshot.voltageV(), snapshot.currentA(), snapshot.lastMessageAt());
    }

    @Scheduled(fixedDelayString = "${app.simulator.reconcile-interval-ms:5000}")
    @Transactional
    public void reconcileSimulatorOrders() {
        if (simulator == null || !simulator.enabled() || bindings == null) return;
        for (ChargeOrder active : orders.selectChargingOrders()) {
            try {
                SimulatorBindingResolver.Binding binding = bindings.resolve(active.getConnectorId());
                SimulatorTcpClient.ConnectorSnapshot snapshot = simulator.liveConnector(binding.deviceId(), binding.localConnectorId()).connector();
                if (snapshot.energyKwh() != null && snapshot.energyKwh().compareTo(BigDecimal.ZERO) > 0) {
                    orders.updateChargingEnergy(active.getConnectorId(), snapshot.energyKwh());
                }
                if ("IDLE".equals(snapshot.status()) && snapshot.energyKwh() != null && snapshot.energyKwh().compareTo(BigDecimal.ZERO) > 0) {
                    log.warn("检测到模拟桩已停止但订单仍在充电，自动结算: orderId={}, deviceId={}, connectorId={}", active.getId(), binding.deviceId(), binding.localConnectorId());
                    stop(active.getId(), null);
                }
            } catch (RuntimeException error) {
                log.debug("模拟桩状态校准暂未完成: orderId={}", active.getId(), error);
            }
        }
    }
    public List<ChargeOrder> list(){return orders.selectList(new LambdaQueryWrapper<ChargeOrder>().eq(ChargeOrder::getArchived, false).orderByDesc(ChargeOrder::getId));}
    public List<ChargeOrder> listForExport(String plateNumber){
        String keyword = plateNumber == null ? "" : plateNumber.trim();
        return orders.selectList(new LambdaQueryWrapper<ChargeOrder>()
                .like(!keyword.isEmpty(), ChargeOrder::getPlateNumber, keyword)
                .eq(ChargeOrder::getArchived, false)
                .orderByDesc(ChargeOrder::getId));
    }
    public byte[] exportCsv(String plateNumber) {
        String header = "订单号,车牌号,站点ID,充电枪ID,状态,开始时间,结束时间,电量(kWh),电费,服务费,总金额\n";
        String rows = listForExport(plateNumber).stream()
                .map(order -> String.join(",", csv(order.getOrderNo()), csv(order.getPlateNumber()), csv(order.getStationId()),
                        csv(order.getConnectorId()), csv(order.getStatus()), csv(order.getStartTime()), csv(order.getEndTime()),
                        csv(order.getEnergyKwh()), csv(order.getElectricityFee()), csv(order.getServiceFee()), csv(order.getTotalAmount())))
                .collect(Collectors.joining("\n"));
        return ("\uFEFF" + header + rows + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).replace("\"", "\"\"");
        return text.contains(",") ? "\"" + text + "\"" : text;
    }
    public Page<ChargeOrder> page(String plateNumber,int page,int size){
        Page<ChargeOrder> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        String keyword = plateNumber == null ? "" : plateNumber.trim();
        return orders.selectPage(result, new LambdaQueryWrapper<ChargeOrder>()
            .like(!keyword.isEmpty(), ChargeOrder::getPlateNumber, keyword)
            .eq(ChargeOrder::getArchived, false)
            .orderByDesc(ChargeOrder::getId));
    }
    public ChargeOrder get(Long id){ChargeOrder order=orders.selectById(id);if(order==null || Boolean.TRUE.equals(order.getArchived())) throw new BusinessException(404,"订单不存在");return order;}
    public ChargeOrder customerOrder(Long id, String username) {
        ChargeOrder order = get(id);
        if (!username.equals(order.getOwnerUsername())) throw new BusinessException(404, "订单不存在");
        return order;
    }
    public List<ChargeOrder> customerOrders(String username) {
        return orders.selectList(new LambdaQueryWrapper<ChargeOrder>()
                .eq(ChargeOrder::getOwnerUsername, username).eq(ChargeOrder::getArchived, false)
                .orderByDesc(ChargeOrder::getId));
    }
    @Transactional
    public ChargeOrder mockPay(Long id, String username, String method) {
        if (!List.of("WECHAT", "ALIPAY").contains(method)) throw new BusinessException(400, "请选择支付方式");
        ChargeOrder order = orders.selectByIdForUpdate(id);
        if (order == null || Boolean.TRUE.equals(order.getArchived()) || !username.equals(order.getOwnerUsername()))
            throw new BusinessException(404, "订单不存在");
        if (!"COMPLETED".equals(order.getStatus())) throw new BusinessException(409, "请先结束充电并结算");
        if ("PAID".equals(order.getPaymentStatus())) return order;
        if (!"UNPAID".equals(order.getPaymentStatus())) throw new BusinessException(409, "该订单无需支付");
        order.payMock(method);
        orders.updateById(order);
        return order;
    }
    @Transactional
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public int clearCompletedTestOrders(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException(400, "请先选择要清理的订单");
        List<Long> distinctIds = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) throw new BusinessException(400, "请先选择要清理的订单");
        List<ChargeOrder> selected = orders.selectBatchIds(distinctIds);
        if (selected.size() != distinctIds.size() || selected.stream().anyMatch(order -> !"COMPLETED".equals(order.getStatus()) || !Boolean.TRUE.equals(order.getTestOrder()) || Boolean.TRUE.equals(order.getArchived()))) {
            throw new BusinessException(409, "只能归档已完成的测试订单，进行中的订单或真实订单不能清理");
        }
        selected.forEach(ChargeOrder::archive);
        selected.forEach(orders::updateById);
        return selected.size();
    }
    public record OrderLiveStatus(Long orderId, String deviceId, Integer connectorId, String status, BigDecimal energyKwh, BigDecimal powerKw, BigDecimal voltageV, BigDecimal currentA, java.time.Instant lastMessageAt) { }
}
