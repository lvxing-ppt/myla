package com.mlms.capl.bootstrap;

import com.mlms.oes.gateway.core.spi.InstrumentDriver;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * 驱动插件加载器 — 从 {@code driver-dir} 目录扫描 JAR 包并发现 {@link InstrumentDriver} 实现。
 * <p>
 * 每个 JAR 使用独立的 {@link URLClassLoader}，实现插件隔离。
 * 后续可在此基础上实现单驱动热升级（销毁旧 ClassLoader → 创建新的）。
 * </p>
 *
 * <h3>使用方式：</h3>
 * <pre>{@code
 * // 加载 plugins 目录下所有驱动 JAR
 * Map<String, InstrumentDriver> plugins = DriverPluginLoader.loadFrom(new File("./drivers"));
 * // 合并到内置驱动缓存
 * allDrivers.putAll(plugins);
 * }</pre>
 *
 * <h3>JAR 包要求：</h3>
 * <ul>
 *   <li>包含 {@code META-INF/services/com.mlms.oes.gateway.core.spi.InstrumentDriver} SPI 文件</li>
 *   <li>实现类有无参构造函数</li>
 *   <li>依赖的共享接口（InstrumentDriver, UnifiedResult 等）由父 ClassLoader 提供</li>
 * </ul>
 */
@Slf4j
public final class DriverPluginLoader {

    private DriverPluginLoader() {}

    /**
     * 扫描目录下所有 {@code *.jar} 文件，加载其中的 {@link InstrumentDriver} 实现。
     *
     * @param driverDir 驱动目录，不存在或为空时返回空 Map
     * @return driverId → InstrumentDriver 实例的映射
     */
    public static Map<String, InstrumentDriver> loadFrom(File driverDir) {
        if (driverDir == null || !driverDir.isDirectory()) {
            log.debug("Driver dir {} does not exist or is not a directory", driverDir);
            return Collections.emptyMap();
        }

        File[] jars = driverDir.listFiles(f -> f.getName().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            log.debug("No JAR files found in {}", driverDir.getAbsolutePath());
            return Collections.emptyMap();
        }

        Map<String, InstrumentDriver> result = new LinkedHashMap<>();
        for (File jar : jars) {
            Map<String, InstrumentDriver> drivers = loadFromJar(jar);
            for (Map.Entry<String, InstrumentDriver> entry : drivers.entrySet()) {
                if (result.containsKey(entry.getKey())) {
                    log.warn("Driver '{}' from {} overrides previously loaded driver (check for duplicate driverId)",
                            entry.getKey(), jar.getName());
                }
                result.put(entry.getKey(), entry.getValue());
            }
        }
        log.info("Loaded {} driver(s) from plugin dir {}: {}",
                result.size(), driverDir.getAbsolutePath(), result.keySet());
        return result;
    }

    // ==================== Private ====================

    private static Map<String, InstrumentDriver> loadFromJar(File jar) {
        log.debug("Scanning driver JAR: {}", jar.getAbsolutePath());
        try {
            URL jarUrl = jar.toURI().toURL();
            // 父 ClassLoader = 当前应用的 ClassLoader，插件可访问共享接口
            URLClassLoader cl = new URLClassLoader(
                    new URL[]{jarUrl},
                    InstrumentDriver.class.getClassLoader());

            // 用自定义 ClassLoader 扫描 SPI
            ServiceLoader<InstrumentDriver> loader =
                    ServiceLoader.load(InstrumentDriver.class, cl);

            Map<String, InstrumentDriver> drivers = new LinkedHashMap<>();
            for (InstrumentDriver driver : loader) {
                String id = driver.getDriverId();
                if (id == null || id.isBlank()) {
                    log.warn("Driver in {} returned null/blank driverId, skipped", jar.getName());
                    continue;
                }
                drivers.put(id, driver);
                log.info("Plugin driver discovered: id={}, class={}, jar={}",
                        id, driver.getClass().getName(), jar.getName());
            }
            return drivers;
        } catch (MalformedURLException e) {
            log.error("Invalid JAR path: {}", jar.getAbsolutePath(), e);
            return Collections.emptyMap();
        }
    }
}
