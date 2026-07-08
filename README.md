# 暴君

NeoForge `1.21.1` Boss 模组。当前内部命名空间为 `tyrant`，玩家可见名称为“暴君”。

## 环境

- Minecraft `1.21.1`
- NeoForge `21.1.65`
- Java `21`
- GeckoLib `4.6.6`

## 常用命令

```powershell
.\gradlew.bat runClient
.\gradlew.bat build
.\gradlew.bat --no-configuration-cache compileJava
```

如果当前终端没有配置 Java，可以临时使用仓库旁的 JDK 21：

```powershell
$env:JAVA_HOME='D:\MinecraftModWorkspace\jdks\microsoft-jdk-21\jdk-21.0.11+10'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## 配置文件

首次启动后会生成 `config/tyrant-common.toml`。整合包可以直接分发这份配置，用来调整：

- `boss.max_health`：暴君最大生命值。
- `boss.attack_damage`：暴君基础攻击属性。
- `boss.armor`：暴君护甲属性。
- `boss.skill_damage_multiplier`：暴君技能伤害倍率。
- `royal_decree.radius`：王令影响半径。
- `royal_decree.initial_cooldown_ticks`：首次王令冷却。
- `royal_decree.cooldown_ticks`：普通阶段王令冷却。
- `royal_decree.phase_two_cooldown_ticks`：二阶段王令冷却。
- `royal_decree.penalty_multiplier`：王令违令累积和惩罚伤害倍率。
- `royal_decree.execution_mark_threshold`：触发裁决需要的王令裁定层数。
- `client_feedback.fear_screen_intensity`：恐惧遮罩、雾色和压迫镜头强度。
- `client_feedback.screen_shake_intensity`：暴君震屏强度。

## 主要文件

- `src/main/java/com/eddyon/tyrant/TyrantMod.java`：模组入口。
- `src/main/java/com/eddyon/tyrant/common/entity/TyrantEntity.java`：暴君实体、技能时序、BossBar、死亡演出和战斗状态。
- `src/main/java/com/eddyon/tyrant/common/entity/TyrantDamageHelper.java`：技能命中区域、伤害、击退和命中附加效果。
- `src/main/java/com/eddyon/tyrant/common/entity/TyrantTerrainHelper.java`：技能落点、方块状态采样和地形破坏。
- `src/main/java/com/eddyon/tyrant/common/entity/tyrant`：暴君战斗决策和王令系统。
- `src/main/resources/META-INF/neoforge.mods.toml`：模组元信息。
- `src/main/resources/assets/tyrant`：模型、动画、贴图、粒子和语言资源。
- `src/main/resources/data/tyrant`：生成、生物群系标签和战利品表数据。
