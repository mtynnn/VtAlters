package com.valerinsmp.valtars.domain;

import com.valerinsmp.valtars.BukkitTest;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AltarDefinitionTest extends BukkitTest {
    @Test
    void duplicateRequirementsRemainSeparatePedestalSlots() {
        AltarDefinition altar = new AltarDefinition("sun", "Boss", new BlockKey("world", 0, 64, 0),
                ItemSpec.vanilla(new ItemStack(Material.DIAMOND)),
                List.of(
                        new ItemRequirement(ItemSpec.vanilla(new ItemStack(Material.STONE)), 1),
                        new ItemRequirement(ItemSpec.vanilla(new ItemStack(Material.STONE, 32)), 2),
                        new ItemRequirement(ItemSpec.nexo("ruby"), 1),
                        new ItemRequirement(ItemSpec.nexo("ruby"), 2)
                ),
                List.of(
                        new BlockKey("world", 1, 64, 0), new BlockKey("world", 2, 64, 0),
                        new BlockKey("world", 3, 64, 0), new BlockKey("world", 4, 64, 0),
                        new BlockKey("world", 5, 64, 0), new BlockKey("world", 6, 64, 0)
                ));

        assertEquals(4, altar.requirements().size());
        assertEquals(List.of(1, 2, 1, 2), altar.requirements().stream().map(ItemRequirement::amount).toList());
        assertEquals(6, altar.requiredItemCount());
        assertEquals(4, altar.requiredPedestalCount());
        assertEquals(1, altar.activationAmount());
        assertEquals(4, altar.withActivationItem(altar.activationItem(), 4).withBoss("Other").activationAmount());
    }
}
