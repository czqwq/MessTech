package com.MessTech.common.item;

import cpw.mods.fml.common.registry.GameRegistry;

public final class MTItems {

    public static final MECreativeOreDisk meCreativeOreDisk = new MECreativeOreDisk();
    public static final MTNACComponentItem nacComponentItem = new MTNACComponentItem();
    public static final ItemMessFood messFood = new ItemMessFood();

    private MTItems() {}

    public static void registerItems() {
        GameRegistry.registerItem(meCreativeOreDisk, "MECreativeOreDisk");
        GameRegistry.registerItem(nacComponentItem, "MTNACComponentItem");
        GameRegistry.registerItem(messFood, "MessFood");
    }
}
