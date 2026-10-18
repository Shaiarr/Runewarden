/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises the actual pinned ConfigManager validator and storage, not a permissive config mock. */
public class GuideProgressConfigTest
{
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void everyBundledGuidePersistsBothFieldsThroughPinnedRuneLiteConfiguration() throws Exception
    {
        ConfigManager configs = realConfigManager();
        try
        {
            configs.setRSProfileConfiguration("runewarden", "guide:quest:Cook's Assistant", 1);
            fail("The pinned client must reject the old colon-containing key");
        }
        catch (IllegalArgumentException expected) { }

        Set<String> unique = new HashSet<>();
        for (GuideCatalog.Entry guide : new GuideCatalog(new Gson()).search("", ""))
        {
            String position = GuideProgress.positionKey(guide.id);
            String completed = GuideProgress.completedKey(guide.id);
            assertTrue(position, position.matches("[A-Za-z0-9_-]+"));
            assertTrue(completed, completed.matches("[A-Za-z0-9_-]+"));
            assertTrue("Different guides and fields must never share a key", unique.add(position));
            assertTrue("Different guides and fields must never share a key", unique.add(completed));
            configs.setRSProfileConfiguration("runewarden", position, 4);
            configs.setRSProfileConfiguration("runewarden", completed, "section:first\nsection:second");
            assertEquals(Integer.valueOf(4), configs.getRSProfileConfiguration("runewarden", position, Integer.class));
            assertEquals("section:first\nsection:second", configs.getRSProfileConfiguration("runewarden", completed, String.class));
        }
        assertEquals(783 * 2, unique.size());
    }

    @Test public void encodedIdsKeepPunctuationAndUnicodeDistinct()
    {
        assertNotEquals(GuideProgress.positionKey("quest:a:b"), GuideProgress.positionKey("quest:a_b"));
        assertNotEquals(GuideProgress.positionKey("quest:a/b"), GuideProgress.positionKey("quest:a+b"));
        String key = GuideProgress.positionKey("quest:$Éléphant's / 问题");
        assertTrue(key.matches("[A-Za-z0-9_-]+"));
        assertEquals(key, GuideProgress.positionKey("quest:$Éléphant's / 问题"));
        assertNotEquals(key, GuideProgress.completedKey("quest:$Éléphant's / 问题"));
    }

    private ConfigManager realConfigManager() throws Exception
    {
        Constructor<?> constructor = ConfigManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Class<?>[] parameters = constructor.getParameterTypes();
        Object[] arguments = new Object[parameters.length];
        for (int index = 0; index < parameters.length; index++)
        {
            Class<?> type = parameters[index];
            arguments[index] = type == String.class ? null : type == Gson.class ? new Gson() : mock(type);
        }
        ConfigManager configs = (ConfigManager) constructor.newInstance(arguments);
        Class<?> dataClass = Class.forName("net.runelite.client.config.ConfigData");
        Constructor<?> dataConstructor = dataClass.getDeclaredConstructor(File.class);
        dataConstructor.setAccessible(true);
        Object data = dataConstructor.newInstance(temporary.newFile("guide-progress.properties"));
        set(configs, "rsProfileConfigProfile", data);
        set(configs, "rsProfileKey", "rsprofile.test-character");
        return configs;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception
    {
        Field field = ConfigManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
