package com.valerinsmp.valtars.config;

import com.valerinsmp.valtars.domain.AltarDefinition;
import com.valerinsmp.valtars.domain.BlockKey;
import com.valerinsmp.valtars.domain.ItemRequirement;
import com.valerinsmp.valtars.domain.ItemSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AltarRepositoryTest {
    @TempDir Path directory;

    @Test
    void loadsNexoOnlyRequirementsWhenVanillaListIsEmpty() throws IOException {
        Path file = directory.resolve("altars.yml");
        Files.writeString(file, """
                altars:
                  nexo_only:
                    boss-name: CrystalBoss
                    center: world,0,64,0
                    central-item-nexo-id: crystal_key
                    required-items: []
                    required-items-nexo:
                      ruby: 16
                    pedestal-locations:
                      - world,1,64,0
                """);

        AltarDefinition altar = new AltarRepository(file).load().get("nexo_only");

        assertNotNull(altar);
        assertTrue(altar.activationItem().isNexo());
        assertEquals("crystal_key", altar.activationItem().nexoId());
        assertEquals(1, altar.activationAmount());
        assertEquals(1, altar.requirements().size());
        assertTrue(altar.requirements().getFirst().item().isNexo());
        assertEquals("ruby", altar.requirements().getFirst().item().nexoId());
        assertEquals(16, altar.requirements().getFirst().amount());
        assertEquals(1, altar.requiredPedestalCount());
    }

    @Test
    void rejectsMoreRequirementSlotsThanPedestalsEvenForTheSameItem() throws IOException {
        Path file = directory.resolve("invalid-altars.yml");
        Files.writeString(file, """
                altars:
                  invalid:
                    center: world,0,64,0
                    central-item-nexo-id: crystal_key
                    required-items:
                      - nexo-id: ruby
                        amount: 16
                      - nexo-id: ruby
                        amount: 16
                    pedestal-locations:
                      - world,1,64,0
                """);

        assertThrows(IllegalArgumentException.class, () -> new AltarRepository(file).load());
    }

    @Test
    void preservesOrderedDuplicateNexoSlotsAcrossSaveAndReload() throws IOException {
        Path file = directory.resolve("ordered-altars.yml");
        Files.createFile(file);
        List<BlockKey> pedestals = List.of(
                new BlockKey("world", 1, 64, 0), new BlockKey("world", 2, 64, 0),
                new BlockKey("world", 3, 64, 0), new BlockKey("world", 4, 64, 0),
                new BlockKey("world", 5, 64, 0), new BlockKey("world", 6, 64, 0),
                new BlockKey("world", 7, 64, 0), new BlockKey("world", 8, 64, 0));
        AltarDefinition original = new AltarDefinition("basalt", "BasaltGuardian",
                new BlockKey("world", 0, 64, 0), ItemSpec.nexo("molten_heart"), 3,
                List.of(
                        new ItemRequirement(ItemSpec.nexo("crimson_fragment"), 16),
                        new ItemRequirement(ItemSpec.nexo("crimson_fragment"), 16),
                        new ItemRequirement(ItemSpec.nexo("infernal_ash"), 16),
                        new ItemRequirement(ItemSpec.nexo("infernal_ash"), 16),
                        new ItemRequirement(ItemSpec.nexo("infernal_essence"), 8),
                        new ItemRequirement(ItemSpec.nexo("infernal_essence"), 8),
                        new ItemRequirement(ItemSpec.nexo("dark_crystal"), 4),
                        new ItemRequirement(ItemSpec.nexo("dark_crystal"), 4)),
                pedestals);

        AltarRepository repository = new AltarRepository(file);
        repository.save(original);
        AltarDefinition loaded = repository.load().get("basalt");

        assertNotNull(loaded);
        assertEquals(3, loaded.activationAmount());
        assertEquals(List.of(16, 16, 16, 16, 8, 8, 4, 4),
                loaded.requirements().stream().map(ItemRequirement::amount).toList());
        assertEquals(List.of("crimson_fragment", "crimson_fragment", "infernal_ash", "infernal_ash",
                        "infernal_essence", "infernal_essence", "dark_crystal", "dark_crystal"),
                loaded.requirements().stream().map(requirement -> requirement.item().nexoId()).toList());
        assertFalse(Files.readString(file).contains("required-items-nexo:"));
        assertTrue(Files.readString(file).contains("central-item-amount: 3"));
    }
}
