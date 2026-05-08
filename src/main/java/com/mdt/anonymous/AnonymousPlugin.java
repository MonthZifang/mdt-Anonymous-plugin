package com.mdt.anonymous;

import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class AnonymousPlugin extends Plugin {
    @Override
    public void init() {
        Log.info("MDT 匿名插件 loaded.");
        Log.info("配置目录建议: config/mods/config/mdt-anonymous-plugin");
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("anonymous-enable", "<true|false>", "开启或关闭匿名插件。", args -> {
            Log.info("MDT 匿名插件 命令占位已触发: anonymous-enable");
        });

        handler.register("anonymous-reroll", "<player>", "给某个玩家重新随机一个匿名名称。", args -> {
            Log.info("MDT 匿名插件 命令占位已触发: anonymous-reroll");
        });

        handler.register("anonymous-status", "查看匿名插件状态。", args -> {
            Log.info("MDT 匿名插件 命令占位已触发: anonymous-status");
        });

    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("anonymous", "查看当前匿名插件是否启用。", (args, player) -> {
            player.sendMessage("[accent]MDT 匿名插件[] 命令占位已触发: anonymous");
        });

    }
}
