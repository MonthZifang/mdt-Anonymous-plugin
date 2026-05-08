<div align="center">
  <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH">
    <img src="./md/logo.png" alt="YUEYUEDAO TECH Logo" width="720" />
  </a>

  <p><strong>YUEYUEDAO TECH 维护 MDT 匿名插件</strong></p>

  <p>
    <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH"><strong>查看月月岛科技详情</strong></a>
  </p>
</div>

# MDT 匿名插件

在服务器玩家进入时随机生成显示名称，但不会更改 com id，并支持其他插件或外部方式检测、开关和重置匿名状态。

## 市场固定识别文件

仓库根目录固定提供以下文件，供插件市场识别：

```text
market.plugin.json
plugin.json
```

## 依赖

- 无强依赖。

## 配置文件

首次启动后建议维护以下配置文件：

```text
config/mods/config/mdt-anonymous-plugin/anonymous-plugin.properties
```

- 匿名名称只影响显示名，不改 com id。
- 支持通过配置文件控制是否默认启用匿名。
- 支持由其他插件直接打开或关闭匿名功能。
- 支持通过名称池随机筛选一个名称分配给玩家。

## 功能说明

- 玩家进入服务器时随机获得匿名显示名。
- 支持其他插件检测匿名是否处于开启状态。
- 支持单独对某个玩家重新随机匿名名。
- 支持通过配置文件维护匿名名称池。

## 数据与写入说明

- 匿名插件不应该覆盖 com id 相关数据。
- 名称池建议单独维护，便于后续扩展多语言昵称。

## 命令

- `anonymous-enable <true|false>`：开启或关闭匿名插件。
- `anonymous-reroll <player>`：给某个玩家重新随机一个匿名名称。
- `anonymous-status`：查看匿名插件状态。
- `/anonymous`：查看当前匿名插件是否启用。

## Help 注册备注

- `help mdt-anonymous-plugin`：查看 MDT 匿名插件 的独立命令说明。
- 中文备注建议写为“匿名开关、匿名重随机、匿名状态”。

## 附带资源

- 附带 `src/main/resources/anonymous-names.txt` 作为默认匿名名称池。

## 插件入口

```text
com.mdt.anonymous.AnonymousPlugin
```

## 版本规则

- 当前插件版本：`v1`
- 当前需求市场版本：`v1`
