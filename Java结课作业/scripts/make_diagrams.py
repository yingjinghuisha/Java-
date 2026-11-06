# -*- coding: utf-8 -*-
"""生成课程报告用架构图、线程模型图、协议帧图、类图、重连状态图。"""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Rectangle
from matplotlib.lines import Line2D

plt.rcParams["font.sans-serif"] = ["Hiragino Sans GB", "Arial Unicode MS", "Songti SC", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "images")
os.makedirs(OUT, exist_ok=True)

BG = "#F4F8FB"
INK = "#102A3A"
MUTED = "#547083"
TEAL = "#0F9E8E"
CYAN = "#1687B8"
BLUE = "#2463A8"
AMBER = "#C77A0A"
RED = "#C2334D"
GREEN = "#198754"
LINE = "#AFC3CF"


def setup(w, h):
    fig, ax = plt.subplots(figsize=(w, h), dpi=180)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis("off")
    fig.patch.set_facecolor(BG)
    ax.set_facecolor(BG)
    return fig, ax


def box(ax, x, y, w, h, title, lines=None, color=CYAN, fill="#FFFFFF", fs=11, tfs=12,
        rounded=1.5, lw=1.3, title_color=None):
    patch = FancyBboxPatch((x, y), w, h, boxstyle=f"round,pad=0.35,rounding_size={rounded}",
                           linewidth=lw, edgecolor=color, facecolor=fill, zorder=2)
    ax.add_patch(patch)
    ax.text(x + w / 2, y + h - 1.7, title, ha="center", va="top", color=title_color or INK,
            fontsize=tfs, fontweight="bold", zorder=3)
    if lines:
        ax.text(x + w / 2, y + h - 4.8, "\n".join(lines), ha="center", va="top",
                color=MUTED, fontsize=fs, linespacing=1.55, zorder=3)
    return patch


def arrow(ax, p1, p2, color=MUTED, lw=1.6, style="-|>", ls="-", label=None, rad=0.0, fs=9):
    arr = FancyArrowPatch(p1, p2, arrowstyle=style, mutation_scale=13, linewidth=lw,
                          color=color, connectionstyle=f"arc3,rad={rad}", linestyle=ls, zorder=1)
    ax.add_patch(arr)
    if label:
        mx, my = (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2
        ax.text(mx, my + 1.3, label, ha="center", va="bottom", fontsize=fs, color=color,
                bbox=dict(boxstyle="round,pad=0.2", fc=BG, ec="none", alpha=0.9), zorder=4)


def section(ax, x, y, w, h, title, color):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.4,rounding_size=2",
                                linewidth=1.1, edgecolor=color, facecolor="none",
                                linestyle=(0, (5, 4)), alpha=0.75, zorder=0))
    ax.text(x + 1.2, y + h - 1.2, title, ha="left", va="top", fontsize=12,
            color=color, fontweight="bold")


# ---------------- 1. 系统架构 ----------------
fig, ax = setup(16, 11)
ax.text(3, 97.5, "智慧矿山瓦斯抽采多传感器监控仿真系统 · 总体架构", fontsize=18, color=INK,
        fontweight="bold", va="top")
ax.text(3, 92.5, "Java Socket / 19 字节工业报文 / 生产者-消费者 / 线程池 / Swing EDT",
        fontsize=10, color=MUTED)

section(ax, 3, 60, 94, 29, "仿真网关服务端（独立进程）", TEAL)
box(ax, 5, 72, 20, 11, "VirtualSensor × 4", ["瓦斯 / 负压 / 流量 / 温度", "独立线程 + 均值回归噪声"], TEAL, "#F3FCFA", fs=8.6)
box(ax, 29, 72, 15, 11, "CyclicBarrier", ["4 路同步采样", "统一时间戳"], TEAL, "#F3FCFA", fs=8.8)
box(ax, 48, 72, 20, 11, "SensorHub", ["事故 / 阈值 / 周期", "4 路数据广播"], TEAL, "#F3FCFA", fs=8.8)
box(ax, 72, 72, 22, 11, "ClientSession + Sender", ["每连接独立发送线程", "有界队列 2048 / 随机丢包"], TEAL, "#F3FCFA", fs=8.6)
box(ax, 5, 61, 20, 9, "GatewayServer", ["ServerSocket accept", "指令解析 / 控制台"], TEAL, "#F3FCFA", fs=8.6, tfs=11)
box(ax, 29, 61, 20, 9, "故障注入", ["drop 0~100%  /  disconnect"], AMBER, "#FFF9EF", fs=8.6, tfs=11)
arrow(ax, (25, 77.5), (29, 77.5), TEAL, lw=1.4)
arrow(ax, (44, 77.5), (48, 77.5), TEAL, lw=1.4)
arrow(ax, (68, 77.5), (72, 77.5), TEAL, lw=1.4)
arrow(ax, (15, 69.5), (15, 72), TEAL, lw=1.2, ls="--")


# network band
ax.add_patch(FancyBboxPatch((29, 49), 42, 6.5, boxstyle="round,pad=0.35,rounding_size=1.5",
                            linewidth=1.4, edgecolor=BLUE, facecolor="#EDF5FC", zorder=2))
ax.text(50, 53.1, "TCP 长连接  ·  19 字节二进制帧  ·  0xAA55  ·  CRC16", ha="center",
        va="center", color=BLUE, fontsize=10.5, fontweight="bold")
ax.text(50, 50.4, "MinePacket：deviceId / type / value(float) / timestamp(long)", ha="center",
        va="center", color=MUTED, fontsize=8.6)
arrow(ax, (85, 72), (71, 55.5), BLUE, lw=1.5)
arrow(ax, (31, 55.5), (19, 61), BLUE, lw=1.5, ls="--")

# client
section(ax, 3, 4, 94, 41, "GUI 监控客户端（主程序）", BLUE)
box(ax, 5, 30, 18, 11, "MineTcpClient", ["网络线程：读 / 拆包 / CRC", "断线检测 + 自动重连"], BLUE, "#F5F9FE", fs=8.7)
box(ax, 28, 30, 14, 11, "DataQueue", ["LinkedBlockingQueue", "容量 5000 / 削峰"], BLUE, "#F5F9FE", fs=8.4)
box(ax, 47, 30, 13, 11, "Dispatcher", ["阻塞 take", "提交业务任务"], BLUE, "#F5F9FE", fs=8.5)
box(ax, 65, 30, 18, 11, "BusinessThreadPool", ["core 3 / max 5", "CallerRunsPolicy 反压"], BLUE, "#F5F9FE", fs=8.5)
box(ax, 5, 14, 18, 11, "ScheduledExecutor", ["心跳 3 s", "离线巡检 2 s / 8 s"], AMBER, "#FFF9EF", fs=8.6)
box(ax, 47, 14, 24, 11, "DeviceManager / AlarmManager", ["ConcurrentHashMap + 读写锁", "告警队列 2048 + 独立分发线程"], RED, "#FFF5F7", fs=8.5)
box(ax, 76, 14, 17, 11, "Swing EDT", ["MainFrame / JTable", "Java2D 曲线 / 巷道图"], TEAL, "#F3FCFA", fs=8.5)

arrow(ax, (23, 35.5), (28, 35.5), BLUE, label="offer", fs=8)
arrow(ax, (42, 35.5), (47, 35.5), BLUE, lw=1.4)
arrow(ax, (60, 35.5), (65, 35.5), BLUE, label="execute", fs=8)
arrow(ax, (74, 30), (65, 25), RED, lw=1.5)
arrow(ax, (71, 19.5), (76, 19.5), RED, label="invokeLater", fs=8)
arrow(ax, (14, 25), (14, 30), AMBER, lw=1.2, ls="--")

ax.text(50, 7.5, "网络线程只做 IO、拆包、CRC 校验；业务处理在线程池；GUI 更新一律通过 SwingUtilities.invokeLater 进入 EDT。",
        ha="center", va="center", fontsize=9.5, color=MUTED)
fig.savefig(os.path.join(OUT, "architecture.png"), bbox_inches="tight", facecolor=BG)
plt.close(fig)

# ---------------- 2. 线程模型 ----------------
fig, ax = setup(16, 10)
ax.text(2, 97, "线程模型与生产者-消费者流水线", fontsize=20, color=INK, fontweight="bold", va="top")
ax.text(2, 92.5, "线程名、队列容量与阻塞点一一对应，便于答辩时说明并发协作关系", fontsize=11, color=MUTED)

lanes = [
    ("采集生产者", 76, TEAL, [
        ("gateway-sensor-GAS-01", "独立线程 · 400 ms 周期"),
        ("gateway-sensor-VAC-02", "独立线程 · CyclicBarrier(4)"),
        ("gateway-sensor-FLOW-03", "统一 roundTimestamp"),
        ("gateway-sensor-TEMP-04", "CyclicBarrier 同步点"),
    ]),
    ("网关发送", 62, CYAN, [
        ("gateway-sender-1", "每连接发送线程"),
        ("LinkedBlockingQueue(2048)", "满则丢弃，保护采集线程"),
        ("Socket OutputStream", "19 B 帧 / 随机丢包"),
        ("gateway-reader-1", "接收心跳 / 指令"),
    ]),
    ("客户端网络", 44, BLUE, [
        ("mine-net-supervisor", "connect + read + reconnect"),
        ("PacketStreamDecoder", "帧头扫描 / 半包缓存 / CRC"),
        ("DataQueueManager(5000)", "offer / drop-oldest"),
        ("mine-net-maintenance", "心跳 3 s / 12 s 超时"),
    ]),
    ("业务消费", 27, RED, [
        ("packet-dispatcher", "take → 按设备串行执行"),
        ("biz-worker-1..5", "core 3 / max 5"),
        ("ArrayBlockingQueue(256)", "CallerRunsPolicy 反压"),
        ("alarm-dispatcher", "告警队列 2048"),
    ]),
    ("UI 渲染", 10, AMBER, [
        ("Swing EDT", "JTable / Java2D / JTextPane"),
        ("SwingUtilities.invokeLater", "后台线程唯一入口"),
        ("javax.swing.Timer", "时钟 / 脉冲动画"),
        ("MainFrame", "只处理按钮与渲染"),
    ]),
]

for title, y, color, items in lanes:
    ax.add_patch(FancyBboxPatch((3, y - 2.2), 94, 10.5, boxstyle="round,pad=0.3,rounding_size=1.2",
                                facecolor="#FFFFFF", edgecolor=color, linewidth=1.2, alpha=0.92))
    ax.text(4.5, y + 7.1, title, fontsize=12.5, color=color, fontweight="bold")
    for i, (name, desc) in enumerate(items):
        x = 4.5 + i * 23.3
        ax.add_patch(FancyBboxPatch((x, y), 21.5, 5.6, boxstyle="round,pad=0.22,rounding_size=1",
                                    facecolor="#F7FAFC", edgecolor=LINE, linewidth=0.9))
        ax.text(x + 1, y + 3.7, name, fontsize=9.2, color=INK, fontweight="bold")
        ax.text(x + 1, y + 1.35, desc, fontsize=8.2, color=MUTED)
        if i < len(items) - 1:
            arrow(ax, (x + 21.5, y + 2.8), (x + 23.2, y + 2.8), color, lw=1.1)
    if y > 10:
        arrow(ax, (50, y - 2.4), (50, y - 5.2), color, lw=1.8, label="队列解耦")

ax.text(50, 4, "阻塞点：DataQueue.take / ArrayBlockingQueue / 告警队列；容量上限是防止 OOM 的关键。",
        ha="center", color=MUTED, fontsize=10)
fig.savefig(os.path.join(OUT, "thread-model.png"), bbox_inches="tight", facecolor=BG)
plt.close(fig)

# ---------------- 3. 协议帧 ----------------
fig, ax = setup(16, 5.6)
ax.text(2, 94, "工业二进制报文帧结构（19 字节，大端序）", fontsize=18, color=INK,
        fontweight="bold", va="top")
fields = [
    ("帧头", "0xAA 0x55", 2, "#0F9E8E"),
    ("deviceId", "short", 2, "#1687B8"),
    ("type", "1 字节", 1, "#2463A8"),
    ("value", "float", 4, "#6C5CE7"),
    ("timestamp", "long (ms)", 8, "#C77A0A"),
    ("CRC", "2 字节", 2, "#C2334D"),
]
byte = 4.45
x0 = 5
y = 40
h = 26
offset = 0
for name, desc, width, color in fields:
    w = width * byte
    ax.add_patch(Rectangle((x0, y), w, h, facecolor=color, edgecolor="white", linewidth=2, alpha=0.88))
    ax.text(x0 + w / 2, y + h / 2 + 2.8, name, ha="center", va="center", color="white",
            fontsize=12, fontweight="bold")
    ax.text(x0 + w / 2, y + h / 2 - 3.2, desc, ha="center", va="center", color="white", fontsize=9)
    ax.text(x0 + w / 2, y + h + 2, f"{offset}", ha="center", color=MUTED, fontsize=9)
    ax.text(x0 + w / 2, y - 3.2, f"{offset + width - 1}", ha="center", color=MUTED, fontsize=9)
    offset += width
    x0 += w
ax.annotate("", xy=(5, y - 8.5), xytext=(5 + 17 * byte, y - 8.5),
            arrowprops=dict(arrowstyle="<->", color=RED, lw=1.6))
ax.text(5 + 8.5 * byte, y - 11.5, "CRC16 覆盖范围：前 17 字节（帧头 ~ timestamp）", ha="center",
        color=RED, fontsize=10.5, fontweight="bold")
ax.text(5, 18, "CRC-16/CCITT-FALSE   poly=0x1021  init=0xFFFF  refin=false  refout=false  xorout=0x0000\n"
               "标准向量：ASCII \"123456789\" → 0x29B1", fontsize=10.5, color=MUTED, linespacing=1.7)
ax.text(5, 8, "type: 0x01 瓦斯  0x02 负压  0x03 流量  0x04 温度  |  0x81 事故  0x82 阈值  0x83 周期  0x84 解除事故  0x90 心跳  0x91 ACK",
        fontsize=9.2, color=INK)
fig.savefig(os.path.join(OUT, "protocol-frame.png"), bbox_inches="tight", facecolor=BG)
plt.close(fig)

# ---------------- 4. 类图 ----------------
fig, ax = setup(16, 10)
ax.text(2, 97, "核心类图（简化）", fontsize=18, color=INK, fontweight="bold", va="top")
ax.text(2, 89.8, "蓝：客户端网络/并发；红：业务与告警；绿：Swing；橙：网关", fontsize=10, color=MUTED)

def cls(x, y, w, h, name, members, color):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.25,rounding_size=1",
                                facecolor="#FFFFFF", edgecolor=color, linewidth=1.2, zorder=2))
    ax.add_patch(FancyBboxPatch((x, y + h - 4.3), w, 4.3, boxstyle="round,pad=0.25,rounding_size=1",
                                facecolor=color, edgecolor=color, linewidth=1.2, zorder=3, alpha=0.92))
    ax.text(x + w / 2, y + h - 2.2, name, ha="center", va="center", color="white",
            fontsize=9.2, fontweight="bold", zorder=4)
    ax.text(x + 1.1, y + h - 5.5, "\n".join(members), ha="left", va="top", fontsize=7.6,
            color=MUTED, linespacing=1.45, zorder=4)

# client
cls(4, 69, 19, 14, "MainFrame", ["JFrame / ClientConnectionListener", "SensorUiListener / AlarmListener",
                                 "SwingUtilities.invokeLater"], TEAL)
cls(27, 69, 19, 14, "ChartPanel / TablePanel", ["Java2D paintComponent", "实时曲线 / JTable 模型"], TEAL)
cls(52, 69, 19, 14, "AlarmLogPanel", ["JTextPane + StyledDocument", "告警红色高亮"], TEAL)
cls(76, 69, 20, 14, "AlarmToast", ["JWindow", "4.5s 自动关闭"], TEAL)
cls(4, 50, 19, 13, "MineTcpClient", ["network thread", "connect / read / reconnect", "PacketStreamDecoder"], BLUE)
cls(27, 50, 19, 13, "DataQueueManager", ["LinkedBlockingQueue(5000)", "offer / drop-oldest"], BLUE)
cls(52, 50, 19, 13, "BusinessThreadPool", ["core 3 / max 5", "ArrayBlockingQueue(256)", "CallerRunsPolicy"], BLUE)
cls(76, 50, 20, 13, "SensorDataService", ["dispatcher + per-device serial", "alarm edge detection"], RED)
cls(4, 30, 19, 13, "DeviceManager", ["ConcurrentHashMap", "markStale / snapshots"], RED)
cls(27, 30, 19, 13, "SensorDevice", ["ReentrantReadWriteLock", "value / threshold / online"], RED)
cls(52, 30, 19, 13, "AlarmManager", ["告警队列 2048", "history / CSV"], RED)
cls(76, 30, 20, 13, "ScheduledExecutor", ["heartbeat 3s", "offline monitor 2s"], AMBER)
# gateway
cls(4, 9, 19, 13, "GatewayServer", ["ServerSocket accept", "sessions / commands"], TEAL)
cls(27, 9, 19, 13, "SensorHub", ["4 worker threads", "CyclicBarrier(4)"], TEAL)
cls(52, 9, 19, 13, "VirtualSensor", ["base + noise", "accident ramp"], TEAL)
cls(76, 9, 20, 13, "ClientSession / Sender", ["bounded send queue", "TCP frame writer"], TEAL)

arrow(ax, (23, 76), (27, 76), TEAL)
arrow(ax, (46, 76), (52, 76), TEAL)
arrow(ax, (71, 76), (76, 76), TEAL)
arrow(ax, (13.5, 69), (13.5, 63), BLUE, label="组合")
arrow(ax, (23, 56.5), (27, 56.5), BLUE)
arrow(ax, (46, 56.5), (52, 56.5), BLUE)
arrow(ax, (71, 56.5), (76, 56.5), BLUE)
arrow(ax, (85.5, 50), (85.5, 43), RED)
arrow(ax, (76, 36.5), (71, 36.5), RED)
arrow(ax, (52, 36.5), (46, 36.5), RED)
arrow(ax, (27, 36.5), (23, 36.5), RED)
arrow(ax, (13.5, 30), (13.5, 23), TEAL)
arrow(ax, (23, 15.5), (27, 15.5), TEAL)
arrow(ax, (46, 15.5), (52, 15.5), TEAL)
arrow(ax, (71, 15.5), (76, 15.5), TEAL)
arrow(ax, (85.5, 30), (85.5, 22), AMBER, ls="--", label="心跳/离线")
ax.text(50, 2.5, "Client 与 Gateway 通过 TCP 19 字节二进制帧通信；两端共用 MinePacket / Crc16Util / PacketStreamDecoder。",
        ha="center", fontsize=9.5, color=MUTED)
fig.savefig(os.path.join(OUT, "class-diagram.png"), bbox_inches="tight", facecolor=BG)
plt.close(fig)

# ---------------- 5. 重连状态图 ----------------
fig, ax = setup(14, 5.2)
ax.text(3, 97, "客户端连接状态机", fontsize=17, color=INK, fontweight="bold", va="top")
ax.text(3, 84, "AtomicBoolean running + AtomicLong generation 防止重复连接与旧线程覆盖新连接",
        fontsize=9.6, color=MUTED)

def state_box(x, y, w, h, text, color):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3,rounding_size=2.2",
                                facecolor="#FFFFFF", edgecolor=color, linewidth=1.6))
    ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=9.8,
            color=color, fontweight="bold", linespacing=1.5)

state_box(6, 48, 17, 13, "DISCONNECTED\n未连接", MUTED)
state_box(30, 48, 17, 13, "CONNECTING\n正在连接", BLUE)
state_box(54, 48, 17, 13, "CONNECTED\n网关在线", GREEN)
state_box(78, 48, 17, 13, "RECONNECTING\n自动重连", AMBER)
arrow(ax, (23, 54.5), (30, 54.5), BLUE, label="start()", fs=8.5)
arrow(ax, (47, 54.5), (54, 54.5), GREEN, label="connect 成功", fs=8.5)
arrow(ax, (71, 54.5), (78, 54.5), AMBER, label="EOF / 超时 / 异常", fs=8)
# reconnect loop routed below, no crossing boxes
arrow(ax, (86.5, 48), (86.5, 35), AMBER, lw=1.4)
arrow(ax, (86.5, 35), (38.5, 35), AMBER, lw=1.4)
arrow(ax, (38.5, 35), (38.5, 48), AMBER, lw=1.4, label="退避 1~5 s 后重试", fs=8.5)
ax.text(50, 22, "读循环阻塞在 Socket InputStream；关闭 Socket 或心跳超过 12 秒触发断开，\n"
                "监视线程按 1→2→3→5 秒退避重连；成功后退避计数清零。\n"
                "任意状态调用 stop() 立即回到 DISCONNECTED，并终止当前监视线程。",
        ha="center", color=MUTED, fontsize=9.2, linespacing=1.6)
fig.savefig(os.path.join(OUT, "reconnect-state.png"), bbox_inches="tight", facecolor=BG)
plt.close(fig)

print("generated:")
for name in ["architecture.png", "thread-model.png", "protocol-frame.png", "class-diagram.png", "reconnect-state.png"]:
    p = os.path.join(OUT, name)
    print(" ", p, os.path.getsize(p))
