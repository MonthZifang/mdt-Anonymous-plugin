package com.mdt.anonymous;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import mindustry.Vars;
import mindustry.game.EventType.PlayerJoin;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class AnonymousPlugin extends Plugin {
    private static final String CONFIG_DIR_NAME = "mdt-anonymous-plugin";
    private static final String CONFIG_FILE_NAME = "anonymous-plugin.properties";
    private static final String NAMES_FILE_NAME = "anonymous-names.txt";
    private static final String ASSIGNMENTS_FILE_NAME = "anonymous-assignments.properties";
    private static final Random RANDOM = new Random();

    private volatile Config config;
    private volatile File dataRoot;
    private volatile File assignmentsFile;
    private final Properties assignments = new Properties();
    private final List<String> namePool = new ArrayList<String>();

    @Override
    public void init() {
        try {
            dataRoot = resolveDataRoot();
            ensureDefaultResources();
            reloadConfig();
            Events.on(PlayerJoin.class, event -> {
                if (config.enabled && config.assignOnJoin) {
                    Timer.schedule(() -> applyAnonymousName(event.player, false), 0.2f);
                }
            });
            Log.info("MDT 匿名插件 loaded.");
            Log.info("配置目录: @", dataRoot.getAbsolutePath());
        } catch (IOException exception) {
            throw new RuntimeException("MDT 匿名插件初始化失败。", exception);
        }
    }

    public static boolean isAnonymousEnabled() {
        return true;
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("anonymous-enable", "<true|false>", "切换匿名插件开关。", args -> {
            config.enabled = Boolean.parseBoolean(args[0]);
            storeConfigFlag("anonymous.enabled", config.enabled);
            refreshAllPlayers(false);
            Log.info("匿名插件已设置为 @", config.enabled);
        });

        handler.register("anonymous-reroll", "<player>", "给某个玩家重新随机匿名名。", args -> {
            Player player = findPlayer(args[0]);
            if (player == null) {
                Log.info("未找到玩家 @", args[0]);
                return;
            }
            String uuid = player.uuid();
            assignments.remove(uuid);
            saveAssignments();
            applyAnonymousName(player, true);
            Log.info("已为 @ 重新分配匿名名。", player.plainName());
        });

        handler.register("anonymous-status", "查看匿名插件状态。", args -> {
            Log.info("enabled=@ assignOnJoin=@ preserveComid=@ poolSize=@ assigned=@",
                config.enabled, config.assignOnJoin, config.preserveComId, namePool.size(), assignments.size());
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("anonymous", "查看当前匿名插件状态。", (args, player) -> {
            String uuid = player.uuid();
            String alias = assignments.getProperty(uuid);
            player.sendMessage("[accent]匿名插件[] enabled=" + config.enabled
                + "\n[accent]alias[]: " + (alias == null ? "<none>" : alias));
        });
    }

    private void refreshAllPlayers(boolean rerollMissing) {
        for (Player player : Groups.player) {
            if (config.enabled) {
                applyAnonymousName(player, rerollMissing);
            }
        }
    }

    private void applyAnonymousName(Player player, boolean reroll) {
        if (player == null) return;
        String uuid = player.uuid();
        if (uuid == null) return;
        String alias = reroll ? createAndStoreAlias(uuid) : getOrCreateAlias(uuid);
        String display = alias;
        if (config.preserveComId) {
            String comId = resolveComId(uuid);
            if (comId != null && !comId.isEmpty()) {
                display = alias + " [lightgray][" + comId + "][]";
            }
        }
        player.name = display;
    }

    private String getOrCreateAlias(String uuid) {
        String existing = assignments.getProperty(uuid);
        if (existing != null && !existing.trim().isEmpty()) {
            return existing;
        }
        return createAndStoreAlias(uuid);
    }

    private String createAndStoreAlias(String uuid) {
        String alias = chooseAlias();
        assignments.setProperty(uuid, alias);
        saveAssignments();
        return alias;
    }

    private String chooseAlias() {
        if (namePool.isEmpty()) {
            return "匿名访客-" + nowText().replace(":", "").replace(" ", "-");
        }
        List<String> available = new ArrayList<String>(namePool);
        if (!config.allowReuseName) {
            available.removeAll(assignments.values());
            if (available.isEmpty()) {
                available.addAll(namePool);
            }
        }
        return available.get(RANDOM.nextInt(available.size()));
    }

    private Player findPlayer(String value) {
        String normalized = Strings.stripColors(value).trim();
        return Groups.player.find(player ->
            player.plainName().equalsIgnoreCase(normalized)
                || Strings.stripColors(player.name).equalsIgnoreCase(normalized)
                || player.uuid().equalsIgnoreCase(normalized)
        );
    }

    private String resolveComId(String uuid) {
        try {
            Object api = resolveSharedService("mdt.jump.api", "com.mdt.jump.JumpComIdPlugin");
            if (api == null) return null;
            Object record = api.getClass().getMethod("getOrCreate", String.class).invoke(api, uuid);
            if (record == null) return null;
            Object value = record.getClass().getMethod("getComId").invoke(record);
            return value == null ? null : value.toString();
        } catch (Exception exception) {
            Log.err("读取 comid 失败: @", exception.getMessage());
            return null;
        }
    }

    private Object resolveSharedService(String serviceKey, String legacyClassName) throws Exception {
        Object fromHub = tryResolveFromHub(serviceKey);
        if (fromHub != null) {
            return fromHub;
        }
        Class<?> pluginClass = Class.forName(legacyClassName);
        return pluginClass.getMethod("getApi").invoke(null);
    }

    private Object tryResolveFromHub(String key) {
        try {
            Class<?> hub = Class.forName("mdt.ServeMdtPlugin");
            return hub.getMethod("getSharedService", String.class).invoke(null, key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void reloadConfig() throws IOException {
        Properties properties = new Properties();
        InputStreamReader configReader = new InputStreamReader(new FileInputStream(new File(dataRoot, CONFIG_FILE_NAME)), StandardCharsets.UTF_8);
        try {
            properties.load(configReader);
        } finally {
            configReader.close();
        }

        assignmentsFile = new File(dataRoot, ASSIGNMENTS_FILE_NAME);
        if (!assignmentsFile.exists()) {
            assignmentsFile.createNewFile();
        }
        assignments.clear();
        InputStreamReader assignmentsReader = new InputStreamReader(new FileInputStream(assignmentsFile), StandardCharsets.UTF_8);
        try {
            assignments.load(assignmentsReader);
        } finally {
            assignmentsReader.close();
        }

        namePool.clear();
        BufferedReader namesReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(dataRoot, NAMES_FILE_NAME)), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = namesReader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    namePool.add(trimmed);
                }
            }
        } finally {
            namesReader.close();
        }

        config = new Config(
            readBoolean(properties, "anonymous.enabled", false),
            readBoolean(properties, "anonymous.assignOnJoin", true),
            readBoolean(properties, "anonymous.preserveComid", true),
            readBoolean(properties, "anonymous.allowReuseName", true)
        );
    }

    private void storeConfigFlag(String englishKey, boolean value) {
        try {
            File configFile = new File(dataRoot, CONFIG_FILE_NAME);
            Properties properties = new Properties();
            InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8);
            try {
                properties.load(reader);
            } finally {
                reader.close();
            }
            properties.setProperty(englishKey, Boolean.toString(value));
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(Files.newOutputStream(configFile.toPath()), StandardCharsets.UTF_8);
            try {
                properties.store(writer, "MDT Anonymous Plugin");
            } finally {
                writer.close();
            }
        } catch (IOException exception) {
            Log.err("写入匿名插件配置失败: @", exception.getMessage());
        }
    }

    private void saveAssignments() {
        try {
            java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(Files.newOutputStream(assignmentsFile.toPath()), StandardCharsets.UTF_8);
            try {
                assignments.store(writer, "MDT Anonymous Assignments");
            } finally {
                writer.close();
            }
        } catch (IOException exception) {
            throw new RuntimeException("写入匿名名分配失败。", exception);
        }
    }

    private boolean readBoolean(Properties properties, String englishKey, boolean fallback) {
        String value = properties.getProperty(englishKey);
        return value == null || value.trim().isEmpty() ? fallback : Boolean.parseBoolean(value.trim());
    }

    private void ensureDefaultResources() throws IOException {
        if (!dataRoot.exists() && !dataRoot.mkdirs() && !dataRoot.isDirectory()) {
            throw new IOException("无法创建配置目录: " + dataRoot.getAbsolutePath());
        }
        copyIfMissing(CONFIG_FILE_NAME);
        copyIfMissing(NAMES_FILE_NAME);
    }

    private void copyIfMissing(String resourceName) throws IOException {
        File target = new File(dataRoot, resourceName);
        if (target.exists()) {
            return;
        }
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (inputStream == null) {
            throw new IOException("缺少默认资源: " + resourceName);
        }
        try {
            Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            inputStream.close();
        }
    }

    private File resolveDataRoot() {
        File modsRoot = new File(Vars.dataDirectory.absolutePath(), "mods");
        return new File(new File(modsRoot, "config"), CONFIG_DIR_NAME);
    }

    private String nowText() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    }

    private static final class Config {
        private boolean enabled;
        private final boolean assignOnJoin;
        private final boolean preserveComId;
        private final boolean allowReuseName;

        private Config(boolean enabled, boolean assignOnJoin, boolean preserveComId, boolean allowReuseName) {
            this.enabled = enabled;
            this.assignOnJoin = assignOnJoin;
            this.preserveComId = preserveComId;
            this.allowReuseName = allowReuseName;
        }
    }
}
