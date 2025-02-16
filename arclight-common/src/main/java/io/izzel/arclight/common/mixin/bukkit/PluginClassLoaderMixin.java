package io.izzel.arclight.common.mixin.bukkit;

import com.google.common.io.ByteStreams;
import cpw.mods.modlauncher.EnumerationHelper;
import io.izzel.arclight.common.asm.SwitchTableFixer;
import io.izzel.arclight.common.bridge.bukkit.JavaPluginLoaderBridge;
import io.izzel.arclight.common.mod.util.remapper.ArclightRemapper;
import io.izzel.arclight.common.mod.util.remapper.ClassLoaderRemapper;
import io.izzel.arclight.common.mod.util.remapper.RemappingClassLoader;
import io.izzel.tools.product.Product2;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.jar.Manifest;

@Mixin(targets = "org.bukkit.plugin.java.PluginClassLoader", remap = false)
public class PluginClassLoaderMixin extends URLClassLoader implements RemappingClassLoader {

    // @formatter:off
    @Shadow @Final private Map<String, Class<?>> classes;
    @Shadow @Final private JavaPluginLoader loader;
    @Shadow @Final private PluginDescriptionFile description;
    @Shadow @Final private Manifest manifest;
    @Shadow @Final private URL url;
    // @formatter:on

    private ClassLoaderRemapper remapper;

    @Override
    public ClassLoaderRemapper getRemapper() {
        if (remapper == null) {
            remapper = ArclightRemapper.createClassLoaderRemapper(this);
        }
        return remapper;
    }

    public PluginClassLoaderMixin(URL[] urls) {
        super(urls);
    }

    /**
     * @author IzzelAliz
     * @reason
     */
    @Overwrite
    @Override
    public URL getResource(String name) {
        Objects.requireNonNull(name);
        URL url = findResource(name);
        if (url == null) {
            if (getParent() != null) {
                url = getParent().getResource(name);
            }
        }
        return url;
    }

    /**
     * @author IzzelAliz
     * @reason
     */
    @Overwrite
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Objects.requireNonNull(name);
        @SuppressWarnings("unchecked")
        Enumeration<URL>[] tmp = (Enumeration<URL>[]) new Enumeration<?>[2];
        if (getParent()!= null) {
            tmp[1] = getParent().getResources(name);
        }
        tmp[0] = findResources(name);
        return EnumerationHelper.merge(tmp[0], tmp[1]);
    }

    /**
     * @author IzzelAliz
     * @reason
     */
    @Overwrite
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.startsWith("org.bukkit.") || name.startsWith("net.minecraft.")) {
            throw new ClassNotFoundException(name);
        }
        Class<?> result = classes.get(name);

        if (result == null) {
            String path = name.replace('.', '/').concat(".class");
            URL url = this.findResource(path);

            if (url != null) {

                URLConnection connection;
                Callable<byte[]> byteSource;
                try {
                    connection = url.openConnection();
                    connection.connect();
                    byteSource = () -> {
                        try (InputStream is = connection.getInputStream()) {
                            byte[] classBytes = ByteStreams.toByteArray(is);
                            classBytes = SwitchTableFixer.INSTANCE.processClass(classBytes);
                            classBytes = Bukkit.getUnsafe().processClass(description, path, classBytes);
                            return classBytes;
                        }
                    };
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }

                Product2<byte[], CodeSource> classBytes = this.getRemapper().remapClass(name, byteSource, connection);

                int dot = name.lastIndexOf('.');
                if (dot != -1) {
                    String pkgName = name.substring(0, dot);
                    if (getPackage(pkgName) == null) {
                        try {
                            if (manifest != null) {
                                definePackage(pkgName, manifest, this.url);
                            } else {
                                definePackage(pkgName, null, null, null, null, null, null, null);
                            }
                        } catch (IllegalArgumentException ex) {
                            if (getPackage(pkgName) == null) {
                                throw new IllegalStateException("Cannot find package " + pkgName);
                            }
                        }
                    }
                }

                result = defineClass(name, classBytes._1, 0, classBytes._1.length, classBytes._2);
            }

            if (result == null) {
                result = super.findClass(name);
            }

            ((JavaPluginLoaderBridge) (Object) loader).bridge$setClass(name, result);
            classes.put(name, result);
        }

        return result;
    }
}
