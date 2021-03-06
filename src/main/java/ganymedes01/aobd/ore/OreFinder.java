package ganymedes01.aobd.ore;

import ganymedes01.aobd.AOBD;
import ganymedes01.aobd.items.AOBDItem;
import ganymedes01.aobd.lib.CompatType;
import ganymedes01.aobd.recipes.ModulesHandler;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

public class OreFinder {

	public static final HashMap<String, AOBDItem> itemMap = new HashMap<String, AOBDItem>();

	private static Collection<String> getMetalsWithPrefixes(String prefix1, String prefix2) {
		Set<String> ores = new LinkedHashSet<String>();
		for (String name : OreDictionary.getOreNames())
			if (name.startsWith(prefix1) && !OreDictionary.getOres(name).isEmpty()) {
				String oreName = name.substring(prefix1.length());
				for (String n : OreDictionary.getOreNames())
					if (n.startsWith(prefix2) && n.endsWith(oreName) && !OreDictionary.getOres(n).isEmpty())
						ores.add(oreName);
			}
		if (ores.contains("Aluminum") && ores.contains("Aluminium"))
			ores.remove("Aluminum");
		if (ores.contains("AluminumBrass") && ores.contains("AluminiumBrass"))
			ores.remove("AluminumBrass");

		return Collections.unmodifiableSet(ores);
	}

	public static void preInit() {
		for (String ore : getMetalsWithPrefixes("ore", "ingot"))
			Ore.newOre(ore);
	}

	public static void initColours() {
		try {
			for (Ore ore : Ore.ores)
				ore.setColour(getColour(ore.name()));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void init() {
		for (CompatType compat : CompatType.values())
			generateItems(compat, compat.prefixes());

		String[] items = AOBD.userDefinedItems.trim().split(",");
		if (items.length > 0)
			for (String prefix : items) {
				prefix = prefix.trim();
				if (!prefix.isEmpty())
					for (Ore ore : Ore.ores) {
						String name = ore.name();
						registerOre(prefix + name, new AOBDItem(prefix, ore));
					}
			}
	}

	private static void generateItems(CompatType compat, String[] prefixes) {
		if (AOBD.isCompatEnabled(compat))
			for (Ore ore : Ore.ores) {
				String name = ore.name();
				if (!ore.isCompatEnabled(compat) || ModulesHandler.isOreBlacklisted(compat, name))
					continue;

				for (String prefix : prefixes) {
					String str = prefix.trim();
					registerOre(str + name, new AOBDItem(str, ore));
				}
			}
	}

	private static void registerOre(String ore, AOBDItem item) {
		if (OreDictionary.getOres(ore).isEmpty()) {
			GameRegistry.registerItem(item, ore);
			OreDictionary.registerOre(ore, item);
			itemMap.put(ore, item);
		}
	}

	private static int getStackColour(ItemStack stack, int pass) {
		if (Loader.isModLoaded("gregtech"))
			try {
				Class<?> cls = Class.forName("gregtech.api.items.GT_MetaGenerated_Item");
				if (cls.isAssignableFrom(stack.getItem().getClass())) {
					Method m = cls.getMethod("getRGBa", ItemStack.class);
					short[] rgba = (short[]) m.invoke(stack.getItem(), stack);
					return new Color(rgba[0], rgba[1], rgba[2], rgba[3]).getRGB();
				}
			} catch (Exception e) {
			}
		return stack.getItem().getColorFromItemStack(stack, pass);
	}

	private static Color getColour(String oreName) throws IOException {
		ArrayList<ItemStack> ores = OreDictionary.getOres("ingot" + oreName);
		if (ores.isEmpty())
			return null;

		float red = 0;
		float green = 0;
		float blue = 0;
		ArrayList<Color> colours = new ArrayList<Color>();
		for (ItemStack stack : ores) {
			ResourceLocation res = getIconResource(stack);
			if (res == null)
				continue;
			BufferedImage texture = ImageIO.read(Minecraft.getMinecraft().getResourceManager().getResource(res).getInputStream());
			Color texColour = getAverageColour(texture);
			colours.add(texColour);
			for (int pass = 0; pass < stack.getItem().getRenderPasses(stack.getItemDamage()); pass++) {
				int c = getStackColour(stack, pass);
				if (c != 0xFFFFFF) {
					colours.add(new Color(c));
					colours.remove(texColour);
				}
			}
		}

		for (Color c : colours) {
			red += c.getRed();
			green += c.getGreen();
			blue += c.getBlue();
		}
		float count = colours.size();
		return new Color((int) (red / count), (int) (green / count), (int) (blue / count)).brighter();
	}

	private static Color getAverageColour(BufferedImage image) {
		float red = 0;
		float green = 0;
		float blue = 0;
		float count = 0;
		for (int i = 0; i < image.getWidth(); i++)
			for (int j = 0; j < image.getHeight(); j++) {
				Color c = new Color(image.getRGB(i, j));
				if (c.getAlpha() <= 10 || c.getRed() <= 10 && c.getGreen() <= 10 && c.getBlue() <= 10)
					continue;
				red += c.getRed();
				green += c.getGreen();
				blue += c.getBlue();
				count++;
			}

		return new Color((int) (red / count), (int) (green / count), (int) (blue / count));
	}

	private static String getIconName(ItemStack stack) {
		IIcon icon = stack.getItem().getIconFromDamage(stack.getItemDamage());
		if (icon != null)
			return icon.getIconName();
		return null;
	}

	private static ResourceLocation getIconResource(ItemStack stack) {
		String iconName = getIconName(stack);
		if (iconName == null)
			return null;

		String string = "minecraft";

		int colonIndex = iconName.indexOf(58);
		if (colonIndex >= 0) {
			if (colonIndex > 1)
				string = iconName.substring(0, colonIndex);

			iconName = iconName.substring(colonIndex + 1, iconName.length());
		}

		string = string.toLowerCase();
		iconName = "textures/items/" + iconName + ".png";
		return new ResourceLocation(string, iconName);
	}
}