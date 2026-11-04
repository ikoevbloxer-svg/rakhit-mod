package com.rakhit.kingdom;

import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RakhitMod.MODID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, RakhitMod.MODID);

    /** Текстура слоя: assets/rakhit/textures/models/armor/crown_layer_1.png */
    public static final Holder<ArmorMaterial> CROWN_MATERIAL = ARMOR_MATERIALS.register("crown", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 0);
                m.put(ArmorItem.Type.LEGGINGS, 0);
                m.put(ArmorItem.Type.CHESTPLATE, 0);
                m.put(ArmorItem.Type.HELMET, 3);
                m.put(ArmorItem.Type.BODY, 0);
            }),
            25,
            SoundEvents.ARMOR_EQUIP_GOLD,
            () -> Ingredient.of(Items.GOLD_INGOT),
            List.of(new ArmorMaterial.Layer(RakhitMod.id("crown"))),
            0.0f, 0.0f));

    public static final DeferredItem<ArmorItem> CROWN = ITEMS.register("crown",
            () -> new ArmorItem(CROWN_MATERIAL, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(60))
                            .rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<WhipItem> WHIP = ITEMS.register("whip",
            () -> new WhipItem(new Item.Properties().durability(500).rarity(Rarity.UNCOMMON)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(RakhitMod.id("whip_reach"), 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                            .build())));

    public static final DeferredItem<BlockItem> BANK_TELLER_ITEM = ITEMS.registerSimpleBlockItem(ModBlocks.BANK_TELLER);

    private ModItems() {}
}
