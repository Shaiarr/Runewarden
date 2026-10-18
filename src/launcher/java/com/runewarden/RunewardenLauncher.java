/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JOptionPane;
import net.runelite.client.RuneLite;

/** Standard native RuneLite bootstrap; this source set is excluded from the Hub JAR. */
public final class RunewardenLauncher
{
    private RunewardenLauncher() { }

    public static void main(String[] args)
    {
        try
        {
            if (Runtime.version().feature() < 17)
                throw new IllegalStateException("Java 17 or newer is required. Use the supplied runtime or install Java 17.");
            RunewardenBuiltinPlugins.register();
            if (Arrays.asList(args).contains("--verify-package"))
            {
                verifyPackage();
                return;
            }
            // The native client owns its normal configuration, account flow and plugin lifecycle.
            RuneLite.main(args);
        }
        catch (Throwable error)
        {
            error.printStackTrace();
            if (!GraphicsEnvironment.isHeadless())
                JOptionPane.showMessageDialog(null, "RuneLite could not start.\n\n" + error.getClass().getSimpleName()
                    + ": " + String.valueOf(error.getMessage())
                    + "\n\nRun Verify-Runewarden.bat to inspect the package.", "Runewarden", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private static void verifyPackage() throws Exception
    {
        Path root = Path.of("").toAbsolutePath().normalize();
        JsonObject manifest;
        try (Reader reader = Files.newBufferedReader(root.resolve("package-manifest.json"), StandardCharsets.UTF_8))
        { manifest = new Gson().fromJson(reader, JsonObject.class); }
        if (manifest == null || !"runewarden-hud-test-package".equals(manifest.get("format").getAsString())
            || !RunewardenVersion.VERSION.equals(manifest.get("version").getAsString())
            || manifest.get("shortestPathBundled").getAsBoolean() != RunewardenBuiltinPlugins.hasShortestPath())
            throw new IOException("Package metadata and bootstrap do not match.");
        Set<Path> declaredLibraries = new HashSet<>();
        Set<String> declaredFiles = new HashSet<>();
        for (JsonElement value : manifest.getAsJsonArray("files"))
        {
            JsonObject entry = value.getAsJsonObject();
            String name = entry.get("name").getAsString();
            Path file = root.resolve(name).normalize();
            if (name.contains("\\") || !file.startsWith(root) || !declaredFiles.add(name)
                || !Files.isRegularFile(file) || Files.size(file) != entry.get("bytes").getAsLong()
                || !digest(file).equals(entry.get("sha256").getAsString()))
                throw new IOException("Package file differs from its manifest: " + name);
            if (name.startsWith("lib/") && name.endsWith(".jar")) declaredLibraries.add(file);
        }
        try (java.util.stream.Stream<Path> libraries = Files.list(root.resolve("lib")))
        {
            Set<Path> actual = new HashSet<>();
            libraries.filter(path -> path.getFileName().toString().endsWith(".jar")).forEach(actual::add);
            if (!actual.equals(declaredLibraries)) throw new IOException("Unexpected library in package. Extract into a new folder.");
        }
        requireResource("/client.class");
        requireResource("/net/runelite/api/Client.class");
        requireResource("/net/runelite/client/RuneLite.class");
        requireResource("/com/runewarden/RunewardenPlugin.class");
        requireResource("/com/runewarden/RunewardenConfig.class");
        for (String unwanted : new String[]{"/org/junit/Test.class", "/org/mockito/Mockito.class",
            "/com/questhelper/QuestHelperPlugin.class", "/com/runewarden/GuideCatalog.class",
            "/com/runewarden/guides.json", "/com/runewarden/marker-packs.json"})
            if (RunewardenLauncher.class.getResource(unwanted) != null)
                throw new IOException("Unexpected test, companion or full-guide content: " + unwanted);
        RunewardenBuiltinPlugins.verifyResources();
        if (manifest.get("runtimeBundled").getAsBoolean()
            && (!Files.isRegularFile(root.resolve("runtime/bin/javaw.exe")) || !Files.isDirectory(root.resolve("runtime/legal"))))
            throw new IOException("Bundled Windows runtime or legal notices are incomplete.");
        System.out.println("Runewarden HUD " + RunewardenVersion.VERSION + " package verified: native RuneLite "
            + manifest.get("runeLiteVersion").getAsString() + ", " + declaredFiles.size() + " files; Shortest Path "
            + (RunewardenBuiltinPlugins.hasShortestPath() ? "included" : "not included")
            + ". This check does not launch a game or establish live acceptance.");
    }

    private static void requireResource(String name) throws IOException
    {
        if (RunewardenLauncher.class.getResource(name) == null) throw new IOException("Missing native/package resource: " + name);
    }

    private static String digest(Path file) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file))
        {
            byte[] buffer = new byte[65536]; int count;
            while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder text = new StringBuilder(64);
        for (byte value : digest.digest()) text.append(String.format("%02x", value & 0xff));
        return text.toString();
    }
}
