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
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
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
    private final ChargeOrderMapper orders; private final ConnectorMapper connectors; private final ChargerMapper chargers; private final StationMapper stations; private final PricePeriodMapper prices; private final ChargeBillingService billing; private final OrderEventPublisher events; private final SimulatorTcpClient simulator;
    @Autowired
    public ChargeOrderService(ChargeOrderMapper orders,ConnectorMapper connectors,ChargerMapper chargers,StationMapper stations,PricePeriodMapper prices,ChargeBillingService billing,OrderEventPublisher events, SimulatorTcpClient simulator){this.orders=orders;this.connectors=connectors;this.chargers=chargers;this.stations=stations;this.prices=prices;this.billing=billing;this.events=events;this.simulator=simulator;}
    public ChargeOrderService(ChargeOrderMapper orders,ConnectorMapper connectors,ChargerMapper chargers,StationMapper stations,PricePeriodMapper prices,ChargeBillingService billing,OrderEventPublisher events){this(orders, connectors, chargers, stations, prices, billing, events, null);}
    @Transactional
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public ChargeOrder start(OrderRequests.Start request){
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
        ChargeOrder order = new ChargeOrder(no,request.stationId(),request.connectorId(),request.plateNumber().trim());
        orders.insert(order);
        notifySimulator("START", order.getConnectorId());
        return order;
    }
    @Transactional
    @CacheEvict(cacheNames = "dashboard", key = "'summary'")
    public ChargeOrder stop(Long id,OrderRequests.Stop request){
        ChargeOrder order=orders.selectByIdForUpdate(id);
        if(order == null || !"CHARGING".equals(order.getStatus())) throw new BusinessException(409,"订单不存在或已经结束");
        LocalDateTime endTime = LocalDateTime.now();
        var result = billing.calculate(order.getStartTime(), endTime, request.energyKwh(), prices.selectByStationId(order.getStationId()));
        order.complete(request.energyKwh().setScale(3,RoundingMode.HALF_UP),result.electricityFee(),result.serviceFee(),endTime);
        Connector connector=connectors.selectById(order.getConnectorId());
        if(connector == null) throw new BusinessException(500,"订单关联的充电枪不存在");
        connector.changeStatus("IDLE"); connectors.updateById(connector);
        orders.updateById(order);
        events.orderCompleted(new OrderCompletedEvent(order.getId(), order.getStationId(), order.getTotalAmount()));
        notifySimulator("STOP", order.getConnectorId());
        return order;
    }
    private void notifySimulator(String command, Long connectorId) {
        if (simulator == null || !simulator.enabled()) return;
        try { simulator.send(new SimulatorTcpClient.Command(command, connectorId == null ? null : connectorId.intValue())); }
        catch (RuntimeException error) { log.warn("虚拟桩命令发送失败，不影响订单结果: command={}, connectorId={}", command, connectorId, error); }
    }
    public List<ChargeOrder> list(){return orders.selectList(new LambdaQueryWrapper<ChargeOrder>().orderByDesc(ChargeOrder::getId));}
    public List<ChargeOrder> listForExport(String plateNumber){
        String keyword = plateNumber == null ? "" : plateNumber.trim();
        return orders.selectList(new LambdaQueryWrapper<ChargeOrder>()
                .like(!keyword.isEmpty(), ChargeOrder::getPlateNumber, keyword)
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
            .orderByDesc(ChargeOrder::getId));
    }
    public ChargeOrder get(Long id){ChargeOrder order=orders.selectById(id);if(order==null) throw new BusinessException(404,"订单不存在");return order;}
}
