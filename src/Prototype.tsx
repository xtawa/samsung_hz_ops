import { type ComponentType, type CSSProperties, useMemo, useState } from "react";
import {
  ActivityLogIcon,
  ArrowLeftIcon,
  BarChartIcon,
  CheckCircledIcon,
  ChevronRightIcon,
  DesktopIcon,
  DotsHorizontalIcon,
  DotsVerticalIcon,
  GearIcon,
  GlobeIcon,
  LightningBoltIcon,
  Link2Icon,
  ListBulletIcon,
  LockClosedIcon,
  MagnifyingGlassIcon,
  MixerHorizontalIcon,
  MobileIcon,
  MoonIcon,
  ResetIcon,
  RocketIcon,
  StopwatchIcon,
  UpdateIcon,
} from "@radix-ui/react-icons";
import { MobileScroll } from "./mobile";

type IconType = ComponentType<{ width?: string | number; height?: string | number }>;
type TabId = "control" | "rules" | "tools" | "more";
type RefreshMode = "standard" | "adaptive" | "maximum";

type FeatureItem = {
  id: string;
  title: string;
  summary: string;
  icon: IconType;
  value?: string;
  toggle?: boolean;
  enabled?: boolean;
  experimental?: boolean;
};

type FeatureSection = { title: string; items: FeatureItem[] };

const MODES: Array<{ id: RefreshMode; label: string }> = [
  { id: "standard", label: "标准" },
  { id: "adaptive", label: "自适应" },
  { id: "maximum", label: "最高" },
];

const REFRESH_STEPS = [24, 30, 60, 96, 120];

const RULE_SECTIONS: FeatureSection[] = [
  {
    title: "刷新率规则",
    items: [
      { id: "per-app", title: "分应用刷新率", summary: "为游戏、视频和普通应用设置独立策略", icon: MobileIcon, value: "3 个规则" },
      { id: "adaptive-mod", title: "Adaptive Mod", summary: "根据交互、亮度、相机与投屏动态切换", icon: ActivityLogIcon, toggle: true, enabled: true },
      { id: "keep-mode", title: "防重置守护", summary: "系统覆盖刷新率设置时自动恢复", icon: LockClosedIcon, toggle: true, enabled: true },
    ],
  },
  {
    title: "系统状态配置",
    items: [
      { id: "normal-profile", title: "日常模式", summary: "自适应 · 24–120 Hz", icon: MixerHorizontalIcon, value: "已启用" },
      { id: "psm-profile", title: "省电模式", summary: "保持高刷并阻止切回标准模式", icon: LightningBoltIcon, value: "60–120 Hz" },
      { id: "low-battery", title: "低电量", summary: "低于 15% 时应用节能范围", icon: BarChartIcon, value: "24–60 Hz" },
      { id: "screen-off", title: "息屏与 AOD", summary: "息屏、充电与 AOD 独立设置", icon: MoonIcon, value: "1 Hz" },
      { id: "fold-profile", title: "Fold / Flip 双屏", summary: "内屏与外屏使用独立 Profile", icon: DesktopIcon, value: "自动" },
      { id: "auto-psm", title: "息屏自动省电", summary: "锁屏时启用 PSM，解锁后恢复快照", icon: StopwatchIcon, toggle: true, enabled: false },
    ],
  },
];

const TOOL_SECTIONS: FeatureSection[] = [
  {
    title: "显示与流畅度",
    items: [
      { id: "resolution", title: "分辨率切换", summary: "HD+、FHD+、QHD+", icon: DesktopIcon, value: "QHD+" },
      { id: "aod-hz", title: "熄屏与 AOD 刷新率", summary: "熄屏时使用设备最低刷新率", icon: MoonIcon, value: "1 Hz" },
      { id: "hz-monitor", title: "刷新率监视器", summary: "悬浮窗、常驻通知与快速设置磁贴", icon: BarChartIcon, value: "关闭" },
      { id: "animation", title: "动画速度", summary: "窗口、过渡与动画程序", icon: RocketIcon, value: "0.5×" },
    ],
  },
  {
    title: "电量与后台",
    items: [
      { id: "quick-doze", title: "Quick Doze", summary: "提前进入空闲模式并管理白名单与历史", icon: MoonIcon, toggle: true, enabled: false },
      { id: "battery-protect", title: "电池保护", summary: "限充、计划、暂停充电与直通供电", icon: LightningBoltIcon, value: "85%" },
      { id: "auto-sync", title: "自动同步", summary: "息屏时暂停，解锁后恢复原状态", icon: UpdateIcon, toggle: true, enabled: true },
      { id: "sensors-off", title: "自动关闭传感器", summary: "通过开发者磁贴执行的 One UI 兼容方案", icon: ActivityLogIcon, toggle: true, enabled: false, experimental: true },
    ],
  },
  {
    title: "系统行为",
    items: [
      { id: "force-resizable", title: "强制可调整窗口", summary: "允许所有应用在多窗口中调整大小", icon: DesktopIcon, toggle: true, enabled: false },
      { id: "network-speed", title: "网速指示器", summary: "悬浮显示实时上传与下载速度", icon: GlobeIcon, toggle: true, enabled: true },
      { id: "developer-tile", title: "开发者与调试磁贴", summary: "快速切换常用开发者选项", icon: GearIcon, value: "2 个" },
    ],
  },
];

const MORE_SECTIONS: FeatureSection[] = [
  {
    title: "运行状态",
    items: [
      { id: "master-switch", title: "主开关", summary: "暂停或恢复所有自动化规则", icon: LightningBoltIcon, toggle: true, enabled: true },
      { id: "privileges", title: "特权与兼容性", summary: "安全设置权限已授予 · Shizuku 已就绪", icon: LockClosedIcon, value: "正常" },
      { id: "device-capability", title: "设备能力", summary: "刷新率、分辨率、Fold 与电池节点探测", icon: MobileIcon, value: "Galaxy" },
      { id: "xposed", title: "Root / LSPosed 绕过", summary: "One UI 6.1 及以下的 60 Hz vote 绕过", icon: GearIcon, value: "未激活", experimental: true },
    ],
  },
  {
    title: "快捷控制",
    items: [
      { id: "qs-tiles", title: "快速设置磁贴", summary: "刷新率、监视器、分辨率、电池与网速", icon: MixerHorizontalIcon, value: "6 个" },
      { id: "tasker", title: "Tasker / Locale", summary: "将刷新率与省电操作暴露为动作和条件", icon: Link2Icon, value: "已连接" },
      { id: "shortcuts", title: "应用快捷方式", summary: "从桌面直接应用常用 Profile", icon: RocketIcon, value: "4 个" },
    ],
  },
  {
    title: "应用",
    items: [
      { id: "language-theme", title: "外观与语言", summary: "动态配色、深色模式、中文与 English", icon: GlobeIcon, value: "跟随系统" },
      { id: "updates", title: "检查更新", summary: "功能兼容表与设备策略数据", icon: UpdateIcon, value: "最新" },
      { id: "about", title: "关于 Samsung Hz Ops", summary: "独立实现 · UI prototype", icon: DotsHorizontalIcon, value: "0.1.0" },
    ],
  },
];

const DETAIL_COPY: Record<string, { description: string; options: string[]; note?: string }> = {
  "per-app": { description: "为每个前台应用分配固定或自适应刷新率，并可为 Fold 外屏建立独立规则。", options: ["Chrome · 自适应 24–120 Hz", "YouTube · 固定 60 Hz", "游戏 · 最高 120 Hz"] },
  "adaptive-mod": { description: "根据触摸、滚动、输入、亮度、相机、投屏和锁屏状态动态调整刷新率。", options: ["无交互后降频：1.5 秒", "低亮度防闪烁阈值：20%", "相机与投屏时暂停切换"] },
  "keep-mode": { description: "监视系统对模式与刷新率范围的覆盖，并重新应用当前 Profile。", options: ["守护间隔：即时事件", "允许热限制覆盖", "记录最近 50 次恢复"] },
  "normal-profile": { description: "设备正常使用时的默认刷新率策略。", options: ["模式：自适应", "最低：24 Hz", "最高：120 Hz"] },
  "psm-profile": { description: "省电模式开启时重新应用高刷新率，并阻止系统切回标准模式。", options: ["PSM 下保持高刷", "最低：60 Hz", "最高：120 Hz", "保留 CPU 限速"] },
  "low-battery": { description: "低电量广播触发时使用独立 Profile，电量恢复后返回之前状态。", options: ["触发电量：15%", "最低：24 Hz", "最高：60 Hz"] },
  "screen-off": { description: "熄屏与 AOD 状态使用设备最低支持刷新率，并在亮屏后恢复。", options: ["AOD：1 Hz", "充电时仍应用", "解锁后恢复当前应用规则"] },
  "fold-profile": { description: "分别配置折叠屏主屏与外屏的模式、上下限与 PSM 行为。", options: ["主屏：24–120 Hz", "外屏：60–120 Hz", "开合后自动切换"] },
  "auto-psm": { description: "息屏后保存 low power、AOD 与刷新率快照，解锁后按原状态恢复。", options: ["息屏后延迟：10 秒", "保护 AOD 设置", "仅电量低于 40% 时启用"] },
  "resolution": { description: "在设备支持的物理分辨率之间快速切换，并可添加快速设置磁贴。", options: ["HD+ · 1544 × 720", "FHD+ · 2316 × 1080", "QHD+ · 3088 × 1440"] },
  "aod-hz": { description: "控制熄屏和常亮显示场景的刷新率，避免破坏当前日常 Profile。", options: ["AOD：1 Hz", "熄屏：最低支持档位", "充电时忽略：关闭"] },
  "hz-monitor": { description: "实时显示 Android Display 报告的刷新率，可同步至悬浮窗、通知与磁贴。", options: ["悬浮窗：关闭", "常驻通知：开启", "快速设置磁贴：已添加"] },
  "animation": { description: "统一调整窗口、过渡和动画程序时长比例。", options: ["窗口动画：0.5×", "过渡动画：0.5×", "动画程序：0.5×"] },
  "quick-doze": { description: "调整 Device Idle 常量以提前进入 Doze，并以合并写入方式保留 ROM 现有参数。", options: ["Quick Doze：关闭", "系统白名单：12 个应用", "查看空闲历史"] },
  "battery-protect": { description: "配置三星电池保护策略；Root 环境可显示额外的暂停充电与直通供电能力。", options: ["充电上限：85%", "计划：23:00–07:00", "直通供电：设备不支持"] },
  "auto-sync": { description: "息屏时保存主同步状态并关闭，解锁后仅在原本开启时恢复。", options: ["息屏时暂停", "解锁后恢复", "充电时不暂停"] },
  "sensors-off": { description: "通过 Sensors Off 快速设置磁贴和无障碍点击兼容部分 One UI 版本。", options: ["实验功能：关闭", "自动添加磁贴", "锁屏 30 秒后执行"] },
  "force-resizable": { description: "启用 Android 的强制可调整窗口设置，改善分屏与弹窗兼容。", options: ["强制可调整窗口：关闭", "重启应用后生效"] },
  "network-speed": { description: "根据 TrafficStats 差值显示实时网络速度。", options: ["上传与下载分开显示", "更新间隔：1 秒", "仅联网时显示"] },
  "developer-tile": { description: "管理开发者选项与调试相关的快速设置磁贴。", options: ["无线调试", "显示刷新率", "传感器关闭"] },
  "master-switch": { description: "暂停所有自动化服务，不删除已保存的 Profile 与应用规则。", options: ["规则引擎：运行中", "开机自动启动", "前台服务通知"] },
  "privileges": { description: "展示安全设置、Shizuku、无障碍、通知与悬浮窗权限的准备状态。", options: ["WRITE_SECURE_SETTINGS · 已授予", "Shizuku · 已连接", "无障碍服务 · 已启用", "悬浮窗 · 未授予"] },
  "device-capability": { description: "从 Android Display 与 Samsung Floating Feature 读取设备支持能力。", options: ["刷新率：24、30、60、96、120 Hz", "分辨率：HD+、FHD+、QHD+", "Fold 外屏：不适用"] },
  "xposed": { description: "Root 设备可通过 LSPosed 模块移除旧版系统的低功耗 60 Hz vote。", options: ["Root：未检测到", "LSPosed 模块：未激活", "仅支持已验证的 One UI 版本"] },
  "qs-tiles": { description: "选择要添加到系统下拉面板的快速设置磁贴。", options: ["刷新率模式", "最低/最高刷新率", "刷新率监视器", "分辨率", "电池保护", "网速指示器"] },
  "tasker": { description: "为 Tasker / Locale 提供刷新率、PSM、Doze 与电池保护动作。", options: ["设置刷新率", "切换 PSM 高刷", "应用 Profile", "Quick Doze"] },
  "shortcuts": { description: "配置从桌面图标长按菜单直接触发的快捷动作。", options: ["自适应 24–120 Hz", "固定 60 Hz", "省电高刷", "刷新率监视器"] },
  "language-theme": { description: "界面遵循 Material 3 动态配色与系统深色模式。", options: ["主题：跟随系统", "动态配色：开启", "语言：简体中文"] },
  "updates": { description: "检查应用更新与适用于不同 One UI 版本的设备策略数据。", options: ["应用版本：0.1.0", "策略数据：2026.08", "自动检查：开启"] },
  "about": { description: "Samsung Hz Ops 是刷新率控制工具的独立 UI 与实现方案。", options: ["Material 3", "Kotlin / Compose 规划", "不包含第三方专有代码"] },
};

function nearestStep(value: number) {
  return REFRESH_STEPS.reduce((nearest, step) => Math.abs(step - value) < Math.abs(nearest - value) ? step : nearest);
}

function MaterialSwitch({ enabled, label, onToggle }: { enabled: boolean; label: string; onToggle: () => void }) {
  return <button type="button" role="switch" aria-checked={enabled} aria-label={label} className={`material-switch ${enabled ? "on" : ""}`} onClick={(event) => { event.stopPropagation(); onToggle(); }}><span /></button>;
}

export default function Prototype() {
  const [tab, setTab] = useState<TabId>("control");
  const [detail, setDetail] = useState<FeatureItem | null>(null);
  const [mode, setMode] = useState<RefreshMode>("adaptive");
  const [minimumHz, setMinimumHz] = useState(24);
  const [maximumHz, setMaximumHz] = useState(120);
  const [keepHighRefresh, setKeepHighRefresh] = useState(true);
  const [menuOpen, setMenuOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const [toggleState, setToggleState] = useState<Record<string, boolean>>(() => {
    const items = [...RULE_SECTIONS, ...TOOL_SECTIONS, ...MORE_SECTIONS].flatMap((section) => section.items);
    return Object.fromEntries(items.filter((item) => item.toggle).map((item) => [item.id, item.enabled ?? false]));
  });
  const [announcement, setAnnouncement] = useState("界面预览模式");

  const currentHz = useMemo(() => mode === "standard" ? 60 : maximumHz, [maximumHz, mode]);

  const resetPreview = () => {
    setMode("adaptive"); setMinimumHz(24); setMaximumHz(120); setKeepHighRefresh(true); setMenuOpen(false);
    setAnnouncement("已恢复默认预览设置");
  };

  const openTab = (next: TabId) => { setDetail(null); setSearching(false); setTab(next); };
  const toggleFeature = (id: string, title: string) => {
    setToggleState((state) => ({ ...state, [id]: !state[id] }));
    setAnnouncement(`${title}${toggleState[id] ? "已关闭" : "已开启"}`);
  };

  const renderSections = (sections: FeatureSection[]) => (
    <>
      {sections.map((section) => (
        <section className="list-section" key={section.title}>
          <h2>{section.title}</h2>
          <div className="settings-list">
            {section.items.map((item) => {
              const Icon = item.icon;
              return (
                <div
                  className="settings-row"
                  role="button"
                  tabIndex={0}
                  key={item.id}
                  onClick={() => setDetail(item)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      setDetail(item);
                    }
                  }}
                >
                  <span className="row-icon"><Icon width="24" height="24" /></span>
                  <span className="row-copy">
                    <span className="row-title">{item.title}</span>
                    <span className="row-summary">{item.summary}{item.experimental ? <em>实验性</em> : null}</span>
                  </span>
                  {item.toggle ? <MaterialSwitch enabled={toggleState[item.id]} label={item.title} onToggle={() => toggleFeature(item.id, item.title)} /> : (
                    <span className="row-end"><span>{item.value}</span><ChevronRightIcon width="20" height="20" /></span>
                  )}
                </div>
              );
            })}
          </div>
        </section>
      ))}
    </>
  );

  const renderControl = () => (
    <main className="refresh-screen control-page" data-testid="control-page" aria-label="刷新率控制界面">
      <header className="top-app-bar">
        <h1>刷新率</h1>
        <button className="icon-button" type="button" aria-label="更多选项" aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}><DotsVerticalIcon width="24" height="24" /></button>
        {menuOpen && <div className="overflow-menu" role="menu"><button type="button" role="menuitem" onClick={resetPreview}><ResetIcon width="18" height="18" />恢复默认设置</button></div>}
      </header>
      <section className="live-readout" aria-label="当前刷新率"><p className="live-value" aria-live="polite">{currentHz} <span>Hz</span></p><p className="live-label">当前刷新率</p></section>
      <section className="settings-section" aria-labelledby="display-mode-title"><h2 id="display-mode-title">显示模式</h2><div className="segmented-control" role="radiogroup" aria-label="显示模式">{MODES.map((item) => <button key={item.id} type="button" role="radio" aria-checked={mode === item.id} className={mode === item.id ? "selected" : ""} onClick={() => { setMode(item.id); setAnnouncement(`已切换为${item.label}模式`); }}>{item.label}</button>)}</div></section>
      <section className="settings-section range-section" aria-labelledby="range-title"><h2 id="range-title">刷新率范围</h2>
        <label className="slider-setting"><span className="setting-name">最低刷新率</span><span className="setting-value">{minimumHz} Hz</span><input type="range" min="24" max="120" step="1" value={minimumHz} aria-label="最低刷新率" onChange={(event) => { const next = Math.min(nearestStep(Number(event.target.value)), maximumHz); setMinimumHz(next); setAnnouncement(`最低刷新率已设为 ${next} Hz`); }} style={{ "--range-progress": `${((minimumHz - 24) / 96) * 100}%` } as CSSProperties} /><span className="slider-dots" aria-hidden="true">{REFRESH_STEPS.map((step) => <i key={step} />)}</span></label>
        <label className="slider-setting"><span className="setting-name">最高刷新率</span><span className="setting-value">{maximumHz} Hz</span><input type="range" min="24" max="120" step="1" value={maximumHz} aria-label="最高刷新率" onChange={(event) => { const next = Math.max(nearestStep(Number(event.target.value)), minimumHz); setMaximumHz(next); setAnnouncement(`最高刷新率已设为 ${next} Hz`); }} style={{ "--range-progress": `${((maximumHz - 24) / 96) * 100}%` } as CSSProperties} /><span className="slider-dots" aria-hidden="true">{REFRESH_STEPS.map((step) => <i key={step} />)}</span></label>
      </section>
      <section className="switch-setting"><div><h2>省电模式保持高刷</h2><p>省电模式开启时仍使用最高刷新率</p></div><MaterialSwitch enabled={keepHighRefresh} label="省电模式保持高刷" onToggle={() => { setKeepHighRefresh((enabled) => !enabled); setAnnouncement(keepHighRefresh ? "省电模式高刷已关闭" : "省电模式高刷已开启"); }} /></section>
      <div className="permission-status" role="status"><CheckCircledIcon width="24" height="24" /><span>设置权限已授予</span></div>
    </main>
  );

  const renderListPage = (title: string, sections: FeatureSection[], testId: string) => (
    <main className="refresh-screen list-page" data-testid={testId} aria-label={title}>
      <header className="top-app-bar list-app-bar"><h1>{title}</h1><div className="bar-actions"><button className="icon-button" type="button" aria-label="搜索功能" onClick={() => setSearching((value) => !value)}><MagnifyingGlassIcon width="24" height="24" /></button><button className="icon-button" type="button" aria-label="更多选项"><DotsVerticalIcon width="24" height="24" /></button></div></header>
      {searching ? <div className="search-preview"><MagnifyingGlassIcon width="20" height="20" /><span>搜索功能、状态或设置</span><button type="button" onClick={() => setSearching(false)}>取消</button></div> : null}
      {renderSections(sections)}
    </main>
  );

  const renderDetail = (item: FeatureItem) => {
    const copy = DETAIL_COPY[item.id] ?? { description: item.summary, options: ["模拟配置项", "实际功能将在下一阶段接入"] };
    const Icon = item.icon;
    const detailEnabled = item.toggle ? toggleState[item.id] : true;
    return (
      <main className="refresh-screen detail-page" data-testid="detail-page" aria-label={`${item.title}设置`}>
        <header className="top-app-bar detail-app-bar"><button className="icon-button" type="button" aria-label="返回" onClick={() => setDetail(null)}><ArrowLeftIcon width="24" height="24" /></button><h1>{item.title}</h1><button className="icon-button" type="button" aria-label="更多选项"><DotsVerticalIcon width="24" height="24" /></button></header>
        <section className="detail-summary"><span className="detail-icon"><Icon width="28" height="28" /></span><div><h2>{item.title}</h2><p>{copy.description}</p></div>{item.toggle ? <MaterialSwitch enabled={detailEnabled} label={`启用${item.title}`} onToggle={() => toggleFeature(item.id, item.title)} /> : null}</section>
        <section className="list-section detail-options"><h2>配置</h2><div className="settings-list">{copy.options.map((option, index) => <button className="settings-row compact-row" type="button" key={option} onClick={() => setAnnouncement(`${item.title}配置项 ${index + 1}`)}><span className="row-copy"><span className="row-title">{option}</span><span className="row-summary">点击编辑模拟配置</span></span><ChevronRightIcon width="20" height="20" /></button>)}</div></section>
        <aside className="info-callout"><LockClosedIcon width="20" height="20" /><p>UI 预览不会读取或修改系统设置。后续实现时将按设备能力和权限自动降级。</p></aside>
      </main>
    );
  };

  return (
    <div className="prototype-shell">
      <MobileScroll key={detail ? `detail-${detail.id}` : tab} className="app-screen">
        {detail ? renderDetail(detail) : tab === "control" ? renderControl() : tab === "rules" ? renderListPage("自动化规则", RULE_SECTIONS, "rules-page") : tab === "tools" ? renderListPage("系统工具", TOOL_SECTIONS, "tools-page") : renderListPage("更多设置", MORE_SECTIONS, "more-page")}
      </MobileScroll>
      {!detail ? <nav className="bottom-nav" aria-label="主要导航">
        {([
          ["control", "控制", MixerHorizontalIcon], ["rules", "规则", ListBulletIcon], ["tools", "工具", GearIcon], ["more", "更多", DotsHorizontalIcon],
        ] as Array<[TabId, string, IconType]>).map(([id, label, Icon]) => <button type="button" key={id} aria-current={tab === id ? "page" : undefined} className={tab === id ? "active" : ""} onClick={() => openTab(id)}><span><Icon width="24" height="24" /></span><em>{label}</em></button>)}
      </nav> : null}
      <p className="preview-note" aria-live="polite">{announcement}</p>
    </div>
  );
}
