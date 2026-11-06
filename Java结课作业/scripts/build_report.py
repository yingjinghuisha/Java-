# -*- coding: utf-8 -*-
"""生成课程设计报告 DOCX（python-docx）。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING, WD_BREAK
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "智慧矿山瓦斯抽采监控系统-课程设计报告.docx")

TEAL = RGBColor(0x0F, 0x5C, 0x5C)
DARK = RGBColor(0x16, 0x2C, 0x38)
GRAY = RGBColor(0x5A, 0x6E, 0x7A)
LIGHT = RGBColor(0x7A, 0x8E, 0x99)
RED = RGBColor(0xA8, 0x25, 0x35)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)

BODY_FONT = "宋体"
HEAD_FONT = "微软雅黑"
WEST_FONT = "Times New Roman"


def set_run_font(run, name=BODY_FONT, size=10.5, bold=False, color=None, italic=False):
    run.font.name = WEST_FONT if name == BODY_FONT else name
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    if color is not None:
        run.font.color.rgb = color
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), WEST_FONT if name == BODY_FONT else name)
    rfonts.set(qn("w:hAnsi"), WEST_FONT if name == BODY_FONT else name)
    east = name if name in (BODY_FONT, HEAD_FONT) else BODY_FONT
    rfonts.set(qn("w:eastAsia"), east)
    return run


def add_run(paragraph, text, name=BODY_FONT, size=10.5, bold=False, color=None, italic=False):
    run = paragraph.add_run(text)
    return set_run_font(run, name, size, bold, color, italic)


def body(text, indent=True, size=10.5, color=None, spacing=1.5, space_after=0, align=None):
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing = spacing
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.space_before = Pt(0)
    if indent:
        p.paragraph_format.first_line_indent = Pt(size * 2)
    if align is not None:
        p.alignment = align
    add_run(p, text, size=size, color=color)
    return p


def bullet(text, level=0):
    p = doc.add_paragraph(style="List Bullet" if level == 0 else "List Bullet 2")
    p.paragraph_format.line_spacing = 1.35
    p.paragraph_format.space_after = Pt(2)
    add_run(p, text, size=10.5)
    return p


def heading(text, level=1):
    p = doc.add_heading(level=level)
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.space_before = Pt(14 if level == 1 else 10)
    p.paragraph_format.space_after = Pt(6)
    if level == 1:
        add_run(p, text, name=HEAD_FONT, size=15, bold=True, color=TEAL)
    elif level == 2:
        add_run(p, text, name=HEAD_FONT, size=12.5, bold=True, color=DARK)
    else:
        add_run(p, text, name=HEAD_FONT, size=11, bold=True, color=DARK)
    return p


def caption(text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(8)
    add_run(p, text, size=8.8, color=GRAY)
    return p


def figure(relative_path, cap, width_cm=15.8):
    path = os.path.join(ROOT, relative_path)
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(0)
    run = p.add_run()
    run.add_picture(path, width=Cm(width_cm))
    caption(cap)


def shade(cell, color_hex):
    tcPr = cell._tc.get_or_add_tcPr()
    old = tcPr.find(qn("w:shd"))
    if old is not None:
        tcPr.remove(old)
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), color_hex)
    tcPr.append(shd)


def set_cell_text(cell, text, size=9, bold=False, color=None, align=WD_ALIGN_PARAGRAPH.LEFT):
    cell.text = ""
    p = cell.paragraphs[0]
    p.alignment = align
    p.paragraph_format.space_before = Pt(1)
    p.paragraph_format.space_after = Pt(1)
    p.paragraph_format.line_spacing = 1.12
    add_run(p, text, size=size, bold=bold, color=color)
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER


def set_table_borders(table, color="B9CCD5", size=4):
    tbl = table._tbl
    tblPr = tbl.tblPr
    old = tblPr.find(qn("w:tblBorders"))
    if old is not None:
        tblPr.remove(old)
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        el = OxmlElement("w:" + edge)
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), str(size))
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), color)
        borders.append(el)
    tblPr.append(borders)


def repeat_header(row):
    trPr = row._tr.get_or_add_trPr()
    tblHeader = OxmlElement("w:tblHeader")
    tblHeader.set(qn("w:val"), "true")
    trPr.append(tblHeader)


def add_table(headers, rows, widths=None, font_size=8.8, header_fill="D9EDEA"):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = "Table Grid"
    table.autofit = False
    table.allow_autofit = False
    tblW = OxmlElement("w:tblW")
    tblW.set(qn("w:w"), str(int(15.8 / 2.54 * 1440)))
    tblW.set(qn("w:type"), "dxa")
    table._tbl.tblPr.append(tblW)
    set_table_borders(table)
    hdr = table.rows[0]
    repeat_header(hdr)
    for i, text in enumerate(headers):
        set_cell_text(hdr.cells[i], text, size=font_size, bold=True, color=DARK)
        shade(hdr.cells[i], header_fill)
    for r_idx, row in enumerate(rows):
        cells = table.add_row().cells
        for i, text in enumerate(row):
            set_cell_text(cells[i], str(text), size=font_size)
            if r_idx % 2 == 1:
                shade(cells[i], "F6FAFB")
    if widths:
        for row in table.rows:
            for i, width in enumerate(widths):
                row.cells[i].width = Cm(width)
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(4)
    return table


def add_code(code, size=8.3):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(table, "D6E2E8", 4)
    cell = table.rows[0].cells[0]
    shade(cell, "F4F7F9")
    cell.text = ""
    lines = code.strip("\n").split("\n")
    for i, line in enumerate(lines):
        p = cell.paragraphs[0] if i == 0 else cell.add_paragraph()
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.08
        add_run(p, line if line else " ", name="Consolas", size=size, color=DARK)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def add_field(paragraph, instr, placeholder=""):
    def field_run():
        run = paragraph.add_run()
        set_run_font(run, size=9, color=LIGHT)
        return run

    run = field_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    run._r.append(begin)

    run = field_run()
    instr_el = OxmlElement("w:instrText")
    instr_el.set(qn("xml:space"), "preserve")
    instr_el.text = instr
    run._r.append(instr_el)

    run = field_run()
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    run._r.append(separate)

    run = field_run()
    text_el = OxmlElement("w:t")
    text_el.text = placeholder
    run._r.append(text_el)

    run = field_run()
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run._r.append(end)


def add_toc():
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(4)
    add_run(p, "目  录", name=HEAD_FONT, size=16, bold=True, color=TEAL)
    p2 = doc.add_paragraph()
    add_field(p2, ' TOC \\o "1-2" \\h \\z \\u ', "打开文档后请右键选择“更新域”生成目录。")
    note = doc.add_paragraph()
    note.alignment = WD_ALIGN_PARAGRAPH.CENTER
    add_run(note, "（提示：在 Word 中按 Ctrl+A 后 F9，可更新目录与页码）", size=8.5, color=LIGHT)


def page_break():
    doc.add_page_break()


doc = Document()

# 页面与基础样式
section = doc.sections[0]
section.page_width = Cm(21.0)
section.page_height = Cm(29.7)
section.top_margin = Cm(2.4)
section.bottom_margin = Cm(2.3)
section.left_margin = Cm(2.6)
section.right_margin = Cm(2.4)
section.header_distance = Cm(1.2)
section.footer_distance = Cm(1.2)
section.different_first_page_header_footer = True

normal = doc.styles["Normal"]
normal.font.name = WEST_FONT
normal.font.size = Pt(10.5)
normal.element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
normal.paragraph_format.line_spacing = 1.5
normal.paragraph_format.space_after = Pt(0)

for lvl in (1, 2, 3):
    st = doc.styles[f"Heading {lvl}"]
    st.font.name = HEAD_FONT
    st.font.color.rgb = TEAL if lvl == 1 else DARK
    st.font.bold = True
    st.font.size = Pt(15 if lvl == 1 else (12.5 if lvl == 2 else 11))
    st.element.rPr.rFonts.set(qn("w:eastAsia"), HEAD_FONT)
    st.paragraph_format.keep_with_next = True

# 页眉页脚（首页不显示）
header = section.header
hp = header.paragraphs[0]
hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
add_run(hp, "智慧矿山瓦斯抽采多传感器监控仿真系统 · 课程设计报告", size=8.5, color=LIGHT)
hp.paragraph_format.space_after = Pt(2)

footer = section.footer
fp = footer.paragraphs[0]
fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
add_run(fp, "第 ", size=8.5, color=LIGHT)
add_field(fp, " PAGE ", "1")
add_run(fp, " 页 / 共 ", size=8.5, color=LIGHT)
add_field(fp, " NUMPAGES ", "1")
add_run(fp, " 页", size=8.5, color=LIGHT)

# 文档属性
doc.core_properties.title = "基于TCP与Java并发的智慧矿山瓦斯抽采多传感器监控仿真系统"
doc.core_properties.subject = "Java 网络编程与并发课程设计"
doc.core_properties.author = "（请填写姓名）"
doc.core_properties.keywords = "Java, TCP, Socket, 多线程, 线程池, 阻塞队列, ConcurrentHashMap, Swing, CRC16"

# ---------------- 封面 ----------------
cover_space = doc.add_paragraph()
cover_space.paragraph_format.space_after = Pt(36)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(10)
add_run(p, "课 程 设 计 报 告", name=HEAD_FONT, size=26, bold=True, color=TEAL)

line_table = doc.add_table(rows=1, cols=1)
line_table.alignment = WD_TABLE_ALIGNMENT.CENTER
line_table.autofit = False
line_cell = line_table.rows[0].cells[0]
line_cell.width = Cm(12.0)
shade(line_cell, "0F5C5C")
line_cell.text = ""
line_cell.paragraphs[0].paragraph_format.space_before = Pt(0)
line_cell.paragraphs[0].paragraph_format.space_after = Pt(0)
add_run(line_cell.paragraphs[0], " ", size=1)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(52)
p.paragraph_format.space_after = Pt(8)
add_run(p, "基于 TCP 与 Java 并发的", name=HEAD_FONT, size=20, bold=True, color=DARK)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(6)
add_run(p, "智慧矿山瓦斯抽采", name=HEAD_FONT, size=26, bold=True, color=TEAL)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(24)
add_run(p, "多传感器监控仿真系统", name=HEAD_FONT, size=26, bold=True, color=TEAL)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(60)
add_run(p, "—— 仿真网关 · 并发处理 · Swing 实时可视化 ——", name=HEAD_FONT, size=11, color=GRAY)

info = doc.add_table(rows=5, cols=2)
info.alignment = WD_TABLE_ALIGNMENT.CENTER
info.autofit = False
info_data = [
    ("课程名称", "Java 网络编程与并发课程设计"),
    ("学生姓名", "________________"),
    ("学    号", "________________"),
    ("班    级", "________________"),
    ("指导教师", "________________"),
]
for i, (k, v) in enumerate(info_data):
    c0, c1 = info.rows[i].cells
    c0.width = Cm(4.2)
    c1.width = Cm(7.8)
    set_cell_text(c0, k, size=10.5, bold=True, color=DARK)
    set_cell_text(c1, v, size=10.5)
    c0.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.RIGHT
    c1.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.LEFT
    for c in (c0, c1):
        c.paragraphs[0].paragraph_format.space_before = Pt(5)
        c.paragraphs[0].paragraph_format.space_after = Pt(5)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(95)
add_run(p, "2026 年 10 月", size=11, color=GRAY)

page_break()

# ---------------- 摘要 ----------------
heading("摘  要", 1)
body("针对井下瓦斯抽采监测场景中多测点、高频上报、网络抖动与界面实时性等问题，本课程设计实现了一套基于 TCP 与 Java 并发的智慧矿山瓦斯抽采多传感器监控仿真系统。系统由两个独立程序构成：仿真网关服务端使用 ServerSocket 监听指定 IP 与端口，以 4 个独立线程模拟瓦斯、负压、流量、温度传感器，通过 CyclicBarrier 完成同步采样，并以 19 字节自定义二进制报文、CRC16 校验和 TCP 长连接推送数据；Swing 监控客户端采用生产者-消费者模型，由网络线程完成拆包与 CRC 校验，合法报文进入有界阻塞队列，业务线程池并发完成阈值判断、ConcurrentHashMap 设备状态维护与告警边沿检测，最终通过 SwingUtilities.invokeLater 在 EDT 上安全刷新表格、Java2D 曲线、巷道示意图和着色告警日志。系统支持瓦斯事故模拟、阈值与采样周期下发、心跳保活、设备离线判定、断线自动重连、随机丢包和 CSV 导出。自动化测试覆盖协议、拆包、并发容器、线程池饱和、脏报文和真实 TCP 断线重连，共 92 项检查全部通过。")
body("关键词：TCP Socket；Java 多线程；线程池；阻塞队列；ConcurrentHashMap；CRC16；Swing EDT；智慧矿山", indent=False, size=10.5, color=DARK)

page_break()
add_toc()
page_break()

# ---------------- 1 项目背景 ----------------
heading("1  项目背景与意义", 1)
heading("1.1  项目背景", 2)
body("瓦斯是煤矿井下最主要的危险源之一。瓦斯抽采通过在地面或井下布置抽采管路，将煤层中的瓦斯抽出并输送至利用或排放端，是防治瓦斯事故、保障采掘安全的重要措施。抽采效果依赖瓦斯浓度、抽采负压、抽采流量和环境温度等多个参数：浓度反映抽采质量，负压决定抽采动力，流量反映抽采强度，温度则与井下环境及设备状态相关。")
body("真实工业现场通常由井下边缘网关采集传感器数据，再通过工业以太网或环网上传至地面监控中心。课程设计受限于硬件条件，无法接入真实传感器和 PLC，因此需要用软件仿真一个“边缘网关”，在本地或局域网内复现多测点数据上报、远程指令下发、网络异常和告警处置等完整链路。")


heading("1.2  项目目标", 2)
body("本项目旨在综合运用 Java Socket 网络编程、多线程与线程池、并发容器、读写锁、原子类、自定义二进制协议和 Swing GUI，完成一套可运行、可演示、可测试的仿真系统。具体目标包括：")
bullet("仿真网关：支持指定 IP/端口监听、多客户端接入、4 路传感器同步采样、事故模拟、随机丢包与主动断线。")
bullet("可靠通信：自定义 19 字节定长报文，CRC16 校验，处理 TCP 粘包/半包，支持心跳与 ACK。")
bullet("并发处理：网络线程只做 IO，业务线程池消费，有界队列削峰，ConcurrentHashMap 与读写锁维护设备状态。")
bullet("实时可视化：Swing 看板实时刷新传感器表格、瓦斯曲线、巷道示意和告警日志，严格遵循 EDT 单线程规则。")
bullet("工程化验证：提供协议、并发、业务和真实 TCP 联调的自检程序，以及可复现的运行截图。")

heading("1.3  技术路线", 2)
body("项目采用“先协议、再网关、后客户端、最后联调与文档”的开发顺序：首先实现 MinePacket、Crc16Util 与拆包器，保证两端协议一致；随后实现仿真网关与多线程传感器；再完成客户端网络层、并发业务层；最后开发 Swing 界面并进行异常场景测试。整个项目不依赖任何第三方库，构建产物为两个可执行 JAR。")

# ---------------- 2 需求分析 ----------------
heading("2  需求分析", 1)
heading("2.1  功能需求", 2)
add_table(
    ["编号", "功能", "说明"],
    [
        ["F-01", "网关监听", "绑定指定 IP 与端口，支持多个客户端同时接入"],
        ["F-02", "多传感器仿真", "瓦斯、负压、流量、温度 4 路独立线程，周期采样"],
        ["F-03", "同步采样", "CyclicBarrier 控制 4 路同一时刻采样，统一时间戳"],
        ["F-04", "二进制协议", "帧头 0xAA55、设备号、类型、float 值、long 时间戳、CRC16"],
        ["F-05", "客户端接入与拆包", "TCP 长连接，帧头扫描，半包缓存，CRC 校验"],
        ["F-06", "实时看板", "表格、瓦斯曲线、巷道图、告警日志、指标卡"],
        ["F-07", "告警", "阈值判断、超限/恢复边沿、红色日志、弹窗与提示音"],
        ["F-08", "双向指令", "事故、阈值、采样周期、解除事故，网关 ACK"],
        ["F-09", "心跳与离线", "3 秒心跳，8 秒设备离线，12 秒链路超时"],
        ["F-10", "断线重连", "网关关闭/重启后自动退避重连"],
        ["F-11", "故障模拟", "5% 随机丢包、主动断开连接"],
        ["F-12", "数据导出", "告警记录导出 CSV，UTF-8 BOM"],
        ["F-13", "IP 区间校验", "客户端仅允许 172.20.205.0~172.20.224.255 或 127.0.0.1"],
        ["F-14", "工控机源 IP", "每台客户端可绑定本机 IP（Socket.bind），留空自动"],
    ],
    widths=[1.4, 3.4, 11.0],
)

heading("2.2  非功能需求", 2)
body("并发安全：网络、业务、UI 三层线程边界清晰；共享状态使用 ConcurrentHashMap、读写锁和原子类；禁止后台线程直接操作 Swing 组件。")
body("实时性：默认 4 路 × 2Hz 数据下界面流畅；高频 20 帧/秒时仍能正常刷新；曲线只保留并绘制最近 60 秒。")
body("健壮性：脏字节、CRC 错误、短包、粘包、连接中断、网关重启均不应导致客户端崩溃；队列有界，避免 OOM。")
body("可演示性：提供控制台故障命令、一键事故按钮、可视化曲线和告警浮窗，便于课堂答辩。")
body("可维护性：公共协议层独立，客户端按 network/concurrent/service/gui 分包，接口解耦，自检程序可重复运行。")

heading("2.3  运行环境", 2)
add_table(
    ["项目", "要求"],
    [
        ["JDK", "JDK 8 或 JDK 17+（源码按 Java 8 语法编写）"],
        ["构建", "javac/jar 脚本；可选 Maven"],
        ["第三方库", "无"],
        ["操作系统", "macOS / Windows / Linux（Swing 桌面环境）"],
        ["网络", "客户端允许 172.20.205.0~172.20.224.255；本机 127.0.0.1；支持绑定本机源 IP"],
    ],
    widths=[3.8, 12.0],
)

# ---------------- 3 总体设计 ----------------
heading("3  系统总体设计", 1)
heading("3.1  总体架构", 2)
body("系统分为仿真网关与监控客户端两个进程，二者通过 TCP 长连接传输 19 字节二进制帧。网关侧由传感器线程生产数据，经过会话发送队列写入 Socket；客户端侧由网络线程接收、拆包、CRC 校验后写入有界数据队列，业务线程池消费并更新设备状态与告警，最终由 EDT 渲染界面。")
figure("docs/images/architecture.png", "图 3-1  系统总体架构", 15.8)

heading("3.2  模块划分", 2)
add_table(
    ["层次", "模块", "主要职责"],
    [
        ["公共层", "Protocol / MinePacket / Crc16Util / PacketStreamDecoder", "协议常量、编解码、CRC、粘包拆包"],
        ["网关", "GatewayServer / SensorHub / VirtualSensor / ClientSession", "监听、采样、广播、指令、故障模拟"],
        ["客户端网络", "MineTcpClient", "连接、读循环、心跳、超时、重连、发送指令、绑定源 IP"],
        ["客户端并发", "DataQueueManager / BusinessThreadPool", "有界队列削峰、线程池业务处理、反压"],
        ["客户端业务", "SensorDataService / DeviceManager / AlarmManager", "解析、设备状态、告警、离线巡检"],
        ["客户端 GUI", "MainFrame / SensorTablePanel / ChartPanel / MineMapPanel / AlarmLogPanel", "Swing 看板与 Java2D 绘图"],
    ],
    widths=[2.2, 6.8, 6.8],
)

heading("3.3  数据流", 2)
body("一条遥测帧的完整路径如下：")
add_code("""
网关 VirtualSensor 线程
  → MinePacket.encode()（19 B，CRC）
  → ClientSession 发送队列（2048）
  → GatewayPacketSender → TCP
  → 客户端 MineTcpClient 读线程
  → PacketStreamDecoder（半包 / 帧头 / CRC）
  → DataQueueManager（5000）
  → packet-dispatcher（按 deviceId 串行）
  → BusinessThreadPool（3~5 线程）
  → DeviceManager 更新 + 告警边沿判断
  → SwingUtilities.invokeLater
  → EDT：表格、曲线、巷道图、告警日志
""")
body("反方向的控制指令由 GUI 按钮触发，经 MineTcpClient 发送到网关，网关闭环执行后返回 ACK；ACK 不作为业务告警，只在参数面板显示执行结果。")

heading("3.4  核心类关系", 2)
body("公共层 MinePacket/Crc16Util/PacketStreamDecoder 被两端复用；客户端 MainFrame 组合各面板与网络、业务对象；MineTcpClient 通过数据队列把报文交给 SensorDataService，后者使用 BusinessThreadPool、DeviceManager 与 AlarmManager 完成处理；网关侧 GatewayServer 组合 SensorHub、VirtualSensor 与会话对象。")
figure("docs/images/class-diagram.png", "图 3-2  核心类图（简化）", 15.8)

# ---------------- 4 协议 ----------------
heading("4  通信协议设计", 1)
heading("4.1  帧格式", 2)
body("协议采用 19 字节定长帧，全部使用大端序。选择定长帧的原因是工业遥测单帧字段固定，定长可以显著简化拆包逻辑，并让半包缓存上限保持为 18 字节。")
figure("docs/images/protocol-frame.png", "图 4-1  19 字节二进制报文帧结构", 15.8)
add_table(
    ["偏移", "字段", "字节", "说明"],
    [
        ["0", "帧头", "2", "固定 0xAA 0x55"],
        ["2", "deviceId", "2", "1 瓦斯，2 负压，3 流量，4 温度，0 广播"],
        ["4", "type", "1", "01~04 遥测；0x81+ 指令"],
        ["5", "value", "4", "float，大端 IEEE-754"],
        ["9", "timestamp", "8", "long 毫秒时间戳"],
        ["17", "crc", "2", "CRC-16/CCITT-FALSE，覆盖 0~16"],
    ],
    widths=[1.4, 2.8, 1.6, 10.0],
)

heading("4.2  CRC16 校验", 2)
body("采用 CRC-16/CCITT-FALSE，参数为 poly=0x1021、init=0xFFFF、refin=false、refout=false、xorout=0x0000。发送端计算前 17 字节的 CRC 并写入帧尾；接收端重新计算并比较，不一致则抛出 CrcMismatchException 并丢弃该帧。实现使用 256 项查表法，避免逐位循环。")
add_code("""
static {
    for (int i = 0; i < 256; i++) {
        int crc = i << 8;
        for (int bit = 0; bit < 8; bit++) {
            crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) : (crc << 1);
            crc &= 0xFFFF;
        }
        TABLE[i] = crc;
    }
}
public static int calcCrc16(byte[] data, int offset, int len) {
    int crc = 0xFFFF;
    for (int i = offset; i < offset + len; i++) {
        int index = ((crc >>> 8) ^ (data[i] & 0xFF)) & 0xFF;
        crc = ((crc << 8) ^ TABLE[index]) & 0xFFFF;
    }
    return crc;
}
""")
body("标准测试向量 ASCII “123456789” 的 CRC 为 0x29B1，项目自检程序对此进行了断言。")

heading("4.3  控制指令与 ACK", 2)
body("指令复用 19 字节结构：deviceId 表示目标设备，type 为指令码，value 为参数，timestamp 为请求时间。网关 ACK 的 deviceId 回显目标，type=0x91，value=1/0 表示成功/失败，timestamp 回显请求时间，便于客户端关联。")
add_table(
    ["type", "指令", "目标", "参数"],
    [
        ["0x81", "触发事故", "1~4 或 0", "峰值浓度"],
        ["0x82", "修改阈值", "1~4", "新阈值"],
        ["0x83", "修改采样周期", "0", "200~5000 ms"],
        ["0x84", "解除事故", "0 或 1~4", "无"],
        ["0x90", "心跳", "0", "无"],
        ["0x91", "ACK", "回显", "1 成功 / 0 失败"],
    ],
    widths=[1.9, 2.7, 3.0, 8.2],
)

heading("4.4  TCP 粘包/半包处理", 2)
body("TCP 是字节流，不保留消息边界。PacketStreamDecoder 维护一个可增长缓冲区：查找帧头 0xAA55，丢弃前导脏字节；不足 19 字节则等待下一次 read；凑满后做 CRC 校验。CRC 失败时不丢弃整段缓冲，而是只前移 1 字节重新寻找帧头，从而保证后续合法帧仍可被解析。")
add_code("""
while (true) {
    int headerPos = findHeader(start, end);
    if (headerPos < 0) { keepLastAA(); break; }
    if (headerPos > start) { garbageBytes += headerPos - start; start = headerPos; }
    if (end - start < 19) break;            // 半包
    try {
        MinePacket p = MinePacket.decode(buffer, start, 19);
        out.add(p); start += 19;             // 粘包：继续解析
    } catch (CrcMismatchException e) {
        crcErrors++; start++;                // 重同步
    }
}
""")

# ---------------- 5 网关 ----------------
heading("5  仿真网关服务端设计", 1)
heading("5.1  监听与会话", 2)
body("GatewayServer 使用 ServerSocket 绑定指定 IP/端口，accept 循环为每个客户端创建 ClientSession。每个会话拥有独立的 gateway-reader、gateway-sender 线程和容量 2048 的发送队列，因此慢客户端不会反压传感器采集线程。广播时网关遍历在线会话，按会话独立执行随机丢包。")

heading("5.2  多线程传感器与同步采样", 2)
body("4 路传感器分别运行在 gateway-sensor-GAS-01、VAC-02、FLOW-03、TEMP-04 线程中。每轮先睡眠采样周期，再在 CyclicBarrier(4) 上对齐；Barrier 的 action 写入统一的 roundTimestamp，4 个线程随后分别生成数据，因此同一组遥测帧共享时间戳。")
add_code("""
barrier = new CyclicBarrier(4, () -> roundTimestamp = System.currentTimeMillis());
while (running) {
    Thread.sleep(samplePeriodMs);
    barrier.await(12, TimeUnit.SECONDS);
    MinePacket packet = sensor.nextPacket(roundTimestamp);
    publisher.publish(packet);        // 广播到所有客户端
}
""")

heading("5.3  数据模型与事故模拟", 2)
body("传感器采用“均值回归 + 高斯噪声”的模型，避免随机游走导致数值漂移；并对每种类型设置安全上下限。正常情况下瓦斯约 0.35%，负压约 -28 kPa，流量约 26 m³/min，温度约 24 °C。触发事故后，瓦斯按 2.5 秒爬升、9 秒保持、7 秒回落，合计约 18.5 秒，峰值默认 2.80%。")
add_code("""
if (accidentActive) {
    long elapsed = now - accidentStartMs;
    double progress = (elapsed < 2500) ? elapsed / 2500.0
            : (elapsed < 11500) ? 1.0
            : Math.max(0, 1 - (elapsed - 11500) / 7000.0);
    target = baseValue + (accidentPeak - baseValue) * progress;
}
trend = trend * 0.55 + (target - currentValue) * 0.18;
currentValue += trend + random.nextGaussian() * noise;
""")

heading("5.4  指令与故障注入", 2)
body("网关控制台提供 accident、reset、threshold、period、drop、disconnect、status、clients、sensors、quit 等命令。drop 设置随机丢包率；disconnect 主动关闭全部客户端；accident 可指定峰值。指令处理在 GatewayServer.handlePacket 中完成，成功后回 ACK。")
add_table(
    ["控制台命令", "作用"],
    [
        ["accident [峰值]", "触发瓦斯突增事故"],
        ["reset", "解除事故模拟"],
        ["period <ms>", "修改全局采样周期 200~5000 ms"],
        ["threshold <设备号> <阈值>", "修改告警阈值，0 表示全部"],
        ["drop <0~100>", "设置模拟丢包率"],
        ["disconnect", "主动断开所有客户端，测试重连"],
        ["status / clients / sensors", "查看运行统计、连接和传感器状态"],
    ],
    widths=[4.8, 11.0],
)

# ---------------- 6 客户端并发 ----------------
heading("6  客户端并发设计", 1)
heading("6.1  线程模型", 2)
figure("docs/images/thread-model.png", "图 6-1  客户端与网关线程模型", 15.8)
body("客户端将网络 IO、业务处理和界面渲染分离到不同线程：mine-net-supervisor 负责连接与阻塞读；packet-dispatcher 从数据队列取包；biz-worker 执行阈值判断与状态更新；alarm-dispatcher 分发告警；EDT 只做渲染。线程之间通过有界队列和不可变快照交换数据，避免共享可变 UI 状态。")

heading("6.2  网络层与断线重连", 2)
body("MineTcpClient 的读循环只做三件事：读取字节、调用 PacketStreamDecoder、把合法帧 offer 到数据队列。连接状态由 AtomicBoolean running/connected 和 AtomicLong generation 控制；监视线程捕获自己的 generation，快速“断开再连接”时旧线程不会覆盖新连接。")
body("连接前由 IpRange.validateHost 校验目标地址：只允许课程实验网段 172.20.205.0~172.20.224.255，本机联调额外放行 127.0.0.1/localhost；网关的绑定地址不受此限制，仍可监听 0.0.0.0 或本机实际 IP。")
body("每台工控机可在【本机IP】字段或通过 --local-ip 指定自己的源地址。MineTcpClient 在 connect 前调用 Socket.bind(new InetSocketAddress(localIp, 0))，连接成功后再用 getLocalAddress() 校验实际源地址；留空则跟随系统路由。若指定 IP 未配置在网卡上，绑定失败会直接进入 DISCONNECTED 并提示原因，而不是无限重连。这样多台工控机可以分别以 172.20.205.11、172.20.205.12 等源地址接入，网关 console 的 clients 命令可看到不同远端 IP。")
add_code("""
while (isActive(gen)) {
    Socket socket = new Socket();
    try {
        socket.connect(new InetSocketAddress(host, port), 3000);
        notifyState(gen, CONNECTED, "网关连接成功");
        readLoop(gen, socket, input);      // 阻塞读，EOF/异常时退出
    } catch (IOException e) {
        reason = e.getMessage();
    } finally {
        closeQuietly(socket);
        if (this.socket == socket) connected.set(false);
    }
    long backoff = Math.min(5000, 1000L * Math.min(5, ++failures));
    notifyState(gen, RECONNECTING, "第 N 次重连将在 " + backoff / 1000 + " 秒后发起");
    sleepInterruptibly(backoff, gen);
}
""")

body("客户端连接状态在 DISCONNECTED、CONNECTING、CONNECTED、RECONNECTING 之间迁移。断开后按 1→2→3→4→5 秒退避重连；用户主动 stop 会自增代际，旧监视线程立即失效。")
figure("docs/images/reconnect-state.png", "图 6-2  客户端连接状态机", 15.4)

heading("6.3  有界队列与线程池", 2)
body("网络线程使用 offer 而非阻塞 put，队列满时丢弃最旧帧并计数，保证网络线程不会被业务处理拖死。业务线程池 core=3、max=5、队列 256，拒绝策略使用带计数的 CallerRunsPolicy：池饱和时在分发线程执行任务，形成反压。")
add_code("""
executor = new ThreadPoolExecutor(
    3, 5, 60L, TimeUnit.SECONDS,
    new ArrayBlockingQueue<Runnable>(256),
    namedFactory("biz-worker-"),
    new ThreadPoolExecutor.CallerRunsPolicy() {
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            callerRuns.incrementAndGet();
            super.rejectedExecution(r, e);
        }
    });
""")
body("为了避免同一测点的报文被不同工作线程乱序完成，SensorDataService 为每个 deviceId 维护一个串行执行器：同一测点按到达顺序执行，不同测点仍由线程池并行处理。SensorDevice 再用时间戳单调校验拒绝旧帧，形成双保险。")

heading("6.4  设备状态与读写锁", 2)
body("设备表使用 ConcurrentHashMap<Integer, SensorDevice> 保存所有测点；每个 SensorDevice 内部使用 ReentrantReadWriteLock 保护 value、threshold、online、lastUpdate 等多个字段。业务线程写锁更新，GUI 通过 snapshot() 读锁生成不可变 DeviceSnapshot，随后在 EDT 中安全渲染，不再持有锁。")
add_code("""
public boolean update(double newValue, long packetTimestamp, long now) {
    lock.writeLock().lock();
    try {
        if (sampleCount > 0 && packetTimestamp < timestamp) return false; // 拒绝旧帧
        value = newValue; timestamp = packetTimestamp;
        lastUpdateMillis = now; online = true; sampleCount++;
        return true;
    } finally { lock.writeLock().unlock(); }
}
""")

heading("6.5  告警与离线巡检", 2)
body("SensorDataService 维护每设备当前告警布尔值，只在“正常→超限”和“超限→正常”时发布告警，避免持续超限时每帧刷屏。AlarmManager 使用容量 2048 的告警队列和独立 alarm-dispatcher 线程，写入历史记录并回调 GUI。设备离线由 ScheduledExecutorService 每 2 秒巡检，超过 8 秒无上报则标记离线并产生预警。")

# ---------------- 7 GUI ----------------
heading("7  Swing GUI 设计", 1)
heading("7.1  EDT 线程安全原则", 2)
body("Swing 组件只能在事件调度线程（EDT）访问。项目通过 SensorUiListener、AlarmListener 和 ClientConnectionListener 把后台线程与 GUI 解耦，所有回调实现内部统一使用 SwingUtilities.invokeLater。按钮点击中的网络连接、指令发送、文件导出都不在 EDT 执行。")
add_code("""
// 业务线程（biz-worker）
public void onSensorUpdate(DeviceSnapshot snapshot, MinePacket packet, ...) {
    SwingUtilities.invokeLater(() -> {
        tablePanel.updateSnapshot(snapshot);
        chartPanel.addSnapshot(snapshot);
        mapPanel.update(snapshot);
        refreshCards();
    });
}
""")

heading("7.2  主界面与指标卡", 2)
body("主窗口采用深色工业风，顶部为品牌、时钟、连接状态、IP/端口/本机IP 与操作按钮；中部左侧为 4 个指标卡、传感器表格和巷道示意图，右侧为实时曲线和参数下发；底部为告警日志和管线状态条。控制条最右侧提供【退出系统】按钮，并绑定 Ctrl+Q / ⌘Q 快捷键；点击并确认后会依次停止 UI 定时器、网络客户端、业务服务、告警线程与线程池，再释放窗口，适用于没有标题栏关闭按钮的虚拟机环境。")
figure("docs/screenshots/01-dashboard.png", "图 7-1  正常监控界面", 15.8)

heading("7.3  实时表格与曲线", 2)
body("传感器表格包含设备 ID、类型、当前数值、单位、告警阈值、状态、最后上报七列；状态使用自绘徽章（正常/告警/离线），数值按传感器类型着色，行背景交替。曲线使用纯 Java2D 绘制，支持 4 类传感器切换、最近 60 秒窗口、动态 Y 轴、阈值虚线、危险区、告警点与最新值气泡；离线时显示“链路离线 · 等待重连”。")
figure("docs/screenshots/02-gas-alarm.png", "图 7-2  瓦斯超限告警界面", 15.8)

heading("7.4  巷道示意图与告警日志", 2)
body("巷道图将 4 个测点映射到回风巷示意中，正常为绿色、告警为红色脉冲、离线为灰色，并显示每个测点的 tag 与实时数值。告警日志使用 JTextPane + StyledDocument，按严重/预警/恢复/信息分色，自动滚动并限制文档长度。严重告警同时弹出置顶浮窗并响铃，可在参数面板关闭。")
figure("docs/screenshots/03-reconnecting.png", "图 7-3  断线自动重连界面", 15.8)

heading("7.5  参数下发与 CSV 导出", 2)
body("参数面板支持选择目标设备、修改阈值、修改全局采样周期、解除事故和恢复默认阈值。下发前在本地更新阈值并刷新表格和曲线，随后异步发送指令；网关 ACK 返回后显示执行结果。CSV 导出在独立线程执行，使用 UTF-8 BOM，表头包含序号、时间、级别、设备 ID、类型、数值、单位、阈值、告警信息，Excel 打开中文不乱码。")

# ---------------- 8 难点 ----------------
heading("8  关键难点与解决方案", 1)
heading("8.1  Swing 线程违规", 2)
body("问题：网络线程直接更新 JTable、JLabel 会造成随机崩溃、闪烁甚至死锁。解决：定义 UI 回调接口，所有实现内部统一 invokeLater；EDT 不执行网络 IO、文件导出或锁等待；曲线和表格只接收不可变快照。")

heading("8.2  TCP 粘包与半包", 2)
body("问题：TCP 字节流没有消息边界，一次 read 可能包含多个帧或不完整帧。解决：19 字节定长帧 + 帧头扫描 + 半包缓存；CRC 失败时逐字节重同步而不是清空缓冲。该逻辑封装在 PacketStreamDecoder 中，并用 54 项协议自检覆盖。")

heading("8.3  多线程共享状态", 2)
body("问题：HashMap 并发写可能结构损坏，直接暴露可变字段会让 GUI 读到中间状态。解决：ConcurrentHashMap 管理设备表；SensorDevice 使用读写锁保护多字段一致性；snapshot() 在读锁内创建不可变对象。")

heading("8.4  重复连接与旧线程覆盖", 2)
body("问题：快速点击连接/断开可能创建多个 Socket，旧监视线程可能关闭新连接。解决：AtomicBoolean running + AtomicLong generation；旧线程所有赋值和状态通知都校验代际；stop 时自增代际并关闭当前 Socket。")

heading("8.5  高频刷新与 OOM", 2)
body("问题：高频数据会刷爆 EDT、队列无限增长导致内存溢出。解决：数据队列 5000、线程池队列 256、网关发送队列 2048、告警队列 2048；队列满分别丢弃最旧/反压/丢弃；曲线只保留 900 点并只绘制 60 秒；告警边沿去重；CSV、文件操作放到后台线程。")

heading("8.6  设备乱序与告警回退", 2)
body("问题：多个工作线程并发处理同一测点，旧帧可能后完成。解决：按 deviceId 的串行执行器保证同一测点顺序，SensorDevice 再用时间戳单调校验拒绝旧帧；不同设备仍并行处理。")

# ---------------- 9 测试 ----------------
heading("9  测试与结果", 1)
heading("9.1  测试方法", 2)
body("测试分为三层：协议与并发自检（ProtocolSelfTest）、业务层自检（ServiceSelfTest）、真实 TCP 端到端测试（GatewayClientIntegrationTest）。项目不依赖 JUnit，使用普通 main 方法 + 断言，便于在任何 JDK 8/17 环境直接运行。端到端测试真实启动网关、建立 Socket、注入脏报文并重启网关验证自动重连。")
add_code("""
$ ./run-tests.sh
[PASS] ProtocolSelfTest 全部 54 项检查通过
[PASS] ServiceSelfTest 全部 15 项检查通过
[PASS] GatewayClientIntegrationTest 全部 23 项检查通过
全部自检通过。
""")

heading("9.2  测试用例摘要", 2)
add_table(
    ["类别", "用例", "结果"],
    [
        ["协议", "CRC 标准向量、编解码往返、坏 CRC、短包", "通过"],
        ["拆包", "粘包、半包、前导脏字节、CRC 错误重同步", "通过"],
        ["并发", "并发编解码、设备表并发读写、队列容量、线程池饱和", "通过"],
        ["业务", "告警边沿、恢复、ACK、CSV、离线标记", "通过"],
        ["安全边界", "IP 区间边界、越界/非法地址、回环例外", "通过"],
        ["工控机 IP", "绑定本机源 IP、非法本机 IP、重连后保持绑定", "通过"],
        ["联调", "4 类遥测、心跳、阈值/事故指令 ACK", "通过"],
        ["异常", "脏报文注入、网关关闭、自动重连、重启恢复", "通过"],
        ["界面", "正常监控、告警、断线重连、高频刷新", "通过"],
    ],
    widths=[2.2, 11.6, 2.0],
)

heading("9.3  测试结论", 2)
body("自动化测试共 92 项检查全部通过。协议层能够正确处理 CRC 错误和粘包半包；业务层告警边沿和恢复行为符合预期；端到端测试证明客户端可以在网关重启后自动恢复连接并继续接收数据。手工测试中，默认 8 帧/秒和 20 帧/秒高频模式下界面均无卡顿，设备离线、断线重连、告警弹窗和 CSV 导出均正常。")
body("测试环境为 macOS + JDK 17，源码以 Java 8 为目标字节码构建，未使用任何第三方库。详细测试步骤与截图见《测试报告》。")

# ---------------- 10 运行 ----------------
heading("10  运行与演示", 1)
heading("10.1  启动方式", 2)
add_code("""
./build.sh                 # 编译并生成 dist/mine-client.jar、dist/mine-gateway.jar
./run-gateway.sh           # 终端 1：仿真网关，默认 127.0.0.1:8888
./check-network.sh --host 127.0.0.1 --port 8888 --local-ip 127.0.0.1
./run-client.sh            # 终端 2：Swing 客户端
./run-tests.sh             # 运行 92 项自检
""")
body("局域网演示时，网关使用 --host 0.0.0.0 绑定所有网卡，客户端输入网关所在电脑的局域网 IP（必须位于 172.20.205.0~172.20.224.255，或本机 127.0.0.1），并在【本机IP】填写该工控机自己的地址（也可留空自动）；如无法连接，请检查防火墙是否允许 8888/TCP 入站。也可以直接运行 ./run-client-pc.sh 172.20.205.11 172.20.205.10 8888。")
body("连接前建议先在客户端执行 ./check-network.sh --host <网关IP> --port 8888 --local-ip <本机IP>。预检会列出本机 IPv4、检查目标/源 IP 是否在允许区间、尝试 TCP 连接并发送心跳，只有收到 ACK 才返回 [PASS]；失败时根据 [FAIL] 提示修复静态 IP、网关或防火墙。")

heading("10.2  演示步骤", 2)
body("（1）启动网关与客户端，连接后观察 4 路数据、曲线和巷道图；（2）点击“模拟瓦斯事故”，观察表格变红、曲线越限、红色告警与浮窗；（3）点击“解除事故”或等待自动回落，观察恢复记录；（4）在网关控制台执行 disconnect，观察客户端离线与自动重连；（5）修改阈值/采样周期，观察 ACK 与数据变化；（6）导出 CSV。")

# ---------------- 11 总结 ----------------
heading("11  总结与展望", 1)
body("本课程设计完成了一个从协议、仿真网关、TCP 客户端、并发业务层到 Swing 可视化的完整闭环，覆盖了 Java 网络编程与并发编程的主要知识点。系统通过生产者-消费者模型、有界队列、自定义线程池、ConcurrentHashMap、读写锁和原子类保证并发安全；通过 CRC16、帧头拆包、心跳和自动重连保证通信可靠性；通过 invokeLater 和接口解耦严格遵循 Swing EDT 规则。")
body("项目仍有可扩展空间：协议可增加版本号、长度、序列号与 TLS；通信层可迁移到 Netty；设备状态可持久化到时序数据库；告警可支持多级阈值、持续时间与迟滞；界面可增加历史回放和多网关拓扑。后续若接入真实 PLC/OPC-UA 数据源，可作为矿山边缘监控的原型基础。")

# ---------------- 参考文献 ----------------
heading("参考文献", 1)
refs = [
    "[1] Brian Goetz, Tim Peierls, Joshua Bloch, et al. Java Concurrency in Practice[M]. Addison-Wesley, 2006.",
    "[2] Cay S. Horstmann. Core Java Volume I—Fundamentals (11th Edition)[M]. Pearson, 2018.",
    "[3] 周志明. 深入理解Java虚拟机：JVM高级特性与最佳实践（第3版）[M]. 北京: 机械工业出版社, 2019.",
    "[4] 谢希仁. 计算机网络（第8版）[M]. 北京: 电子工业出版社, 2021.",
    "[5] Oracle. Java Platform, Standard Edition Documentation[EB/OL]. https://docs.oracle.com/en/java/.",
]
for ref in refs:
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.75)
    p.paragraph_format.first_line_indent = Cm(-0.75)
    p.paragraph_format.line_spacing = 1.25
    p.paragraph_format.space_after = Pt(3)
    add_run(p, ref, size=9.5)

# ---------------- 附录 ----------------
heading("附录 A  项目结构", 1)
add_code("""
src/main/java/com/mine
├── common/     Protocol, MinePacket, Crc16Util, PacketStreamDecoder, IpRange, SensorType
├── gateway/    GatewayMain, GatewayServer, GatewayConsole, SensorHub,
│               VirtualSensor, ClientSession, GatewayPacketSender
└── client/     MainClient
    ├── network/    MineTcpClient, ConnectionState, ClientStats
    ├── concurrent/ DataQueueManager, BusinessThreadPool
    ├── service/    SensorDataService, DeviceManager, SensorDevice,
    │               AlarmManager, AlarmEvent, CsvExporter
    ├── gui/        MainFrame, SensorTablePanel, ChartPanel, MineMapPanel,
    │               AlarmLogPanel, CommandPanel, UiTheme
    └── tools/      NetworkPreflight, GuiScreenshotTool
src/test/java/com/mine
├── ProtocolSelfTest.java
├── ServiceSelfTest.java
└── GatewayClientIntegrationTest.java
""", size=7.9)

heading("附录 B  核心参数", 1)
add_table(
    ["参数", "值", "说明"],
    [
        ["帧长", "19 B", "固定长度，大端"],
        ["数据队列", "5000", "客户端削峰"],
        ["业务池", "core 3 / max 5", "ArrayBlockingQueue(256)，CallerRuns"],
        ["告警队列", "2048", "独立分发线程"],
        ["网关发送队列", "2048", "每客户端独立"],
        ["心跳", "3 s", "CMD_HEARTBEAT"],
        ["链路超时", "12 s", "无有效帧则重连"],
        ["离线判定", "8 s", "每 2 s 巡检"],
        ["重连退避", "1→2→3→4→5 s", "上限 5 s"],
        ["网关 IP 范围", "172.20.205.0~172.20.224.255", "客户端校验；127.0.0.1 例外"],
        ["本机工控机 IP", "可选 / --local-ip", "Socket.bind 源地址，留空自动"],
        ["曲线窗口", "60 s / 900 点", "Java2D 绘制"],
    ],
    widths=[3.8, 3.8, 8.2],
)

# 更新域设置：打开 Word 时自动更新目录/页码
settings = doc.settings.element
update_fields = OxmlElement("w:updateFields")
update_fields.set(qn("w:val"), "true")
settings.append(update_fields)

doc.save(OUT)
print("saved", OUT, os.path.getsize(OUT))
