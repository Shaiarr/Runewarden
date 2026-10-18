package com.runewarden;
import com.google.gson.Gson;
import java.util.*;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RecoveryToolTest
{
    @Test public void rejectsForeignSettingsFutureBackupsAndDuplicateAddresses()
    {
        Gson gson=new Gson();
        for(String raw:new String[]{"{\"version\":2,\"settings\":[]}","{\"settings\":[{\"group\":\"banktags\",\"key\":\"x\",\"value\":\"y\"}]}","{\"profile\":\"someone\",\"settings\":[]}","{\"settings\":[{\"group\":\"runewarden\",\"key\":\"x\",\"value\":\"a\"},{\"group\":\"runewarden\",\"key\":\"x\",\"value\":\"b\"}]}","{\"settings\":[{\"group\":\"runewarden\",\"key\":\"x\",\"value\":\"a\",\"character\":true}]}"})
            try{RecoveryTool.parse(raw,gson);fail(raw);}catch(IllegalArgumentException expected){}
    }
    @Test public void backupKeepsGlobalAndCharacterKeysSeparate()
    {
        Gson gson=new Gson();ConfigManager config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("rsprofile.alice");
        when(config.getConfigurationKeys("runewarden.")).thenReturn(Arrays.asList("runewarden.hudOpacity"));when(config.getConfiguration("runewarden","hudOpacity")).thenReturn("85");
        when(config.getRSProfileConfigurationKeys("runewarden","rsprofile.alice","")).thenReturn(Arrays.asList("personalGoalsV1"));when(config.getConfiguration("runewarden","rsprofile.alice","personalGoalsV1")).thenReturn("{\"version\":1,\"goals\":[]}");
        RecoveryTool tool=new RecoveryTool(config,gson,new RunewardenDataStore(config,gson));RecoveryTool.Backup result=RecoveryTool.parse(tool.exportBackup(),gson);
        assertEquals("rsprofile.alice",result.profile);assertEquals(2,result.settings.size());assertFalse(result.settings.get(0).character);assertTrue(result.settings.get(1).character);
    }
    @Test public void characterMismatchCannotPreviewOrRestore()
    {
        ConfigManager config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("rsprofile.bob");Gson gson=new Gson();RecoveryTool tool=new RecoveryTool(config,gson,new RunewardenDataStore(config,gson));
        try{tool.previewImport("{\"profile\":\"rsprofile.alice\",\"settings\":[]}");fail();}catch(IllegalArgumentException expected){}
        tool.restorePreview();verify(config,never()).setConfiguration(anyString(),anyString(),anyString(),anyString());
    }
    @Test public void profileChangeBetweenPreviewAndApplyCannotWriteToEitherCharacter()
    {
        ConfigManager config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("rsprofile.alice");Gson gson=new Gson();RecoveryTool tool=new RecoveryTool(config,gson,new RunewardenDataStore(config,gson));
        tool.previewImport("{\"profile\":\"rsprofile.alice\",\"settings\":[{\"group\":\"runewarden\",\"key\":\"x\",\"value\":\"new\",\"character\":true}]}");when(config.getRSProfileKey()).thenReturn("rsprofile.bob");tool.restorePreview();verify(config,never()).setConfiguration(anyString(),anyString(),anyString(),anyString());
    }
    @Test public void previewIsReadOnlyAndRestoreMergesOnlyReviewedSettings()
    {
        ConfigManager config=mock(ConfigManager.class);when(config.getRSProfileKey()).thenReturn("rsprofile.alice");Gson gson=new Gson();RecoveryTool tool=new RecoveryTool(config,gson,new RunewardenDataStore(config,gson));
        tool.previewImport("{\"profile\":\"rsprofile.alice\",\"settings\":[{\"group\":\"runewarden\",\"key\":\"personalGoalsV1\",\"value\":\"reviewed\",\"character\":true}]}");verify(config,never()).setConfiguration(anyString(),anyString(),anyString(),anyString());
        tool.restorePreview();verify(config).setConfiguration("runewarden","rsprofile.alice","personalGoalsV1","reviewed");verify(config,never()).unsetConfiguration(anyString(),anyString(),anyString());
    }
    @Test public void undoRestoresPreviousValuesAndRemovesOnlyKeysAddedByRestore()
    {
        RunewardenDataStoreTest.Fixture f=new RunewardenDataStoreTest.Fixture();when(f.config.getRSProfileKey()).thenReturn("rsprofile.alice");
        when(f.config.getRSProfileConfigurationKeys("runewarden","rsprofile.alice","")).thenReturn(Collections.singletonList("keep"));
        f.settings.put("runewarden/rsprofile.alice/keep","before");f.settings.put("otherplugin/rsprofile.alice/untouched","private");
        RecoveryTool tool=new RecoveryTool(f.config,new Gson(),f.store);
        tool.previewImport("{\"profile\":\"rsprofile.alice\",\"settings\":[{\"group\":\"runewarden\",\"key\":\"keep\",\"value\":\"after\",\"character\":true},{\"group\":\"runewarden\",\"key\":\"added\",\"value\":\"new\",\"character\":true}]}");tool.restorePreview();
        assertEquals("after",f.settings.get("runewarden/rsprofile.alice/keep"));assertEquals("new",f.settings.get("runewarden/rsprofile.alice/added"));
        tool.undoRestore();assertEquals("before",f.settings.get("runewarden/rsprofile.alice/keep"));assertNull(f.settings.get("runewarden/rsprofile.alice/added"));assertEquals("private",f.settings.get("otherplugin/rsprofile.alice/untouched"));
    }
}
