package com.mine.gateway;

import com.mine.common.MinePacket;
import com.mine.common.SensorType;

import java.util.Random;

/**
 * 虚拟传感器：内置基础值 + 均值回归随机噪声，支持一键触发“事故突增”。
 *
 * <p>每个实例由 {@link SensorHub} 创建一个独立线程运行 {@link #run()}，
 * 4 路传感器通过 CyclicBarrier 对齐到同一采样时刻。</p>
 */
public class VirtualSensor implements Runnable {

    private static final long ACCIDENT_RAMP_MS = 2500L;
    private static final long ACCIDENT_HOLD_MS = 9000L;
    private static final long ACCIDENT_RECOVER_MS = 7000L;
    private static final long ACCIDENT_TOTAL_MS = ACCIDENT_RAMP_MS + ACCIDENT_HOLD_MS + ACCIDENT_RECOVER_MS;

    private final SensorHub hub;
    private final int deviceId;
    private final SensorType type;
    private final double baseValue;
    private final double noiseAmplitude;
    private final Random random;

    private double currentValue;
    private double trend;
    private volatile double threshold;

    private boolean accidentActive;
    private long accidentStartMs;
    private double accidentPeak;

    VirtualSensor(SensorHub hub, int deviceId, SensorType type, double baseValue,
                  double noiseAmplitude, long seed) {
        this.hub = hub;
        this.deviceId = deviceId;
        this.type = type;
        this.baseValue = baseValue;
        this.noiseAmplitude = noiseAmplitude;
        this.currentValue = baseValue;
        this.trend = 0.0;
        this.threshold = type.getDefaultThreshold();
        this.random = new Random(seed);
    }

    @Override
    public void run() {
        hub.sensorLoop(this);
    }

    /**
     * 产生一帧遥测数据（由传感器线程调用）。
     * 均值回归避免数值无限漂移；事故期间按“爬升-保持-回落”曲线叠加。
     */
    public synchronized MinePacket nextPacket(long timestamp) {
        double target = baseValue;
        double localNoise = noiseAmplitude;

        if (accidentActive) {
            long elapsed = Math.max(0L, timestamp - accidentStartMs);
            double progress;
            if (elapsed < ACCIDENT_RAMP_MS) {
                progress = elapsed / (double) ACCIDENT_RAMP_MS;
            } else if (elapsed < ACCIDENT_RAMP_MS + ACCIDENT_HOLD_MS) {
                progress = 1.0;
            } else if (elapsed < ACCIDENT_TOTAL_MS) {
                progress = 1.0 - (elapsed - ACCIDENT_RAMP_MS - ACCIDENT_HOLD_MS)
                        / (double) ACCIDENT_RECOVER_MS;
            } else {
                progress = 0.0;
                accidentActive = false;
                GatewayLog.info("SENSOR", type.getDisplayName() + " 事故模拟自动结束，数值恢复常态");
            }
            target = baseValue + (accidentPeak - baseValue) * Math.max(0.0, progress);
            localNoise = noiseAmplitude * 1.4;
        }

        // 二阶趋势 + 随机噪声，既有惯性又不会漂移
        trend = trend * 0.55 + (target - currentValue) * 0.18;
        currentValue += trend + random.nextGaussian() * localNoise;
        if (currentValue < type.getSafeMin() || currentValue > type.getSafeMax()) {
            currentValue = type.clamp(currentValue);
            trend = 0.0;
        }
        double rounded = round(currentValue, type.getDecimals());
        return new MinePacket(deviceId, type.getCode(), (float) rounded, timestamp);
    }

    /** 触发事故：数值在 2.5 秒内爬升到 peak，保持 9 秒后逐步回落。 */
    public synchronized void triggerAccident(double peak) {
        double bounded = type.clamp(peak);
        this.accidentPeak = bounded;
        this.accidentStartMs = System.currentTimeMillis();
        this.accidentActive = true;
        GatewayLog.warn("SENSOR", type.getDisplayName() + " 事故模拟触发，目标峰值 "
                + type.formatWithUnit(bounded));
    }

    public synchronized void resetAccident() {
        if (accidentActive) {
            GatewayLog.info("SENSOR", type.getDisplayName() + " 事故模拟解除，开始自然回落");
        }
        accidentActive = false;
    }

    public synchronized boolean isAccidentActive() {
        return accidentActive;
    }

    public void setThreshold(double threshold) {
        this.threshold = type.clamp(threshold);
    }

    public double getThreshold() {
        return threshold;
    }

    public synchronized double getCurrentValue() {
        return currentValue;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public SensorType getType() {
        return type;
    }

    public double getBaseValue() {
        return baseValue;
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
