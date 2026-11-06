package com.mine.client.service;

import com.mine.common.MinePacket;
import com.mine.common.SensorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备管理器：ConcurrentHashMap 维护所有传感器实时状态。
 */
public class DeviceManager {

    private final ConcurrentHashMap<Integer, SensorDevice> deviceMap =
            new ConcurrentHashMap<Integer, SensorDevice>();

    public DeviceManager() {
        for (SensorType type : new SensorType[]{SensorType.GAS, SensorType.VACUUM,
                SensorType.FLOW, SensorType.TEMP}) {
            int id = type.getCode();
            deviceMap.put(id, new SensorDevice(id, type, type.getDefaultThreshold()));
        }
    }

    /**
     * 更新设备；未知类型或时间戳落后的报文返回 null。
     */
    public DeviceSnapshot update(MinePacket packet) {
        if (packet == null) {
            return null;
        }
        SensorType type = SensorType.fromCode(packet.getDataType());
        if (!type.isKnown()) {
            return null;
        }
        SensorDevice device = deviceMap.get(packet.getDeviceId());
        if (device == null) {
            SensorDevice created = new SensorDevice(packet.getDeviceId(), type,
                    type.getDefaultThreshold());
            SensorDevice previous = deviceMap.putIfAbsent(packet.getDeviceId(), created);
            device = previous == null ? created : previous;
        }
        if (device.update(packet.getValue(), packet.getTimestamp(), System.currentTimeMillis())) {
            return device.snapshot();
        }
        return null;
    }

    public DeviceSnapshot getSnapshot(int deviceId) {
        SensorDevice device = deviceMap.get(deviceId);
        return device == null ? null : device.snapshot();
    }

    public double getThreshold(int deviceId) {
        SensorDevice device = deviceMap.get(deviceId);
        return device == null ? 0.0 : device.getThreshold();
    }

    public boolean setThreshold(int deviceId, double threshold) {
        SensorDevice device = deviceMap.get(deviceId);
        if (device == null || Double.isNaN(threshold)) {
            return false;
        }
        device.setThreshold(threshold);
        return true;
    }

    public List<DeviceSnapshot> snapshots() {
        List<DeviceSnapshot> snapshots = new ArrayList<DeviceSnapshot>(deviceMap.size());
        for (Map.Entry<Integer, SensorDevice> entry : deviceMap.entrySet()) {
            snapshots.add(entry.getValue().snapshot());
        }
        Collections.sort(snapshots, new Comparator<DeviceSnapshot>() {
            @Override
            public int compare(DeviceSnapshot a, DeviceSnapshot b) {
                return Integer.compare(a.getDeviceId(), b.getDeviceId());
            }
        });
        return snapshots;
    }

    /** 巡检超时设备，返回本次由在线变为离线的快照。 */
    public List<DeviceSnapshot> markStale(long timeoutMs) {
        List<DeviceSnapshot> changed = new ArrayList<DeviceSnapshot>();
        long now = System.currentTimeMillis();
        for (SensorDevice device : deviceMap.values()) {
            if (device.markOfflineIfStale(now, timeoutMs)) {
                changed.add(device.snapshot());
            }
        }
        return changed;
    }

    /** 连接断开时立即把所有设备置为离线，返回全部快照供 GUI 刷新。 */
    public List<DeviceSnapshot> markAllOffline() {
        for (SensorDevice device : deviceMap.values()) {
            device.markOffline();
        }
        return snapshots();
    }

    public int size() {
        return deviceMap.size();
    }

    public int onlineCount() {
        int count = 0;
        for (SensorDevice device : deviceMap.values()) {
            if (device.snapshot().isOnline()) {
                count++;
            }
        }
        return count;
    }

    public int alarmCount() {
        int count = 0;
        for (SensorDevice device : deviceMap.values()) {
            if (device.snapshot().isAlarm()) {
                count++;
            }
        }
        return count;
    }

    public ConcurrentHashMap<Integer, SensorDevice> getDeviceMap() {
        return deviceMap;
    }
}
